package com.qrzzzz.lyricscard.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.requireValid
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EditorUiState(
    val currentProject: Project? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LyricsCardApplication
    private val repository = app.projectRepository
    val assetStore = app.assetStore
    private val preferencesRepository = app.preferencesRepository

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val projects: StateFlow<List<ProjectSummary>> = repository.observeProjects()
        .catch { cause -> setError(cause.message ?: "无法读取项目") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _editor = MutableStateFlow(EditorUiState())
    val editor: StateFlow<EditorUiState> = _editor.asStateFlow()
    private val saveMutex = Mutex()
    private var autosaveJob: Job? = null
    private val undoStack = ArrayDeque<RenderSpec>()
    private val redoStack = ArrayDeque<RenderSpec>()
    private var editRevision = 0L
    private var savedRevision = 0L

    suspend fun createBlank(): Project? {
        if (!flushAutosave()) return null
        return guarded("无法创建项目") { loadCreated(repository.createBlank()) }
    }

    suspend fun createSample(): Project? {
        if (!flushAutosave()) return null
        return guarded("无法创建示例") { loadCreated(repository.createSample()) }
    }

    suspend fun openProject(id: String): Project? {
        if (!flushAutosave()) return null
        _editor.value = _editor.value.copy(isLoading = true, errorMessage = null)
        return try {
            val project = repository.getProject(id) ?: error("项目不存在或已被删除")
            project.also {
                resetHistory()
                editRevision = 0L
                savedRevision = 0L
                _editor.value = EditorUiState(currentProject = project)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            _editor.value = _editor.value.copy(isLoading = false, errorMessage = cause.message ?: "无法打开项目")
            null
        }
    }

    fun updateSpec(transform: (RenderSpec) -> RenderSpec) {
        val project = _editor.value.currentProject ?: return
        val updated = runCatching { project.copy(spec = transform(project.spec).requireValid()) }
            .getOrElse { cause ->
                setError(cause.message ?: "设置无效")
                return
            }
        if (updated.spec == project.spec) return
        undoStack.addLast(project.spec)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        _editor.value = _editor.value.copy(
            currentProject = updated,
            errorMessage = null,
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
        )
        markEdited()
        scheduleAutosave()
    }

    fun undo() {
        val project = _editor.value.currentProject ?: return
        val previous = undoStack.pollLast() ?: return
        redoStack.addLast(project.spec)
        _editor.value = _editor.value.copy(
            currentProject = project.copy(spec = previous),
            canUndo = undoStack.isNotEmpty(),
            canRedo = true,
        )
        markEdited()
        scheduleAutosave()
    }

    fun redo() {
        val project = _editor.value.currentProject ?: return
        val next = redoStack.pollLast() ?: return
        undoStack.addLast(project.spec)
        _editor.value = _editor.value.copy(
            currentProject = project.copy(spec = next),
            canUndo = true,
            canRedo = redoStack.isNotEmpty(),
        )
        markEdited()
        scheduleAutosave()
    }

    fun updateMeasuredHeight(height: Int) {
        val project = _editor.value.currentProject ?: return
        if (!project.spec.canvas.autoHeight || project.spec.canvas.height == height) return
        val updatedSpec = project.spec.copy(canvas = project.spec.canvas.copy(height = height)).requireValid()
        _editor.value = _editor.value.copy(currentProject = project.copy(spec = updatedSpec))
        markEdited()
        scheduleAutosave()
    }

    fun updatePalette(palette: PaletteSpec) {
        updateSpec { spec -> spec.copy(visual = spec.visual.copy(palette = palette)) }
    }

    fun updateProjectName(value: String) {
        val project = _editor.value.currentProject ?: return
        if (value.isBlank() || value.length > 120) return
        _editor.value = _editor.value.copy(currentProject = project.copy(name = value))
        markEdited()
        scheduleAutosave()
    }

    fun importCover(uri: Uri) {
        val projectId = _editor.value.currentProject?.id ?: return
        viewModelScope.launch {
            try {
                val id = assetStore.importCover(uri)
                if (_editor.value.currentProject?.id != projectId) {
                    assetStore.delete(id)
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

    suspend fun duplicateProject(id: String): Project? = guarded("复制失败") { repository.duplicate(id) }

    suspend fun renameProject(id: String, name: String): Boolean = guarded("重命名失败") {
        if (_editor.value.currentProject?.id == id && !flushAutosave()) {
            false
        } else {
            val renamed = repository.rename(id, name)
            if (renamed && _editor.value.currentProject?.id == id) {
                repository.getProject(id)?.let { refreshed ->
                    _editor.value = _editor.value.copy(currentProject = refreshed, isSaving = false)
                }
            }
            renamed
        }
    } ?: false

    suspend fun deleteProject(id: String): Boolean = guarded("删除失败") {
        if (_editor.value.currentProject?.id == id) {
            autosaveJob?.cancelAndJoin()
            autosaveJob = null
        }
        val deleted = saveMutex.withLock { repository.delete(id) }
        if (_editor.value.currentProject?.id == id) clearEditor()
        deleted
    } ?: false

    suspend fun flushAutosave(): Boolean {
        autosaveJob?.cancelAndJoin()
        autosaveJob = null
        val snapshot = _editor.value.currentProject ?: return true
        return if (editRevision > savedRevision) persistSnapshot(snapshot, editRevision) else true
    }

    suspend fun recordExport(image: ExportedImage) {
        val project = _editor.value.currentProject ?: return
        val thumbnail = withContext(Dispatchers.IO) {
            val directory = File(app.filesDir, "thumbnails").apply { mkdirs() }
            File(directory, "${project.id}.png").also { createThumbnail(image.file, it) }
        }
        val refreshed = saveMutex.withLock {
            check(repository.updateThumbnail(project.id, thumbnail.absolutePath)) { "项目已被删除，无法记录缩略图" }
            check(repository.markExported(project.id)) { "项目已被删除，无法记录导出时间" }
            repository.getProject(project.id) ?: error("项目已被删除，无法记录导出结果")
        }
        val latest = _editor.value.currentProject
        if (latest?.id == refreshed.id) {
            _editor.value = _editor.value.copy(
                currentProject = latest.copy(
                    thumbnailPath = refreshed.thumbnailPath,
                    updatedAt = maxOf(latest.updatedAt, refreshed.updatedAt),
                    lastExportedAt = refreshed.lastExportedAt,
                ),
            )
        }
    }

    fun clearError() {
        _editor.value = _editor.value.copy(errorMessage = null)
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            guarded("无法保存设置") { preferencesRepository.setDarkMode(enabled) }
        }
    }

    fun setDefaultExportScale(scale: Int) {
        viewModelScope.launch {
            guarded("无法保存设置") { preferencesRepository.setDefaultExportScale(scale) }
        }
    }

    fun setShowSafeArea(enabled: Boolean) {
        viewModelScope.launch {
            guarded("无法保存设置") { preferencesRepository.setShowSafeArea(enabled) }
        }
    }

    override fun onCleared() {
        autosaveJob?.cancel()
        super.onCleared()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        val snapshot = _editor.value.currentProject ?: return
        val revision = editRevision
        _editor.value = _editor.value.copy(isSaving = true)
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(AUTOSAVE_DELAY_MS)
            persistSnapshot(snapshot, revision)
        }
    }

    private suspend fun persistSnapshot(snapshot: Project, revision: Long): Boolean = saveMutex.withLock {
        if (revision <= savedRevision) return@withLock true
        try {
            val saved = repository.save(snapshot)
            savedRevision = maxOf(savedRevision, revision)
            val latest = _editor.value.currentProject
            if (latest?.id == saved.id) {
                val unchanged = latest.spec == snapshot.spec && latest.name == snapshot.name
                _editor.value = _editor.value.copy(
                    currentProject = if (unchanged) saved else latest,
                    isSaving = editRevision > savedRevision,
                    errorMessage = null,
                )
            }
            true
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            if (_editor.value.currentProject?.id == snapshot.id) {
                _editor.value = _editor.value.copy(
                    isSaving = false,
                    errorMessage = cause.message ?: "自动保存失败",
                )
            }
            false
        }
    }

    private suspend fun <T> guarded(defaultMessage: String, block: suspend () -> T): T? = try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        setError(cause.message ?: defaultMessage)
        null
    }

    private fun loadCreated(project: Project): Project {
        resetHistory()
        editRevision = 0L
        savedRevision = 0L
        _editor.value = EditorUiState(currentProject = project)
        return project
    }

    private fun clearEditor() {
        resetHistory()
        editRevision = 0L
        savedRevision = 0L
        _editor.value = EditorUiState()
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
        _editor.value = _editor.value.copy(errorMessage = message)
    }

    private companion object {
        const val AUTOSAVE_DELAY_MS = 500L
        const val MAX_HISTORY = 50
        const val THUMBNAIL_EDGE = 480

        fun createThumbnail(source: File, target: File) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取导出图片" }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > THUMBNAIL_EDGE * 2) {
                sampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: error("无法生成项目缩略图")
            val scale = minOf(
                1f,
                THUMBNAIL_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat(),
            )
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = if (width == bitmap.width && height == bitmap.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
            try {
                target.outputStream().use { output ->
                    check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) { "缩略图编码失败" }
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }
    }
}
