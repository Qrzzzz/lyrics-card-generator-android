package com.qrzzzz.lyricscard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qrzzzz.lyricscard.AppContainer
import kotlinx.coroutines.launch

@Composable
fun LyricsCardApp(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
) {
    val navController = rememberNavController()
    val factory = remember(container) { LyricsCardViewModelFactory(container) }
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { entry ->
            val homeViewModel: HomeViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = factory,
            )
            val state by homeViewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                homeViewModel.onNavigationResumed()
            }

            LaunchedEffect(state.errorMessage) {
                state.errorMessage?.let {
                    snackbar.showSnackbar(it)
                    homeViewModel.clearError()
                }
            }

            HomeScreen(
                projects = state.projects,
                isWorking = state.isWorking,
                snackbarHost = { SnackbarHost(snackbar) },
                onCreateBlank = {
                    scope.launch {
                        homeViewModel.createBlank()?.let { id ->
                            homeViewModel.commitNavigation {
                                navController.navigate(EditorRoute(id))
                            }
                        }
                    }
                },
                onCreateSample = {
                    scope.launch {
                        homeViewModel.createSample()?.let { id ->
                            homeViewModel.commitNavigation {
                                navController.navigate(EditorRoute(id))
                            }
                        }
                    }
                },
                onOpen = { id ->
                    scope.launch {
                        homeViewModel.openProject(id)?.let { projectId ->
                            homeViewModel.commitNavigation {
                                navController.navigate(EditorRoute(projectId))
                            }
                        }
                    }
                },
                onDuplicate = { id -> scope.launch { homeViewModel.duplicateProject(id) } },
                onRename = { id, name -> scope.launch { homeViewModel.renameProject(id, name) } },
                onDelete = { id -> scope.launch { homeViewModel.deleteProject(id) } },
                onSettings = {
                    if (homeViewModel.beginNavigation()) {
                        homeViewModel.commitNavigation { navController.navigate(SettingsRoute) }
                    }
                },
            )
        }

        composable<EditorRoute> { entry ->
            val editorViewModel: EditorViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = factory,
            )
            val state by editorViewModel.uiState.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            val snackbar = remember { SnackbarHostState() }

            LaunchedEffect(state.errorMessage) {
                state.errorMessage?.let {
                    snackbar.showSnackbar(it)
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
                    scope.launch {
                        editorViewModel.popAfterSaving(navController)
                    }
                }
            }

            if (state.currentProject == null) {
                ProjectLoading()
            } else {
                EditorScreen(
                    state = state,
                    showSafeArea = settingsState.preferences.showSafeArea,
                    renderer = container.rendererController,
                    snackbarHost = { SnackbarHost(snackbar) },
                    onBack = {
                        scope.launch {
                            editorViewModel.popAfterSaving(navController)
                        }
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
                    },
                )
            }
        }

        composable<ExportRoute> { entry ->
            val exportViewModel: ExportViewModel = viewModel(
                viewModelStoreOwner = entry,
                factory = factory,
            )
            val state by exportViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(state.projectUnavailable) {
                if (state.projectUnavailable) navController.returnHome()
            }

            if (state.project == null) {
                ProjectLoading()
            } else {
                ExportScreen(
                    state = state,
                    renderer = container.rendererController,
                    onBack = { navController.popBackStack() },
                    onMultiplier = exportViewModel::setMultiplier,
                    onFileName = exportViewModel::setFileName,
                    onMeasuredHeight = exportViewModel::setMeasuredHeight,
                    onSave = exportViewModel::save,
                    onShare = exportViewModel::share,
                    onCancel = exportViewModel::cancelExport,
                    onRetry = exportViewModel::retry,
                    onSaveDestination = exportViewModel::saveTo,
                    onEffectConsumed = exportViewModel::consumeEffect,
                    onExternalActionError = exportViewModel::reportExternalActionError,
                )
            }
        }

        composable<SettingsRoute> {
            SettingsScreen(
                state = settingsState,
                onBack = { navController.popBackStack() },
                onDarkMode = settingsViewModel::setDarkMode,
                onDefaultExportScale = settingsViewModel::setDefaultExportScale,
                onShowSafeArea = settingsViewModel::setShowSafeArea,
                onClearExportCache = settingsViewModel::clearExportCache,
            )
        }
    }
}

private fun NavHostController.returnHome() {
    if (!popBackStack()) navigate(HomeRoute) { launchSingleTop = true }
}

private inline fun HomeViewModel.commitNavigation(navigate: () -> Unit) {
    markNavigationCommitted()
    try {
        navigate()
    } catch (cause: Throwable) {
        navigationFailed()
        throw cause
    }
}

private suspend fun EditorViewModel.popAfterSaving(navController: NavHostController) {
    if (!prepareForNavigation()) return
    markNavigationCommitted()
    try {
        if (!navController.popBackStack()) navigationFailed()
    } catch (cause: Throwable) {
        navigationFailed()
        throw cause
    }
}

@Composable
private fun ProjectLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
