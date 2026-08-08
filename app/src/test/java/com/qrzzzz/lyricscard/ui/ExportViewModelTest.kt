package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import com.qrzzzz.lyricscard.R
import androidx.lifecycle.SavedStateHandle
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicBoolean
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
        assertEquals(ExportOperationState.RENDERING, viewModel.uiState.value.operation)

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
        assertEquals(UiText.resource(R.string.export_interrupted_error), viewModel.uiState.value.errorMessage)

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
        val first = ExportViewModel(
            handle,
            store,
            preferences,
            renderer,
            exportFiles,
            clock = { 0L },
            previewDecoder = successfulPreviewDecoder(),
        )
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
                ExportViewModel.STATUS_KEY to handle.get<UiText>(ExportViewModel.STATUS_KEY),
                ExportViewModel.ERROR_KEY to handle.get<UiText>(ExportViewModel.ERROR_KEY),
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
            previewDecoder = successfulPreviewDecoder(),
        )
        runCurrent()

        assertEquals(2, restored.uiState.value.multiplier)
        assertEquals("my-card.png", restored.uiState.value.fileName)
        assertEquals(ExportOperationState.SUCCESS, restored.uiState.value.operation)
        assertEquals(path, restored.uiState.value.exported?.file?.absolutePath)
        assertEquals(UiText.resource(R.string.export_saved), restored.uiState.value.status)
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

        val viewModel = exportViewModel(
            project,
            handle = handle,
            previewDecoder = ExportPreviewDecoder { ExportPreviewDecodeResult.Missing },
        )
        runCurrent()

        assertEquals(ExportOperationState.FAILURE, viewModel.uiState.value.operation)
        assertNull(viewModel.uiState.value.exported)
        assertNotNull(viewModel.uiState.value.errorMessage)
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
        assertEquals(UiText.resource(R.string.export_failure), first.uiState.value.errorMessage)

        val restoredHandle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.OPERATION_KEY to handle.get<String>(ExportViewModel.OPERATION_KEY),
                ExportViewModel.STATUS_KEY to handle.get<UiText>(ExportViewModel.STATUS_KEY),
                ExportViewModel.ERROR_KEY to handle.get<UiText>(ExportViewModel.ERROR_KEY),
            ),
        )
        val restoredRenderer = FakeRendererOperations()
        val restored = exportViewModel(project, renderer = restoredRenderer, handle = restoredHandle)
        runCurrent()

        assertEquals(ExportOperationState.FAILURE, restored.uiState.value.operation)
        assertEquals(UiText.resource(R.string.export_failed_retryable), restored.uiState.value.status)
        assertEquals(UiText.resource(R.string.export_failure), restored.uiState.value.errorMessage)
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

    @Test
    fun cancelDuringDelayedThumbnailCannotInterruptFinalization() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-finalizing")
        val store = FakeProjectStore(listOf(project))
        val thumbnailStarted = CompletableDeferred<Unit>()
        val releaseThumbnail = CompletableDeferred<Unit>()
        val exportFiles = FakeExportFiles().apply {
            createThumbnailBlock = { projectId, image ->
                thumbnailStarted.complete(Unit)
                releaseThumbnail.await()
                File(image.file.parentFile, "$projectId-thumbnail.png").absolutePath
            }
        }
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val viewModel = exportViewModel(project, store, renderer, exportFiles)
        runCurrent()

        viewModel.save()
        runCurrent()
        assertTrue(thumbnailStarted.isCompleted)
        assertEquals(ExportOperationState.FINALIZING, viewModel.uiState.value.operation)
        assertFalse(viewModel.uiState.value.canCancel)

        viewModel.cancelExport()
        runCurrent()
        assertEquals(ExportOperationState.FINALIZING, viewModel.uiState.value.operation)

        releaseThumbnail.complete(Unit)
        advanceUntilIdle()
        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertEquals(1, renderer.exportCalls)
        assertEquals(1, exportFiles.thumbnailCalls)
        assertEquals(1, store.recordExportCalls)
    }

    @Test
    fun metadataFailureIsVisibleAndDoesNotHalfCommitProjectFields() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-metadata-warning")
        val store = FakeProjectStore(listOf(project)).apply {
            recordExportFailure = IllegalStateException("metadata unavailable")
        }
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val viewModel = exportViewModel(project, store, renderer)
        runCurrent()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertNotNull(viewModel.uiState.value.exported)
        assertEquals(UiText.resource(R.string.export_metadata_warning), viewModel.uiState.value.errorMessage)
        val stored = store.getProject(project.id)
        assertNull(stored?.thumbnailPath)
        assertNull(stored?.lastExportedAt)
    }

    @Test
    fun thumbnailFailureCanRetryWithoutDuplicateAutomaticExport() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-thumbnail-retry")
        val store = FakeProjectStore(listOf(project))
        val exportFiles = FakeExportFiles().apply {
            createThumbnailBlock = { _, _ -> throw IllegalStateException("atomic thumbnail failed") }
        }
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val viewModel = exportViewModel(project, store, renderer, exportFiles)
        runCurrent()

        viewModel.save()
        advanceUntilIdle()
        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertEquals(1, renderer.exportCalls)
        assertEquals(0, store.recordExportCalls)
        assertEquals(UiText.resource(R.string.export_metadata_warning), viewModel.uiState.value.errorMessage)

        exportFiles.createThumbnailBlock = { projectId, image ->
            File(image.file.parentFile, "$projectId-thumbnail.png").absolutePath
        }
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, renderer.exportCalls)
        assertEquals(2, exportFiles.thumbnailCalls)
        assertEquals(1, store.recordExportCalls)
        assertEquals(ExportOperationState.SUCCESS, viewModel.uiState.value.operation)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun restoredFinalizingOperationIsInterruptedWithoutDuplicateExport() = runTest(mainDispatcherRule.dispatcher) {
        val project = project("export-finalizing-restore")
        val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
        val handle = SavedStateHandle(
            mapOf(
                ExportViewModel.PROJECT_ID_KEY to project.id,
                ExportViewModel.OPERATION_KEY to ExportOperationState.FINALIZING.name,
            ),
        )

        val restored = exportViewModel(project, renderer = renderer, handle = handle)
        runCurrent()

        assertEquals(ExportOperationState.INTERRUPTED, restored.uiState.value.operation)
        assertEquals(0, renderer.exportCalls)
        assertNull(restored.uiState.value.exported)
    }

    private fun exportViewModel(
        project: Project,
        store: FakeProjectStore = FakeProjectStore(listOf(project)),
        renderer: FakeRendererOperations = FakeRendererOperations(),
        exportFiles: FakeExportFiles = FakeExportFiles(),
        handle: SavedStateHandle = SavedStateHandle(mapOf(ExportViewModel.PROJECT_ID_KEY to project.id)),
        previewDecoder: ExportPreviewDecoder = successfulPreviewDecoder(),
    ) = ExportViewModel(
        savedStateHandle = handle,
        projects = store,
        preferences = FakePreferencesStore(),
        renderer = renderer,
        exportFiles = exportFiles,
        clock = { 0L },
        previewDecoder = previewDecoder,
    )

    private fun project(id: String): Project = ProjectTemplates.blank(id = id, now = 1L)

    private fun image(): ExportedImage {
        val file = File.createTempFile("lyrics-card-export-", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        return ExportedImage(file = file, width = 1080, height = 1350)
    }

    @Test
    fun multiplierIsRestrictedToOneOrTwoAndInvalidValuesCannotAddAThirdMode() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = project("export-scale-contract")
            val viewModel = exportViewModel(project)
            runCurrent()

            viewModel.setMultiplier(-20)
            assertEquals(1, viewModel.uiState.value.multiplier)
            viewModel.setMultiplier(20)
            assertEquals(2, viewModel.uiState.value.multiplier)
        }

    @Test
    fun cancelledSafDestinationKeepsResultAndPublishesUnderstandableStatus() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = project("export-saf-cancel")
            val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
            val viewModel = exportViewModel(project, renderer = renderer)
            runCurrent()

            viewModel.retry()
            advanceUntilIdle()
            val exported = viewModel.uiState.value.exported

            viewModel.saveTo(null)

            assertEquals(exported, viewModel.uiState.value.exported)
            assertEquals(UiText.resource(R.string.export_save_cancelled), viewModel.uiState.value.status)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun safWriteFailureKeepsGeneratedResultAndNeverExposesExceptionOrDestination() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = project("export-saf-failure")
            val exportFiles = FakeExportFiles().apply {
                copyFailure = SecurityException("content://private/secret and raw storage detail")
            }
            val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
            val viewModel = exportViewModel(project, renderer = renderer, exportFiles = exportFiles)
            runCurrent()

            viewModel.retry()
            advanceUntilIdle()
            val exported = viewModel.uiState.value.exported
            viewModel.saveTo(android.net.Uri.parse("content://private/secret"))
            advanceUntilIdle()

            assertEquals(exported, viewModel.uiState.value.exported)
            assertEquals(
                UiText.resource(R.string.export_save_failed_retryable),
                viewModel.uiState.value.errorMessage,
            )
        }

    @Test
    fun replacingResultCancelsStalePreviewAndRecyclesPreviousBitmap() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = project("export-preview-replace")
            val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
            val firstStarted = CompletableDeferred<Unit>()
            val firstCancelled = AtomicBoolean(false)
            var decodeCalls = 0
            val readyBitmap = Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888)
            val decoder = ExportPreviewDecoder {
                decodeCalls += 1
                if (decodeCalls == 1) {
                    firstStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (cause: CancellationException) {
                        firstCancelled.set(true)
                        throw cause
                    }
                }
                ExportPreviewDecodeResult.Success(readyBitmap)
            }
            val viewModel = exportViewModel(
                project,
                renderer = renderer,
                previewDecoder = decoder,
            )
            runCurrent()

            viewModel.retry()
            runCurrent()
            assertTrue(firstStarted.isCompleted)
            assertEquals(ExportPreviewPhase.LOADING, viewModel.uiState.value.preview.phase)

            viewModel.setMultiplier(1)
            runCurrent()
            assertTrue(firstCancelled.get())
            assertNull(viewModel.uiState.value.exported)
            assertEquals(ExportPreviewPhase.EMPTY, viewModel.uiState.value.preview.phase)

            viewModel.retry()
            advanceUntilIdle()
            assertEquals(ExportPreviewPhase.READY, viewModel.uiState.value.preview.phase)
            assertEquals(readyBitmap, viewModel.uiState.value.preview.bitmap)

            viewModel.setMultiplier(2)
            assertTrue(readyBitmap.isRecycled)
        }

    @Test
    fun corruptPngPreviewInvalidatesSuccessWithoutDispatchingExternalAction() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = project("export-corrupt-preview")
            val renderer = FakeRendererOperations().apply { exportBlock = { _, _ -> image() } }
            val viewModel = exportViewModel(
                project,
                renderer = renderer,
                previewDecoder = ExportPreviewDecoder { ExportPreviewDecodeResult.InvalidPng },
            )
            runCurrent()

            viewModel.share()
            advanceUntilIdle()

            assertEquals(ExportOperationState.FAILURE, viewModel.uiState.value.operation)
            assertNull(viewModel.uiState.value.exported)
            assertEquals(ExportPreviewPhase.ERROR, viewModel.uiState.value.preview.phase)
            assertEquals(UiText.resource(R.string.export_result_invalid_png), viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.effect)
        }

    private fun successfulPreviewDecoder() = ExportPreviewDecoder {
        ExportPreviewDecodeResult.Success(
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
        )
    }
}
