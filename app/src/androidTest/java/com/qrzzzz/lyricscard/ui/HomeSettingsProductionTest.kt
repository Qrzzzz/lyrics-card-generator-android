package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qrzzzz.lyricscard.DiagnosticsSnapshot
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeSettingsProductionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeEmptyListAndExistingActionsRemainOperableWithValidatedDialogs() {
        val projects = mutableStateOf(emptyList<ProjectSummary>())
        val opened = mutableListOf<String>()
        val duplicated = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        var blankCreates = 0
        var sampleCreates = 0
        var settingsOpens = 0

        compose.setContent {
            LyricsCardTheme {
                HomeScreen(
                    projects = projects.value,
                    isWorking = false,
                    snackbarHost = {},
                    onCreateBlank = { blankCreates += 1 },
                    onCreateSample = { sampleCreates += 1 },
                    onOpen = opened::add,
                    onDuplicate = duplicated::add,
                    onRename = { id, name -> renamed += id to name },
                    onDelete = deleted::add,
                    onSettings = { settingsOpens += 1 },
                    thumbnailLoader = ThumbnailLoader { _, _, _ -> null },
                )
            }
        }

        compose.onNodeWithTag(HOME_EMPTY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).performClick()
        compose.onNodeWithTag(HOME_CREATE_SAMPLE_TAG).performClick()
        compose.onNodeWithTag(HOME_SETTINGS_TAG).performClick()
        compose.runOnIdle {
            assertEquals(1, blankCreates)
            assertEquals(1, sampleCreates)
            assertEquals(1, settingsOpens)
            projects.value = listOf(summary("project-a", "很长的项目名称用来验证列表不会破坏操作顺序", 2L))
        }

        compose.onNodeWithTag("$HOME_PROJECT_ROW_PREFIX${"project-a"}").performClick()
        compose.onNodeWithTag("$HOME_PROJECT_MENU_PREFIX${"project-a"}").performClick()
        compose.onNodeWithText(text(R.string.home_duplicate)).performClick()
        compose.runOnIdle {
            assertEquals(listOf("project-a"), opened)
            assertEquals(listOf("project-a"), duplicated)
        }

        compose.onNodeWithTag("$HOME_PROJECT_MENU_PREFIX${"project-a"}").performClick()
        compose.onNodeWithText(text(R.string.home_rename)).performClick()
        compose.onNodeWithTag(HOME_RENAME_FIELD_TAG)
            .assertIsFocused()
            .performTextClearance()
        compose.onNodeWithTag(HOME_RENAME_CONFIRM_TAG).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.home_project_name_required)).assertIsDisplayed()
        compose.onNodeWithTag(HOME_RENAME_FIELD_TAG).performTextInput("正式名称")
        compose.onNodeWithTag(HOME_RENAME_FIELD_TAG).performImeAction()
        compose.runOnIdle { assertEquals(listOf("project-a" to "正式名称"), renamed) }

        compose.onNodeWithTag("$HOME_PROJECT_MENU_PREFIX${"project-a"}").performClick()
        compose.onNodeWithText(text(R.string.common_delete)).performClick()
        compose.onNodeWithText(text(R.string.home_delete_project_body)).assertIsDisplayed()
        compose.onNodeWithTag(HOME_DELETE_CONFIRM_TAG).performClick()
        compose.runOnIdle { assertEquals(listOf("project-a"), deleted) }
    }

    @Test
    fun thumbnailReplacementCancelsTheOldRequestAndMissingImageUsesFallback() {
        val projects = mutableStateOf(listOf(summary("thumb", "缩略图", 1L, "old.png")))
        val oldGate = CompletableDeferred<androidx.compose.ui.graphics.ImageBitmap?>()
        val oldCancelled = AtomicBoolean(false)
        val replacement = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }.asImageBitmap()
        val loader = ThumbnailLoader { path, _, _ ->
            when (path) {
                "old.png" -> try {
                    oldGate.await()
                } catch (cause: CancellationException) {
                    oldCancelled.set(true)
                    throw cause
                }
                "replacement.png" -> replacement
                else -> null
            }
        }

        compose.setContent {
            LyricsCardTheme {
                HomeScreen(
                    projects = projects.value,
                    isWorking = false,
                    snackbarHost = {},
                    onCreateBlank = {},
                    onCreateSample = {},
                    onOpen = {},
                    onDuplicate = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onSettings = {},
                    thumbnailLoader = loader,
                )
            }
        }

        compose.onNodeWithTag("$HOME_THUMBNAIL_LOADING_PREFIX${"thumb"}").assertIsDisplayed()
        compose.runOnIdle {
            projects.value = listOf(summary("thumb", "缩略图", 2L, "replacement.png"))
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            oldCancelled.get() && compose.onAllNodes(
                androidx.compose.ui.test.hasTestTag("$HOME_THUMBNAIL_IMAGE_PREFIX${"thumb"}"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("$HOME_THUMBNAIL_IMAGE_PREFIX${"thumb"}").assertIsDisplayed()

        compose.runOnIdle {
            oldGate.complete(
                Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.RED)
                }.asImageBitmap(),
            )
            projects.value = listOf(summary("thumb", "缩略图", 3L, "missing.png"))
        }
        compose.onNodeWithTag("$HOME_THUMBNAIL_FALLBACK_PREFIX${"thumb"}").assertIsDisplayed()
        compose.onNodeWithTag("$HOME_THUMBNAIL_IMAGE_PREFIX${"thumb"}").assertDoesNotExist()
    }

    @Test
    fun settingsRowsAreSingleAccessibleControlsAndKeepTwoStatePreferences() {
        val state = mutableStateOf(
            SettingsUiState(
                preferences = UserPreferences(
                    darkMode = false,
                    defaultExportScale = 1,
                    showSafeArea = false,
                ),
                isLoading = false,
                diagnostics = diagnostics(),
                isLoadingDiagnostics = false,
            ),
        )
        var cacheClears = 0

        compose.setContent {
            LyricsCardTheme(darkTheme = state.value.preferences.darkMode) {
                SettingsScreen(
                    state = state.value,
                    onBack = {},
                    onDarkMode = { value ->
                        state.value = state.value.copy(
                            preferences = state.value.preferences.copy(darkMode = value),
                        )
                    },
                    onDefaultExportScale = { value ->
                        state.value = state.value.copy(
                            preferences = state.value.preferences.copy(defaultExportScale = value),
                        )
                    },
                    onShowSafeArea = { value ->
                        state.value = state.value.copy(
                            preferences = state.value.preferences.copy(showSafeArea = value),
                        )
                    },
                    onClearExportCache = {
                        cacheClears += 1
                        state.value = state.value.copy(isClearingCache = true)
                    },
                )
            }
        }

        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onAllNodes(switchRole).assertCountEquals(2)
        compose.onNodeWithTag(SETTINGS_DARK_MODE_TAG)
            .assert(switchRole)
            .performClick()
        compose.onNodeWithTag(SETTINGS_EXPORT_QUALITY_TAG).performClick()
        compose.onNodeWithTag(SETTINGS_SAFE_AREA_TAG)
            .assert(switchRole)
            .performClick()
        compose.runOnIdle {
            assertTrue(state.value.preferences.darkMode)
            assertEquals(2, state.value.preferences.defaultExportScale)
            assertTrue(state.value.preferences.showSafeArea)
        }

        compose.onNodeWithTag(SETTINGS_CLEAR_CACHE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(SETTINGS_CLEAR_CACHE_TAG).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, cacheClears) }
        compose.onNodeWithTag(SETTINGS_APP_VERSION_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1.0.0 (10000)").assertIsDisplayed()
        compose.onNodeWithTag(SETTINGS_WEBVIEW_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("com.google.android.webview 150.0.0").assertIsDisplayed()
        compose.onNodeWithText("默认不声明 INTERNET", substring = true).assertDoesNotExist()
        compose.onNodeWithText("已声明 INTERNET", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun compactWideLandscapeAndTwoHundredPercentFontKeepKeyTargetsReachable() {
        val width = mutableStateOf(360.dp)
        val height = mutableStateOf(640.dp)
        val fontScale = mutableFloatStateOf(2f)
        val screen = mutableStateOf(TestScreen.Home)
        val project = summary("layout", "超长项目名称在两倍字体下仍应保留菜单入口", 1L)

        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue),
                LocalLayoutDirection provides LayoutDirection.Ltr,
            ) {
                LyricsCardTheme {
                    Box(
                        Modifier
                            .requiredSize(width.value, height.value)
                            .testTag(VIEWPORT_TAG),
                    ) {
                        when (screen.value) {
                            TestScreen.Home -> HomeScreen(
                                projects = listOf(project),
                                isWorking = false,
                                snackbarHost = {},
                                onCreateBlank = {},
                                onCreateSample = {},
                                onOpen = {},
                                onDuplicate = {},
                                onRename = { _, _ -> },
                                onDelete = {},
                                onSettings = {},
                                thumbnailLoader = ThumbnailLoader { _, _, _ -> null },
                            )
                            TestScreen.Settings -> SettingsScreen(
                                state = SettingsUiState(
                                    preferences = UserPreferences(),
                                    isLoading = false,
                                    diagnostics = diagnostics(),
                                    isLoadingDiagnostics = false,
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

        compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).assertIsDisplayed()
        compose.onNodeWithTag(HOME_CREATE_SAMPLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag("$HOME_PROJECT_MENU_PREFIX${"layout"}").performScrollTo().assertIsDisplayed()
        assertMinTouchTarget(HOME_CREATE_BLANK_TAG)
        assertMinTouchTarget(HOME_CREATE_SAMPLE_TAG)
        assertMinTouchTarget("$HOME_PROJECT_ROW_PREFIX${"layout"}")
        assertMinTouchTarget("$HOME_PROJECT_MENU_PREFIX${"layout"}")

        compose.runOnIdle { screen.value = TestScreen.Settings }
        compose.onNodeWithTag(SETTINGS_DARK_MODE_TAG).assertIsDisplayed()
        assertMinTouchTarget(SETTINGS_DARK_MODE_TAG)
        compose.onNodeWithTag(SETTINGS_CLEAR_CACHE_TAG).performScrollTo().assertIsDisplayed()
        assertMinTouchTarget(SETTINGS_CLEAR_CACHE_TAG)

        compose.runOnIdle {
            screen.value = TestScreen.Home
            width.value = 840.dp
            height.value = 420.dp
            fontScale.floatValue = 1.3f
        }
        compose.onNodeWithTag(HOME_CREATE_BLANK_TAG).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag(HOME_CREATE_SAMPLE_TAG).assertIsDisplayed().assertIsEnabled()
    }

    private fun assertMinTouchTarget(tag: String) {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue("$tag width was ${bounds.width}", bounds.width >= 48f)
        assertTrue("$tag height was ${bounds.height}", bounds.height >= 48f)
    }

    private fun summary(id: String, name: String, updatedAt: Long, thumbnail: String? = null): ProjectSummary =
        ProjectTemplates.blank(id = id, now = updatedAt)
            .copy(name = name, thumbnailPath = thumbnail)
            .toSummary()

    private fun Project.toSummary() = ProjectSummary(
        id = id,
        name = name,
        schemaVersion = spec.schemaVersion,
        rendererVersion = spec.rendererVersion,
        coverAssetId = coverAssetId,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastExportedAt = lastExportedAt,
    )

    private fun diagnostics() = DiagnosticsSnapshot(
        appVersionName = "1.0.0",
        appVersionCode = 10000,
        rendererVersion = "android-alpha-renderer-1",
        rendererSchemaVersion = 1,
        rendererProtocolVersion = 1,
        rendererSourcePackageVersion = "4.3.8",
        rendererSourceCommit = "b894db9e121848122a16ddcdaaab1283ffab1e27",
        rendererFontManifestHash = "cfd264f074ff825dba28f74f8234ee01f7e55bff4aeb7b1676fd54943563bf7c",
        systemWebViewPackage = "com.google.android.webview",
        systemWebViewVersion = "150.0.0",
    )

    private fun text(resource: Int): String = compose.activity.getString(resource)

    private enum class TestScreen { Home, Settings }

    private companion object {
        const val VIEWPORT_TAG = "home-settings-viewport"
    }
}
