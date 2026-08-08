package com.qrzzzz.lyricscard.ui

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityFrameworkTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableAccessibilityTestFramework() {
        AccessibilityChecks.enable().setRunChecksFromRootView(true)
    }

    @After
    fun disableAccessibilityTestFramework() {
        AccessibilityChecks.disable()
    }

    @Test
    fun coreHomeEditorExportAndSettingsActionsPassAccessibilityTestFramework() {
        compose.onNodeWithText("空白项目").performClick()
        waitForText("第 1 步，共 6 步，选择歌曲")

        for (step in 2..6) {
            compose.onNode(hasContentDescription("第 $step 步，共 6 步", substring = true))
                .performScrollTo()
                .performClick()
            waitForText("第 $step 步，共 6 步", substring = true)
        }
        compose.onNodeWithText("导出 PNG").performClick()
        waitForText("导出歌词卡片")
        compose.onNodeWithText("2×").performClick()

        compose.onNode(hasContentDescription("返回")).performClick()
        waitForText("第 6 步，共 6 步", substring = true)
        compose.onNode(hasContentDescription("返回")).performClick()
        waitForText("最近项目")
        compose.onNode(hasContentDescription("设置")).performClick()
        waitForText("设置")
        compose.onNodeWithText("深色模式").performClick()
        compose.onNodeWithText("导出质量").performClick()
        compose.onNodeWithText("安全区参考线").performClick()
    }

    private fun waitForText(value: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodes(hasText(value, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
