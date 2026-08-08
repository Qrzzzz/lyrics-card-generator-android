package com.qrzzzz.lyricscard.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
            assertEquals(
                index.toFloat(),
                compose.onNodeWithContentDescription(description)
                    .assertExists()
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.TraversalIndex],
            )
        }
        compose.onNodeWithText(text(EditorStep.CHOOSE_SONG.description))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
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

        val resultAction = hasContentDescription("晴天", substring = true) and hasClickAction()
        compose.onNode(resultAction).performClick()
        assertEquals("42", resolvedId.value)

        compose.runOnIdle {
            lookup.value = lookup.value.copy(
                isResolving = true,
                phase = NeteaseLookupPhase.RESOLVING,
                message = UiText.resource(R.string.editor_netease_resolving),
            )
        }
        compose.onNode(resultAction).assertIsNotEnabled()
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
        val translationToggle = compose.onNode(
            hasText(text(R.string.editor_show_translation)) and hasClickAction(),
        ).performScrollTo()
        assertMinTouchHeight(translationToggle, "translation setting row")
        translationToggle.performClick()
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
        var releasedBitmap: Bitmap? = null
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
                        onPreviewBitmapReleased = { releasedBitmap = it },
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
            .also { assertMinTouchHeight(it, "export cancel") }

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
            .also { assertMinTouchHeight(it, "export save") }
        compose.onNodeWithText(text(R.string.common_share)).performScrollTo().assertIsEnabled()
            .also { assertMinTouchHeight(it, "export share") }
        compose.runOnIdle { state.value = state.value.copy(preview = ExportPreviewUiState()) }
        compose.waitForIdle()
        assertTrue("Compose did not release the replaced preview bitmap", releasedBitmap === bitmap)
        if (!bitmap.isRecycled) bitmap.recycle()
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

    @Test
    fun compactBottomSheetKeepsPreviewReserveAndNextAboveImeAtTwoXFontScale() {
        val originalOrientation = compose.activity.requestedOrientation
        compose.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        waitForOrientation(Configuration.ORIENTATION_PORTRAIT)
        val deviceDensity = compose.activity.resources.displayMetrics.density
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val project = project("compact-font-ime")
        val imeBottomPx = mutableIntStateOf(0)
        try {
            compose.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = deviceDensity, fontScale = 2f),
                ) {
                    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                    SideEffect { imeBottomPx.intValue = imeBottom }
                    LyricsCardTheme {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag(COMPACT_IME_VIEWPORT_TAG),
                        ) {
                            EditorScreen(
                                state = EditorUiState(
                                    projectId = project.id,
                                    currentProject = project.copy(
                                        spec = project.spec.copy(
                                            canvas = project.spec.canvas.copy(ratio = CanvasRatio.CUSTOM),
                                        ),
                                    ),
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
                                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                            )
                        }
                    }
                }
            }

            val widthField = compose.onNodeWithTag(EDITOR_WIDTH_FIELD_TAG)
                .performScrollTo()
                .performClick()
            var platformImeBottomPx = 0
            waitForCondition(UI_TIMEOUT_MS) {
                platformImeBottomPx = currentPlatformImeBottom()
                platformImeBottomPx > 0
            }
            waitForCondition(UI_TIMEOUT_MS) { imeBottomPx.intValue > 0 }
            widthField.performTextReplacement("900")

            val screen = compose.onNodeWithTag(EDITOR_SCREEN_TAG).fetchSemanticsNode().boundsInRoot
            val handleNode = compose.onNodeWithTag(
                EDITOR_COMPACT_SHEET_HANDLE_TAG,
                useUnmergedTree = true,
            )
            val handle = handleNode.fetchSemanticsNode().boundsInRoot
            try {
                handleNode.assertIsDisplayed()
            } catch (cause: AssertionError) {
                val viewport = compose.onNodeWithTag(COMPACT_IME_VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
                throw AssertionError(
                    "compact handle was not displayed; screen=$screen handle=$handle " +
                        "viewport=$viewport platformImeBottomPx=$platformImeBottomPx " +
                        "composeImeBottomPx=${imeBottomPx.intValue}",
                    cause,
                )
            }
            assertTrue(
                "compact preview reserve was ${handle.top - screen.top}px",
                handle.top - screen.top >= 72f * deviceDensity,
            )

            val viewport = compose.onNodeWithTag(COMPACT_IME_VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
            val next = compose.onNodeWithText(text(R.string.editor_next_step))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue("Next starts outside the compact viewport", next.top >= viewport.top)
            assertTrue(
                "Next is obscured by the IME",
                next.bottom <= viewport.bottom - imeBottomPx.intValue,
            )
            assertTrue("Next height was ${next.height}px", next.height >= 48f * deviceDensity)
        } finally {
            compose.runOnIdle { controller.close() }
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = originalOrientation
            }
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

    private fun assertMinTouchHeight(
        node: androidx.compose.ui.test.SemanticsNodeInteraction,
        label: String,
    ) {
        val height = node.fetchSemanticsNode().boundsInRoot.height
        assertTrue("$label height was $height", height >= 48f)
    }

    private fun waitForCondition(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        var satisfied = runCatching(condition).getOrDefault(false)
        while (!satisfied && SystemClock.elapsedRealtime() < deadline) {
            compose.mainClock.advanceTimeBy(POLL_FRAME_MILLIS)
            compose.waitForIdle()
            SystemClock.sleep(50)
            satisfied = runCatching(condition).getOrDefault(false)
        }
        assertTrue("condition timed out after $timeoutMillis ms", satisfied)
    }

    private fun currentPlatformImeBottom(): Int {
        var bottom = 0
        compose.activityRule.scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
        }
        return bottom
    }

    private fun waitForOrientation(expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + ORIENTATION_TIMEOUT_MS
        var actual = runCatching { compose.activity.resources.configuration.orientation }.getOrDefault(0)
        while (actual != expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
            actual = runCatching { compose.activity.resources.configuration.orientation }.getOrDefault(0)
        }
        assertTrue("orientation was $actual instead of $expected", actual == expected)
    }

    private enum class PanelHarness { LYRICS, LAYOUT, TYPOGRAPHY }

    private companion object {
        const val COMPACT_IME_VIEWPORT_TAG = "compact-ime-viewport"
        const val UI_TIMEOUT_MS = 20_000L
        const val ORIENTATION_TIMEOUT_MS = 5_000L
        const val POLL_FRAME_MILLIS = 100L
    }
}
