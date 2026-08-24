package com.qrzzzz.lyricscard.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.IdlingPolicies
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.AppThemeMode
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import java.util.concurrent.TimeUnit
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
                                        preferences = UserPreferences(themeMode = AppThemeMode.DARK),
                                        isLoading = false,
                                    ),
                                    onBack = {},
                                    onThemeMode = {},
                                    onDefaultExportScale = {},
                                    onShowSafeArea = {},
                                    onClearExportCache = {},
                                )
                            }
                        }
                    }
                }
            }

            listOf(1f, 1.3f, 2f).forEach { scale ->
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
                            compose.onNodeWithTag(EDITOR_NETEASE_QUERY_FIELD_TAG)
                                .performScrollTo()
                                .assertIsDisplayed()
                            compose.onNodeWithText(text(R.string.editor_next_step))
                                .assertIsDisplayed()
                        }
                        HarnessScreen.EXPORT -> {
                            assertDisplayedInsideViewport(text(R.string.export_title))
                            compose.onNodeWithTag(EXPORT_OPTIONS_LIST_TAG)
                                .performScrollToNode(hasTestTag(EXPORT_SAVE_ACTION_TAG))
                            compose.onNodeWithTag(EXPORT_SAVE_ACTION_TAG)
                                .assertIsDisplayed()
                        }
                        HarnessScreen.SETTINGS -> {
                            assertDisplayedInsideViewport(text(R.string.settings_title))
                            compose.onNodeWithTag(SETTINGS_LIST_TAG)
                                .performScrollToNode(hasTestTag(SETTINGS_CLEAR_CACHE_TAG))
                            compose.onNodeWithTag(SETTINGS_CLEAR_CACHE_TAG)
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
    fun landscapeEditorAndExportKeepActionsReachableAfterImeFocus() = withEspressoTimeouts {
        val startedAt = SystemClock.elapsedRealtime()
        logImeStage(startedAt, "test-start")
        val originalOrientation = compose.activity.requestedOrientation
        val originalConfigurationOrientation = compose.activity.resources.configuration.orientation
        logImeStage(startedAt, "orientation-request-begin")
        compose.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        waitForOrientation(Configuration.ORIENTATION_LANDSCAPE)
        compose.activityRule.scenario.onActivity { activity ->
            activity.configureImeTestWindow()
            activity.enableEdgeToEdge()
        }
        logImeStage(startedAt, "orientation-ready")
        val deviceDensity = compose.activity.resources.displayMetrics.density
        val context = compose.activity.applicationContext
        val controller = RendererController(context, ProjectAssetStore(context))
        val project = ProjectTemplates.blank(id = "landscape-shell", now = 1L)
        val showExport = mutableStateOf(false)
        val composeImeBottomPx = mutableIntStateOf(0)
        val effectiveImeBottomPx = mutableIntStateOf(0)

        try {
            logImeStage(startedAt, "set-content-begin")
            compose.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = deviceDensity, fontScale = 2f),
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                ) {
                    val rawComposeImeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
                    val imeInsets = rememberLyricsImeInsets()
                    SideEffect {
                        composeImeBottomPx.intValue = rawComposeImeBottom
                        effectiveImeBottomPx.intValue = imeInsets.effectiveBottomPx
                    }
                    LyricsCardTheme {
                        Box(
                            Modifier
                                .fillMaxSize()
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
            logImeStage(startedAt, "set-content-ready")

            logImeStage(startedAt, "editor-input-begin")
            compose.onNodeWithTag(EDITOR_NETEASE_QUERY_FIELD_TAG)
                .performScrollTo()
                .performClick()
            requestPlatformIme(startedAt, "editor")
            compose.onNodeWithTag(EDITOR_NETEASE_QUERY_FIELD_TAG)
                .performTextInput("landscape query")
            logImeStage(startedAt, "editor-input-complete")
            var platformImeBottomPx = 0
            waitForCondition(IME_TIMEOUT_MS) {
                platformImeBottomPx = currentPlatformImeBottom()
                platformImeBottomPx > 0
            }
            waitForCondition(IME_TIMEOUT_MS) { effectiveImeBottomPx.intValue > 0 }
            logImeSnapshot(
                startedAt = startedAt,
                screen = "editor",
                platformBottomPx = platformImeBottomPx,
                composeBottomPx = composeImeBottomPx.intValue,
                effectiveBottomPx = effectiveImeBottomPx.intValue,
            )
            logImeStage(startedAt, "editor-ime-ready")
            assertTrue("effective IME inset did not become visible", effectiveImeBottomPx.intValue > 0)
            assertTextDisplayedInsideImeVisibleViewport(
                value = text(R.string.editor_next_step),
                effectiveImeBottomPx = effectiveImeBottomPx.intValue,
            )

            compose.activityRule.scenario.onActivity { activity ->
                Log.i(
                    IME_TAG,
                    "stage=ime-dismiss screen=editor requested=${activity.dismissImeFromCurrentFocus()}",
                )
            }
            waitForCondition(IME_TIMEOUT_MS) {
                currentPlatformImeBottom() == 0 && effectiveImeBottomPx.intValue == 0
            }
            logImeStage(startedAt, "export-switch-begin")
            compose.runOnIdle { showExport.value = true }
            assertDisplayedInsideViewport(text(R.string.export_title))
            logImeStage(startedAt, "export-switch-ready")
            compose.onNodeWithTag(EXPORT_OPTIONS_LIST_TAG)
                .performScrollToNode(hasTestTag(EXPORT_FILE_NAME_TAG))
            logImeStage(startedAt, "export-input-begin")
            compose.onNodeWithTag(EXPORT_FILE_NAME_TAG)
                .performClick()
            requestPlatformIme(startedAt, "export")
            compose.onNodeWithTag(EXPORT_FILE_NAME_TAG)
                .performTextInput("x")
            logImeStage(startedAt, "export-input-complete")
            waitForCondition(IME_TIMEOUT_MS) {
                platformImeBottomPx = currentPlatformImeBottom()
                platformImeBottomPx > 0
            }
            waitForCondition(IME_TIMEOUT_MS) { effectiveImeBottomPx.intValue > 0 }
            logImeSnapshot(
                startedAt = startedAt,
                screen = "export",
                platformBottomPx = platformImeBottomPx,
                composeBottomPx = composeImeBottomPx.intValue,
                effectiveBottomPx = effectiveImeBottomPx.intValue,
            )
            logImeStage(startedAt, "export-ime-ready")
            assertTrue("effective IME inset was not visible over Export", effectiveImeBottomPx.intValue > 0)
            assertMinimumHeight(EXPORT_OPTIONS_LIST_TAG, MIN_TOUCH_TARGET_DP)
            compose.onNodeWithTag(EXPORT_OPTIONS_LIST_TAG)
                .performScrollToNode(hasTestTag(EXPORT_SAVE_ACTION_TAG))
            compose.onNodeWithTag(EXPORT_SAVE_ACTION_TAG)
                .assertIsDisplayed()
            assertMinimumHeight(EXPORT_SAVE_ACTION_TAG, MIN_TOUCH_TARGET_DP)
            assertTagDisplayedInsideImeVisibleViewport(
                tag = EXPORT_SAVE_ACTION_TAG,
                effectiveImeBottomPx = effectiveImeBottomPx.intValue,
            )
            logImeStage(startedAt, "assertions-complete")
        } finally {
            logImeStage(startedAt, "final-ime-dismiss-begin")
            compose.activityRule.scenario.onActivity { activity ->
                Log.i(
                    IME_TAG,
                    "stage=ime-dismiss screen=final requested=${activity.dismissImeFromCurrentFocus()}",
                )
            }
            waitForCondition(IME_TIMEOUT_MS) {
                currentPlatformImeBottom() == 0 && effectiveImeBottomPx.intValue == 0
            }
            SystemClock.sleep(IME_SETTLE_MS)
            logImeStage(startedAt, "final-ime-dismiss-complete")
            logImeStage(startedAt, "controller-close-begin")
            compose.runOnIdle { controller.close() }
            logImeStage(startedAt, "controller-close-complete")
            logImeStage(startedAt, "orientation-restore-begin")
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = originalOrientation
            }
            waitForOrientation(originalConfigurationOrientation)
            SystemClock.sleep(IME_SETTLE_MS)
            logImeStage(startedAt, "orientation-restore-complete")
        }
        logImeStage(startedAt, "test-complete")
    }

    private inline fun withEspressoTimeouts(block: () -> Unit) {
        val master = IdlingPolicies.getMasterIdlingPolicy()
        val resource = IdlingPolicies.getDynamicIdlingResourceErrorPolicy()
        IdlingPolicies.setMasterPolicyTimeout(ESPRESSO_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        IdlingPolicies.setIdlingResourceTimeout(ESPRESSO_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try {
            block()
        } finally {
            IdlingPolicies.setMasterPolicyTimeout(master.idleTimeout, master.idleTimeoutUnit)
            IdlingPolicies.setIdlingResourceTimeout(resource.idleTimeout, resource.idleTimeoutUnit)
        }
    }

    private fun logImeStage(startedAt: Long, stage: String) {
        Log.i(IME_TAG, "stage=$stage elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")
    }

    private fun requestPlatformIme(startedAt: Long, screen: String) {
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { activity ->
            val result = activity.requestImeForCurrentFocus()
            Log.i(
                IME_TAG,
                "stage=ime-request screen=$screen elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                    "requested=${result.requested} target=${result.targetClass} " +
                    "attached=${result.attached} windowFocused=${result.windowFocused}",
            )
        }
    }

    private fun currentPlatformImeBottom(): Int {
        var bottom = 0
        compose.activityRule.scenario.onActivity { activity ->
            bottom = currentPlatformImeBottom(activity.window.decorView)
        }
        return bottom
    }

    private fun logImeSnapshot(
        startedAt: Long,
        screen: String,
        platformBottomPx: Int,
        composeBottomPx: Int,
        effectiveBottomPx: Int,
    ) {
        Log.i(
            IME_TAG,
            "stage=ime-snapshot screen=$screen elapsedMs=${SystemClock.elapsedRealtime() - startedAt} " +
                "platformBottomPx=$platformBottomPx composeBottomPx=$composeBottomPx " +
                "effectiveBottomPx=$effectiveBottomPx",
        )
    }

    private fun assertDisplayedInsideViewport(value: String) {
        val node = compose.onNodeWithText(value).assertIsDisplayed().fetchSemanticsNode()
        val viewport = compose.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val bounds = node.boundsInRoot
        Log.i(IME_TAG, "stage=action-bounds label=$value viewport=$viewport action=$bounds")
        assertTrue("$value starts outside viewport", bounds.left >= viewport.left && bounds.top >= viewport.top)
        assertTrue("$value ends outside viewport", bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
    }

    private fun assertTextDisplayedInsideImeVisibleViewport(
        value: String,
        effectiveImeBottomPx: Int,
    ) {
        val interaction = compose.onNodeWithText(value)
        val bounds = interaction.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        Log.i(
            IME_TAG,
            "stage=ime-pre-display-bounds label=$value viewport=$viewport action=$bounds " +
                "effectiveImeBottomPx=$effectiveImeBottomPx visibleBottom=${viewport.bottom - effectiveImeBottomPx}",
        )
        interaction.assertIsDisplayed()
        assertBoundsInsideImeVisibleViewport(value, bounds, effectiveImeBottomPx)
    }

    private fun assertTagDisplayedInsideImeVisibleViewport(
        tag: String,
        effectiveImeBottomPx: Int,
    ) {
        val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertBoundsInsideImeVisibleViewport(tag, bounds, effectiveImeBottomPx)
    }

    private fun assertBoundsInsideImeVisibleViewport(
        label: String,
        bounds: androidx.compose.ui.geometry.Rect,
        effectiveImeBottomPx: Int,
    ) {
        val viewport = compose.onNodeWithTag(VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val visibleBottom = viewport.bottom - effectiveImeBottomPx
        Log.i(
            IME_TAG,
            "stage=ime-action-bounds label=$label viewport=$viewport action=$bounds " +
                "effectiveImeBottomPx=$effectiveImeBottomPx visibleBottom=$visibleBottom",
        )
        assertTrue("$label starts outside viewport", bounds.left >= viewport.left && bounds.top >= viewport.top)
        assertTrue("$label ends outside viewport width", bounds.right <= viewport.right)
        assertTrue(
            "$label bottom ${bounds.bottom} was below IME-visible bottom $visibleBottom",
            bounds.bottom <= visibleBottom,
        )
    }

    private fun assertMinimumHeight(tag: String, minimumDp: Float) {
        val height = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        val minimumPx = minimumDp * compose.activity.resources.displayMetrics.density
        assertTrue("$tag height $height px was below $minimumPx px", height >= minimumPx)
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

    private fun waitForOrientation(expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + ORIENTATION_TIMEOUT_MS
        var actual = runCatching { compose.activity.resources.configuration.orientation }.getOrDefault(0)
        while (actual != expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
            actual = runCatching { compose.activity.resources.configuration.orientation }.getOrDefault(0)
        }
        assertTrue("orientation was $actual instead of $expected", actual == expected)
    }

    private fun text(resource: Int): String = compose.activity.getString(resource)

    private enum class HarnessScreen { HOME, EDITOR, EXPORT, SETTINGS }

    private companion object {
        const val VIEWPORT_TAG = "app-shell-viewport"
        const val IME_TIMEOUT_MS = 5_000L
        const val ORIENTATION_TIMEOUT_MS = 5_000L
        const val POLL_FRAME_MILLIS = 100L
        const val ESPRESSO_TIMEOUT_MS = 20_000L
        const val IME_SETTLE_MS = 500L
        const val MIN_TOUCH_TARGET_DP = 48f
        const val IME_TAG = "LCG_IME"
    }
}
