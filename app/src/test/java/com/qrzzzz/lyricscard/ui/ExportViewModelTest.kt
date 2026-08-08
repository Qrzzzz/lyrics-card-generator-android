package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.SavedStateHandle
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun repeatedActionWhileOperationIsRunningDoesNotDuplicateExport() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-once")
        val renderer = FakeRendererOperations()
        val completion = CompletableDeferred<ExportedImage>()
        renderer.exportBlock = { _, _ -> completion.await() }
        val viewModel = exportViewModel(project, renderer = renderer)
        runCurrent()

        viewModel.save()
        viewModel.share()
        runCurrent()

        assertEquals(1, renderer.exportCalls)
        assertEquals(ExportOperationState.RUNNING, viewModel.uiState.value.operation)

        completion.complete(image())
        advanceUntilIdle()

        assertEquals(1, renderer.exportCalls)
        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertEquals(ExportPendingAction.SAVE, viewModel.uiState.value.effect?.action)
    }

    @Test
    fun cancelThenRetryUsesRendererRecoveryAndStartsExactlyOneNewExport() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-cancel")
        val renderer = FakeRendererOperations()
        renderer.exportBlock = { _, _ -> awaitCancellation() }
        val viewModel = exportViewModel(project, renderer = renderer)
        runCurrent()

        viewModel.save()
        runCurrent()
        viewModel.cancelExport()
        runCurrent()

        assertEquals(ExportOperationState.CANCELLED, viewModel.uiState.value.operation)
        assertEquals(1, renderer.exportCalls)

        renderer.exportBlock = { _, _ -> image() }
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(1, renderer.retryCalls)
        assertEquals(2, renderer.exportCalls)
        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertNotNull(viewModel.uiState.value.exported)
    }

    @Test
    fun restoredRunningOperationBecomesInterruptedWithoutAutomaticExport() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-interrupted")
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val staleImage = image()
        val handle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.OPERATION_KEY to ExportOperationState.RUNNING.name,
                ExportViewModel.MULTIPLIER_KEY to 2,
                ExportViewModel.FILE_NAME_KEY to "restored.png",
                ExportViewModel.EXPORTED_PATH_KEY to staleImage.file.absolutePath,
                ExportViewModel.EXPORTED_WIDTH_KEY to staleImage.width,
                ExportViewModel.EXPORTED_HEIGHT_KEY to staleImage.height,
            ),
        )

        val viewModel = exportViewModel(project, renderer = renderer, handle = handle)
        runCurrent()

        assertEquals(ExportOperationState.INTERRUPTED, viewModel.uiState.value.operation)
        assertEquals(0, renderer.exportCalls)
        assertNull(viewModel.uiState.value.exported)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("中断"))

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, renderer.retryCalls)
        assertEquals(1, renderer.exportCalls)
    }

    @Test
    fun multiplierFilenameAndSuccessfulFileMetadataRestore() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-config")
        val store = FakeProjectStore(listOf(project))
        val preferences = FakePreferencesStore(UserPreferences(defaultExportScale = 1))
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val exportFiles = FakeExportFiles()
        val handle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to project.id))
        val first = ExportViewModel(handle, store, preferences, renderer, exportFiles, clock = { 0L })
        runCurrent()

        first.setMultiplier(2)
        first.setFileName("my-card.png")
        first.retry()
        advanceUntilIdle()
        first.saveTo(android.net.Uri.parse("content://test/export.png"))
        advanceUntilIdle()
        val path = first.uiState.value.exported?.file?.absolutePath

        val restoredHandle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.MULTIPLIER_KEY to handle.get<Int>(ExportViewModel.MULTIPLIER_KEY),
                ExportViewModel.FILE_NAME_KEY to handle.get<String>(ExportViewModel.FILE_NAME_KEY),
                ExportViewModel.MEASURED_HEIGHT_KEY to handle.get<Int>(ExportViewModel.MEASURED_HEIGHT_KEY),
                ExportViewModel.OPERATION_KEY to handle.get<String>(ExportViewModel.OPERATION_KEY),
                ExportViewModel.EXPORTED_PATH_KEY to handle.get<String>(ExportViewModel.EXPORTED_PATH_KEY),
                ExportViewModel.EXPORTED_WIDTH_KEY to handle.get<Int>(ExportViewModel.EXPORTED_WIDTH_KEY),
                ExportViewModel.EXPORTED_HEIGHT_KEY to handle.get<Int>(ExportViewModel.EXPORTED_HEIGHT_KEY),
                ExportViewModel.EXPORTED_MIME_KEY to handle.get<String>(ExportViewModel.EXPORTED_MIME_KEY),
                ExportViewModel.STATUS_KEY to handle.get<String>(ExportViewModel.STATUS_KEY),
                ExportViewModel.ERROR_KEY to handle.get<String>(ExportViewModel.ERROR_KEY),
            ),
        )
        val restoredRenderer = FakeRendererOperations()
        val restored = ExportViewModel(
            restoredHandle,
            store,
            preferences,
            restoredRenderer,
            FakeExportFiles(),
            clock = { 0L },
        )
        runCurrent()

        assertEquals(2, restored.uiState.value.multiplier)
        assertEquals("my-card.png", restored.uiState.value.fileName)
        assertEquals(ExportOperationState.SUCCESS, restored.uiState.value.operation)
        assertEquals(path, restored.uiState.value.exported?.file?.absolutePath)
        assertEquals("已保存到所选位置", restored.uiState.value.status)
        assertEquals(0, restoredRenderer.exportCalls)
    }

    @Test
    fun missingRestoredResultFileIsFailureNotFalseSuccess() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-missing")
        val missing = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.png")
        val handle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.OPERATION_KEY to ExportOperationState.SUCCESS.name,
                ExportViewModel.EXPORTED_PATH_KEY to missing.absolutePath,
                ExportViewModel.EXPORTED_WIDTH_KEY to 100,
                ExportViewModel.EXPORTED_HEIGHT_KEY to 100,
            ),
        )

        val viewModel = exportViewModel(project, handle = handle)
        runCurrent()

        assertEquals(ExportOperationState.FAILURE, viewModel.uiState.value.operation)
        assertNull(viewModel.uiState.value.exported)
        assertFalse(viewModel.uiState.value.errorMessage.isNullOrBlank())
    }

    @Test
    fun rendererFailureReasonRestoresWithoutBeingReplacedByMissingFileMessage() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-failure-reason")
        val handle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to project.id))
        val firstRenderer = FakeRendererOperations().apply {
            exportBlock = { _, _ -> error("renderer session lost") }
        }
        val first = exportViewModel(project, renderer = firstRenderer, handle = handle)
        runCurrent()

        first.save()
        advanceUntilIdle()

        assertEquals(ExportOperationState.FAILURE, first.uiState.value.operation)
        assertEquals("renderer session lost", first.uiState.value.errorMessage)

        val restoredHandle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.OPERATION_KEY to handle.get<String>(ExportViewModel.OPERATION_KEY),
                ExportViewModel.STATUS_KEY to handle.get<String>(ExportViewModel.STATUS_KEY),
                ExportViewModel.ERROR_KEY to handle.get<String>(ExportViewModel.ERROR_KEY),
            ),
        )
        val restoredRenderer = FakeRendererOperations()
        val restored = exportViewModel(project, renderer = restoredRenderer, handle = restoredHandle)
        runCurrent()

        assertEquals(ExportOperationState.FAILURE, restored.uiState.value.operation)
        assertEquals("导出失败，可重试", restored.uiState.value.status)
        assertEquals("renderer session lost", restored.uiState.value.errorMessage)
        assertEquals(0, restoredRenderer.exportCalls)
    }

    @Test
    fun blankFilenameDraftRestoresInsteadOfBeingReplacedWithDefault() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-blank-filename")
        val handle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.FILE_NAME_KEY to "",
            ),
        )

        val restored = exportViewModel(project, handle = handle)
        runCurrent()

        assertEquals("", restored.uiState.value.fileName)
        assertEquals("", handle.get<String>(ExportViewModel.FILE_NAME_KEY))
    }

    private fun exportViewModel(
        project: Project,
        renderer: FakeRendererOperations = FakeRendererOperations(),
        handle: SavedStateHandle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to project.id)),
    ) = ExportViewModel(
        savedStateHandle = handle,
        projects = FakeProjectStore(listOf(project)),
        preferences = FakePreferencesStore(),
        renderer = renderer,
        exportFiles = FakeExportFiles(),
        clock = { 0L },
    )

    private fun project(id: String): Project = ProjectTemplates.blank(id = id, now = 1L)

    private fun image(): ExportedImage {
        val file = File.createTempFile("lyrics-card-export-", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        return ExportedImage(file = file, width = 1080, height = 1350)
    }
}
