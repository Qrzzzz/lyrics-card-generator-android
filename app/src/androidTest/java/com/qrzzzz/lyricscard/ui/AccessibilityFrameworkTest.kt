package com.qrzzzz.lyricscard.ui

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @OptIn(ExperimentalTestApi::class)
    private fun assertAtf(stage: String) {
        compose.waitForIdle()
        if (Build.VERSION.SDK_INT < COMPOSE_ATF_MIN_SDK) {
            Log.i(
                ATF_TAG,
                "stage=$stage engine=compose-atf assertion=unsupported sdk=${Build.VERSION.SDK_INT} min=$COMPOSE_ATF_MIN_SDK",
            )
            return
        }
        compose.enableAccessibilityChecks()
        try {
            compose.onRoot().tryPerformAccessibilityChecks()
            Log.i(ATF_TAG, "stage=$stage engine=compose-atf assertion=pass")
        } finally {
            compose.disableAccessibilityChecks()
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
        const val COMPOSE_ATF_MIN_SDK = 34
        const val ATF_TAG = "LCG_ATF"
    }
}
