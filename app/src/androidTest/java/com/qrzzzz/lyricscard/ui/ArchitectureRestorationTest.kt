package com.qrzzzz.lyricscard.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchitectureRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun editorRouteStepAndSearchDraftSurviveActivityRecreation() {
        composeRule.onNodeWithText("空白项目").performClick()
        waitForText("1/6", substring = true)

        composeRule.onNodeWithText("歌曲名或歌手").performTextInput("rotation-query")
        composeRule.onNodeWithText("4. 字体方案").performScrollTo().performClick()
        waitForText("4/6", substring = true)

        composeRule.activityRule.scenario.recreate()

        waitForText("4/6", substring = true)
        composeRule.onNodeWithText("1. 选择歌曲").performScrollTo().performClick()
        composeRule.onNodeWithText("rotation-query", substring = true).fetchSemanticsNode()
    }

    private fun waitForText(value: String, substring: Boolean) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText(value, substring = substring),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
