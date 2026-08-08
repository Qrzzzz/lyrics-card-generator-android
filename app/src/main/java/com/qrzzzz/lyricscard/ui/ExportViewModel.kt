package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.ExportFiles
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.RendererOperations
import com.qrzzzz.lyricscard.UserPreferencesStore
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportOperationState {
    IDLE,
    RUNNING,
    FINALIZING,
    SUCCESS,
    CANCELLED,
    FAILURE,
    INTERRUPTED,
}

enum class ExportPendingAction {
    SAVE,
    SHARE,
}

data class ExportEffect(
    val id: Long,
    val action: ExportPendingAction,
)

data class ExportUiState(
    val projectId: String,
    val project: Project? = null,
    val isLoading: Boolean = true,
    val projectUnavailable: Boolean = false,
    val multiplier: Int = 2,
    val fileName: String = "",
    val measuredHeight: Int = 0,
    val operation: ExportOperationState = ExportOperationState.IDLE,
    val exported: ExportedImage? = null,
    val status: String = "准备导出",
    val errorMessage: String? = null,
    val effect: ExportEffect? = null,
) {
    val isBusy: Boolean
        get() = operation == ExportOperationState.RUNNING || operation == ExportOperationState.FINALIZING

    val canCancel: Boolean
        get() = operation == ExportOperationState.RUNNING
}

class ExportViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val projects: ProjectStore,
    private val preferences: UserPreferencesStore,
    private val renderer: RendererOperations,
    private val exportFiles: ExportFiles,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val projectId: String = checkNotNull(savedStateHandle[PROJECT_ID_KEY]) {
        "Export route requires projectId"
    }
    private val hadSavedMultiplier = savedStateHandle.contains(MULTIPLIER_KEY)
    private val hadSavedFileName = savedStateHandle.contains(FILE_NAME_KEY)
    private val restoredOperation = savedStateHandle.get<String>(OPERATION_KEY)
        ?.let { value -> ExportOperationState.entries.firstOrNull { it.name == value } }
        ?: ExportOperationState.IDLE
    private val restoredImage = restoreExportedImage(savedStateHandle)
    private val initialImage = restoredImage.takeIf { restoredOperation == ExportOperationState.SUCCESS }
    private val initialOperation = when {
        restoredOperation == ExportOperationState.RUNNING ||
            restoredOperation == ExportOperationState.FINALIZING -> ExportOperationState.INTERRUPTED
        restoredOperation == ExportOperationState.SUCCESS && initialImage == null -> ExportOperationState.FAILURE
        else -> restoredOperation
    }
    private val initialStatus = when (initialOperation) {
        ExportOperationState.SUCCESS -> savedStateHandle[STATUS_KEY]
            ?: initialImage?.let { "已生成 ${it.width} × ${it.height} PNG" }
            ?: "导出结果已失效"
        ExportOperationState.CANCELLED -> savedStateHandle[STATUS_KEY] ?: "导出已取消，可立即重试"
        ExportOperationState.FAILURE -> if (restoredOperation == ExportOperationState.SUCCESS) {
            "上次导出结果已失效，请重试"
        } else {
            savedStateHandle[STATUS_KEY] ?: "导出失败，可重试"
        }
        ExportOperationState.INTERRUPTED -> "上次导出被系统中断，没有自动重试"
        else -> savedStateHandle[STATUS_KEY] ?: "准备导出"
    }
    private val initialError = when (initialOperation) {
        ExportOperationState.FAILURE -> if (restoredOperation == ExportOperationState.SUCCESS) {
            "导出结果文件已不存在，请重试"
        } else {
            savedStateHandle[ERROR_KEY] ?: "上次导出失败，可手动重试"
        }
        ExportOperationState.INTERRUPTED -> "上次导出被系统中断，可手动重试"
        else -> savedStateHandle[ERROR_KEY]
    }
    private val _uiState = MutableStateFlow(
        ExportUiState(
            projectId = projectId,
            multiplier = (savedStateHandle[MULTIPLIER_KEY] ?: 2).coerceIn(1, 2),
            fileName = savedStateHandle[FILE_NAME_KEY] ?: "",
            measuredHeight = savedStateHandle[MEASURED_HEIGHT_KEY] ?: 0,
            operation = initialOperation,
            exported = initialImage,
            status = initialStatus,
            errorMessage = initialError,
        ),
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var exportJob: Job? = null
    private var nextEffectId = 0L

    init {
        savedStateHandle[PROJECT_ID_KEY] = projectId
        if (initialOperation != restoredOperation) {
            clearPersistedImage()
            savedStateHandle[OPERATION_KEY] = initialOperation.name
            persistMessages(initialStatus, initialError)
        }
        viewModelScope.launch { loadProject() }
    }

    fun setMultiplier(value: Int) {
        if (_uiState.value.isBusy) return
        val next = value.coerceIn(1, 2)
        savedStateHandle[MULTIPLIER_KEY] = next
        clearPersistedImage()
        _uiState.update {
            it.copy(
                multiplier = next,
                operation = ExportOperationState.IDLE,
                exported = null,
                status = "准备导出",
                errorMessage = null,
                effect = null,
            )
        }
        savedStateHandle[OPERATION_KEY] = ExportOperationState.IDLE.name
        persistMessages("准备导出", null)
    }

    fun setFileName(value: String) {
        if (_uiState.value.isBusy) return
        val next = value.take(MAX_FILE_NAME_LENGTH)
        savedStateHandle[FILE_NAME_KEY] = next
        _uiState.update { it.copy(fileName = next) }
    }

    fun setMeasuredHeight(value: Int) {
        if (value <= 0) return
        savedStateHandle[MEASURED_HEIGHT_KEY] = value
        _uiState.update { it.copy(measuredHeight = value) }
    }

    fun save() {
        exportOrDispatch(ExportPendingAction.SAVE)
    }

    fun share() {
        exportOrDispatch(ExportPendingAction.SHARE)
    }

    fun retry() {
        if (_uiState.value.isBusy) return
        renderer.retry()
        startExport(action = null)
    }

    fun cancelExport() {
        if (_uiState.value.operation == ExportOperationState.FINALIZING) return
        val job = exportJob ?: return
        if (!job.isActive) return
        _uiState.update { it.copy(status = "正在取消并恢复渲染器…", errorMessage = null) }
        persistMessages("正在取消并恢复渲染器…", null)
        job.cancel()
    }

    fun consumeEffect(id: Long) {
        _uiState.update { state ->
            if (state.effect?.id == id) state.copy(effect = null) else state
        }
    }

    fun saveTo(destination: Uri) {
        val image = _uiState.value.exported ?: return
        viewModelScope.launch {
            try {
                exportFiles.copyTo(image, destination)
                _uiState.update { it.copy(status = "已保存到所选位置", errorMessage = null) }
                persistMessages("已保存到所选位置", null)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                val message = cause.message ?: "保存失败"
                _uiState.update { it.copy(errorMessage = message) }
                persistMessages(_uiState.value.status, message)
            }
        }
    }

    fun reportExternalActionError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
        persistMessages(_uiState.value.status, message)
    }

    private suspend fun loadProject() {
        _uiState.update { it.copy(isLoading = true, projectUnavailable = false) }
        try {
            val project = projects.getProject(projectId) ?: error("项目不存在或已被删除")
            val defaultMultiplier = if (hadSavedMultiplier) {
                _uiState.value.multiplier
            } else {
                preferences.preferences.first().defaultExportScale.coerceIn(1, 2)
            }
            val fileName = if (hadSavedFileName) {
                _uiState.value.fileName
            } else {
                defaultFileName(project.spec.song.title, clock())
            }
            savedStateHandle[MULTIPLIER_KEY] = defaultMultiplier
            savedStateHandle[FILE_NAME_KEY] = fileName
            if (_uiState.value.measuredHeight <= 0) {
                savedStateHandle[MEASURED_HEIGHT_KEY] = project.spec.canvas.height
            }
            _uiState.update {
                it.copy(
                    project = project,
                    isLoading = false,
                    projectUnavailable = false,
                    multiplier = defaultMultiplier,
                    fileName = fileName,
                    measuredHeight = it.measuredHeight.takeIf { height -> height > 0 } ?: project.spec.canvas.height,
                )
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            val message = cause.message ?: "无法打开项目"
            _uiState.update {
                it.copy(
                    isLoading = false,
                    projectUnavailable = true,
                    errorMessage = message,
                )
            }
            persistMessages(_uiState.value.status, message)
        }
    }

    private fun exportOrDispatch(action: ExportPendingAction) {
        if (_uiState.value.isBusy) return
        if (_uiState.value.exported != null) {
            dispatch(action)
        } else {
            startExport(action)
        }
    }

    private fun startExport(action: ExportPendingAction?) {
        if (exportJob?.isActive == true) return
        val project = _uiState.value.project ?: return
        lateinit var launched: Job
        launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            clearPersistedImage()
            persistOperation(ExportOperationState.RUNNING)
            val runningStatus = "正在生成 ${_uiState.value.multiplier}× PNG…"
            persistMessages(runningStatus, null)
            _uiState.update {
                it.copy(
                    operation = ExportOperationState.RUNNING,
                    exported = null,
                    errorMessage = null,
                    status = runningStatus,
                    effect = null,
                )
            }
            try {
                val image = renderer.exportPng(project, _uiState.value.multiplier)
                finalizeExport(project, image, action)
            } catch (cause: CancellationException) {
                if (_uiState.value.operation == ExportOperationState.SUCCESS) throw cause
                clearPersistedImage()
                persistOperation(ExportOperationState.CANCELLED)
                persistMessages("导出已取消，可立即重试", null)
                _uiState.update {
                    it.copy(
                        operation = ExportOperationState.CANCELLED,
                        exported = null,
                        errorMessage = null,
                        status = "导出已取消，可立即重试",
                        effect = null,
                    )
                }
                throw cause
            } catch (cause: Throwable) {
                clearPersistedImage()
                persistOperation(ExportOperationState.FAILURE)
                val message = cause.message ?: "导出失败"
                persistMessages("导出失败，可重试", message)
                _uiState.update {
                    it.copy(
                        operation = ExportOperationState.FAILURE,
                        exported = null,
                        errorMessage = message,
                        status = "导出失败，可重试",
                        effect = null,
                    )
                }
            } finally {
                if (exportJob === launched) exportJob = null
            }
        }
        exportJob = launched
        launched.start()
    }

    /**
     * Point of no return: once the renderer has produced a complete PNG, publishing the thumbnail,
     * committing both Room metadata fields, and recording the restorable success state must finish
     * as one non-cancellable finalization sequence.
     */
    private suspend fun finalizeExport(
        project: Project,
        image: ExportedImage,
        action: ExportPendingAction?,
    ) = withContext(NonCancellable) {
        val finalizingStatus = "正在完成缩略图与导出记录…"
        persistOperation(ExportOperationState.FINALIZING)
        persistMessages(finalizingStatus, null)
        _uiState.update {
            it.copy(
                operation = ExportOperationState.FINALIZING,
                status = finalizingStatus,
                errorMessage = null,
            )
        }

        val metadataWarning = recordExport(project, image)
        persistImage(image)
        persistOperation(ExportOperationState.SUCCESS)
        val successStatus = "已生成 ${image.width} × ${image.height} PNG"
        persistMessages(successStatus, metadataWarning)
        _uiState.update {
            it.copy(
                operation = ExportOperationState.SUCCESS,
                exported = image,
                status = successStatus,
                errorMessage = metadataWarning,
            )
        }
        if (action != null) dispatch(action)
    }

    private suspend fun recordExport(project: Project, image: ExportedImage): String? = try {
        val thumbnailPath = exportFiles.createThumbnail(project.id, image)
        check(projects.recordExport(project.id, thumbnailPath)) { "项目已被删除，无法记录导出信息" }
        projects.getProject(project.id)?.let { refreshed ->
            _uiState.update { it.copy(project = refreshed) }
        }
        null
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        cause.message ?: "图片已生成，但无法更新项目导出记录"
    }

    private fun dispatch(action: ExportPendingAction) {
        if (_uiState.value.exported == null) return
        val effect = ExportEffect(id = ++nextEffectId, action = action)
        _uiState.update { it.copy(effect = effect) }
    }

    private fun persistOperation(value: ExportOperationState) {
        savedStateHandle[OPERATION_KEY] = value.name
    }

    private fun persistMessages(status: String, error: String?) {
        savedStateHandle[STATUS_KEY] = status
        if (error == null) savedStateHandle.remove<String>(ERROR_KEY) else savedStateHandle[ERROR_KEY] = error
    }

    private fun persistImage(image: ExportedImage) {
        savedStateHandle[EXPORTED_PATH_KEY] = image.file.absolutePath
        savedStateHandle[EXPORTED_WIDTH_KEY] = image.width
        savedStateHandle[EXPORTED_HEIGHT_KEY] = image.height
        savedStateHandle[EXPORTED_MIME_KEY] = image.mimeType
    }

    private fun clearPersistedImage() {
        savedStateHandle.remove<String>(EXPORTED_PATH_KEY)
        savedStateHandle.remove<Int>(EXPORTED_WIDTH_KEY)
        savedStateHandle.remove<Int>(EXPORTED_HEIGHT_KEY)
        savedStateHandle.remove<String>(EXPORTED_MIME_KEY)
    }

    companion object {
        const val PROJECT_ID_KEY = "projectId"
        const val MULTIPLIER_KEY = "exportMultiplier"
        const val FILE_NAME_KEY = "exportFileName"
        const val MEASURED_HEIGHT_KEY = "exportMeasuredHeight"
        const val OPERATION_KEY = "exportOperation"
        const val STATUS_KEY = "exportStatus"
        const val ERROR_KEY = "exportError"
        const val EXPORTED_PATH_KEY = "exportedPath"
        const val EXPORTED_WIDTH_KEY = "exportedWidth"
        const val EXPORTED_HEIGHT_KEY = "exportedHeight"
        const val EXPORTED_MIME_KEY = "exportedMime"
        private const val MAX_FILE_NAME_LENGTH = 80
        private val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|]+")

        internal fun defaultFileName(title: String, timestamp: Long): String {
            val safe = title.ifBlank { "lyrics-card" }.replace(INVALID_FILE_CHARS, "-").take(48)
            val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(timestamp))
            return "$safe-$date.png"
        }

        private fun restoreExportedImage(handle: SavedStateHandle): ExportedImage? {
            val path = handle.get<String>(EXPORTED_PATH_KEY) ?: return null
            val file = File(path).takeIf(File::isFile) ?: return null
            val width = handle.get<Int>(EXPORTED_WIDTH_KEY)?.takeIf { it > 0 } ?: return null
            val height = handle.get<Int>(EXPORTED_HEIGHT_KEY)?.takeIf { it > 0 } ?: return null
            return ExportedImage(
                file = file,
                width = width,
                height = height,
                mimeType = handle[EXPORTED_MIME_KEY] ?: "image/png",
            )
        }
    }
}
