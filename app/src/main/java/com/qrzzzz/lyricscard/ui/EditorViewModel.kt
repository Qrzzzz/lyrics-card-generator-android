package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.EditorAutosaveSession
import com.qrzzzz.lyricscard.EditorSessionRegistry
import com.qrzzzz.lyricscard.NeteaseClient
import com.qrzzzz.lyricscard.ProjectAssets
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.RendererOperations
import com.qrzzzz.lyricscard.data.NeteaseServiceError
import com.qrzzzz.lyricscard.data.NeteaseServiceException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

enum class NeteaseLookupPhase {
    IDLE,
    SEARCHING,
    RESULTS,
    EMPTY,
    RESOLVING,
    SUCCESS,
    ERROR,
}

data class NeteaseLookupUiState(
    val results: List<NeteaseSongSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isResolving: Boolean = false,
    val phase: NeteaseLookupPhase = NeteaseLookupPhase.IDLE,
    val message: UiText = UiText.resource(R.string.editor_netease_idle),
)

data class EditorUiState(
    val projectId: String,
    val currentProject: Project? = null,
    val isLoading: Boolean = true,
    val projectUnavailable: Boolean = false,
    val autosaveStatus: AutosaveStatus = AutosaveStatus.SAVED,
    val errorMessage: UiText? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val selectedStep: Int = 0,
    val drafts: EditorDrafts = EditorDrafts(),
    val netease: NeteaseLookupUiState = NeteaseLookupUiState(),
    val isImportingCover: Boolean = false,
    val isExtractingPalette: Boolean = false,
    val paletteError: UiText? = null,
    val isLeaving: Boolean = false,
)

class EditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val projects: ProjectStore,
    private val projectAssets: ProjectAssets,
    private val neteaseClient: NeteaseClient,
    private val renderer: RendererOperations,
    private val sessions: EditorSessionRegistry,
) : ViewModel(), EditorAutosaveSession {
    private data class EditorMutationSnapshot(
        val project: Project,
        val undoHistory: List<RenderSpec>,
        val redoHistory: List<RenderSpec>,
        val editRevision: Long,
        val savedRevision: Long,
        val autosaveStatus: AutosaveStatus,
        val errorMessage: UiText?,
    )

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
        if (next != _uiState.value.drafts.searchQuery && _uiState.value.netease.isSearching) {
            neteaseSearchJob?.cancel()
            updateNetease {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    phase = NeteaseLookupPhase.IDLE,
                    message = UiText.resource(R.string.editor_netease_idle),
                )
            }
        }
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
            setError(lineLimitMessage(cause) ?: UiText.resource(R.string.editor_error_invalid_setting))
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
                setError(UiText.resource(R.string.editor_error_import_cover))
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
            updateNetease {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    phase = NeteaseLookupPhase.IDLE,
                    message = UiText.resource(R.string.editor_netease_enter_query),
                )
            }
            return
        }
        updateNetease {
            it.copy(
                results = emptyList(),
                isSearching = true,
                phase = NeteaseLookupPhase.SEARCHING,
                message = UiText.resource(R.string.editor_netease_searching),
            )
        }
        neteaseSearchJob = viewModelScope.launch {
            try {
                val results = neteaseClient.search(normalized)
                if (_uiState.value.currentProject?.id != projectId) return@launch
                updateNetease {
                    it.copy(
                        results = results,
                        isSearching = false,
                        phase = if (results.isEmpty()) NeteaseLookupPhase.EMPTY else NeteaseLookupPhase.RESULTS,
                        message = UiText.resource(
                            if (results.isEmpty()) {
                                R.string.editor_netease_no_results
                            } else {
                                R.string.editor_netease_select_result
                            },
                        ),
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                if (_uiState.value.currentProject?.id == projectId) {
                    updateNetease {
                        it.copy(
                            isSearching = false,
                            phase = NeteaseLookupPhase.ERROR,
                            message = neteaseFailureText(cause, R.string.editor_error_netease_search),
                        )
                    }
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
                _uiState.update {
                    it.copy(paletteError = UiText.resource(R.string.editor_error_extract_palette))
                }
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
            setError(UiText.resource(R.string.editor_error_busy_leaving))
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
            val stored = projects.getProject(projectId) ?: error("Project does not exist")
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
                        ?: UiText.resource(R.string.editor_error_open_project),
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
        updateNetease {
            it.copy(
                isResolving = true,
                phase = NeteaseLookupPhase.RESOLVING,
                message = UiText.resource(R.string.editor_netease_resolving),
            )
        }
        neteaseResolveJob = viewModelScope.launch {
            var importedCoverId: String? = null
            var coverWarning = false
            var mutationSnapshot: EditorMutationSnapshot? = null
            try {
                val resolved = block()
                val importedLineCount = LyricTextLimits.countPhysicalLines(resolved.lyrics)
                if (resolved.lyrics.isNotEmpty() && importedLineCount > LyricTextLimits.MAX_LINES) {
                    val message = lineLimitText(
                        RenderSpecViolation(
                            path = "content.lyrics",
                            message = "exceeds line limit",
                            constraint = RenderSpecViolation.Constraint.MAX_LINES,
                            limit = LyricTextLimits.MAX_LINES,
                            actual = importedLineCount,
                        ),
                        loadingStoredProject = false,
                    )
                    updateNetease {
                        it.copy(
                            isResolving = false,
                            phase = NeteaseLookupPhase.ERROR,
                            message = message,
                        )
                    }
                    setError(message)
                    return@launch
                }
                importedCoverId = resolved.coverUrl.takeIf(String::isNotBlank)?.let { coverUrl ->
                    try {
                        val bytes = neteaseClient.downloadCover(coverUrl)
                        projectAssets.importCover(bytes)
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (cause: Throwable) {
                        coverWarning = true
                        null
                    }
                }
                currentCoroutineContext().ensureActive()
                if (_uiState.value.currentProject?.id != projectId) {
                    importedCoverId?.let { cleanupPendingCover(it) }
                    return@launch
                }
                val nextCoverId = importedCoverId
                val nextTitle = resolved.title.take(240)
                val nextArtist = resolved.artist.take(240)
                val nextAlbum = resolved.album.take(240)
                mutationSnapshot = captureEditorMutationSnapshot()
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
                    "NetEase import was not applied"
                }
                if (!flushAutosave()) {
                    val message = _uiState.value.errorMessage
                        ?: UiText.resource(R.string.editor_error_save_netease)
                    restoreEditorMutation(checkNotNull(mutationSnapshot), message, restartPendingAutosave = false)
                    mutationSnapshot = null
                    error("NetEase import could not be persisted")
                }
                importedCoverId = null
                mutationSnapshot = null
                val imported = UiText.joined(
                    R.string.list_separator,
                    buildList {
                        add(UiText.resource(R.string.editor_import_part_song))
                        if (resolved.lyrics.isNotBlank()) {
                            add(UiText.resource(R.string.editor_import_part_lyrics))
                        }
                        if (nextCoverId != null) add(UiText.resource(R.string.editor_import_part_cover))
                    },
                )
                val resultMessage = UiText.resource(
                    if (coverWarning) {
                        R.string.editor_netease_import_success_cover_warning
                    } else {
                        R.string.editor_netease_import_success
                    },
                    imported,
                )
                updateNetease {
                    it.copy(
                        isResolving = false,
                        phase = NeteaseLookupPhase.SUCCESS,
                        message = resultMessage,
                    )
                }
            } catch (cause: CancellationException) {
                mutationSnapshot?.let {
                    restoreEditorMutation(it, failureMessage = null, restartPendingAutosave = true)
                }
                importedCoverId?.let { cleanupPendingCover(it, cause) }
                throw cause
            } catch (cause: Throwable) {
                mutationSnapshot?.let {
                    restoreEditorMutation(
                        it,
                        failureMessage = neteaseFailureText(
                            cause,
                            R.string.editor_error_netease_resolve,
                        ),
                        restartPendingAutosave = true,
                    )
                }
                importedCoverId?.let { cleanupPendingCover(it, cause) }
                if (_uiState.value.currentProject?.id == projectId) {
                    updateNetease {
                        it.copy(
                            isResolving = false,
                            phase = NeteaseLookupPhase.ERROR,
                            message = neteaseFailureText(
                                cause,
                                R.string.editor_error_netease_resolve,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun captureEditorMutationSnapshot(): EditorMutationSnapshot {
        val state = _uiState.value
        return EditorMutationSnapshot(
            project = checkNotNull(state.currentProject),
            undoHistory = undoStack.toList(),
            redoHistory = redoStack.toList(),
            editRevision = editRevision,
            savedRevision = savedRevision,
            autosaveStatus = state.autosaveStatus,
            errorMessage = state.errorMessage,
        )
    }

    private fun restoreEditorMutation(
        snapshot: EditorMutationSnapshot,
        failureMessage: UiText?,
        restartPendingAutosave: Boolean,
    ) {
        autosaveJob?.cancel()
        autosaveJob = null
        undoStack.clear()
        undoStack.addAll(snapshot.undoHistory)
        redoStack.clear()
        redoStack.addAll(snapshot.redoHistory)
        editRevision = snapshot.editRevision
        savedRevision = snapshot.savedRevision
        _uiState.update {
            it.copy(
                currentProject = snapshot.project,
                autosaveStatus = if (failureMessage == null) snapshot.autosaveStatus else AutosaveStatus.FAILED,
                errorMessage = failureMessage ?: snapshot.errorMessage,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        if (restartPendingAutosave && editRevision > savedRevision) scheduleAutosave()
    }

    private suspend fun cleanupPendingCover(id: String, primaryFailure: Throwable? = null) {
        try {
            withContext(NonCancellable + Dispatchers.IO) {
                projectAssets.delete(id)
            }
        } catch (cleanupFailure: Throwable) {
            if (primaryFailure == null) throw cleanupFailure
            primaryFailure.addSuppressed(cleanupFailure)
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
                        errorMessage = UiText.resource(R.string.editor_autosave_failed),
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

    private fun setError(message: UiText) {
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
    ): UiText? {
        val violation = cause.findLineLimitViolation() ?: return null
        return lineLimitText(violation, loadingStoredProject)
    }

    private fun lineLimitText(
        violation: RenderSpecViolation,
        loadingStoredProject: Boolean,
    ): UiText {
        val field = UiText.resource(
            if (violation.path == "content.translation") {
                R.string.lyric_field_translation
            } else {
                R.string.lyric_field_original
            },
        )
        return UiText.resource(
            if (loadingStoredProject) {
                R.string.error_loaded_lyric_line_limit
            } else {
                R.string.error_lyric_line_limit
            },
            field,
            violation.limit ?: LyricTextLimits.MAX_LINES,
            violation.actual ?: 0,
        )
    }

    private fun neteaseFailureText(cause: Throwable, fallback: Int): UiText {
        val resource = when ((cause as? NeteaseServiceException)?.error) {
            NeteaseServiceError.INVALID_QUERY -> R.string.network_invalid_query
            NeteaseServiceError.QUERY_TOO_LONG -> R.string.network_query_too_long
            NeteaseServiceError.INVALID_SONG_ID -> R.string.network_invalid_song_id
            NeteaseServiceError.INVALID_LINK -> R.string.network_invalid_link
            NeteaseServiceError.UNSAFE_URL -> R.string.network_unsafe_url
            NeteaseServiceError.REDIRECT_NOT_ALLOWED -> R.string.network_redirect_not_allowed
            NeteaseServiceError.REDIRECT_LOOP -> R.string.network_redirect_loop
            NeteaseServiceError.TOO_MANY_REDIRECTS -> R.string.network_too_many_redirects
            NeteaseServiceError.REQUEST_REJECTED -> R.string.network_request_rejected
            NeteaseServiceError.RATE_LIMITED -> R.string.network_rate_limited
            NeteaseServiceError.SERVICE_UNAVAILABLE -> R.string.network_service_unavailable
            NeteaseServiceError.EMPTY_RESPONSE -> R.string.network_empty_response
            NeteaseServiceError.MALFORMED_RESPONSE -> R.string.network_malformed_response
            NeteaseServiceError.RESPONSE_TOO_LARGE -> R.string.network_response_too_large
            NeteaseServiceError.TIMEOUT -> R.string.network_timeout
            NeteaseServiceError.NETWORK_UNAVAILABLE -> R.string.network_unavailable
            NeteaseServiceError.DNS_FAILURE -> R.string.network_dns_failure
            NeteaseServiceError.TLS_FAILURE -> R.string.network_tls_failure
            NeteaseServiceError.SONG_NOT_FOUND -> R.string.network_song_not_found
            null -> fallback
        }
        return UiText.resource(resource)
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
