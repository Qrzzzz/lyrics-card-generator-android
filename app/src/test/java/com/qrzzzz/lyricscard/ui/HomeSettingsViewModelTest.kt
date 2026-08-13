package com.qrzzzz.lyricscard.ui

import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.data.AppThemeMode
import com.qrzzzz.lyricscard.model.ProjectTemplates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `theme modes default to system and decode persisted values safely`() {
        assertEquals(AppThemeMode.SYSTEM, UserPreferences().themeMode)
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromPersistedValue(0))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromPersistedValue(1))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromPersistedValue(2))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromPersistedValue(Int.MAX_VALUE))
    }

    @Test
    fun `home empty list and all project actions stay behind the state owner`() =
        runTest(mainDispatcherRule.dispatcher) {
            val project = ProjectTemplates.blank(id = "project-a", now = 1L)
            val store = FakeProjectStore(listOf(project))
            val viewModel = HomeViewModel(store)
            runCurrent()

            assertEquals(listOf("project-a"), viewModel.uiState.value.projects.map { it.id })
            assertTrue(viewModel.renameProject("project-a", "新名称"))
            assertTrue(viewModel.duplicateProject("project-a"))
            assertTrue(viewModel.deleteProject("project-a"))
            runCurrent()

            assertFalse(viewModel.uiState.value.projects.any { it.id == "project-a" })
            assertTrue(viewModel.uiState.value.projects.any { it.name == "新名称 副本" })
            assertFalse(viewModel.uiState.value.isWorking)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `home missing project failures are localized and never leak exceptions`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = HomeViewModel(FakeProjectStore())
            runCurrent()

            assertFalse(viewModel.renameProject("missing", "名称"))
            assertEquals(UiText.resource(R.string.home_error_rename), viewModel.uiState.value.errorMessage)
            viewModel.clearError()
            assertFalse(viewModel.deleteProject("missing"))
            assertEquals(UiText.resource(R.string.home_error_delete), viewModel.uiState.value.errorMessage)
            viewModel.clearError()
            assertFalse(viewModel.duplicateProject("missing"))
            assertEquals(UiText.resource(R.string.home_error_duplicate), viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `settings keep three-state theme one and two scale safe area and real diagnostics`() =
        runTest(mainDispatcherRule.dispatcher) {
            val preferences = FakePreferencesStore(UserPreferences())
            val diagnostics = FakeDiagnosticsReader()
            val viewModel = SettingsViewModel(preferences, FakeExportFiles(), diagnostics)
            runCurrent()

            viewModel.setThemeMode(AppThemeMode.DARK)
            assertTrue(viewModel.uiState.value.isSavingPreference)
            runCurrent()
            viewModel.setDefaultExportScale(9)
            runCurrent()
            viewModel.setShowSafeArea(true)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AppThemeMode.DARK, state.preferences.themeMode)
            assertEquals(2, state.preferences.defaultExportScale)
            assertTrue(state.preferences.showSafeArea)
            assertEquals(diagnostics.snapshot, state.diagnostics)
            assertFalse(state.isLoadingDiagnostics)
            assertFalse(state.isSavingPreference)
        }

    @Test
    fun `cache clear is single flight and reports actual success then failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Long>()
            val files = FakeExportFiles().apply { clearBlock = { gate.await() } }
            val viewModel = SettingsViewModel(FakePreferencesStore(), files, FakeDiagnosticsReader())
            runCurrent()

            viewModel.clearExportCache()
            viewModel.clearExportCache()
            runCurrent()
            assertTrue(viewModel.uiState.value.isClearingCache)
            assertEquals(1, files.clearCalls)

            gate.complete(3L * 1024L * 1024L)
            runCurrent()
            assertFalse(viewModel.uiState.value.isClearingCache)
            assertEquals(
                UiText.resource(R.string.settings_cache_cleared, 3.0),
                viewModel.uiState.value.cacheStatus,
            )

            files.clearBlock = { error("disk failure") }
            viewModel.clearExportCache()
            runCurrent()
            assertEquals(2, files.clearCalls)
            assertEquals(
                UiText.resource(R.string.settings_error_clear_cache),
                viewModel.uiState.value.errorMessage,
            )
            assertNull(viewModel.uiState.value.cacheStatus)
        }

    @Test
    fun `diagnostics failure degrades without exposing the cause`() =
        runTest(mainDispatcherRule.dispatcher) {
            val diagnostics = FakeDiagnosticsReader().apply {
                failure = IllegalStateException("sensitive payload")
            }
            val viewModel = SettingsViewModel(FakePreferencesStore(), FakeExportFiles(), diagnostics)
            runCurrent()

            assertNull(viewModel.uiState.value.diagnostics)
            assertFalse(viewModel.uiState.value.isLoadingDiagnostics)
            assertEquals(
                UiText.resource(R.string.settings_diagnostics_unavailable),
                viewModel.uiState.value.diagnosticsError,
            )
        }
}
