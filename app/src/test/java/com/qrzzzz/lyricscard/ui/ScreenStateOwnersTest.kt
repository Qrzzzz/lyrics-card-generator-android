package com.qrzzzz.lyricscard.ui

import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.ProjectTemplates
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
class ScreenStateOwnersTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeOwnerCollectsProjectsAndPerformsCrudThroughStoreBoundary() = runTest(mainDispatcherRule.dispatcher) {
        val initial = ProjectTemplates.blank(id = "home-project", now = 1L)
        val store = FakeProjectStore(listOf(initial))
        val viewModel = HomeViewModel(store)
        runCurrent()

        assertEquals(listOf(initial.id), viewModel.uiState.value.projects.map { it.id })
        val createdId = viewModel.createBlank()
        val duplicateTapId = viewModel.createBlank()
        runCurrent()

        assertTrue(createdId.orEmpty().startsWith("blank-"))
        assertNull(duplicateTapId)
        assertTrue(viewModel.uiState.value.projects.any { it.id == createdId })
        assertTrue(viewModel.uiState.value.isWorking)

        viewModel.markNavigationCommitted()
        viewModel.onNavigationResumed()

        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun settingsOwnerPublishesPreferencesAndCacheOperationState() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakePreferencesStore(UserPreferences(darkMode = false))
        val files = FakeExportFiles().apply { clearBytes = 2L * 1024L * 1024L }
        val diagnostics = FakeDiagnosticsReader()
        val viewModel = SettingsViewModel(preferences, files, diagnostics)
        runCurrent()

        viewModel.setDarkMode(true)
        runCurrent()
        viewModel.setDefaultExportScale(1)
        runCurrent()
        viewModel.clearExportCache()
        runCurrent()

        assertTrue(viewModel.uiState.value.preferences.darkMode)
        assertEquals(1, viewModel.uiState.value.preferences.defaultExportScale)
        assertEquals(
            UiText.resource(R.string.settings_cache_cleared, 2.0),
            viewModel.uiState.value.cacheStatus,
        )
        assertFalse(viewModel.uiState.value.isClearingCache)
        assertEquals(diagnostics.snapshot, viewModel.uiState.value.diagnostics)
        assertEquals(1, diagnostics.reads)
    }
}
