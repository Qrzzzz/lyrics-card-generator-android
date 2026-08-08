package com.qrzzzz.lyricscard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.qrzzzz.lyricscard.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EditorRouteContent(
    entry: NavBackStackEntry,
    factory: ViewModelProvider.Factory,
    container: AppContainer,
    showSafeArea: Boolean,
    navController: NavHostController,
) {
    val editorViewModel: EditorViewModel = viewModel(
        viewModelStoreOwner = entry,
        factory = factory,
    )
    val state by editorViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it.resolve(context))
            editorViewModel.clearError()
        }
    }
    LaunchedEffect(state.projectUnavailable) {
        if (state.projectUnavailable) navController.returnHome()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        editorViewModel.requestFlush()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        editorViewModel.onNavigationResumed()
    }
    BackHandler(enabled = state.currentProject != null) {
        if (!state.isLeaving) {
            scope.launch { editorViewModel.popAfterSaving(navController) }
        }
    }

    if (state.currentProject == null) {
        ProjectLoading()
    } else {
        EditorScreen(
            state = state,
            showSafeArea = showSafeArea,
            renderer = container.rendererController,
            snackbarHost = { SnackbarHost(snackbar) },
            onBack = {
                scope.launch { editorViewModel.popAfterSaving(navController) }
            },
            onSelectedStep = editorViewModel::selectStep,
            onSearchQueryChange = editorViewModel::updateSearchQuery,
            onLinkInputChange = editorViewModel::updateLinkInput,
            onProjectNameChange = editorViewModel::updateProjectName,
            onSpecChange = { next -> editorViewModel.updateSpec { next } },
            onMeasuredHeight = editorViewModel::updateMeasuredHeight,
            onExtractPalette = editorViewModel::extractPalette,
            onUndo = editorViewModel::undo,
            onRedo = editorViewModel::redo,
            onSelectCover = editorViewModel::importCover,
            onRemoveCover = editorViewModel::removeCover,
            onSearchNetease = editorViewModel::searchNetease,
            onResolveNeteaseSong = editorViewModel::resolveNeteaseSong,
            onResolveNeteaseLink = editorViewModel::resolveNeteaseLink,
            onExport = {
                scope.launch {
                    if (editorViewModel.prepareForNavigation()) {
                        withContext(Dispatchers.Main.immediate) {
                            editorViewModel.markNavigationCommitted()
                            try {
                                navController.navigate(ExportRoute(state.projectId)) {
                                    launchSingleTop = true
                                }
                            } catch (cause: Throwable) {
                                editorViewModel.navigationFailed()
                                throw cause
                            }
                        }
                    }
                }
            },
        )
    }
}

private suspend fun EditorViewModel.popAfterSaving(navController: NavHostController) {
    if (!prepareForNavigation()) return
    withContext(Dispatchers.Main.immediate) {
        markNavigationCommitted()
        try {
            if (!navController.popBackStack()) navigationFailed()
        } catch (cause: Throwable) {
            navigationFailed()
            throw cause
        }
    }
}
