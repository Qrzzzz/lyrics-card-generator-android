package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.NeteaseSongSearchResult
import com.qrzzzz.lyricscard.model.CanvasRatio
import com.qrzzzz.lyricscard.model.LyricTextLimits
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.TextColorMode
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.PreviewStatus
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import com.qrzzzz.lyricscard.renderer.RENDERER_ERROR_TAG
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorExportProductionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sixStepsSupportDirectSelectionBackNextAndExactTalkBackProgress() {
        val project = project("six-step-editor")
        val selectedStep = mutableIntStateOf(0)
        compose.setContent {
            LyricsCardTheme {
                Box(Modifier.requiredSize(width = 360.dp, height = 640.dp)) {
                    EditorPanelContent(
                        state = EditorUiState(
                            projectId = project.id,
                            currentProject = project,
                            isLoading = false,
                            selectedStep = selectedStep.intValue,
                        ),
                        actions = actions(onSelectedStep = { selectedStep.intValue = it }),
                    )
                }
            }
        }

        EditorStep.entries.forEachIndexed { index, step ->
            val description = compose.activity.getString(
                R.string.editor_step_accessibility,
                index + 1,
                EDITOR_STEP_COUNT,
                compose.activity.getString(step.label),
            )
            compose.onNodeWithContentDescription(description).assertExists()
        }
        compose.onNodeWithContentDescription("第 1 步，共 6 步，选择歌曲").assertIsSelected()

        compose.onNodeWithContentDescription("第 4 步，共 6 步，字体方案")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
        assertEquals(3, selectedStep.intValue)

        compose.onNodeWithText(text(R.string.editor_previous_step)).performClick()
        assertEquals(2, selectedStep.intValue)
        compose.onNodeWithText(text(R.string.editor_next_step)).performClick()
        assertEquals(3, selectedStep.intValue)
    }

    @Test
    fun chooseSongShowsSearchResultEmptyErrorAndDisabledResolvingStates() {
        val project = project("choose-song")
        val resolvedId = mutableStateOf<String?>(null)
        val lookup = mutableStateOf(
            NeteaseLookupUiState(
                results = listOf(
                    NeteaseSongSearchResult("42", "晴天", "周杰伦", "叶惠美"),
                ),
                phase = NeteaseLookupPhase.RESULTS,
                message = UiText.resource(R.string.editor_netease_select_result),
            ),
        )
        compose.setContent {
            LyricsCardTheme {
                Box(
                    Modifier
                        .requiredSize(width = 360.dp, height = 640.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ChooseSongPanel(
                        project = project,
                        drafts = EditorDrafts(projectName = project.name),
                        netease = lookup.value,
                        actions = actions(onResolveNeteaseSong = { resolvedId.value = it }),
                    )
                }
            }
        }

        compose.onNode(hasText("晴天") and hasClickAction()).performClick()
        assertEquals("42", resolvedId.value)

        compose.runOnIdle {
            lookup.value = lookup.value.copy(
                isResolving = true,
                phase = NeteaseLookupPhase.RESOLVING,
                message = UiText.resource(R.string.editor_netease_resolving),
            )
        }
        compose.onNode(hasText("晴天") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.editor_netease_resolving)).assertExists()

        compose.runOnIdle {
            lookup.value = NeteaseLookupUiState(
                phase = NeteaseLookupPhase.EMPTY,
                message = UiText.resource(R.string.editor_netease_no_results),
            )
        }
        compose.onNodeWithText(text(R.string.editor_netease_no_results)).assertExists()
        compose.onNodeWithText("晴天").assertDoesNotExist()

        compose.runOnIdle {
            lookup.value = NeteaseLookupUiState(
                phase = NeteaseLookupPhase.ERROR,
                message = UiText.Dynamic("搜索暂时不可用"),
            )
        }
        compose.onNodeWithText("搜索暂时不可用").assertExists()
    }

    @Test
    fun editorDraftFieldsRejectInvalidLyricsNumbersAndColorsWhileSliderRemainsAccessible() {
        val mode = mutableStateOf(PanelHarness.LYRICS)
        val spec = mutableStateOf(RenderSpec())
        compose.setContent {
            LyricsCardTheme {
                Box(
                    Modifier
                        .requiredSize(width = 360.dp, height = 640.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (mode.value) {
                        PanelHarness.LYRICS -> LyricsPanel(spec.value) { spec.value = it }
                        PanelHarness.LAYOUT -> LayoutPanel(spec.value) { spec.value = it }
                        PanelHarness.TYPOGRAPHY -> TypographyPanel(spec.value) { spec.value = it }
                    }
                }
            }
        }

        val invalidLyrics = List(LyricTextLimits.MAX_LINES + 1) { "line-$it" }.joinToString("\n")
        compose.onNodeWithTag(EDITOR_LYRICS_FIELD_TAG).performTextReplacement(invalidLyrics)
        compose.onNodeWithText(
            compose.activity.getString(
                R.string.editor_lyric_line_limit_inline,
                LyricTextLimits.MAX_LINES + 1,
                LyricTextLimits.MAX_LINES,
            ),
        ).assertExists()
        compose.runOnIdle { assertEquals("", spec.value.content.lyrics) }

        compose.onNodeWithTag(EDITOR_LYRICS_FIELD_TAG).performTextReplacement("第一行\n第二行")
        compose.runOnIdle { assertEquals("第一行\n第二行", spec.value.content.lyrics) }
        compose.onNodeWithText(text(R.string.editor_show_translation)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(spec.value.content.translationEnabled) }

        compose.runOnIdle {
            spec.value = spec.value.copy(canvas = spec.value.canvas.copy(ratio = CanvasRatio.CUSTOM))
            mode.value = PanelHarness.LAYOUT
        }
        val originalWidth = spec.value.canvas.width
        compose.onNodeWithTag(EDITOR_WIDTH_FIELD_TAG).performScrollTo().performTextReplacement("12")
        compose.runOnIdle { assertEquals(originalWidth, spec.value.canvas.width) }
        compose.onNodeWithTag(EDITOR_WIDTH_FIELD_TAG).performTextReplacement("900")
        compose.runOnIdle { assertEquals(900, spec.value.canvas.width) }

        compose.runOnIdle {
            spec.value = spec.value.copy(
                typography = spec.value.typography.copy(
                    textColorMode = TextColorMode.CUSTOM,
                    customTextColor = "#FFFFFF",
                ),
            )
            mode.value = PanelHarness.TYPOGRAPHY
        }
        compose.onNodeWithTag(EDITOR_CUSTOM_TEXT_COLOR_TAG)
            .performScrollTo()
            .performTextReplacement("#GGGGGG")
        compose.runOnIdle { assertEquals("#FFFFFF", spec.value.typography.customTextColor) }
        compose.onNodeWithTag(EDITOR_CUSTOM_TEXT_COLOR_TAG).performTextReplacement("#112233")
        compose.runOnIdle { assertEquals("#112233", spec.value.typography.customTextColor) }

        compose.onNodeWithContentDescription(text(R.string.editor_lyric_size))
            .performScrollTo()
            .assert(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            )
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue(setProgress(68f))
            }
        compose.runOnIdle { assertEquals(68, spec.value.typography.lyricSize) }
    }

    @Test
    fun rendererErrorIsUnderstandableAssertiveAndRetryable() {
        val retries = mutableIntStateOf(0)
        compose.setContent {
            LyricsCardTheme {
                Box(Modifier.requiredSize(width = 360.dp, height = 320.dp)) {
                    PreviewStatus(
                        message = "当前 WebView 不受支持",
                        retry = { retries.intValue += 1 },
                        isError = true,
                    )
                }
            }
        }

        compose.onNodeWithTag(RENDERER_ERROR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Assertive,
                ),
            )
        compose.onNodeWithText("当前 WebView 不受支持").assertExists()
        compose.onNodeWithText(text(R.string.common_retry)).performClick()
        assertEquals(1, retries.intValue)
    }

    @Test
    fun exportControlsExposeOnlyOneAndTwoXAndMatchBusyFailureAndSuccessStates() {
        val project = project("export-controls")
        val state = mutableStateOf(
            ExportUiState(
                projectId = project.id,
                project = project,
                isLoading = false,
                multiplier = 1,
                fileName = "lyrics.png",
                measuredHeight = project.spec.canvas.height,
            ),
        )
        val retries = mutableIntStateOf(0)
        compose.setContent {
            LyricsCardTheme {
                Box(Modifier.requiredSize(width = 360.dp, height = 640.dp)) {
                    ExportControls(
                        state = state.value,
                        project = project,
                        onMultiplier = { state.value = state.value.copy(multiplier = it) },
                        onFileName = { state.value = state.value.copy(fileName = it) },
                        onSave = {},
                        onShare = {},
                        onCancel = {},
                        onRetry = { retries.intValue += 1 },
                        modifier = Modifier.testTag("export-controls"),
                    )
                }
            }
        }

        compose.onNodeWithText("1× 标准").assertIsSelected()
        compose.onNodeWithText("2× 高清").performClick().assertIsSelected()
        assertEquals(2, state.value.multiplier)
        compose.onNodeWithText("3×", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(EXPORT_FILE_NAME_TAG).performTextReplacement("final-card")
        assertEquals("final-card", state.value.fileName)

        compose.runOnIdle {
            state.value = state.value.copy(operation = ExportOperationState.PREPARING)
        }
        compose.onNodeWithText(text(R.string.common_cancel)).performScrollTo().assertIsEnabled()

        compose.runOnIdle {
            state.value = state.value.copy(operation = ExportOperationState.FINALIZING)
        }
        compose.onNodeWithText(text(R.string.export_finalizing)).performScrollTo().assertIsNotEnabled()

        compose.runOnIdle {
            state.value = state.value.copy(
                operation = ExportOperationState.FAILURE,
                errorMessage = UiText.resource(R.string.export_failure),
            )
        }
        compose.onNodeWithText(text(R.string.export_retry)).performScrollTo().performClick()
        assertEquals(1, retries.intValue)

        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        compose.runOnIdle {
            state.value = state.value.copy(
                operation = ExportOperationState.SUCCESS,
                exported = ExportedImage(File(compose.activity.cacheDir, "test-export.png"), 1080, 1350),
                preview = ExportPreviewUiState(ExportPreviewPhase.READY, bitmap),
                errorMessage = null,
            )
        }
        compose.onNodeWithText(text(R.string.common_save)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.common_share)).performScrollTo().assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(preview = ExportPreviewUiState()) }
        compose.waitForIdle()
        bitmap.recycle()
    }

    @Test
    fun editorAndExportRenderExplicitCompactMediumAndExpandedLayouts() {
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val project = project("adaptive-editor-export")
        val widthClass = mutableStateOf(WindowWidthSizeClass.Compact)
        val export = mutableStateOf(false)
        try {
            compose.setContent {
                LyricsCardTheme {
                    if (export.value) {
                        ExportScreen(
                            state = ExportUiState(
                                projectId = project.id,
                                project = project,
                                isLoading = false,
                                multiplier = 1,
                                fileName = "adaptive.png",
                                measuredHeight = project.spec.canvas.height,
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
                            windowWidthSizeClass = widthClass.value,
                        )
                    } else {
                        EditorScreen(
                            state = EditorUiState(
                                projectId = project.id,
                                currentProject = project,
                                isLoading = false,
                                selectedStep = EditorStep.LAYOUT.ordinal,
                            ),
                            showSafeArea = true,
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
                            windowWidthSizeClass = widthClass.value,
                        )
                    }
                }
            }

            compose.onNodeWithTag(EDITOR_COMPACT_SHEET_TAG).assertExists()
            compose.runOnIdle { widthClass.value = WindowWidthSizeClass.Medium }
            compose.onNodeWithTag(EDITOR_MEDIUM_LAYOUT_TAG).assertExists()
            compose.runOnIdle { widthClass.value = WindowWidthSizeClass.Expanded }
            compose.onNodeWithTag(EDITOR_EXPANDED_LAYOUT_TAG).assertExists()

            compose.runOnIdle {
                export.value = true
                widthClass.value = WindowWidthSizeClass.Compact
            }
            compose.onNodeWithTag(EXPORT_COMPACT_LAYOUT_TAG).assertExists()
            compose.runOnIdle { widthClass.value = WindowWidthSizeClass.Medium }
            compose.onNodeWithTag(EXPORT_MEDIUM_LAYOUT_TAG).assertExists()
            compose.runOnIdle { widthClass.value = WindowWidthSizeClass.Expanded }
            compose.onNodeWithTag(EXPORT_EXPANDED_LAYOUT_TAG).assertExists()
        } finally {
            compose.runOnIdle { controller.close() }
        }
    }

    private fun actions(
        onSelectedStep: (Int) -> Unit = {},
        onResolveNeteaseSong: (String) -> Unit = {},
    ) = EditorScreenActions(
        onSelectedStep = onSelectedStep,
        onSearchQueryChange = {},
        onLinkInputChange = {},
        onProjectNameChange = {},
        onSpecChange = {},
        onExtractPalette = {},
        onPickCover = {},
        onRemoveCover = {},
        onSearchNetease = {},
        onResolveNeteaseSong = onResolveNeteaseSong,
        onResolveNeteaseLink = {},
        onExport = {},
    )

    private fun project(id: String): Project = ProjectTemplates.blank(id = id, now = 1L)

    private fun text(resource: Int): String = compose.activity.getString(resource)

    private enum class PanelHarness { LYRICS, LAYOUT, TYPOGRAPHY }
}
