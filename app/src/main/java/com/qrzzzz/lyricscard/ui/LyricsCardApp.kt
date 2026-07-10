package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val PROJECT_ID = "projectId"
    const val EDITOR = "editor/{$PROJECT_ID}"
    const val EXPORT = "export/{$PROJECT_ID}"
    const val SETTINGS = "settings"

    fun editor(projectId: String) = "editor/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

@Composable
fun LyricsCardApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val projects by viewModel.projects.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(editor.errorMessage) {
        editor.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                projects = projects,
                snackbarHost = { SnackbarHost(snackbar) },
                onCreateBlank = {
                    scope.launch {
                        viewModel.createBlank()?.let { navController.navigate(Routes.editor(it.id)) }
                    }
                },
                onCreateSample = {
                    scope.launch {
                        viewModel.createSample()?.let { navController.navigate(Routes.editor(it.id)) }
                    }
                },
                onOpen = { id ->
                    scope.launch {
                        if (viewModel.openProject(id) != null) navController.navigate(Routes.editor(id))
                    }
                },
                onDuplicate = { id -> scope.launch { viewModel.duplicateProject(id) } },
                onRename = { id, name -> scope.launch { viewModel.renameProject(id, name) } },
                onDelete = { id -> scope.launch { viewModel.deleteProject(id) } },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument(Routes.PROJECT_ID) { type = NavType.StringType }),
        ) { entry ->
            val projectId = checkNotNull(entry.arguments?.getString(Routes.PROJECT_ID))
            val project = editor.currentProject?.takeIf { it.id == projectId }
            LaunchedEffect(projectId, project?.id) {
                if (project == null && viewModel.openProject(projectId) == null) {
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME)
                    }
                }
            }
            if (project == null) {
                ProjectLoading()
            } else {
                EditorScreen(
                    project = project,
                    isSaving = editor.isSaving,
                    canUndo = editor.canUndo,
                    canRedo = editor.canRedo,
                    showSafeArea = preferences.showSafeArea,
                    renderer = viewModel.rendererController,
                    netease = editor.netease,
                    onBack = {
                        scope.launch {
                            if (viewModel.flushAutosave()) navController.popBackStack()
                        }
                    },
                    onProjectNameChange = viewModel::updateProjectName,
                    onSpecChange = { next -> viewModel.updateSpec { next } },
                    onMeasuredHeight = viewModel::updateMeasuredHeight,
                    onPaletteExtracted = viewModel::updatePalette,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSelectCover = viewModel::importCover,
                    onRemoveCover = viewModel::removeCover,
                    onSearchNetease = viewModel::searchNetease,
                    onResolveNeteaseSong = viewModel::resolveNeteaseSong,
                    onResolveNeteaseLink = viewModel::resolveNeteaseLink,
                    onExport = {
                        scope.launch {
                            if (viewModel.flushAutosave()) navController.navigate(Routes.export(project.id))
                        }
                    },
                )
            }
        }
        composable(
            route = Routes.EXPORT,
            arguments = listOf(navArgument(Routes.PROJECT_ID) { type = NavType.StringType }),
        ) { entry ->
            val projectId = checkNotNull(entry.arguments?.getString(Routes.PROJECT_ID))
            val project = editor.currentProject?.takeIf { it.id == projectId }
            LaunchedEffect(projectId, project?.id) {
                if (project == null && viewModel.openProject(projectId) == null) {
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME)
                    }
                }
            }
            if (project == null) {
                ProjectLoading()
            } else {
                ExportScreen(
                    project = project,
                    renderer = viewModel.rendererController,
                    defaultMultiplier = preferences.defaultExportScale,
                    onBack = { navController.popBackStack() },
                    onExportRecorded = viewModel::recordExport,
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                preferences = preferences,
                onBack = { navController.popBackStack() },
                onDarkMode = viewModel::setDarkMode,
                onDefaultExportScale = viewModel::setDefaultExportScale,
                onShowSafeArea = viewModel::setShowSafeArea,
            )
        }
    }
}

@Composable
private fun ProjectLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
