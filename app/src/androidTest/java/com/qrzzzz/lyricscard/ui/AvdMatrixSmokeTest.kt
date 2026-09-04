package com.qrzzzz.lyricscard.ui

import android.content.Context
import android.content.ContentValues
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.MainActivity
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.RENDERER_PREVIEW_TAG
import com.qrzzzz.lyricscard.renderer.RendererStatus
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class AvdMatrixSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app = ApplicationProvider.getApplicationContext<Context>() as LyricsCardApplication

    @Test
    fun productionMainActivitySixStepPreviewAndOneTwoXExportsWork() = runSmoke(verifyPlatformExport = false)

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun productionMainActivitySavesAndSharesExportedBytes() = runSmoke(verifyPlatformExport = true)

    private fun runSmoke(verifyPlatformExport: Boolean) {
        val beforeIds = projectIds()
        val exported = mutableListOf<File>()
        var returnedHome = false
        var navigationDepth = 0
        var createdId: String? = null

        try {
            waitUntil(UI_TIMEOUT_MS) {
                compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).assertIsDisplayed().assertIsEnabled()
                true
            }
            qualityStage("home-ready")
            settleNavigation()
            compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).performClick()
            navigationDepth = 1
            qualityStage("create-clicked")
            createdId = waitForCreatedProject(beforeIds)
            qualityStage("project-created")
            waitForStep(1)
            qualityStage("editor-step-1")

            for (step in 2..EDITOR_STEP_COUNT) {
                val label = stepAccessibilityLabel(step)
                compose.onNodeWithContentDescription(label).performScrollTo().performClick()
                waitForStep(step)
                qualityStage("editor-step-$step")

                if (step == 3) {
                    compose.onNodeWithTag(RENDERER_PREVIEW_TAG).assertExists()
                    waitUntil(RENDERER_TIMEOUT_MS) {
                        app.container.rendererController.status.value.phase == RendererStatus.Phase.READY &&
                            app.container.rendererController.status.value.lastRenderMillis != null
                    }
                    qualityStage("renderer-ready")
                }
            }

            val project = checkNotNull(runBlocking { app.container.projects.getProject(checkNotNull(createdId)) })
            app.container.rendererController.updateSpec(project.spec)
            waitUntil(RENDERER_TIMEOUT_MS) {
                app.container.rendererController.status.value.phase == RendererStatus.Phase.READY
            }

            val oneX = runBlocking { app.container.renderer.exportPng(project, 1) }
            exported += oneX.file
            assertPng(oneX)
            qualityStage("export-1x")

            val twoX = runBlocking { app.container.renderer.exportPng(project, 2) }
            exported += twoX.file
            assertPng(twoX)
            qualityStage("export-2x")
            assertEquals(oneX.width * 2, twoX.width)
            assertEquals(oneX.height * 2, twoX.height)
            assertNotEquals(oneX.file.canonicalPath, twoX.file.canonicalPath)
            assertNoPartialExports(oneX.file.parentFile)
            if (verifyPlatformExport) verifySaveAndShare(twoX)

            compose.onNodeWithText(
                compose.activity.getString(R.string.editor_export_png, compose.activity.getString(R.string.file_png)),
            ).performClick()
            navigationDepth = 2
            waitUntil(UI_TIMEOUT_MS) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasText(compose.activity.getString(R.string.export_title)),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            qualityStage("export-route")

            returnToHome(navigationDepth)
            navigationDepth = 0
            returnedHome = true
            qualityStage("home-returned")
        } catch (cause: Throwable) {
            qualityStage("failure-${cause::class.java.simpleName}")
            runCatching {
                repeat(2) { settleNavigation() }
                if (navigationDepth > 0) {
                    returnToHome(navigationDepth)
                    navigationDepth = 0
                    returnedHome = true
                }
            }
            throw cause
        } finally {
            exported.forEach { file -> if (file.exists()) assertTrue("smoke export cleanup failed", file.delete()) }
            if (returnedHome) {
                val createdIds = projectIds() - beforeIds
                createdIds.forEach { id -> runBlocking { app.container.projects.delete(id) } }
                qualityStage("cleanup-complete")
                repeat(2) { settleNavigation() }
                qualityStage("teardown-settled")
            }
        }
    }

    private fun verifySaveAndShare(image: ExportedImage) {
        val resolver = app.contentResolver
        val originalDigest = sha256(image.file.readBytes())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "lcg-release-validation-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LyricCardValidation")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val destination = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            runBlocking { app.container.exportFiles.copyTo(image, destination) }
            val saved = checkNotNull(resolver.openInputStream(destination)).use { it.readBytes() }
            assertEquals("saved PNG bytes differ", originalDigest, sha256(saved))
            assertEquals(1, resolver.update(destination, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null))

            val shareUri = FileProvider.getUriForFile(app, "${app.packageName}.files", image.file)
            val shared = checkNotNull(resolver.openInputStream(shareUri)).use { it.readBytes() }
            assertEquals("shared FileProvider bytes differ", originalDigest, sha256(shared))
            val intent = buildShareIntent(app, image, shareUri)
            assertTrue("share URI grant is missing", intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            var shareError: UiText? = null
            compose.activityRule.scenario.onActivity { shareError = shareImage(it, image) }
            assertEquals("share chooser launch failed", null, shareError)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var chooserVisible = false
            while (!chooserVisible && SystemClock.elapsedRealtime() < deadline) {
                val activities = ParcelFileDescriptor.AutoCloseInputStream(
                    automation.executeShellCommand("dumpsys activity activities"),
                ).bufferedReader().use { it.readText() }
                chooserVisible = activities.lineSequence().any {
                    (it.contains("mResumedActivity") || it.contains("topResumedActivity")) &&
                        (it.contains("Chooser", ignoreCase = true) || it.contains("Resolver", ignoreCase = true))
                }
                if (!chooserVisible) SystemClock.sleep(100)
            }
            assertTrue("platform share chooser did not become visible", chooserVisible)
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            SystemClock.sleep(500)
            Log.i("LCG_RELEASE", "platform-export savedSha256=$originalDigest shareSha256=$originalDigest chooserVisible=true sent=false")
        } finally {
            assertEquals("validation image cleanup failed", 1, resolver.delete(destination, null, null))
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun qualityStage(stage: String) {
        Log.i(QUALITY_TAG, "matrix-smoke stage=$stage api=${android.os.Build.VERSION.SDK_INT}")
    }

    private fun waitForStep(step: Int) {
        val expected = stepAccessibilityLabel(step)
        waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(expected),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        settleNavigation()
    }

    private fun settleNavigation() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(NAVIGATION_SETTLE_MS)
        SystemClock.sleep(NAVIGATION_SETTLE_MS)
        compose.waitForIdle()
    }

    private fun returnToHome(depth: Int) {
        repeat(depth) {
            compose.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            settleNavigation()
        }
        compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).assertExists()
    }

    private fun stepAccessibilityLabel(step: Int): String {
        val stepLabel = compose.activity.getString(EditorStep.entries[step - 1].label)
        return compose.activity.getString(
            R.string.editor_step_accessibility,
            step,
            EDITOR_STEP_COUNT,
            stepLabel,
        )
    }

    private fun waitForCreatedProject(beforeIds: Set<String>): String {
        var created: Set<String> = emptySet()
        waitUntil(UI_TIMEOUT_MS) {
            created = projectIds() - beforeIds
            created.size == 1
        }
        return created.single()
    }

    private fun projectIds(): Set<String> = runBlocking {
        app.container.projects.observeProjects().first().map { project -> project.id }.toSet()
    }

    private fun assertPng(image: ExportedImage) {
        assertTrue("export file missing", image.file.isFile)
        assertEquals("image/png", image.mimeType)
        assertTrue("invalid export width", image.width > 0)
        assertTrue("invalid export height", image.height > 0)
        val signature = image.file.inputStream().use { input ->
            ByteArray(PNG_SIGNATURE.size).also { assertEquals(PNG_SIGNATURE.size, input.read(it)) }
        }
        assertTrue("invalid PNG signature", signature.contentEquals(PNG_SIGNATURE))
    }

    private fun assertNoPartialExports(directory: File?) {
        assertFalse(
            "partial export remained after matrix smoke",
            directory?.listFiles().orEmpty().any { file ->
                file.name.endsWith(".part") || file.name.endsWith(".tmp")
            },
        )
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var satisfied = runCatching(condition).getOrDefault(false)
        while (!satisfied && SystemClock.elapsedRealtime() < deadline) {
            compose.mainClock.advanceTimeBy(POLL_FRAME_MILLIS)
            compose.waitForIdle()
            SystemClock.sleep(50)
            satisfied = runCatching(condition).getOrDefault(false)
        }
        assertTrue(
            "condition timed out after $timeoutMillis ms; rendererPhase=" +
                "${app.container.rendererController.status.value.phase}; " +
                "generation=${app.container.rendererController.generation.value}",
            satisfied,
        )
    }

    private companion object {
        const val UI_TIMEOUT_MS = 20_000L
        const val RENDERER_TIMEOUT_MS = 20_000L
        const val NAVIGATION_SETTLE_MS = 1_000L
        const val POLL_FRAME_MILLIS = 100L
        const val QUALITY_TAG = "LCG_QUALITY"
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
