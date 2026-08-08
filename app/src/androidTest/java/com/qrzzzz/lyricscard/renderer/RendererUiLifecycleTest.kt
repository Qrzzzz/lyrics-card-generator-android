package com.qrzzzz.lyricscard.renderer

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.ui.EditorScreen
import com.qrzzzz.lyricscard.ui.ExportScreen
import com.qrzzzz.lyricscard.ui.HomeScreen
import com.qrzzzz.lyricscard.ui.NeteaseLookupUiState
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererUiLifecycleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeDoesNotCreateAWebView() {
        compose.setContent {
            LyricsCardTheme {
                HomeScreen(
                    projects = emptyList(),
                    snackbarHost = {},
                    onCreateBlank = {},
                    onCreateSample = {},
                    onOpen = {},
                    onDuplicate = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onSettings = {},
                )
            }
        }

        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0, compose.activity.webViews().size)
        }
    }

    @Test
    fun editorDefersCreationUntilStepThreeThenReusesThroughExport() {
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val showExport = mutableStateOf(false)
        val project = Project(
            id = "renderer-ui-lifecycle",
            name = "Renderer lifecycle",
            spec = RenderSpec(),
            createdAt = 1L,
            updatedAt = 1L,
        )

        try {
            compose.setContent {
                LyricsCardTheme {
                    if (showExport.value) {
                        ExportScreen(
                            project = project,
                            renderer = controller,
                            defaultMultiplier = 1,
                            onBack = {},
                            onExportRecorded = {},
                        )
                    } else {
                        EditorScreen(
                            project = project,
                            isSaving = false,
                            canUndo = false,
                            canRedo = false,
                            showSafeArea = false,
                            renderer = controller,
                            netease = NeteaseLookupUiState(),
                            snackbarHost = {},
                            onBack = {},
                            onProjectNameChange = {},
                            onSpecChange = {},
                            onMeasuredHeight = {},
                            onPaletteExtracted = {},
                            onUndo = {},
                            onRedo = {},
                            onSelectCover = {},
                            onRemoveCover = {},
                            onSearchNetease = {},
                            onResolveNeteaseSong = {},
                            onResolveNeteaseLink = {},
                            onExport = { showExport.value = true },
                        )
                    }
                }
            }

            compose.waitForIdle()
            assertWebViewCount(0)

            compose.onAllNodesWithText("下一步").onFirst().performClick()
            compose.waitForIdle()
            assertWebViewCount(0)

            compose.onAllNodesWithText("下一步").onFirst().performClick()
            compose.waitForIdle()
            val shared = onlyWebView()

            repeat(3) {
                compose.onAllNodesWithText("下一步").onFirst().performClick()
                compose.waitForIdle()
                compose.runOnIdle { assertSame(shared, compose.activity.webViews().single()) }
            }

            compose.onAllNodesWithText("导出 PNG").onFirst().performClick()
            compose.waitForIdle()
            compose.runOnIdle { assertSame(shared, compose.activity.webViews().single()) }
        } finally {
            compose.runOnIdle { controller.close() }
        }
    }

    private fun assertWebViewCount(expected: Int) {
        compose.runOnIdle { assertEquals(expected, compose.activity.webViews().size) }
    }

    private fun onlyWebView(): WebView {
        lateinit var result: WebView
        compose.runOnIdle { result = compose.activity.webViews().single() }
        return result
    }
}

private fun ComponentActivity.webViews(): List<WebView> =
    findViewById<View>(android.R.id.content).descendants().filterIsInstance<WebView>()

private fun View.descendants(): List<View> = buildList {
    add(this@descendants)
    if (this@descendants is ViewGroup) {
        for (index in 0 until childCount) {
            addAll(getChildAt(index).descendants())
        }
    }
}
