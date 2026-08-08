package com.qrzzzz.lyricscard.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellAccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fourChineseLtrScreensKeepKeyActionsReachableAtOneAndTwoXFontScale() {
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val project = ProjectTemplates.blank(id = "accessibility-shell", now = 1L)
        val screen = mutableStateOf(HarnessScreen.HOME)
        val fontScale = mutableFloatStateOf(1f)

        try {
            compose.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    LyricsCardTheme(darkTheme = screen.value == HarnessScreen.SETTINGS) {
                        Box(
                            Modifier
                                .requiredSize(width = 360.dp, height = 640.dp)
                                .testTag(VIEWPORT_TAG),
                        ) {
                            when (screen.value) {
                                HarnessScreen.HOME -> HomeScreen(
                                    projects = emptyList(),
                                    isWorking = false,
                                    snackbarHost = {},
                                    onCreateBlank = {},
                                    onCreateSample = {},
                                    onOpen = {},
                                    onDuplicate = {},
                                    onRename = { _, _ -> },
                                    onDelete = {},
                                    onSettings = {},
                                )
                                HarnessScreen.EDITOR -> EditorScreen(
                                    state = EditorUiState(
                                        projectId = project.id,
                                        currentProject = project,
                                        isLoading = false,
                                    ),
                                    showSafeArea = false,
                                    renderer = controller,
                                    snackbarHost = {},
                                    onBack = {},
                                    onSelectedStep = {},
                                    onSearchQueryChange = {},
                                    onLinkInputChange = {},
                                    onProjectNameChange = {},
                                    onSpecChange = {},
                                    onMeasuredHeight = {},
                                    onExtractPalette = {},
                                    onUndo = {},
                                    onRedo = {},
                                    onSelectCover = {},
                                    onRemoveCover = {},
                                    onSearchNetease = {},
                                    onResolveNeteaseSong = {},
                                    onResolveNeteaseLink = {},
                                    onExport = {},
                                )
                                HarnessScreen.EXPORT -> ExportScreen(
                                    state = ExportUiState(
                                        projectId = project.id,
                                        project = project,
                                        isLoading = false,
                                        multiplier = 1,
                                        fileName = "accessibility-shell.png",
                                    ),
                                    renderer = controller,
                                    onBack = {},
                                    onMultiplier = {},
                                    onFileName = {},
                                    onMeasuredHeight = {},
                                    onSave = {},
                                    onShare = {},
                                    onCancel = {},
                                    onRetry = {},
                                    onSaveDestination = {},
                                    onEffectConsumed = {},
                                    onExternalActionError = {},
                                )
                                HarnessScreen.SETTINGS -> SettingsScreen(
                                    state = SettingsUiState(
                                        preferences = UserPreferences(darkMode = true),
                                        isLoading = false,
                                    ),
                                    onBack = {},
                                    onDarkMode = {},
                                    onDefaultExportScale = {},
                                    onShowSafeArea = {},
                                    onClearExportCache = {},
                                )
                            }
                        }
                    }
                }
            }

            listOf(1f, 2f).forEach { scale ->
                HarnessScreen.entries.forEach { target ->
                    compose.runOnIdle {
                        fontScale.floatValue = scale
                        screen.value = target
                    }
                    when (target) {
                        HarnessScreen.HOME -> {
                            assertDisplayedInsideViewport(text(R.string.home_title))
                            assertDisplayedInsideViewport(text(R.string.home_blank_project))
                        }
                        HarnessScreen.EDITOR -> {
                            compose.onNodeWithText(text(R.string.editor_netease_query_label))
                                .performScrollTo()
                                .assertIsDisplayed()
                            compose.onNodeWithText(text(R.string.editor_next_step))
                                .performScrollTo()
                                .assertIsDisplayed()
                        }
                        HarnessScreen.EXPORT -> {
                            assertDisplayedInsideViewport(text(R.string.export_title))
                            compose.onAllNodesWithText(text(R.string.common_save)).onFirst()
                                .performScrollTo()
                                .assertIsDisplayed()
                        }
                        HarnessScreen.SETTINGS -> {
                            assertDisplayedInsideViewport(text(R.string.settings_title))
                            compose.onNodeWithText(text(R.string.settings_clear_export_cache))
                                .performScrollTo()
                                .assertIsDisplayed()
                        }
                    }
                }
            }
        } finally {
            compose.runOnIdle { controller.close() }
        }
    }

    @Test
    fun landscapeEditorAndExportKeepActionsReachableAfterImeFocus() {
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val project = ProjectTemplates.blank(id = "landscape-shell", now = 1L)
        val showExport = mutableStateOf(false)
        val imeBottomPx = mutableIntStateOf(0)

        try {
            compose.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 2f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                    SideEffect { imeBottomPx.intValue = imeBottom }
                    LyricsCardTheme {
                        Box(
                            Modifier
                                .requiredSize(width = 840.dp, height = 420.dp)
                                .testTag(VIEWPORT_TAG),
                        ) {
                            if (showExport.value) {
                                ExportScreen(
                                    state = ExportUiState(
                                        projectId = project.id,
                                        project = project,
                                        isLoading = false,
                                        multiplier = 1,
                                        fileName = "landscape-shell.png",
                                    ),
                                    renderer = controller,
                                    onBack = {},
                                    onMultiplier = {},
                                    onFileName = {},
                                    onMeasuredHeight = {},
                                    onSave = {},
                                    onShare = {},
                                    onCancel = {},
                                    onRetry = {},
                                    onSaveDestination = {},
                                    onEffectConsumed = {},
                                    onExternalActionError = {},
                                )
                            } else {
                                EditorScreen(
                                    state = EditorUiState(
                                        projectId = project.id,
                                        currentProject = project,
                                        isLoading = false,
                                    ),
                                    showSafeArea = false,
                                    renderer = controller,
                                    snackbarHost = {},
                                    onBack = {},
                                    onSelectedStep = {},
                                    onSearchQueryChange = {},
                                    onLinkInputChange = {},
                                    onProjectNameChange = {},
                                    onSpecChange = {},
                                    onMeasuredHeight = {},
                                    onExtractPalette = {},
                                    onUndo = {},
                                    onRedo = {},
                                    onSelectCover = {},
                                    onRemoveCover = {},
                                    onSearchNetease = {},
                                    onResolveNeteaseSong = {},
                                    onResolveNeteaseLink = {},
                                    onExport = {},
                                )
                            }
                        }
                    }
                }
            }

            compose.onNodeWithText(text(R.string.editor_netease_query_label))
                .performClick()
            compose.waitUntil(timeoutMillis = 5_000) { imeBottomPx.intValue > 0 }
            assertTrue("IME inset did not become visible", imeBottomPx.intValue > 0)
            compose.onNodeWithText(text(R.string.editor_netease_query_label))
                .performTextInput("landscape query")
            assertDisplayedInsideViewport(text(R.string.editor_next_step))

            compose.runOnIdle { showExport.value = true }
            assertDisplayedInsideViewport(text(R.string.export_title))
            compose.onNodeWithText(text(R.string.export_file_name)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) { imeBottomPx.intValue > 0 }
            assertTrue("IME inset was not visible over Export", imeBottomPx.intValue > 0)
            compose.onAllNodesWithText(text(R.string.common_save)).onFirst()
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            compose.runOnIdle { controller.close() }
        }
    }

    private fun assertDisplayedInsideViewport(value: String) {
        val node = compose.onNodeWithText(value).assertIsDisplayed().fetchSemanticsNode()
        val viewport = compose.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val bounds = node.boundsInRoot
        assertTrue("$value starts outside viewport", bounds.left >= viewport.left && bounds.top >= viewport.top)
        assertTrue("$value ends outside viewport", bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
    }

    private fun text(resource: Int): String = compose.activity.getString(resource)

    private enum class HarnessScreen { HOME, EDITOR, EXPORT, SETTINGS }

    private companion object {
        const val VIEWPORT_TAG = "app-shell-viewport"
    }
}
