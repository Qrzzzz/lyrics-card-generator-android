package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.EditorAutosaveSession
import com.qrzzzz.lyricscard.EditorMessageResolver
import com.qrzzzz.lyricscard.EditorSessionRegistry
import com.qrzzzz.lyricscard.NeteaseClient
import com.qrzzzz.lyricscard.ProjectAssets
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.RendererOperations
import com.qrzzzz.lyricscard.data.NeteaseSongSearchResult
import com.qrzzzz.lyricscard.data.ResolvedNeteaseSong
import com.qrzzzz.lyricscard.model.InvalidRenderSpecException
import com.qrzzzz.lyricscard.model.LyricTextLimits
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecViolation
import com.qrzzzz.lyricscard.model.SongSource
import com.qrzzzz.lyricscard.model.requireValid
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AutosaveStatus {
    SAVED,
    SAVING,
    FAILED,
}

data class EditorDrafts(
    val searchQuery: String = "",
    val linkInput: String = "",
    val projectName: String = "",
)

data class NeteaseLookupUiState(
    val results: List<NeteaseSongSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isResolving: Boolean = false,
    val message: String = "可按歌曲名搜索，或贴入网易云分享链接",
)

data class EditorUiState(
    val projectId: String,
    val currentProject: Project? = null,
    val isLoading: Boolean = true,
    val projectUnavailable: Boolean = false,
    val autosaveStatus: AutosaveStatus = AutosaveStatus.SAVED,
    val errorMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedStep: Int = 0,
    val drafts: EditorDrafts = EditorDrafts(),
    val netease: NeteaseLookupUiState = NeteaseLookupUiState(),
    val isImportingCover: Boolean = false,
    val isExtractingPalette: Boolean = false,
    val paletteError: String? = null,
    val isLeaving: Boolean = false,
)

class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val projects: ProjectStore,
    private val projectAssets: ProjectAssets,
    private val neteaseClient: NeteaseClient,
    private val renderer: RendererOperations,
    private val messages: EditorMessageResolver,
    private val sessions: EditorSessionRegistry,
) : ViewModel(), EditorAutosaveSession {
    private val projectId: String = checkNotNull(savedStateHandle[PROJECT_ID_KEY]) {
        "Editor route requires projectId"
    }
    private val hadSavedProjectName = savedStateHandle.contains(PROJECT_NAME_KEY)
    private val _uiState = MutableStateFlow(
        EditorUiState(
            projectId = projectId,
            selectedStep = savedStateHandle.get<Int>(STEP_KEY)?.coerceIn(0, LAST_EDITOR_STEP) ?: 0,
            drafts = EditorDrafts(
                searchQuery = savedStateHandle[SEARCH_QUERY_KEY] ?: "",
                linkInput = savedStateHandle[LINK_INPUT_KEY] ?: "",
                projectName = savedStateHandle[PROJECT_NAME_KEY] ?: "",
            ),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val saveMutex = Mutex()
    private var autosaveJob: Job? = null
    private var coverImportJob: Job? = null
    private var neteaseSearchJob: Job? = null
    private var neteaseResolveJob: Job? = null
    private var paletteJob: Job? = null
    private val undoStack = ArrayDeque<RenderSpec>()
    private val redoStack = ArrayDeque<RenderSpec>()
    private var editRevision = 0L
    private var savedRevision = 0L
    private val navigationInFlight = AtomicBoolean(false)
    private val navigationCommitted = AtomicBoolean(false)

    init {
        savedStateHandle[PROJECT_ID_KEY] = projectId
        sessions.register(this)
        viewModelScope.launch { loadProject() }
    }

    fun selectStep(step: Int) {
        if (_uiState.value.isLeaving) return
        val value = step.coerceIn(0, LAST_EDITOR_STEP)
        savedStateHandle[STEP_KEY] = value
        _uiState.update { it.copy(selectedStep = value) }
    }

    fun updateSearchQuery(value: String) {
        val next = value.take(MAX_SEARCH_QUERY_LENGTH)
        savedStateHandle[SEARCH_QUERY_KEY] = next
        _uiState.update { it.copy(drafts = it.drafts.copy(searchQuery = next)) }
    }

    fun updateLinkInput(value: String) {
        val next = value.take(MAX_LINK_INPUT_LENGTH)
        savedStateHandle[LINK_INPUT_KEY] = next
        _uiState.update { it.copy(drafts = it.drafts.copy(linkInput = next)) }
    }

    fun updateProjectName(value: String) {
        if (_uiState.value.isLeaving) return
        val next = value.take(MAX_PROJECT_NAME_LENGTH)
        savedStateHandle[PROJECT_NAME_KEY] = next
        _uiState.update { it.copy(drafts = it.drafts.copy(projectName = next)) }
        if (next.isBlank()) return
        val project = _uiState.value.currentProject ?: return
        if (project.name == next) return
        _uiState.update { it.copy(currentProject = project.copy(name = next)) }
        markEdited()
        scheduleAutosave()
    }

    fun updateSpec(transform: (RenderSpec) -> RenderSpec) {
        if (_uiState.value.isLeaving) return
        val project = _uiState.value.currentProject ?: return
        val updated = runCatching {
            val candidate = transform(project.spec)
            project.copy(spec = candidate.requireValid())
        }.getOrElse { cause ->
            setError(lineLimitMessage(cause) ?: cause.message ?: "设置无效")
            return
        }
        if (updated.spec == project.spec) return
        undoStack.addLast(project.spec)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update {
            it.copy(
                currentProject = updated,
                errorMessage = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false,
            )
        }
        markEdited()
        scheduleAutosave()
    }

    fun undo() {
        if (_uiState.value.isLeaving) return
        val project = _uiState.value.currentProject ?: return
        val previous = undoStack.pollLast() ?: return
        redoStack.addLast(project.spec)
        _uiState.update {
            it.copy(
                currentProject = project.copy(spec = previous),
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
            )
        }
        markEdited()
        scheduleAutosave()
    }

    fun redo() {
        if (_uiState.value.isLeaving) return
        val project = _uiState.value.currentProject ?: return
        val next = redoStack.pollLast() ?: return
        undoStack.addLast(project.spec)
        _uiState.update {
            it.copy(
                currentProject = project.copy(spec = next),
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
            )
        }
        markEdited()
        scheduleAutosave()
    }

    fun updateMeasuredHeight(height: Int) {
        if (_uiState.value.isLeaving) return
        val project = _uiState.value.currentProject ?: return
        if (!project.spec.canvas.autoHeight || project.spec.canvas.height == height) return
        val updatedSpec = project.spec.copy(canvas = project.spec.canvas.copy(height = height)).requireValid()
        _uiState.update { it.copy(currentProject = project.copy(spec = updatedSpec)) }
        markEdited()
        scheduleAutosave()
    }

    fun importCover(uri: Uri) {
        if (_uiState.value.isLeaving || _uiState.value.isImportingCover) return
        _uiState.update { it.copy(isImportingCover = true) }
        coverImportJob = viewModelScope.launch {
            try {
                val id = projectAssets.importCover(uri)
                if (_uiState.value.currentProject?.id != projectId) {
                    projectAssets.delete(id)
                    return@launch
                }
                updateSpec { spec ->
                    spec.copy(
                        song = spec.song.copy(coverAssetId = id),
                        visibility = spec.visibility.copy(showCover = true),
                    )
                }
                flushAutosave()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                setError(cause.message ?: "无法导入封面")
            } finally {
                _uiState.update { it.copy(isImportingCover = false) }
            }
        }
    }

    fun removeCover() {
        updateSpec { spec ->
            spec.copy(
                song = spec.song.copy(coverAssetId = null),
                visibility = spec.visibility.copy(showCover = false),
            )
        }
    }

    fun searchNetease(keyword: String = _uiState.value.drafts.searchQuery) {
        updateSearchQuery(keyword)
        val normalized = keyword.trim()
        neteaseSearchJob?.cancel()
        if (normalized.isBlank()) {
            updateNetease { it.copy(results = emptyList(), isSearching = false, message = "请输入歌曲名或歌手") }
            return
        }
        updateNetease { it.copy(isSearching = true, message = "正在搜索网易云音乐…") }
        neteaseSearchJob = viewModelScope.launch {
            try {
                val results = neteaseClient.search(normalized)
                if (_uiState.value.currentProject?.id != projectId) return@launch
                updateNetease {
                    it.copy(
                        results = results,
                        isSearching = false,
                        message = if (results.isEmpty()) {
                            "没有找到匹配歌曲，可继续手动填写"
                        } else {
                            "选择一首歌曲以导入信息与可用歌词"
                        },
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                if (_uiState.value.currentProject?.id == projectId) {
                    updateNetease { it.copy(isSearching = false, message = cause.message ?: "网易云搜索失败") }
                }
            }
        }
    }

    fun resolveNeteaseSong(id: String) {
        resolveNetease { neteaseClient.resolveSong(id) }
    }

    fun resolveNeteaseLink(input: String = _uiState.value.drafts.linkInput) {
        updateLinkInput(input)
        resolveNetease { neteaseClient.resolveLink(input) }
    }

    fun extractPalette() {
        if (_uiState.value.isLeaving) return
        val coverId = _uiState.value.currentProject?.spec?.song?.coverAssetId ?: return
        paletteJob?.cancel()
        _uiState.update { it.copy(isExtractingPalette = true, paletteError = null) }
        paletteJob = viewModelScope.launch {
            try {
                val palette = renderer.extractPalette(coverId)
                if (_uiState.value.currentProject?.id == projectId) updatePalette(palette)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _uiState.update { it.copy(paletteError = cause.message ?: "无法提取颜色") }
            } finally {
                _uiState.update { it.copy(isExtractingPalette = false) }
            }
        }
    }

    override suspend fun flushAutosave(): Boolean {
        autosaveJob?.cancelAndJoin()
        autosaveJob = null
        while (editRevision > savedRevision) {
            val snapshot = _uiState.value.currentProject ?: return !_uiState.value.projectUnavailable
            val revision = editRevision
            if (!persistSnapshot(snapshot, revision)) return false
        }
        return true
    }

    suspend fun prepareForNavigation(): Boolean {
        val state = _uiState.value
        if (state.isImportingCover || state.isExtractingPalette || state.netease.isResolving) {
            setError("正在应用封面、歌曲信息或配色，请完成后再离开")
            return false
        }
        if (!navigationInFlight.compareAndSet(false, true)) return false
        _uiState.update { it.copy(isLeaving = true) }
        return try {
            val saved = flushAutosave()
            if (!saved) releaseNavigation()
            saved
        } catch (cause: CancellationException) {
            releaseNavigation()
            throw cause
        } catch (cause: Throwable) {
            releaseNavigation()
            throw cause
        }
    }

    fun markNavigationCommitted() {
        if (navigationInFlight.get()) navigationCommitted.set(true)
    }

    fun navigationFailed() {
        releaseNavigation()
    }

    fun onNavigationResumed() {
        if (navigationCommitted.compareAndSet(true, false)) releaseNavigation()
    }

    fun requestFlush() {
        viewModelScope.launch { flushAutosave() }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        sessions.unregister(this)
        autosaveJob?.cancel()
        coverImportJob?.cancel()
        neteaseSearchJob?.cancel()
        neteaseResolveJob?.cancel()
        paletteJob?.cancel()
        super.onCleared()
    }

    private suspend fun loadProject() {
        _uiState.update { it.copy(isLoading = true, projectUnavailable = false, errorMessage = null) }
        try {
            val stored = projects.getProject(projectId) ?: error("项目不存在或已被删除")
            resetHistory()
            editRevision = 0L
            savedRevision = 0L
            val restoredName = if (hadSavedProjectName) _uiState.value.drafts.projectName else stored.name
            savedStateHandle[PROJECT_NAME_KEY] = restoredName
            val restored = if (restoredName.isNotBlank() && restoredName != stored.name) {
                stored.copy(name = restoredName)
            } else {
                stored
            }
            _uiState.update {
                it.copy(
                    currentProject = restored,
                    isLoading = false,
                    projectUnavailable = false,
                    drafts = it.drafts.copy(projectName = restoredName),
                    autosaveStatus = AutosaveStatus.SAVED,
                )
            }
            if (restored !== stored) {
                markEdited()
                scheduleAutosave()
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    projectUnavailable = true,
                    errorMessage = lineLimitMessage(cause, loadingStoredProject = true)
                        ?: cause.message
                        ?: "无法打开项目",
                )
            }
        }
    }

    private fun updatePalette(palette: PaletteSpec) {
        updateSpec { spec -> spec.copy(visual = spec.visual.copy(palette = palette)) }
    }

    private fun resolveNetease(block: suspend () -> ResolvedNeteaseSong) {
        if (_uiState.value.isLeaving) return
        neteaseResolveJob?.cancel()
        updateNetease { it.copy(isResolving = true, message = "正在解析歌曲信息、歌词与封面…") }
        neteaseResolveJob = viewModelScope.launch {
            var importedCoverId: String? = null
            try {
                val resolved = block()
                val importedLineCount = LyricTextLimits.countPhysicalLines(resolved.lyrics)
                if (resolved.lyrics.isNotEmpty() && importedLineCount > LyricTextLimits.MAX_LINES) {
                    val message = messages.lineLimit(
                        RenderSpecViolation(
                            path = "content.lyrics",
                            message = "exceeds line limit",
                            constraint = RenderSpecViolation.Constraint.MAX_LINES,
                            limit = LyricTextLimits.MAX_LINES,
                            actual = importedLineCount,
                        ),
                        loadingStoredProject = false,
                    )
                    updateNetease { it.copy(isResolving = false, message = message) }
                    setError(message)
                    return@launch
                }
                importedCoverId = resolved.coverUrl.takeIf(String::isNotBlank)?.let { coverUrl ->
                    runCatching {
                        val bytes = neteaseClient.downloadCover(coverUrl)
                        projectAssets.importCover(bytes)
                    }.getOrNull()
                }
                if (_uiState.value.currentProject?.id != projectId) {
                    importedCoverId?.let { projectAssets.delete(it) }
                    return@launch
                }
                val nextCoverId = importedCoverId
                val nextTitle = resolved.title.take(240)
                val nextArtist = resolved.artist.take(240)
                val nextAlbum = resolved.album.take(240)
                updateSpec { spec ->
                    spec.copy(
                        song = spec.song.copy(
                            source = SongSource.NETEASE,
                            title = nextTitle,
                            artist = nextArtist,
                            album = nextAlbum,
                            coverAssetId = nextCoverId ?: spec.song.coverAssetId,
                        ),
                        content = if (resolved.lyrics.isBlank()) {
                            spec.content
                        } else {
                            spec.content.copy(lyrics = resolved.lyrics)
                        },
                        visibility = if (nextCoverId == null) {
                            spec.visibility
                        } else {
                            spec.visibility.copy(showCover = true)
                        },
                        branding = spec.branding.copy(platform = SongSource.NETEASE),
                    )
                }
                val appliedSong = _uiState.value.currentProject?.spec?.song
                check(appliedSong?.title == nextTitle && (nextCoverId == null || appliedSong.coverAssetId == nextCoverId)) {
                    "无法应用网易云歌曲信息"
                }
                importedCoverId = null
                val imported = buildList {
                    add("歌曲信息")
                    if (resolved.lyrics.isNotBlank()) add("歌词")
                    if (nextCoverId != null) add("封面")
                }.joinToString("、")
                updateNetease { it.copy(isResolving = false, message = "已从网易云导入$imported") }
                flushAutosave()
            } catch (cause: CancellationException) {
                importedCoverId?.let { projectAssets.delete(it) }
                throw cause
            } catch (cause: Throwable) {
                importedCoverId?.let { projectAssets.delete(it) }
                if (_uiState.value.currentProject?.id == projectId) {
                    updateNetease { it.copy(isResolving = false, message = cause.message ?: "网易云解析失败") }
                }
            }
        }
    }

    private fun updateNetease(transform: (NeteaseLookupUiState) -> NeteaseLookupUiState) {
        _uiState.update { it.copy(netease = transform(it.netease)) }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        val snapshot = _uiState.value.currentProject ?: return
        val revision = editRevision
        _uiState.update { it.copy(autosaveStatus = AutosaveStatus.SAVING) }
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTOSAVE_DELAY_MS)
            persistSnapshot(snapshot, revision)
        }
    }

    private suspend fun persistSnapshot(snapshot: Project, revision: Long): Boolean = saveMutex.withLock {
        if (revision <= savedRevision) return@withLock true
        try {
            val saved = projects.save(snapshot)
            savedRevision = maxOf(savedRevision, revision)
            val latest = _uiState.value.currentProject
            if (latest?.id == saved.id) {
                val unchanged = latest.spec == snapshot.spec && latest.name == snapshot.name
                _uiState.update {
                    it.copy(
                        currentProject = if (unchanged) saved else latest,
                        autosaveStatus = if (editRevision > savedRevision) {
                            AutosaveStatus.SAVING
                        } else {
                            AutosaveStatus.SAVED
                        },
                        errorMessage = null,
                    )
                }
            }
            true
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (_uiState.value.currentProject?.id == snapshot.id) {
                _uiState.update {
                    it.copy(
                        autosaveStatus = AutosaveStatus.FAILED,
                        errorMessage = cause.message ?: "自动保存失败",
                    )
                }
            }
            false
        }
    }

    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun markEdited() {
        editRevision = if (editRevision == Long.MAX_VALUE) 1L else editRevision + 1L
        if (editRevision == 1L) savedRevision = 0L
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun releaseNavigation() {
        navigationCommitted.set(false)
        navigationInFlight.set(false)
        _uiState.update { it.copy(isLeaving = false) }
    }

    private fun lineLimitMessage(
        cause: Throwable,
        loadingStoredProject: Boolean = false,
    ): String? {
        val violation = cause.findLineLimitViolation() ?: return null
        return messages.lineLimit(violation, loadingStoredProject)
    }

    companion object {
        const val PROJECT_ID_KEY = "projectId"
        const val STEP_KEY = "editorStep"
        const val SEARCH_QUERY_KEY = "searchQuery"
        const val LINK_INPUT_KEY = "linkInput"
        const val PROJECT_NAME_KEY = "projectNameDraft"
        const val AUTOSAVE_DELAY_MS = 500L
        const val MAX_HISTORY = 50
        const val LAST_EDITOR_STEP = 5
        private const val MAX_SEARCH_QUERY_LENGTH = 120
        private const val MAX_LINK_INPUT_LENGTH = 8_192
        private const val MAX_PROJECT_NAME_LENGTH = 120
    }
}

private fun Throwable.findLineLimitViolation(): RenderSpecViolation? {
    var current: Throwable? = this
    repeat(16) {
        val violation = (current as? InvalidRenderSpecException)
            ?.violations
            ?.firstOrNull { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }
        if (violation != null) return violation
        current = current?.cause ?: return null
    }
    return null
}
