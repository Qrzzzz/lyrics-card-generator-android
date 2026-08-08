package com.qrzzzz.lyricscard.ui

import android.app.UiAutomation
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchyAndroid
import com.qrzzzz.lyricscard.MainActivity
import com.qrzzzz.lyricscard.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityFrameworkTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun coreHomeEditorExportAndSettingsActionsPassAccessibilityTestFramework() {
        assertPaneTitleDefined()
        assertAtf("home")
        compose.onNodeWithText("空白项目").performClick()
        waitForText("第 1 步，共 6 步，选择歌曲")
        assertPaneTitleDefined()
        assertAtf("editor")

        for (step in 2..6) {
            compose.onNode(hasContentDescription("第 $step 步，共 6 步", substring = true))
                .performScrollTo()
                .performClick()
            waitForText("第 $step 步，共 6 步", substring = true)
        }
        compose.onNodeWithText("导出 PNG").performClick()
        waitForText(compose.activity.getString(R.string.export_title))
        assertPaneTitleDefined()
        assertAtf("export")
        compose.onNodeWithText(
            compose.activity.getString(
                R.string.export_scale_label,
                2,
                compose.activity.getString(R.string.common_high_definition),
            ),
        ).performClick()

        compose.onNode(hasContentDescription("返回")).performClick()
        waitForText("第 6 步，共 6 步", substring = true)
        compose.onNode(hasContentDescription("返回")).performClick()
        waitForText("最近项目")
        compose.onNode(hasContentDescription("设置")).performClick()
        waitForText("设置")
        assertPaneTitleDefined()
        assertAtf("settings")
        compose.onNodeWithText(compose.activity.getString(R.string.settings_dark_mode)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_default_export_quality)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.settings_safe_area)).performClick()
    }

    private fun assertPaneTitleDefined() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assertExists()
    }

    @Suppress("DEPRECATION")
    private fun assertAtf(stage: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES,
        )
        val root = waitForAccessibilityRoot(automation)
        try {
            val hierarchy = AccessibilityHierarchyAndroid
                .newBuilder(root, instrumentation.targetContext)
                .build()
            val nodeCount = hierarchy.activeWindow?.allViews?.size ?: 0
            assertTrue("ATF captured no virtual accessibility descendants", nodeCount > 1)
            val checks = AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
                AccessibilityCheckPreset.VERSION_3_1_CHECKS,
            )
            val results = checks.flatMap { it.runCheckOnHierarchy(hierarchy) }
            val actionable = results.filter {
                it.type == AccessibilityCheckResultType.ERROR ||
                    it.type == AccessibilityCheckResultType.WARNING
            }
            val safeSummary = actionable.joinToString(limit = 12) {
                "${it.sourceCheckClass.simpleName}#${it.resultId}:${it.type}"
            }
            assertTrue("ATF findings: $safeSummary", actionable.isEmpty())
            Log.i(
                ATF_TAG,
                "stage=$stage engine=node-hierarchy-atf preset=3.1 checks=${checks.size} nodes=$nodeCount " +
                    "results=${results.size} assertion=pass",
            )
        } finally {
            root.recycle()
        }
    }

    private fun waitForAccessibilityRoot(automation: UiAutomation): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS
        var root = automation.rootInActiveWindow
        while (root == null && SystemClock.elapsedRealtime() < deadline) {
            compose.mainClock.advanceTimeBy(POLL_FRAME_MILLIS)
            compose.waitForIdle()
            SystemClock.sleep(50)
            root = automation.rootInActiveWindow
        }
        return requireNotNull(root) {
            "ATF could not capture the active accessibility window within $UI_TIMEOUT_MS ms"
        }
    }

    private fun waitForText(value: String, substring: Boolean = false) {
        val deadline = SystemClock.elapsedRealtime() + UI_TIMEOUT_MS
        var found = textExists(value, substring)
        while (!found && SystemClock.elapsedRealtime() < deadline) {
            compose.mainClock.advanceTimeBy(POLL_FRAME_MILLIS)
            compose.waitForIdle()
            SystemClock.sleep(50)
            found = textExists(value, substring)
        }
        assertTrue("text did not appear within $UI_TIMEOUT_MS ms", found)
        compose.mainClock.advanceTimeBy(NAVIGATION_SETTLE_MS)
        compose.waitForIdle()
    }

    private fun textExists(value: String, substring: Boolean): Boolean = runCatching {
        compose.onAllNodes(hasText(value, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)

    private companion object {
        const val UI_TIMEOUT_MS = 20_000L
        const val POLL_FRAME_MILLIS = 100L
        const val NAVIGATION_SETTLE_MS = 1_000L
        const val ATF_TAG = "LCG_ATF"
    }
}
