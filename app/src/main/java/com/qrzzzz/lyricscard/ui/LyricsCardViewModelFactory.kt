package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.qrzzzz.lyricscard.AppContainer

class LyricsCardViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) -> HomeViewModel(
            projects = container.projects,
        )

        modelClass.isAssignableFrom(EditorViewModel::class.java) -> EditorViewModel(
            savedStateHandle = extras.createSavedStateHandle(),
            projects = container.projects,
            projectAssets = container.projectAssets,
            neteaseClient = container.netease,
            renderer = container.renderer,
            sessions = container.editorSessions,
        )

        modelClass.isAssignableFrom(ExportViewModel::class.java) -> ExportViewModel(
            savedStateHandle = extras.createSavedStateHandle(),
            projects = container.projects,
            preferences = container.preferences,
            renderer = container.renderer,
            exportFiles = container.exportFiles,
        )

        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
            preferences = container.preferences,
            exportFiles = container.exportFiles,
            diagnostics = container.diagnostics,
        )

        else -> error("Unknown ViewModel class: ${modelClass.name}")
    } as T
}
