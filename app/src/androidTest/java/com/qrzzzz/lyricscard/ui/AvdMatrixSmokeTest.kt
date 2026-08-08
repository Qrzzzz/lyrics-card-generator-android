package com.qrzzzz.lyricscard.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.MainActivity
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.RENDERER_PREVIEW_TAG
import com.qrzzzz.lyricscard.renderer.RendererStatus
import java.io.File
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
    fun productionMainActivitySixStepPreviewAndOneTwoXExportsWork() {
        val beforeIds = projectIds()
        val exported = mutableListOf<File>()

        try {
            compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).performClick()
            waitForStep(1)

            for (step in 2..EDITOR_STEP_COUNT) {
                val label = stepAccessibilityLabel(step)
                compose.onNodeWithContentDescription(label).performScrollTo().performClick()
                waitForStep(step)

                if (step == 3) {
                    compose.onNodeWithTag(RENDERER_PREVIEW_TAG).assertExists()
                    waitUntil(RENDERER_TIMEOUT_MS) {
                        app.container.rendererController.status.value.phase == RendererStatus.Phase.READY &&
                            app.container.rendererController.generation.value > 0
                    }
                }
            }

            val createdId = waitForCreatedProject(beforeIds)
            val project = checkNotNull(runBlocking { app.container.projects.getProject(createdId) })
            app.container.rendererController.updateSpec(project.spec)
            waitUntil(RENDERER_TIMEOUT_MS) {
                app.container.rendererController.status.value.phase == RendererStatus.Phase.READY
            }

            val oneX = runBlocking { app.container.renderer.exportPng(project, 1) }
            exported += oneX.file
            assertPng(oneX)

            val twoX = runBlocking { app.container.renderer.exportPng(project, 2) }
            exported += twoX.file
            assertPng(twoX)
            assertEquals(oneX.width * 2, twoX.width)
            assertEquals(oneX.height * 2, twoX.height)
            assertNotEquals(oneX.file.canonicalPath, twoX.file.canonicalPath)
            assertNoPartialExports(oneX.file.parentFile)

            compose.onNodeWithText(
                compose.activity.getString(R.string.editor_export_png, compose.activity.getString(R.string.file_png)),
            ).performClick()
            waitUntil(UI_TIMEOUT_MS) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasText(compose.activity.getString(R.string.export_title)),
                ).fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            exported.forEach { file -> if (file.exists()) assertTrue("smoke export cleanup failed", file.delete()) }
            val createdIds = projectIds() - beforeIds
            createdIds.forEach { id -> runBlocking { app.container.projects.delete(id) } }
        }
    }

    private fun waitForStep(step: Int) {
        val expected = stepAccessibilityLabel(step)
        waitUntil(UI_TIMEOUT_MS) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(expected),
            ).fetchSemanticsNodes().isNotEmpty()
        }
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
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("condition timed out after $timeoutMillis ms", condition())
    }

    private companion object {
        const val UI_TIMEOUT_MS = 20_000L
        const val RENDERER_TIMEOUT_MS = 30_000L
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
    }
}
