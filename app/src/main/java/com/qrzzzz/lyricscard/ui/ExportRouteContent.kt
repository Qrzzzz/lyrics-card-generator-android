package com.qrzzzz.lyricscard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.qrzzzz.lyricscard.AppContainer

@Composable
internal fun ExportRouteContent(
    entry: NavBackStackEntry,
    factory: ViewModelProvider.Factory,
    container: AppContainer,
    navController: NavHostController,
) {
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
