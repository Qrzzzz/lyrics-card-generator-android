package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview
import com.qrzzzz.lyricscard.ui.theme.LyricsCardShapeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    showSafeArea: Boolean,
    renderer: RendererController,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
    onSelectedStep: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onExtractPalette: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectCover: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onExport: () -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass? = null,
) {
    val project = checkNotNull(state.currentProject)
    val selectedStep = state.selectedStep
    val windowWidth = currentLyricsWindowWidth(windowWidthSizeClass)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onSelectCover)
    }
    val actions = EditorScreenActions(
        onSelectedStep = onSelectedStep,
        onSearchQueryChange = onSearchQueryChange,
        onLinkInputChange = onLinkInputChange,
        onProjectNameChange = onProjectNameChange,
        onSpecChange = onSpecChange,
        onExtractPalette = onExtractPalette,
        onPickCover = { coverPicker.launch("image/*") },
        onRemoveCover = onRemoveCover,
        onSearchNetease = onSearchNetease,
        onResolveNeteaseSong = onResolveNeteaseSong,
        onResolveNeteaseLink = onResolveNeteaseLink,
        onExport = onExport,
    )

    Scaffold(
        modifier = Modifier.semantics { paneTitle = project.name },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, maxLines = 1, fontWeight = FontWeight.Bold)
                        EditorProgressStatus(state)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isLeaving) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = state.canUndo && !state.isLeaving) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.common_undo),
                        )
                    }
                    IconButton(onClick = onRedo, enabled = state.canRedo && !state.isLeaving) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = stringResource(R.string.common_redo),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        val showPreview = selectedStep >= EditorStep.LAYOUT.ordinal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(EDITOR_SCREEN_TAG),
        ) {
            when {
                windowWidth == LyricsWindowWidth.COMPACT && showPreview -> CompactEditorBottomSheet(
                    state = state,
                    actions = actions,
                    showSafeArea = showSafeArea,
                    renderer = renderer,
                    onMeasuredHeight = onMeasuredHeight,
                )
                windowWidth == LyricsWindowWidth.COMPACT -> EditorProperties(
                    state = state,
                    actions = actions,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                )
                else -> AdaptiveEditorLayout(
                    state = state,
                    actions = actions,
                    showPreview = showPreview,
                    showSafeArea = showSafeArea,
                    renderer = renderer,
                    onMeasuredHeight = onMeasuredHeight,
                    windowWidth = windowWidth,
                )
            }
        }
    }
}

@Composable
private fun EditorProgressStatus(state: EditorUiState) {
    val step = EditorStep.entries[state.selectedStep]
    val progress = stringResource(
        R.string.editor_step_accessibility,
        state.selectedStep + 1,
        EditorStep.entries.size,
        stringResource(step.label),
    )
    val isFailure = state.autosaveStatus == AutosaveStatus.FAILED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.then(
            if (isFailure) {
                Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            progress,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.isLeaving -> {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Text(
                    stringResource(R.string.editor_saving_and_leaving),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            state.autosaveStatus == AutosaveStatus.SAVING -> {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Text(
                    stringResource(R.string.editor_autosave_saving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            isFailure -> Text(
                stringResource(R.string.editor_autosave_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AdaptiveEditorLayout(
    state: EditorUiState,
    actions: EditorScreenActions,
    showPreview: Boolean,
    showSafeArea: Boolean,
    renderer: RendererController,
    onMeasuredHeight: (Int) -> Unit,
    windowWidth: LyricsWindowWidth,
) {
    val project = checkNotNull(state.currentProject)
    if (!showPreview) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            EditorProperties(
                state = state,
                actions = actions,
                modifier = Modifier
                    .widthIn(
                        max = if (windowWidth == LyricsWindowWidth.EXPANDED) 960.dp else 720.dp,
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag(
                if (windowWidth == LyricsWindowWidth.MEDIUM) {
                    EDITOR_MEDIUM_LAYOUT_TAG
                } else {
                    EDITOR_EXPANDED_LAYOUT_TAG
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RendererPreview(
            spec = project.spec,
            controller = renderer,
            onMeasuredHeight = onMeasuredHeight,
            showSafeArea = showSafeArea,
            modifier = Modifier
                .weight(if (windowWidth == LyricsWindowWidth.EXPANDED) 1.65f else 1.15f)
                .fillMaxHeight()
                .widthIn(min = 240.dp),
        )
        EditorProperties(
            state = state,
            actions = actions,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .widthIn(
                    min = if (windowWidth == LyricsWindowWidth.EXPANDED) 360.dp else 320.dp,
                    max = if (windowWidth == LyricsWindowWidth.EXPANDED) 560.dp else 480.dp,
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactEditorBottomSheet(
    state: EditorUiState,
    actions: EditorScreenActions,
    showSafeArea: Boolean,
    renderer: RendererController,
    onMeasuredHeight: (Int) -> Unit,
) {
    val project = checkNotNull(state.currentProject)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val reservedPreviewHeight = when {
            maxHeight < 480.dp -> 72.dp
            maxHeight < 720.dp -> 96.dp
            else -> 120.dp
        }
        val sheetHeight = (maxHeight - reservedPreviewHeight - COMPACT_SHEET_HANDLE_HEIGHT)
            .coerceAtLeast(1.dp)
            .coerceAtMost(COMPACT_SHEET_MAX_CONTENT_HEIGHT)
        val peekHeight = if (maxHeight < 520.dp) 88.dp else 112.dp
        val sheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = true,
        )
        val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
        val density = LocalDensity.current
        val imeInsets = rememberLyricsImeInsets()
        val effectiveImeWindowInsets = WindowInsets(
            bottom = with(density) { imeInsets.effectiveBottomPx.toDp() },
        )
        val imeVisible = imeInsets.isVisible

        LaunchedEffect(imeVisible, sheetState.currentValue) {
            if (imeVisible && sheetState.currentValue != SheetValue.Expanded) {
                sheetState.expand()
            }
        }

        BottomSheetScaffold(
            modifier = Modifier.fillMaxSize().testTag(EDITOR_COMPACT_SHEET_TAG),
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetShape = LyricsCardShapeTokens.topSheet,
            sheetDragHandle = { CompactSheetDragHandle() },
            sheetContent = {
                EditorPanelContent(
                    state = state,
                    actions = actions,
                    modifier = Modifier
                        .height(sheetHeight)
                        .windowInsetsPadding(effectiveImeWindowInsets),
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {
                RendererPreview(
                    spec = project.spec,
                    controller = renderer,
                    onMeasuredHeight = onMeasuredHeight,
                    showSafeArea = showSafeArea,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CompactSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(COMPACT_SHEET_HANDLE_HEIGHT)
            .testTag(EDITOR_COMPACT_SHEET_HANDLE_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(width = 32.dp, height = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        ) {}
    }
}

@Composable
private fun EditorProperties(
    state: EditorUiState,
    actions: EditorScreenActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        EditorPanelContent(
            state = state,
            actions = actions,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

internal const val EDITOR_SCREEN_TAG = "editor-screen"
internal const val EDITOR_COMPACT_SHEET_TAG = "editor-compact-sheet"
internal const val EDITOR_COMPACT_SHEET_HANDLE_TAG = "editor-compact-sheet-handle"
internal const val EDITOR_MEDIUM_LAYOUT_TAG = "editor-medium-layout"
internal const val EDITOR_EXPANDED_LAYOUT_TAG = "editor-expanded-layout"
private val COMPACT_SHEET_HANDLE_HEIGHT = 32.dp
private val COMPACT_SHEET_MAX_CONTENT_HEIGHT = 688.dp
