package com.qrzzzz.lyricscard.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qrzzzz.lyricscard.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val context = LocalContext.current

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                homeViewModel.onNavigationResumed()
            }

            LaunchedEffect(state.errorMessage) {
                state.errorMessage?.let {
                    snackbar.showSnackbar(it.resolve(context))
                    homeViewModel.clearError()
                }
            }

            HomeScreen(
                projects = state.projects,
                isLoading = state.isLoading,
                isWorking = state.isWorking,
                snackbarHost = { SnackbarHost(snackbar) },
                onCreateBlank = {
                    scope.launch {
                        homeViewModel.createBlank()?.let { id ->
                            withContext(Dispatchers.Main.immediate) {
                                homeViewModel.commitNavigation {
                                    navController.navigate(EditorRoute(id))
                                }
                            }
                        }
                    }
                },
                onCreateSample = {
                    scope.launch {
                        homeViewModel.createSample()?.let { id ->
                            withContext(Dispatchers.Main.immediate) {
                                homeViewModel.commitNavigation {
                                    navController.navigate(EditorRoute(id))
                                }
                            }
                        }
                    }
                },
                onOpen = { id ->
                    scope.launch {
                        homeViewModel.openProject(id)?.let { projectId ->
                            withContext(Dispatchers.Main.immediate) {
                                homeViewModel.commitNavigation {
                                    navController.navigate(EditorRoute(projectId))
                                }
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
            EditorRouteContent(
                entry = entry,
                factory = factory,
                container = container,
                showSafeArea = settingsState.preferences.showSafeArea,
                navController = navController,
            )
        }

        composable<ExportRoute> { entry ->
            ExportRouteContent(
                entry = entry,
                factory = factory,
                container = container,
                navController = navController,
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                state = settingsState,
                onBack = { navController.popBackStack() },
                onThemeMode = settingsViewModel::setThemeMode,
                onDefaultExportScale = settingsViewModel::setDefaultExportScale,
                onShowSafeArea = settingsViewModel::setShowSafeArea,
                onClearExportCache = settingsViewModel::clearExportCache,
                onMessageShown = settingsViewModel::clearMessages,
            )
        }
    }
}

internal fun NavHostController.returnHome() {
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

@Composable
internal fun ProjectLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
