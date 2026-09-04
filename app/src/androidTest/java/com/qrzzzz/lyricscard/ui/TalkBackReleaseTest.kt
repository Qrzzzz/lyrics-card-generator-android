package com.qrzzzz.lyricscard.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.MainActivity
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.renderer.RendererStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the emulator's kernel input path so TalkBack receives the actual touch gestures. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33, maxSdkVersion = 33)
class TalkBackReleaseTest {
    private val app = ApplicationProvider.getApplicationContext<Context>() as LyricsCardApplication
    private val automation: UiAutomation = InstrumentationRegistry.getInstrumentation()
        .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private val focusEvents = AtomicInteger()
    private var verifiedFocusMoves = 0

    @Test
    fun activeTalkBackNavigatesHomeEditorExportAndSettings() {
        assertFalse("Console credentials must be removed before runner argument registration",
            InstrumentationRegistry.getArguments().containsKey("lcgTalkBackConsoleToken"))
        assertEquals("Kernel console gestures are restricted to the authorized AVD", "1", shell("getprop ro.kernel.qemu").trim())
        val manager = checkNotNull(app.getSystemService(AccessibilityManager::class.java))
        await("TalkBack touch exploration is not enabled") { manager.isTouchExplorationEnabled }
        val service = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            .singleOrNull { it.resolveInfo.serviceInfo.packageName == TALKBACK_PACKAGE }
        assertTrue("The real TalkBack service must be enabled", service != null)
        val version = app.packageManager.getPackageInfo(TALKBACK_PACKAGE, 0).versionName
        Log.i(TAG, "talkback-service-active package=$TALKBACK_PACKAGE version=$version touchExploration=true")

        val beforeIds = projectIds()
        var createdId: String? = null
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED && event.packageName?.toString() == app.packageName) {
                focusEvents.incrementAndGet()
            }
        }
        try {
            EmulatorConsoleTouchInput.connect(InstrumentationRegistry.getArguments()).use { input ->
                ActivityScenario.launch(MainActivity::class.java).use {
                    await("Home did not open") { hasText(app.getString(R.string.home_title)) }
                    activate(input, app.getString(R.string.home_blank_project), "home-create") {
                        val newIds = projectIds() - beforeIds
                        check(newIds.size <= 1) { "Ambiguous project ownership; refusing to delete any inferred project" }
                        if (createdId == null && newIds.size == 1) createdId = newIds.single()
                        createdId != null && selectedStep(1)
                    }
                    activate(input, stepLabel(3), "editor-preview") {
                        selectedStep(3) && app.container.rendererController.status.value.let {
                            it.phase == RendererStatus.Phase.READY && it.lastRenderMillis != null
                        }
                    }
                    activate(input, stepLabel(6), "editor-export-step") { selectedStep(6) }
                    activate(input, app.getString(R.string.editor_export_png, app.getString(R.string.file_png)), "export") {
                        hasText(app.getString(R.string.export_title))
                    }
                    activate(input, app.getString(R.string.common_back), "return-editor") { selectedStep(6) }
                    activate(input, app.getString(R.string.common_back), "return-home") {
                        hasText(app.getString(R.string.home_title))
                    }
                    activate(input, app.getString(R.string.home_settings_description), "settings") {
                        hasText(app.getString(R.string.settings_title))
                    }
                    activate(input, app.getString(R.string.common_back), "return-home-from-settings") {
                        hasText(app.getString(R.string.home_title))
                    }
                    assertTrue("No TalkBack focus movement was verified", verifiedFocusMoves > 0)
                }
            }
        } finally {
            automation.setOnAccessibilityEventListener(null)
            createdId?.let { id ->
                assertFalse("Refusing to delete a pre-existing project", id in beforeIds)
                assertTrue("TalkBack fixture cleanup failed", runBlocking { app.container.projects.delete(id) })
            }
            assertEquals("TalkBack test changed unrelated project IDs", beforeIds, projectIds())
        }
        Log.i(TAG, "talkback-focus-moves-verified count=$verifiedFocusMoves")
        Log.i(TAG, "talkback-core-complete stages=home,editor,export,settings input=kernel-console-swipe-double-tap")
    }

    private fun activate(input: EmulatorConsoleTouchInput, label: String, stage: String, completed: () -> Boolean) {
        val visited = mutableListOf<String>()
        var direction = 1
        var found = false
        for (attempt in 0 until 60) {
            input.awaitReadyForGesture()
            val current = focus()
            current?.let { visited += it.label.take(100) }
            if (current != null && current.label.contains(label) && current.clickable && current.enabled) {
                found = true
                break
            }
            val beforeEvents = focusEvents.get()
            input.swipe(direction)
            if (waitFor(1_500) { focusEvents.get() > beforeEvents && focus()?.key?.let { it != current?.key } == true }) {
                verifiedFocusMoves++
            } else {
                // TalkBack does not wrap at the end of a window. Search back from that boundary.
                direction = -direction
            }
        }
        assertTrue("TalkBack could not focus '$label'; visited=${visited.takeLast(12)}", found)
        Log.i(TAG, "talkback-activate stage=$stage focus=${focus()?.key}")
        input.doubleTap()
        val complete = waitFor(15_000, completed)
        assertTrue("TalkBack double-tap did not complete '$stage'; focus=${focus()?.key}", complete)
        Log.i(TAG, "talkback-stage=$stage input=kernel-console-swipe-double-tap postcondition=verified")
    }

    @Suppress("DEPRECATION")
    private fun focus(): Focus? {
        val node = automation.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return null
        return try {
            if (node.packageName?.toString() != app.packageName) return null
            val label = nodeLabel(node)
            val bounds = Rect().also(node::getBoundsInScreen)
            Focus(label, "$label@${bounds.flattenToString()}", node.isClickable, node.isEnabled)
        } finally {
            node.recycle()
        }
    }

    private fun selectedStep(step: Int): Boolean = anyAppNode { it.isSelected && nodeLabel(it).contains(stepLabel(step)) }

    private fun stepLabel(step: Int): String = app.getString(
        R.string.editor_step_accessibility, step, EditorStep.entries.size, app.getString(EditorStep.entries[step - 1].label),
    )

    private fun hasText(text: String): Boolean = anyAppNode { it.text?.toString() == text }

    @Suppress("DEPRECATION")
    private fun anyAppNode(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = automation.rootInActiveWindow ?: return false
        fun visit(node: AccessibilityNodeInfo): Boolean {
            if (node.packageName?.toString() == app.packageName && node.isVisibleToUser && predicate(node)) return true
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try { if (visit(child)) return true } finally { child.recycle() }
            }
            return false
        }
        return try { visit(root) } finally { root.recycle() }
    }

    @Suppress("DEPRECATION")
    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        // Compose buttons can expose their spoken label on descendants of the focused node.
        val labels = mutableListOf<String>()
        fun collect(current: AccessibilityNodeInfo) {
            listOfNotNull(current.text, current.contentDescription).mapTo(labels) { it.toString() }
            for (index in 0 until current.childCount) {
                val child = current.getChild(index) ?: continue
                try { collect(child) } finally { child.recycle() }
            }
        }
        collect(node)
        return labels.filter(String::isNotBlank).distinct().joinToString(" ")
    }

    private fun await(message: String, condition: () -> Boolean) = assertTrue(message, waitFor(15_000, condition))

    private fun waitFor(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        do {
            if (condition()) return true
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        return false
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun projectIds(): Set<String> = runBlocking {
        app.container.projects.observeProjects().first().map { it.id }.toSet()
    }

    private data class Focus(val label: String, val key: String, val clickable: Boolean, val enabled: Boolean)

    private companion object {
        const val TALKBACK_PACKAGE = "com.google.android.marvin.talkback"
        const val TAG = "LCG_RELEASE"
    }
}
