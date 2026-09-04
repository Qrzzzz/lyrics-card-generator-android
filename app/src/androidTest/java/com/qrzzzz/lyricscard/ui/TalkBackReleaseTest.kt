package com.qrzzzz.lyricscard.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.qrzzzz.lyricscard.LyricsCardApplication
import com.qrzzzz.lyricscard.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the installed TalkBack service with real swipe and double-tap input. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33, maxSdkVersion = 33)
class TalkBackReleaseTest {
    private val app = ApplicationProvider.getApplicationContext<Context>() as LyricsCardApplication
    private val automation: UiAutomation = InstrumentationRegistry.getInstrumentation()
        .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun activeTalkBackNavigatesHomeEditorExportAndSettings() {
        val manager = app.getSystemService(AccessibilityManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!manager.isTouchExplorationEnabled && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue("TalkBack touch exploration must actually be enabled", manager.isTouchExplorationEnabled)
        val service = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN)
            .singleOrNull { it.resolveInfo.serviceInfo.packageName == TALKBACK_PACKAGE }
        assertTrue("The real TalkBack service must be enabled", service != null)
        val version = app.packageManager.getPackageInfo(TALKBACK_PACKAGE, 0).versionName
        Log.i(TAG, "talkback-service-active package=$TALKBACK_PACKAGE version=$version touchExploration=true")

        val beforeIds = projectIds()
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                SystemClock.sleep(1_000)
                activate("空白项目", "home-create")
                activate("第 3 步，共 6 步", "editor-preview")
                activate("第 6 步，共 6 步", "editor-export-step")
                activate("导出 PNG", "export")
                activate("返回", "return-editor")
                activate("返回", "return-home")
                activate("设置", "settings")
                activate("返回", "return-home-from-settings")
                Log.i(TAG, "talkback-core-complete stages=home,editor,export,settings input=swipe-double-tap")
            }
        } finally {
            (projectIds() - beforeIds).forEach { id -> runBlocking { app.container.projects.delete(id) } }
        }
    }

    @Suppress("DEPRECATION")
    private fun activate(label: String, stage: String) {
        val metrics = app.resources.displayMetrics
        var found = false
        val visited = mutableListOf<String>()
        for (attempt in 0 until 80) {
            val root = automation.rootInActiveWindow
            val focus = root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            try {
                val text = listOfNotNull(focus?.text, focus?.contentDescription).joinToString(" ")
                if (text.isNotBlank()) visited += text.take(120)
                if (text.contains(label) && focus?.isClickable == true && focus.isEnabled) {
                    found = true
                    break
                }
            } finally {
                focus?.recycle()
                root?.recycle()
            }
            shell("input touchscreen swipe ${metrics.widthPixels * 3 / 10} ${metrics.heightPixels / 2} ${metrics.widthPixels * 7 / 10} ${metrics.heightPixels / 2} 180")
            SystemClock.sleep(180)
        }
        assertTrue("TalkBack could not focus '$label'; visited=${visited.takeLast(12)}", found)
        val x = metrics.widthPixels / 2f
        val y = metrics.heightPixels / 2f
        repeat(2) {
            val downTime = SystemClock.uptimeMillis()
            injectTapEvent(downTime, MotionEvent.ACTION_DOWN, x, y)
            SystemClock.sleep(30)
            injectTapEvent(downTime, MotionEvent.ACTION_UP, x, y)
            SystemClock.sleep(60)
        }
        SystemClock.sleep(700)
        Log.i(TAG, "talkback-stage=$stage gesture=swipe-double-tap")
    }

    private fun injectTapEvent(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue("touch injection failed", automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun projectIds(): Set<String> = runBlocking {
        app.container.projects.observeProjects().first().map { it.id }.toSet()
    }

    private companion object {
        const val TALKBACK_PACKAGE = "com.google.android.marvin.talkback"
        const val TAG = "LCG_RELEASE"
    }
}
