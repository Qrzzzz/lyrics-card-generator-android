package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.model.CanvasSpec
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TEST_TIMEOUT_MS = 75L
private val TEST_SPEC = RenderSpec(
    canvas = CanvasSpec(
        width = 1040,
        height = 1080,
        autoHeight = false,
        pixelRatio = 1,
    ),
)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RendererControllerRecoveryTest {
    @Test
    fun `hung export times out rebuilds session ignores late messages and immediately retries`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportDir = File(context.cacheDir, "exports")
        deletePartFiles(exportDir)
        val bridge = FakeRendererBridge(successPng())
        val controller = RendererController(context, ProjectAssetStore(context), TEST_TIMEOUT_MS, bridge)

        try {
            val oldView = controller.acquireWebView(context, Any())
            val observed = bridge.observeNextExport()
            val result = async { runCatching { controller.exportPng(TEST_SPEC, 1) } }
            val oldAttempt = observed.await()
            runCurrent()

            assertTrue(partFiles(exportDir).isNotEmpty())
            advanceTimeBy(TEST_TIMEOUT_MS + 1)
            runCurrent()

            val failure = result.await().exceptionOrNull()
            assertTrue(failure is RendererException)
            assertTrue(failure?.message?.contains("导出超时") == true)
            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
            assertEquals(1, controller.generation.value)
            assertTrue(partFiles(exportDir).isEmpty())

            bridge.emitLateExportMessages(oldAttempt)
            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
            assertEquals(1, controller.generation.value)
            assertTrue(partFiles(exportDir).isEmpty())

            bridge.mode = FakeRendererBridge.Mode.SUCCESS
            val newView = controller.acquireWebView(context, Any())
            assertNotSame(oldView, newView)
            val image = controller.exportPng(TEST_SPEC, 1)

            assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
            assertEquals(TEST_SPEC.canvas.width, image.width)
            assertEquals(TEST_SPEC.canvas.height, image.height)
            assertTrue(image.file.isFile)
            assertTrue(partFiles(exportDir).isEmpty())
            image.file.delete()
        } finally {
            controller.close()
            deletePartFiles(exportDir)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `user cancellation rebuilds session cleans part and immediately retries`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportDir = File(context.cacheDir, "exports")
        deletePartFiles(exportDir)
        val bridge = FakeRendererBridge(successPng())
        val controller = RendererController(context, ProjectAssetStore(context), TEST_TIMEOUT_MS, bridge)

        try {
            controller.acquireWebView(context, Any())
            val observed = bridge.observeNextExport()
            val exportJob = async { controller.exportPng(TEST_SPEC, 1) }
            observed.await()
            runCurrent()

            assertTrue(partFiles(exportDir).isNotEmpty())
            exportJob.cancelAndJoin()
            runCurrent()

            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
            assertEquals(1, controller.generation.value)
            assertTrue(partFiles(exportDir).isEmpty())

            controller.acquireWebView(context, Any())
            val secondObserved = bridge.observeNextExport()
            val secondExportJob = async { controller.exportPng(TEST_SPEC, 1) }
            secondObserved.await()
            secondExportJob.cancelAndJoin()
            runCurrent()

            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
            assertEquals(2, controller.generation.value)
            assertTrue(partFiles(exportDir).isEmpty())

            bridge.mode = FakeRendererBridge.Mode.SUCCESS
            controller.acquireWebView(context, Any())
            val image = controller.exportPng(TEST_SPEC, 1)

            assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
            assertTrue(image.file.isFile)
            assertTrue(partFiles(exportDir).isEmpty())
            image.file.delete()
        } finally {
            controller.close()
            deletePartFiles(exportDir)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `repeated process loss retry and close are idempotent`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exportDir = File(context.cacheDir, "exports")
        deletePartFiles(exportDir)
        val bridge = FakeRendererBridge(successPng())
        val controller = RendererController(context, ProjectAssetStore(context), TEST_TIMEOUT_MS, bridge)

        try {
            val firstView = controller.acquireWebView(context, Any())
            val observed = bridge.observeNextExport()
            val pendingExport = async { runCatching { controller.exportPng(TEST_SPEC, 1) } }
            observed.await()
            assertTrue(partFiles(exportDir).isNotEmpty())

            assertTrue(controller.handleRenderProcessGone(firstView, didCrash = true))
            assertTrue(pendingExport.await().isFailure)
            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
            assertEquals(1, controller.generation.value)
            assertTrue(partFiles(exportDir).isEmpty())

            assertTrue(controller.handleRenderProcessGone(firstView, didCrash = true))
            controller.retry()
            assertEquals(1, controller.generation.value)

            val recoveredView = controller.acquireWebView(context, Any())
            assertNotSame(firstView, recoveredView)
            assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)

            controller.retry()
            val retryGeneration = controller.generation.value
            controller.retry()
            assertEquals(retryGeneration, controller.generation.value)

            controller.close()
            val closedGeneration = controller.generation.value
            controller.close()
            controller.retry()
            assertEquals(closedGeneration, controller.generation.value)
        } finally {
            controller.close()
            deletePartFiles(exportDir)
            Dispatchers.resetMain()
        }
    }

    private fun successPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(
            TEST_SPEC.canvas.width,
            TEST_SPEC.canvas.height,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.rgb(44, 32, 72))
        return ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun partFiles(exportDir: File): List<File> =
        exportDir.listFiles()
            ?.filter { it.name.endsWith(".part") }
            .orEmpty()

    private fun deletePartFiles(exportDir: File) {
        partFiles(exportDir).forEach { assertTrue(it.delete()) }
    }

}

private class FakeRendererBridge(
    private val pngBytes: ByteArray,
) : RendererBridge {
    enum class Mode { HANG_AFTER_CHUNK, SUCCESS }

    data class Attempt(
        val session: Session,
        val requestId: String,
    )

    data class Session(
        val view: WebView,
        val receive: (String) -> Unit,
    )

    var mode = Mode.HANG_AFTER_CHUNK
    private val sessions = mutableListOf<Session>()
    private var nextExport = CompletableDeferred<Attempt>()

    override fun attach(view: WebView, onMessage: (String) -> Unit): Boolean {
        val session = Session(view, onMessage)
        sessions += session
        emit(
            session,
            RendererEnvelope(
                requestId = "ready-${sessions.size}",
                type = "ready",
            ),
        )
        return true
    }

    override fun send(view: WebView, envelope: RendererEnvelope) {
        val session = sessions.last { it.view === view }
        when (envelope.type) {
            "setSpec" -> emit(session, RendererEnvelope(requestId = envelope.requestId, type = "specApplied"))
            "measure" -> emit(
                session,
                RendererEnvelope(
                    requestId = envelope.requestId,
                    type = "measured",
                    payload = buildJsonObject {
                        put("width", TEST_SPEC.canvas.width)
                        put("height", TEST_SPEC.canvas.height)
                    },
                ),
            )
            "exportPng" -> {
                emit(session, RendererEnvelope(requestId = envelope.requestId, type = "exportStarted"))
                if (mode == Mode.HANG_AFTER_CHUNK) {
                    val partial = pngBytes.copyOfRange(0, minOf(8, pngBytes.size))
                    emit(
                        session,
                        RendererEnvelope(
                            requestId = envelope.requestId,
                            type = "exportChunk",
                            payload = chunkPayload(index = 0, total = 2, bytes = partial),
                        ),
                    )
                    nextExport.complete(Attempt(session, envelope.requestId))
                } else {
                    emitSuccessfulExport(session, envelope.requestId)
                }
            }
        }
    }

    override fun detach(view: WebView) = Unit

    fun observeNextExport(): CompletableDeferred<Attempt> =
        CompletableDeferred<Attempt>().also { nextExport = it }

    fun emitLateExportMessages(attempt: Attempt) {
        emit(
            attempt.session,
            RendererEnvelope(
                requestId = attempt.requestId,
                type = "exportChunk",
                payload = chunkPayload(index = 1, total = 2, bytes = byteArrayOf()),
            ),
        )
        emit(
            attempt.session,
            RendererEnvelope(
                requestId = attempt.requestId,
                type = "exportCompleted",
                payload = buildJsonObject {
                    put("mimeType", "image/png")
                    put("width", TEST_SPEC.canvas.width)
                    put("height", TEST_SPEC.canvas.height)
                    put("totalBytes", 8)
                    put("totalChunks", 2)
                },
            ),
        )
        emit(
            attempt.session,
            RendererEnvelope(
                requestId = attempt.requestId,
                type = "renderError",
                payload = buildJsonObject { put("message", "late old-session error") },
            ),
        )
    }

    private fun emitSuccessfulExport(session: Session, requestId: String) {
        val chunks = pngBytes.asList().chunked(EXPORT_CHUNK_SIZE).map { it.toByteArray() }
        chunks.forEachIndexed { index, bytes ->
            emit(
                session,
                RendererEnvelope(
                    requestId = requestId,
                    type = "exportChunk",
                    payload = chunkPayload(index, chunks.size, bytes),
                ),
            )
        }
        emit(
            session,
            RendererEnvelope(
                requestId = requestId,
                type = "exportCompleted",
                payload = buildJsonObject {
                    put("mimeType", "image/png")
                    put("width", TEST_SPEC.canvas.width)
                    put("height", TEST_SPEC.canvas.height)
                    put("totalBytes", pngBytes.size)
                    put("totalChunks", chunks.size)
                },
            ),
        )
    }

    private fun chunkPayload(index: Int, total: Int, bytes: ByteArray) = buildJsonObject {
        put("index", index)
        put("total", total)
        put("byteLength", bytes.size)
        put("base64", Base64.getEncoder().encodeToString(bytes))
    }

    private fun emit(session: Session, envelope: RendererEnvelope) {
        session.receive(RenderSpecJson.format.encodeToString(envelope))
    }

    private companion object {
        const val EXPORT_CHUNK_SIZE = 384 * 1024
    }
}
