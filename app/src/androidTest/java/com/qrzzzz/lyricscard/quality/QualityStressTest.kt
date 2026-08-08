package com.qrzzzz.lyricscard.quality

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.RendererOperations
import com.qrzzzz.lyricscard.model.GridDensity
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.TextAlignment
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererStatus
import com.qrzzzz.lyricscard.ui.EditorViewModel
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class QualityStressTest {
    private val appContext = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun a_twentyConsecutiveTwoXExportsReturnToTheWarmedMemoryEnvelope() {
        val assetStore = ProjectAssetStore(appContext)
        val controller = RendererController(appContext, assetStore)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val binding = RendererBinding(controller)
        val exportDirectory = File(appContext.cacheDir, "exports")
        val filesBefore = exportDirectory.listFiles().orEmpty().map(File::getCanonicalPath).toSet()
        val created = mutableListOf<File>()
        val strictModeViolations = AtomicInteger(0)
        val violationExecutor = Executors.newSingleThreadExecutor()
        var previousPolicy: StrictMode.ThreadPolicy? = null

        try {
            binding.attach()
            waitForRenderer(controller)
            val spec = releaseStressSpec()
            val measurement = runBlocking { controller.measure(spec) }
            val expectedWidth = measurement.width * 2
            val expectedHeight = measurement.height * 2

            val warmup = runBlocking { controller.exportPng(spec, 2) }
            assertPng(warmup, expectedWidth, expectedHeight)
            assertTrue("warmup export was not removed", warmup.file.delete())
            forceIdleGc()

            runOnResumedActivity { activity ->
                previousPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder(previousPolicy)
                        .detectDiskReads()
                        .detectDiskWrites()
                        .penaltyListener(violationExecutor) { violation ->
                            if (violation.stackTrace.any { frame -> frame.className.startsWith(APP_PACKAGE_PREFIX) }) {
                                strictModeViolations.incrementAndGet()
                            }
                        }
                        .build(),
                )
            }

            val before = memorySample("export-before", exportDirectory, filesBefore)
            var successful = 0
            for (attempt in 1..20) {
                val image = runBlocking { controller.exportPng(spec, 2) }
                assertPng(image, expectedWidth, expectedHeight)
                created += image.file
                successful += 1
                assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
                assertFalse(
                    "partial export remained after attempt $attempt",
                    exportDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") || it.name.endsWith(".tmp") },
                )
                if (attempt in setOf(5, 10, 20)) {
                    memorySample("export-$attempt", exportDirectory, filesBefore)
                }
            }
            assertEquals(20, successful)

            forceIdleGc()
            val idle = memorySample("export-idle-gc", exportDirectory, filesBefore)
            val pssLimit = ceil(before.totalPssKb * 1.20).toInt()
            assertTrue(
                "PSS did not return within 20 percent of the warmed baseline: before=${before.totalPssKb} idle=${idle.totalPssKb}",
                idle.totalPssKb <= pssLimit,
            )
            assertEquals("app main-thread disk I/O violations", 0, strictModeViolations.get())
            Log.i(QUALITY_TAG, "export-summary success=$successful rendererErrors=0 partialFiles=0")
        } finally {
            previousPolicy?.let { policy -> runOnMainThread { StrictMode.setThreadPolicy(policy) } }
            violationExecutor.shutdownNow()
            created.forEach { file ->
                if (file.exists()) assertTrue("stress export cleanup failed", file.delete())
            }
            closeRendererOnMain(binding, scenario, controller)
        }
    }

    @Test
    fun b_thirtyMinuteEditingEndurancePreservesAutosaveRendererAndRestorationState() {
        val application = appContext as LyricsCardApplication
        val container = application.container
        val controller = RendererController(appContext, ProjectAssetStore(appContext))
        val renderer = object : RendererOperations {
            override suspend fun exportPng(project: com.qrzzzz.lyricscard.model.Project, multiplier: Int): ExportedImage =
                controller.exportPng(project.spec, multiplier)

            override suspend fun extractPalette(assetId: String): PaletteSpec = controller.extractPalette(assetId)
            override fun retry() = controller.retry()
        }
        val project = runBlocking { container.projects.createBlank() }
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(
                savedStateHandle = SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to project.id)),
                projects = container.projects,
                projectAssets = container.projectAssets,
                neteaseClient = container.netease,
                renderer = renderer,
                sessions = container.editorSessions,
            ) as T
        }
        val editor = ViewModelProvider(store, factory)[EditorViewModel::class.java]
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val binding = RendererBinding(controller)
        val strictModeViolations = AtomicInteger(0)
        val violationExecutor = Executors.newSingleThreadExecutor()
        var previousPolicy: StrictMode.ThreadPolicy? = null

        try {
            binding.attach()
            waitForRenderer(controller)
            waitUntil(20_000) { !editor.uiState.value.isLoading && editor.uiState.value.currentProject != null }
            runOnResumedActivity { activity ->
                previousPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder(previousPolicy)
                        .detectDiskReads()
                        .detectDiskWrites()
                        .penaltyListener(violationExecutor) { violation ->
                            if (violation.stackTrace.any { frame -> frame.className.startsWith(APP_PACKAGE_PREFIX) }) {
                                strictModeViolations.incrementAndGet()
                            }
                        }
                        .build(),
                )
            }

            forceIdleGc()
            val startMemory = memorySample("edit-start")
            var peakPssKb = startMemory.totalPssKb
            var operations = 0L
            var recreations = 0
            var backgroundCycles = 0
            var lastMemoryCheckpoint = 0L
            var lastRecreation = 0L
            var lastBackground = 0L
            val startedAt = SystemClock.elapsedRealtime()

            while (SystemClock.elapsedRealtime() - startedAt < EDITING_DURATION_MS) {
                val phase = operations.toInt()
                editor.selectStep(phase % 6)
                editor.updateSearchQuery("quality-edit-${phase % 1000}")
                editor.updateProjectName("质量耐久项目 ${phase % 97}")
                editor.updateSpec { spec -> mutateForEndurance(spec, phase) }

                val current = checkNotNull(editor.uiState.value.currentProject).spec
                repeat(3) { pulse ->
                    controller.updateSpec(
                        current.copy(
                            typography = current.typography.copy(
                                lyricSize = 42 + ((phase + pulse) % 15),
                            ),
                        ),
                    )
                }
                if (phase % 4 == 0) editor.undo()
                if (phase % 4 == 1) editor.redo()

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed - lastBackground >= BACKGROUND_INTERVAL_MS) {
                    backgroundAndResume(scenario)
                    backgroundCycles += 1
                    lastBackground = elapsed
                }
                if (elapsed - lastRecreation >= RECREATION_INTERVAL_MS) {
                    rotateAndReattach(binding, controller, recreations)
                    recreations += 1
                    lastRecreation = elapsed
                }
                if (elapsed - lastMemoryCheckpoint >= MEMORY_INTERVAL_MS) {
                    val sample = memorySample("edit-${elapsed / 60_000}m")
                    peakPssKb = maxOf(peakPssKb, sample.totalPssKb)
                    lastMemoryCheckpoint = elapsed
                }

                operations += 1
                SystemClock.sleep(if (phase % 8 == 0) 650 else 35)
            }

            val duration = SystemClock.elapsedRealtime() - startedAt
            assertTrue("endurance run was shorter than 30 minutes: $duration", duration >= EDITING_DURATION_MS)
            assertTrue("final autosave failed", runBlocking { editor.flushAutosave() })
            val expected = checkNotNull(editor.uiState.value.currentProject)
            val persisted = runBlocking { container.projects.getProject(project.id) }
            assertNotNull(persisted)
            assertEquals(expected.name, persisted?.name)
            assertEquals(expected.spec, persisted?.spec)

            controller.updateSpec(expected.spec)
            waitForRenderer(controller)
            forceIdleGc()
            val endMemory = memorySample("edit-idle-gc")
            peakPssKb = maxOf(peakPssKb, endMemory.totalPssKb)
            val pssLimit = ceil(startMemory.totalPssKb * 1.20).toInt()
            assertTrue(
                "editing PSS did not return within 20 percent: start=${startMemory.totalPssKb} end=${endMemory.totalPssKb}",
                endMemory.totalPssKb <= pssLimit,
            )
            assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
            assertEquals("app main-thread disk I/O violations", 0, strictModeViolations.get())
            Log.i(
                QUALITY_TAG,
                "edit-summary durationMs=$duration operations=$operations recreations=$recreations " +
                    "backgroundCycles=$backgroundCycles startPssKb=${startMemory.totalPssKb} " +
                    "peakPssKb=$peakPssKb endPssKb=${endMemory.totalPssKb} rendererErrors=0 autosave=consistent",
            )
        } finally {
            previousPolicy?.let { policy -> runOnMainThread { StrictMode.setThreadPolicy(policy) } }
            violationExecutor.shutdownNow()
            closeRendererOnMain(binding, scenario, controller)
            store.clear()
            runBlocking { container.projects.delete(project.id) }
        }
    }

    @Test
    fun c_largeCoverImportDownsamplesPreviewsAndExportsOnFourGbDevice() {
        val assetStore = ProjectAssetStore(appContext)
        val controller = RendererController(appContext, assetStore)
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val binding = RendererBinding(controller)
        val sourceFile = File(appContext.cacheDir, "quality-large-cover.jpg")
        var assetId: String? = null
        var exported: ExportedImage? = null

        try {
            binding.attach()
            waitForRenderer(controller)
            updateSpecAndAwaitPreview(controller, releaseStressSpec())
            forceIdleGc()
            val before = memorySample("large-cover-before")

            writeLargeDeterministicCover(sourceFile)
            val sourceBounds = decodeBounds(sourceFile)
            assertEquals(LARGE_COVER_EDGE, sourceBounds.first)
            assertEquals(LARGE_COVER_EDGE, sourceBounds.second)
            val sourceBytes = sourceFile.length()
            assertTrue("large cover source was empty", sourceBytes > 0L)

            assetId = runBlocking { assetStore.importCover(Uri.fromFile(sourceFile)) }
            val response = checkNotNull(assetStore.openForWebView(checkNotNull(assetId))) {
                "stored cover was unavailable to the renderer"
            }
            val stored = response.data.use { input -> BitmapFactory.decodeStream(input) }
            assertNotNull("stored cover did not decode", stored)
            checkNotNull(stored).useRecycled {
                assertEquals(STORED_COVER_EDGE, it.width)
                assertEquals(STORED_COVER_EDGE, it.height)
            }

            val spec = releaseStressSpec().copy(
                song = releaseStressSpec().song.copy(
                    title = "Large cover quality fixture",
                    coverAssetId = assetId,
                ),
                canvas = releaseStressSpec().canvas.copy(autoHeight = false, height = 1_080),
            )
            updateSpecAndAwaitPreview(controller, spec)
            val measurement = runBlocking { controller.measure(spec) }
            val image = runBlocking { controller.exportPng(spec, 2) }
            exported = image
            assertPng(image, measurement.width * 2, measurement.height * 2)
            assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
            val exportDirectory = File(appContext.cacheDir, "exports")
            assertFalse(
                "large-cover export left a partial file",
                exportDirectory.listFiles().orEmpty().any { it.name.endsWith(".part") || it.name.endsWith(".tmp") },
            )

            updateSpecAndAwaitPreview(controller, spec.copy(song = spec.song.copy(coverAssetId = null)))
            runBlocking { assetStore.delete(checkNotNull(assetId)) }
            assetId = null
            assertTrue("large cover source cleanup failed", sourceFile.delete())
            forceIdleGc()
            val idle = memorySample("large-cover-idle-gc")
            assertTrue(
                "large-cover PSS did not return within 20 percent: before=${before.totalPssKb} idle=${idle.totalPssKb}",
                idle.totalPssKb <= ceil(before.totalPssKb * 1.20).toInt(),
            )
            Log.i(
                QUALITY_TAG,
                "large-cover-summary source=${sourceBounds.first}x${sourceBounds.second} " +
                    "stored=${STORED_COVER_EDGE}x$STORED_COVER_EDGE sourceBytes=$sourceBytes " +
                    "export=${measurement.width * 2}x${measurement.height * 2} rendererErrors=0 partialFiles=0",
            )
        } finally {
            exported?.file?.let { file -> if (file.exists()) assertTrue("large-cover export cleanup failed", file.delete()) }
            assetId?.let { id -> runBlocking { assetStore.delete(id) } }
            if (sourceFile.exists()) assertTrue("large cover source cleanup failed", sourceFile.delete())
            closeRendererOnMain(binding, scenario, controller)
        }
    }

    @Test
    fun d_repeatedRotationReattachRemainsBoundedAndRendererReady() {
        val controller = RendererController(appContext, ProjectAssetStore(appContext))
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        val binding = RendererBinding(controller)

        try {
            binding.attach()
            waitForRenderer(controller)
            repeat(REATTACH_REGRESSION_CYCLES) { index ->
                rotateAndReattach(binding, controller, index)
                assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
            }
            Log.i(QUALITY_TAG, "reattach-summary cycles=$REATTACH_REGRESSION_CYCLES rendererErrors=0")
        } finally {
            closeRendererOnMain(binding, scenario, controller)
        }
    }

    private fun rotateAndReattach(
        binding: RendererBinding,
        controller: RendererController,
        index: Int,
    ) {
        binding.detach()
        val expectedOrientation = if (index % 2 == 0) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        Log.i(QUALITY_TAG, "edit-recreation-begin index=$index requested=$expectedOrientation")
        runOnResumedActivity { activity -> activity.requestedOrientation = expectedOrientation }
        waitForStableOrientation(expectedOrientation)
        binding.attach()
        waitForRenderer(controller)
        Log.i(QUALITY_TAG, "edit-recreation-ready index=$index")
    }

    private fun closeRendererOnMain(
        binding: RendererBinding,
        scenario: ActivityScenario<ComponentActivity>,
        controller: RendererController,
    ) {
        binding.close()
        var mainThreadTimedOut = false
        try {
            runOnMainThread { controller.close() }
        } catch (timeout: MainThreadActionTimeoutException) {
            mainThreadTimedOut = true
            throw timeout
        } finally {
            if (mainThreadTimedOut) {
                Log.w(QUALITY_TAG, "scenario close skipped after bounded main-thread timeout")
            } else {
                closeScenarioWithinTimeout(scenario)
            }
        }
    }

    private fun closeScenarioWithinTimeout(scenario: ActivityScenario<ComponentActivity>) {
        val startedAt = SystemClock.elapsedRealtime()
        val deadline = startedAt + MAIN_THREAD_TIMEOUT_MS
        val stopped = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val activity = runOnMainThread(remainingCleanupTime(deadline)) {
            ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>()
                .singleOrNull()
        }

        Log.i(QUALITY_TAG, "scenario-cleanup-begin hasResumedActivity=${activity != null}")
        if (activity != null) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> stopped.countDown()
                    Lifecycle.Event.ON_DESTROY -> destroyed.countDown()
                    else -> Unit
                }
            }
            runOnMainThread(remainingCleanupTime(deadline)) {
                activity.lifecycle.addObserver(observer)
                if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED) {
                    stopped.countDown()
                    destroyed.countDown()
                } else {
                    appContext.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                    )
                }
            }
            if (!stopped.await(remainingCleanupTime(deadline), TimeUnit.MILLISECONDS)) {
                throw ActivityCleanupTimeoutException(MAIN_THREAD_TIMEOUT_MS)
            }
            Log.i(
                QUALITY_TAG,
                "scenario-cleanup-background-stopped elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            runOnMainThread(remainingCleanupTime(deadline)) { activity.finishAndRemoveTask() }
            if (!destroyed.await(remainingCleanupTime(deadline), TimeUnit.MILLISECONDS)) {
                throw ActivityCleanupTimeoutException(MAIN_THREAD_TIMEOUT_MS)
            }
            Log.i(
                QUALITY_TAG,
                "scenario-cleanup-activity-destroyed elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }

        val closeResult = AtomicReference<Result<Unit>?>(null)
        val closeCompleted = CountDownLatch(1)
        Thread(
            {
                closeResult.set(runCatching { scenario.close() })
                closeCompleted.countDown()
            },
            "quality-scenario-close",
        ).apply {
            isDaemon = true
            start()
        }
        if (!closeCompleted.await(remainingCleanupTime(deadline), TimeUnit.MILLISECONDS)) {
            throw ActivityCleanupTimeoutException(MAIN_THREAD_TIMEOUT_MS)
        }
        checkNotNull(closeResult.get()) { "scenario close completed without a result" }.getOrThrow()
        Log.i(
            QUALITY_TAG,
            "scenario-cleanup-complete elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
    }

    private fun remainingCleanupTime(deadline: Long): Long {
        val remaining = deadline - SystemClock.elapsedRealtime()
        if (remaining <= 0L) throw ActivityCleanupTimeoutException(MAIN_THREAD_TIMEOUT_MS)
        return remaining
    }

    private fun releaseStressSpec(): RenderSpec = RenderSpec().copy(
        content = RenderSpec().content.copy(
            lyrics = (1..16).joinToString("\n") { index -> "耐久导出行 $index" },
            translationEnabled = true,
            translation = (1..16).joinToString("\n") { index -> "Stress export line $index" },
        ),
        canvas = RenderSpec().canvas.copy(pixelRatio = 2),
    )

    private fun writeLargeDeterministicCover(target: File) {
        val bitmap = Bitmap.createBitmap(LARGE_COVER_EDGE, LARGE_COVER_EDGE, Bitmap.Config.RGB_565)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val tile = LARGE_COVER_EDGE / LARGE_COVER_TILES
            repeat(LARGE_COVER_TILES) { row ->
                repeat(LARGE_COVER_TILES) { column ->
                    paint.color = Color.rgb(
                        (row * 29 + column * 11) and 0xff,
                        (row * 7 + column * 31) and 0xff,
                        (row * 19 + column * 13) and 0xff,
                    )
                    canvas.drawRect(
                        (column * tile).toFloat(),
                        (row * tile).toFloat(),
                        ((column + 1) * tile).toFloat(),
                        ((row + 1) * tile).toFloat(),
                        paint,
                    )
                }
            }
            target.outputStream().use { output ->
                assertTrue("large cover encoding failed", bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeBounds(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private fun updateSpecAndAwaitPreview(controller: RendererController, spec: RenderSpec) = runBlocking {
        val completion = async(start = CoroutineStart.UNDISPATCHED) {
            controller.status
                .map { status: RendererStatus -> status.phase }
                .dropWhile { phase -> phase != RendererStatus.Phase.RENDERING }
                .drop(1)
                .first { phase -> phase == RendererStatus.Phase.READY }
        }
        controller.updateSpec(spec)
        withTimeout(30_000) { completion.await() }
        assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
    }

    private inline fun Bitmap.useRecycled(block: (Bitmap) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }

    private fun mutateForEndurance(spec: RenderSpec, phase: Int): RenderSpec = spec.copy(
        content = spec.content.copy(
            lyrics = "耐久编辑 ${phase % 101}\n第二行保持稳定\nThird line remains deterministic",
            translationEnabled = phase % 2 == 0,
            translation = if (phase % 2 == 0) "Endurance ${phase % 101}\nSecond translated line\n第三行译文" else "",
        ),
        typography = spec.typography.copy(
            lyricSize = 42 + (phase % 15),
            alignment = TextAlignment.entries[phase % TextAlignment.entries.size],
        ),
        visual = spec.visual.copy(
            gridEnabled = phase % 3 != 0,
            gridDensity = GridDensity.entries[phase % GridDensity.entries.size],
            gridOpacity = 0.08 + (phase % 5) * 0.02,
        ),
    )

    private fun assertPng(image: ExportedImage, width: Int, height: Int) {
        assertTrue("export file missing", image.file.isFile)
        assertEquals("image/png", image.mimeType)
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        val signature = image.file.inputStream().use { input -> ByteArray(8).also { assertEquals(8, input.read(it)) } }
        assertTrue("invalid PNG signature", signature.contentEquals(PNG_SIGNATURE))
    }

    private fun waitForRenderer(controller: RendererController) {
        waitUntil(30_000) {
            controller.status.value.phase == RendererStatus.Phase.READY
        }
        assertEquals(RendererStatus.Phase.READY, controller.status.value.phase)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("condition timed out after $timeoutMs ms", condition())
    }

    private fun forceIdleGc() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            SystemClock.sleep(1_000)
        }
    }

    private fun backgroundAndResume(scenario: ActivityScenario<ComponentActivity>) {
        val activity = checkNotNull(currentResumedActivity()) {
            "no resumed host before background cycle"
        }
        appContext.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
        )
        waitUntil(BACKGROUND_TIMEOUT_MS) {
            activity.lifecycle.currentState == Lifecycle.State.CREATED
        }
        SystemClock.sleep(350)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK")
            .close()
        waitUntil(BACKGROUND_TIMEOUT_MS) {
            activity.lifecycle.currentState == Lifecycle.State.RESUMED
        }
    }

    private fun waitForStableOrientation(requestedOrientation: Int) {
        val expectedConfiguration = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> android.content.res.Configuration.ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> android.content.res.Configuration.ORIENTATION_PORTRAIT
            else -> error("unsupported quality-test orientation $requestedOrientation")
        }
        var stableActivity: ComponentActivity? = null
        var stableChecks = 0
        waitUntil(MAIN_THREAD_TIMEOUT_MS) {
            val activity = currentResumedActivity()
            val stable = activity != null &&
                activity.resources.configuration.orientation == expectedConfiguration &&
                activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                activity.window.decorView.isAttachedToWindow &&
                activity.window.decorView.isLaidOut &&
                activity.window.decorView.hasWindowFocus()
            if (stable && activity === stableActivity) {
                stableChecks += 1
            } else {
                stableActivity = if (stable) activity else null
                stableChecks = if (stable) 1 else 0
            }
            stableChecks >= STABLE_ACTIVITY_CHECKS
        }
    }

    private fun currentResumedActivity(): ComponentActivity? = runOnMainThread {
        ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<ComponentActivity>()
            .singleOrNull()
    }

    private fun runOnResumedActivity(block: (ComponentActivity) -> Unit) {
        runOnMainThread {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ComponentActivity>()
                .singleOrNull()
            block(checkNotNull(activity) { "expected exactly one resumed ComponentActivity" })
        }
    }

    private fun <T> runOnMainThread(
        timeoutMs: Long = MAIN_THREAD_TIMEOUT_MS,
        block: () -> T,
    ): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>?>(null)
        val completed = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        val action = Runnable {
            result.set(runCatching(block))
            completed.countDown()
        }
        val posted = handler.post(action)
        assertTrue("failed to enqueue quality action on the main thread", posted)
        if (!completed.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            handler.removeCallbacks(action)
            throw MainThreadActionTimeoutException(timeoutMs)
        }
        return checkNotNull(result.get()) { "main-thread quality action completed without a result" }.getOrThrow()
    }

    private fun memorySample(
        stage: String,
        exportDirectory: File? = null,
        filesBefore: Set<String> = emptySet(),
    ): MemorySample {
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        val sample = MemorySample(
            totalPssKb = memory.totalPss,
            javaPssKb = memory.getMemoryStat("summary.java-heap").toIntOrNull() ?: -1,
            nativePssKb = memory.getMemoryStat("summary.native-heap").toIntOrNull() ?: -1,
            graphicsPssKb = memory.getMemoryStat("summary.graphics").toIntOrNull() ?: -1,
            rssKb = processRssKb(),
        )
        val current = exportDirectory?.listFiles().orEmpty()
            .filterNot { it.getCanonicalPath() in filesBefore }
        val partials = current.count { it.name.endsWith(".part") || it.name.endsWith(".tmp") }
        Log.i(
            QUALITY_TAG,
            "memory stage=$stage totalPssKb=${sample.totalPssKb} rssKb=${sample.rssKb} " +
                "javaPssKb=${sample.javaPssKb} nativePssKb=${sample.nativePssKb} " +
                "graphicsPssKb=${sample.graphicsPssKb} exportFiles=${current.size} partialFiles=$partials",
        )
        return sample
    }

    private fun processRssKb(): Int = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.first { it.startsWith("VmRSS:") }
                .substringAfter(':')
                .trim()
                .substringBefore(' ')
                .toInt()
        }
    }.getOrDefault(-1)

    private data class MemorySample(
        val totalPssKb: Int,
        val javaPssKb: Int,
        val nativePssKb: Int,
        val graphicsPssKb: Int,
        val rssKb: Int,
    )

    private class MainThreadActionTimeoutException(timeoutMs: Long) :
        AssertionError("main-thread quality action timed out after $timeoutMs ms")

    private class ActivityCleanupTimeoutException(timeoutMs: Long) :
        AssertionError("activity cleanup timed out after $timeoutMs ms")

    private inner class RendererBinding(
        private val controller: RendererController,
    ) {
        private var owner: Any? = null
        private var view: android.webkit.WebView? = null

        fun attach() {
            val nextOwner = Any()
            runOnResumedActivity { activity ->
                val root = FrameLayout(activity)
                activity.setContentView(root)
                val nextView = controller.acquireWebView(activity, nextOwner)
                root.addView(
                    nextView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                owner = nextOwner
                view = nextView
            }
        }

        fun detach() {
            val currentOwner = owner ?: return
            val currentView = view ?: return
            runOnMainThread { controller.releaseWebView(currentOwner, currentView) }
            owner = null
            view = null
        }

        fun close() = detach()
    }

    private companion object {
        const val QUALITY_TAG = "LCG_QUALITY"
        const val APP_PACKAGE_PREFIX = "com.qrzzzz.lyricscard"
        const val EDITING_DURATION_MS = 30L * 60L * 1_000L
        const val BACKGROUND_INTERVAL_MS = 90L * 1_000L
        const val BACKGROUND_TIMEOUT_MS = 20_000L
        const val MAIN_THREAD_TIMEOUT_MS = 20_000L
        const val STABLE_ACTIVITY_CHECKS = 10
        const val REATTACH_REGRESSION_CYCLES = 12
        const val RECREATION_INTERVAL_MS = 3L * 60L * 1_000L
        const val MEMORY_INTERVAL_MS = 5L * 60L * 1_000L
        const val LARGE_COVER_EDGE = 4_096
        const val LARGE_COVER_TILES = 32
        const val STORED_COVER_EDGE = 2_048
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
