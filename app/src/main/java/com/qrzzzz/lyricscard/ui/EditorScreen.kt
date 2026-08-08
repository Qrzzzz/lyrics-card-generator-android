package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.BackgroundMode
import com.qrzzzz.lyricscard.model.CanvasRatio
import com.qrzzzz.lyricscard.model.ContentMode
import com.qrzzzz.lyricscard.model.FontScheme
import com.qrzzzz.lyricscard.model.GridDensity
import com.qrzzzz.lyricscard.model.LayoutMode
import com.qrzzzz.lyricscard.model.LyricTextCleaner
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.SongSource
import com.qrzzzz.lyricscard.model.TextAlignment
import com.qrzzzz.lyricscard.model.TextColorMode
import com.qrzzzz.lyricscard.model.TextColorPreset
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview
import com.qrzzzz.lyricscard.ui.theme.LyricsCardLayout
import com.qrzzzz.lyricscard.ui.theme.LyricsCardShapeTokens
import kotlin.math.roundToInt

private enum class EditorStep(
    @param:StringRes val label: Int,
    @param:StringRes val description: Int,
) {
    CHOOSE_SONG(R.string.editor_step_choose_song, R.string.editor_step_choose_song_description),
    LYRICS(R.string.editor_step_lyrics, R.string.editor_step_lyrics_description),
    LAYOUT(R.string.editor_step_layout, R.string.editor_step_layout_description),
    FONT(R.string.editor_step_font, R.string.editor_step_font_description),
    VISUAL(R.string.editor_step_visual, R.string.editor_step_visual_description),
    EXPORT(R.string.editor_step_export, R.string.editor_step_export_description),
}

private const val MOBILE_SHEET_EXPANDED_FRACTION = 0.88f

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
) {
    val project = checkNotNull(state.currentProject)
    val selectedStep = state.selectedStep
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onSelectCover)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(
                                R.string.editor_progress_status,
                                selectedStep + 1,
                                EditorStep.entries.size,
                                if (state.isLeaving) {
                                stringResource(R.string.editor_saving_and_leaving)
                            } else {
                                when (state.autosaveStatus) {
                                    AutosaveStatus.SAVED -> stringResource(R.string.editor_autosave_saved)
                                    AutosaveStatus.SAVING -> stringResource(R.string.editor_autosave_saving)
                                    AutosaveStatus.FAILED -> stringResource(R.string.editor_autosave_failed)
                                }
                            },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= LyricsCardLayout.wideBreakpoint
            val showPreview = selectedStep >= EditorStep.LAYOUT.ordinal
            if (wide) {
                WideEditorLayout(
                    state = state,
                    onSelectedStep = onSelectedStep,
                    showPreview = showPreview,
                    showSafeArea = showSafeArea,
                    renderer = renderer,
                    onSearchQueryChange = onSearchQueryChange,
                    onLinkInputChange = onLinkInputChange,
                    onProjectNameChange = onProjectNameChange,
                    onSpecChange = onSpecChange,
                    onMeasuredHeight = onMeasuredHeight,
                    onExtractPalette = onExtractPalette,
                    onPickCover = { coverPicker.launch("image/*") },
                    onRemoveCover = onRemoveCover,
                    onSearchNetease = onSearchNetease,
                    onResolveNeteaseSong = onResolveNeteaseSong,
                    onResolveNeteaseLink = onResolveNeteaseLink,
                    onPrevious = { onSelectedStep((selectedStep - 1).coerceAtLeast(0)) },
                    onNext = {
                        if (selectedStep == EditorStep.entries.lastIndex) onExport()
                        else onSelectedStep(selectedStep + 1)
                    },
                )
            } else if (showPreview) {
                MobileEditorBottomSheet(
                    state = state,
                    onSelectedStep = onSelectedStep,
                    showSafeArea = showSafeArea,
                    renderer = renderer,
                    onSearchQueryChange = onSearchQueryChange,
                    onLinkInputChange = onLinkInputChange,
                    onProjectNameChange = onProjectNameChange,
                    onSpecChange = onSpecChange,
                    onMeasuredHeight = onMeasuredHeight,
                    onExtractPalette = onExtractPalette,
                    onPickCover = { coverPicker.launch("image/*") },
                    onRemoveCover = onRemoveCover,
                    onSearchNetease = onSearchNetease,
                    onResolveNeteaseSong = onResolveNeteaseSong,
                    onResolveNeteaseLink = onResolveNeteaseLink,
                    onPrevious = { onSelectedStep((selectedStep - 1).coerceAtLeast(0)) },
                    onNext = {
                        if (selectedStep == EditorStep.entries.lastIndex) onExport()
                        else onSelectedStep(selectedStep + 1)
                    },
                )
            } else {
                EditorProperties(
                    state = state,
                    onSelectedStep = onSelectedStep,
                    onSearchQueryChange = onSearchQueryChange,
                    onLinkInputChange = onLinkInputChange,
                    onProjectNameChange = onProjectNameChange,
                    onSpecChange = onSpecChange,
                    onPickCover = { coverPicker.launch("image/*") },
                    onRemoveCover = onRemoveCover,
                    onExtractPalette = onExtractPalette,
                    onSearchNetease = onSearchNetease,
                    onResolveNeteaseSong = onResolveNeteaseSong,
                    onResolveNeteaseLink = onResolveNeteaseLink,
                    onPrevious = { onSelectedStep((selectedStep - 1).coerceAtLeast(0)) },
                    onNext = { onSelectedStep(selectedStep + 1) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun WideEditorLayout(
    state: EditorUiState,
    onSelectedStep: (Int) -> Unit,
    showPreview: Boolean,
    showSafeArea: Boolean,
    renderer: RendererController,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onExtractPalette: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val project = checkNotNull(state.currentProject)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showPreview) {
            RendererPreview(
                spec = project.spec,
                controller = renderer,
                onMeasuredHeight = onMeasuredHeight,
                showSafeArea = showSafeArea,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        EditorProperties(
            state = state,
            onSelectedStep = onSelectedStep,
            onSearchQueryChange = onSearchQueryChange,
            onLinkInputChange = onLinkInputChange,
            onProjectNameChange = onProjectNameChange,
            onSpecChange = onSpecChange,
            onPickCover = onPickCover,
            onRemoveCover = onRemoveCover,
            onExtractPalette = onExtractPalette,
            onSearchNetease = onSearchNetease,
            onResolveNeteaseSong = onResolveNeteaseSong,
            onResolveNeteaseLink = onResolveNeteaseLink,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = if (showPreview) Modifier.width(420.dp) else Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileEditorBottomSheet(
    state: EditorUiState,
    onSelectedStep: (Int) -> Unit,
    showSafeArea: Boolean,
    renderer: RendererController,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onExtractPalette: () -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val project = checkNotNull(state.currentProject)
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Expanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(imeVisible, sheetState.currentValue) {
        if (imeVisible && sheetState.currentValue != SheetValue.Expanded) {
            sheetState.expand()
        }
    }

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = 112.dp,
        sheetMaxWidth = 840.dp,
        sheetShape = LyricsCardShapeTokens.topSheet,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        sheetContent = {
            EditorPanelContent(
                state = state,
                onSelectedStep = onSelectedStep,
                onSearchQueryChange = onSearchQueryChange,
                onLinkInputChange = onLinkInputChange,
                onProjectNameChange = onProjectNameChange,
                onSpecChange = onSpecChange,
                onPickCover = onPickCover,
                onRemoveCover = onRemoveCover,
                onExtractPalette = onExtractPalette,
                onSearchNetease = onSearchNetease,
                onResolveNeteaseSong = onResolveNeteaseSong,
                onResolveNeteaseLink = onResolveNeteaseLink,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier
                    .fillMaxHeight(MOBILE_SHEET_EXPANDED_FRACTION)
                    .imePadding(),
            )
        },
    ) {
        // The preview always uses the full scaffold viewport. The sheet moves over it,
        // so dragging the sheet only changes placement and never the WebView's constraints.
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

@Composable
private fun EditorProperties(
    state: EditorUiState,
    onSelectedStep: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onExtractPalette: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
        EditorPanelContent(
            state = state,
            onSelectedStep = onSelectedStep,
            onSearchQueryChange = onSearchQueryChange,
            onLinkInputChange = onLinkInputChange,
            onProjectNameChange = onProjectNameChange,
            onSpecChange = onSpecChange,
            onPickCover = onPickCover,
            onRemoveCover = onRemoveCover,
            onExtractPalette = onExtractPalette,
            onSearchNetease = onSearchNetease,
            onResolveNeteaseSong = onResolveNeteaseSong,
            onResolveNeteaseLink = onResolveNeteaseLink,
            onPrevious = onPrevious,
            onNext = onNext,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EditorPanelContent(
    state: EditorUiState,
    onSelectedStep: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onExtractPalette: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedStep = state.selectedStep
    Column(modifier = modifier) {
        EditorStepTabs(
            selectedStep = selectedStep,
            onSelectedStep = onSelectedStep,
        )
        Text(
            stringResource(EditorStep.entries[selectedStep].description),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        EditorStepContent(
            state = state,
            onSearchQueryChange = onSearchQueryChange,
            onLinkInputChange = onLinkInputChange,
            onProjectNameChange = onProjectNameChange,
            onSpecChange = onSpecChange,
            onPickCover = onPickCover,
            onRemoveCover = onRemoveCover,
            onExtractPalette = onExtractPalette,
            onSearchNetease = onSearchNetease,
            onResolveNeteaseSong = onResolveNeteaseSong,
            onResolveNeteaseLink = onResolveNeteaseLink,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        EditorNavigationBar(
            selectedStep = selectedStep,
            enabled = !state.isLeaving,
            onPrevious = onPrevious,
            onNext = onNext,
        )
    }
}

@Composable
private fun EditorStepTabs(
    selectedStep: Int,
    onSelectedStep: (Int) -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedStep,
        edgePadding = 8.dp,
        divider = {},
    ) {
        EditorStep.entries.forEachIndexed { index, step ->
            Tab(
                selected = index == selectedStep,
                onClick = { onSelectedStep(index) },
                text = {
                    Text(
                        stringResource(R.string.editor_step_number, index + 1, stringResource(step.label)),
                        fontWeight = if (index == selectedStep) FontWeight.Bold else null,
                    )
                },
            )
        }
    }
}

@Composable
private fun EditorStepContent(
    state: EditorUiState,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onExtractPalette: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val project = checkNotNull(state.currentProject)
    val selectedStep = state.selectedStep
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            when (EditorStep.entries[selectedStep]) {
                EditorStep.CHOOSE_SONG -> ChooseSongPanel(
                    project = project,
                    drafts = state.drafts,
                    netease = state.netease,
                    onSearchQueryChange = onSearchQueryChange,
                    onLinkInputChange = onLinkInputChange,
                    onProjectNameChange = onProjectNameChange,
                    onSpecChange = onSpecChange,
                    onPickCover = onPickCover,
                    onRemoveCover = onRemoveCover,
                    onSearchNetease = onSearchNetease,
                    onResolveNeteaseSong = onResolveNeteaseSong,
                    onResolveNeteaseLink = onResolveNeteaseLink,
                )
                EditorStep.LYRICS -> LyricsPanel(project.spec, onSpecChange)
                EditorStep.LAYOUT -> LayoutPanel(project.spec, onSpecChange)
                EditorStep.FONT -> TypographyPanel(project.spec, onSpecChange)
                EditorStep.VISUAL -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StylePanel(
                        spec = project.spec,
                        isExtractingPalette = state.isExtractingPalette,
                        paletteError = state.paletteError?.asString(),
                        onSpecChange = onSpecChange,
                        onExtractPalette = onExtractPalette,
                    )
                    BrandingPanel(project.spec, onSpecChange)
                }
                EditorStep.EXPORT -> ExportStepPanel(project)
            }
        }
    }
}

@Composable
private fun EditorNavigationBar(
    selectedStep: Int,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(shadowElevation = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = enabled && selectedStep > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Text(stringResource(R.string.editor_previous_step), modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier
                    .weight(1.5f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(
                    if (selectedStep == EditorStep.entries.lastIndex) {
                        Icons.Rounded.FileUpload
                    } else {
                        Icons.AutoMirrored.Rounded.NavigateNext
                    },
                    contentDescription = null,
                )
                Text(
                    if (selectedStep == EditorStep.entries.lastIndex) {
                        stringResource(R.string.editor_export_png, stringResource(R.string.file_png))
                    } else {
                        stringResource(R.string.editor_next_step)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ChooseSongPanel(
    project: Project,
    drafts: EditorDrafts,
    netease: NeteaseLookupUiState,
    onSearchQueryChange: (String) -> Unit,
    onLinkInputChange: (String) -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
) {
    val spec = project.spec
    val clipboard = LocalClipboardManager.current
    val lookupBusy = netease.isSearching || netease.isResolving
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_netease_section))
        Text(
            stringResource(R.string.editor_netease_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = drafts.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_netease_query_label)) },
            placeholder = { Text(stringResource(R.string.editor_netease_query_example)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (netease.isSearching) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
            singleLine = true,
            enabled = !netease.isResolving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchNetease(drafts.searchQuery) }),
        )
        Button(
            onClick = { onSearchNetease(drafts.searchQuery) },
            enabled = drafts.searchQuery.isNotBlank() && !lookupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text(stringResource(R.string.editor_netease_search), modifier = Modifier.padding(start = 8.dp))
        }
        netease.results.forEach { result ->
            OutlinedButton(
                onClick = { onResolveNeteaseSong(result.id) },
                enabled = !lookupBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        listOf(result.title, result.artist)
                            .filter(String::isNotBlank)
                            .joinToString(stringResource(R.string.middle_dot_separator)),
                        fontWeight = FontWeight.Bold,
                    )
                    if (result.album.isNotBlank()) {
                        Text(
                            result.album,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = drafts.linkInput,
            onValueChange = onLinkInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_netease_link_label)) },
            placeholder = { Text(stringResource(R.string.editor_netease_link_example)) },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            minLines = 2,
            maxLines = 4,
            enabled = !lookupBusy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { clipboard.getText()?.text?.let(onLinkInputChange) },
                enabled = !lookupBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Text(stringResource(R.string.editor_paste), modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = { onResolveNeteaseLink(drafts.linkInput) },
                enabled = drafts.linkInput.isNotBlank() && !lookupBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (netease.isResolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Text(stringResource(R.string.editor_parse), modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(
            netease.message.asString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle(stringResource(R.string.editor_manual_section))
        OutlinedTextField(
            value = drafts.projectName,
            onValueChange = onProjectNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_project_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = spec.song.title,
            onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(title = it.take(240)))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_song_title)) },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = spec.song.artist,
                onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(artist = it.take(240)))) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.editor_artist)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = spec.song.album,
                onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(album = it.take(240)))) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.editor_album)) },
                singleLine = true,
            )
        }
        Text(stringResource(R.string.editor_source_platform), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.song.source,
            label = ::songSourceLabel,
            onSelect = { source ->
                onSpecChange(spec.copy(song = spec.song.copy(source = source), branding = spec.branding.copy(platform = source)))
            },
        )
        SettingSwitch(stringResource(R.string.editor_explicit_marker), spec.song.explicit) {
            onSpecChange(spec.copy(song = spec.song.copy(explicit = it)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPickCover, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (spec.song.coverAssetId == null) {
                            R.string.editor_select_cover
                        } else {
                            R.string.editor_replace_cover
                        },
                    ),
                )
            }
            if (spec.song.coverAssetId != null) {
                TextButton(onClick = onRemoveCover) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Text(stringResource(R.string.common_remove))
                }
            }
        }

    }
}

@Composable
private fun LyricsPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    val instrumental = spec.content.mode == ContentMode.INSTRUMENTAL
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_lyrics_content))
        SettingSwitch(stringResource(R.string.editor_instrumental_mode), instrumental) { enabled ->
            onSpecChange(
                if (enabled) {
                    spec.copy(
                        content = spec.content.copy(mode = ContentMode.INSTRUMENTAL, translationEnabled = false),
                        canvas = spec.canvas.copy(
                            layoutMode = LayoutMode.PORTRAIT,
                            ratio = CanvasRatio.SQUARE,
                            width = 1080,
                            height = 1080,
                            autoHeight = false,
                        ),
                    )
                } else {
                    spec.copy(content = spec.content.copy(mode = ContentMode.LYRICS))
                },
            )
        }
        if (instrumental) {
            OutlinedTextField(
                value = spec.content.instrumentalText,
                onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(instrumentalText = it.take(240)))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.editor_instrumental_text)) },
            )
        } else {
            OutlinedTextField(
                value = spec.content.lyrics,
                onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(lyrics = it))) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                label = { Text(stringResource(R.string.editor_original_lyrics)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onSpecChange(spec.copy(content = spec.content.copy(lyrics = LyricTextCleaner.removeTimestamps(spec.content.lyrics))))
                }) { Text(stringResource(R.string.editor_clear_timestamps)) }
                TextButton(onClick = {
                    onSpecChange(spec.copy(content = spec.content.copy(lyrics = LyricTextCleaner.collapseRepeatedBlankLines(spec.content.lyrics))))
                }) { Text(stringResource(R.string.editor_merge_blank_lines)) }
            }
            SettingSwitch(stringResource(R.string.editor_show_translation), spec.content.translationEnabled) {
                onSpecChange(spec.copy(content = spec.content.copy(translationEnabled = it)))
            }
            if (spec.content.translationEnabled) {
                OutlinedTextField(
                    value = spec.content.translation,
                    onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(translation = it))) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    label = { Text(stringResource(R.string.editor_translation_lyrics)) },
                )
            }
        }
    }
}

@Composable
private fun LayoutPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_canvas))
        if (spec.content.mode == ContentMode.INSTRUMENTAL) {
            Text(
                stringResource(R.string.editor_instrumental_canvas_help),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChoiceChips(
                values = LayoutMode.entries,
                selected = spec.canvas.layoutMode,
                label = {
                    stringResource(
                        if (it == LayoutMode.PORTRAIT) R.string.common_portrait else R.string.common_landscape,
                    )
                },
                onSelect = { mode ->
                    val canvas = if (mode == LayoutMode.PORTRAIT) {
                        spec.canvas.copy(layoutMode = mode, ratio = CanvasRatio.PORTRAIT_4_5, width = 1080, height = 1350, autoHeight = false)
                    } else {
                        spec.canvas.copy(layoutMode = mode, ratio = CanvasRatio.LANDSCAPE_16_9, width = 1920, height = 1080, autoHeight = false)
                    }
                    onSpecChange(spec.copy(canvas = canvas))
                },
            )
            Text(stringResource(R.string.editor_ratio), style = MaterialTheme.typography.labelLarge)
            val ratios = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                listOf(CanvasRatio.SQUARE, CanvasRatio.PORTRAIT_4_5, CanvasRatio.PORTRAIT_9_16, CanvasRatio.CUSTOM)
            } else {
                listOf(CanvasRatio.LANDSCAPE_16_9, CanvasRatio.LANDSCAPE_21_9, CanvasRatio.LANDSCAPE_3_2, CanvasRatio.CUSTOM)
            }
            ChoiceChips(
                values = ratios,
                selected = spec.canvas.ratio,
                label = ::ratioLabel,
                onSelect = { ratio ->
                    val width = ratio.width ?: spec.canvas.width
                    val height = ratio.height ?: spec.canvas.height
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(ratio = ratio, width = width, height = height, autoHeight = false)))
                },
            )
            if (spec.canvas.ratio == CanvasRatio.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val widthRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) 720..1440 else 1080..3000
                    val heightRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) 720..3200 else 720..1600
                    NumberField(stringResource(R.string.editor_width), spec.canvas.width, widthRange, Modifier.weight(1f)) { width ->
                        onSpecChange(spec.copy(canvas = spec.canvas.copy(width = width)))
                    }
                    NumberField(stringResource(R.string.editor_height), spec.canvas.height, heightRange, Modifier.weight(1f)) { height ->
                        onSpecChange(spec.copy(canvas = spec.canvas.copy(height = height)))
                    }
                }
                SettingSwitch(
                    stringResource(R.string.editor_auto_height),
                    spec.canvas.autoHeight,
                    enabled = spec.canvas.layoutMode == LayoutMode.PORTRAIT,
                ) { onSpecChange(spec.copy(canvas = spec.canvas.copy(autoHeight = it))) }
            }
        }

        SectionTitle(stringResource(R.string.editor_elements))
        SettingSwitch(stringResource(R.string.editor_show_cover), spec.visibility.showCover, enabled = spec.song.coverAssetId != null) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showCover = it)))
        }
        SettingSwitch(stringResource(R.string.editor_show_song_info), spec.visibility.showSongInfo) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSongInfo = it)))
        }
        SettingSwitch(stringResource(R.string.editor_show_album), spec.visibility.showAlbum) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showAlbum = it)))
        }
        LabeledSlider(stringResource(R.string.editor_cover_crop_scale), spec.media.coverCropScale.toFloat(), 1f..2f, "%.2f".format(spec.media.coverCropScale)) {
            onSpecChange(spec.copy(media = spec.media.copy(coverCropScale = it.toDouble())))
        }
    }
}

@Composable
private fun StylePanel(
    spec: RenderSpec,
    isExtractingPalette: Boolean,
    paletteError: String?,
    onSpecChange: (RenderSpec) -> Unit,
    onExtractPalette: () -> Unit,
) {
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_background))
        ChoiceChips(
            values = BackgroundMode.entries,
            selected = spec.visual.backgroundMode,
            label = {
                stringResource(
                    if (it == BackgroundMode.PALETTE) R.string.editor_palette else R.string.editor_gradient,
                )
            },
            onSelect = { onSpecChange(spec.copy(visual = spec.visual.copy(backgroundMode = it))) },
        )
        val coverId = spec.song.coverAssetId
        Button(
            onClick = onExtractPalette,
            enabled = coverId != null && !isExtractingPalette,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Text(
                stringResource(
                    if (isExtractingPalette) R.string.editor_extracting_palette else R.string.editor_extract_palette,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        paletteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ColorField(stringResource(R.string.editor_dominant_color), spec.visual.palette.dominant) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(dominant = it))))
        }
        ColorField(stringResource(R.string.editor_secondary_color), spec.visual.palette.secondary) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(secondary = it))))
        }
        ColorField(stringResource(R.string.editor_accent_color), spec.visual.palette.accent) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(accent = it))))
        }
        SectionTitle(stringResource(R.string.editor_grid))
        SettingSwitch(stringResource(R.string.editor_show_grid), spec.visual.gridEnabled) {
            onSpecChange(spec.copy(visual = spec.visual.copy(gridEnabled = it)))
        }
        if (spec.visual.gridEnabled) {
            ChoiceChips(
                values = GridDensity.entries,
                selected = spec.visual.gridDensity,
                label = {
                    stringResource(
                        when (it) {
                            GridDensity.SPARSE -> R.string.editor_grid_sparse
                            GridDensity.MEDIUM -> R.string.editor_grid_medium
                            GridDensity.DENSE -> R.string.editor_grid_dense
                        },
                    )
                },
                onSelect = { onSpecChange(spec.copy(visual = spec.visual.copy(gridDensity = it))) },
            )
            LabeledSlider(stringResource(R.string.editor_grid_opacity), spec.visual.gridOpacity.toFloat(), 0f..0.5f, "${(spec.visual.gridOpacity * 100).roundToInt()}%") {
                onSpecChange(spec.copy(visual = spec.visual.copy(gridOpacity = it.toDouble())))
            }
        }
    }
}

@Composable
private fun TypographyPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_font))
        ChoiceChips(
            values = listOf(FontScheme.SANS_HEAVY, FontScheme.SERIF_HEAVY),
            selected = spec.typography.fontScheme,
            label = {
                stringResource(
                    if (it == FontScheme.SANS_HEAVY) R.string.editor_font_sans else R.string.editor_font_serif,
                )
            },
            onSelect = { scheme ->
                val family = if (scheme == FontScheme.SANS_HEAVY) "Source Han Sans SC" else "Source Han Serif SC"
                onSpecChange(spec.copy(typography = spec.typography.copy(fontScheme = scheme, fontFamily = family)))
            },
        )
        LabeledSlider(stringResource(R.string.editor_lyric_size), spec.typography.lyricSize.toFloat(), 36f..72f, "${spec.typography.lyricSize}") {
            onSpecChange(spec.copy(typography = spec.typography.copy(lyricSize = it.roundToInt())))
        }
        LabeledSlider(stringResource(R.string.editor_line_height), spec.typography.lineHeight.toFloat(), 1.1f..1.75f, "%.2f".format(spec.typography.lineHeight)) {
            onSpecChange(spec.copy(typography = spec.typography.copy(lineHeight = it.toDouble())))
        }
        LabeledSlider(stringResource(R.string.editor_translation_scale), spec.typography.translationScale.toFloat(), 0.6f..0.9f, "${(spec.typography.translationScale * 100).roundToInt()}%") {
            onSpecChange(spec.copy(typography = spec.typography.copy(translationScale = it.toDouble())))
        }
        Text(stringResource(R.string.editor_alignment), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            values = TextAlignment.entries,
            selected = spec.typography.alignment,
            label = {
                stringResource(
                    when (it) {
                        TextAlignment.LEFT -> R.string.editor_align_left
                        TextAlignment.CENTER -> R.string.editor_align_center
                        TextAlignment.RIGHT -> R.string.editor_align_right
                    },
                )
            },
            onSelect = { onSpecChange(spec.copy(typography = spec.typography.copy(alignment = it))) },
        )
        SettingSwitch(stringResource(R.string.editor_two_line_title), spec.typography.twoLineTitle) {
            onSpecChange(spec.copy(typography = spec.typography.copy(twoLineTitle = it)))
        }
        SectionTitle(stringResource(R.string.editor_text_color))
        ChoiceChips(
            values = TextColorMode.entries,
            selected = spec.typography.textColorMode,
            label = {
                stringResource(
                    when (it) {
                        TextColorMode.AUTO -> R.string.editor_color_auto
                        TextColorMode.PRESET -> R.string.editor_color_preset
                        TextColorMode.CUSTOM -> R.string.common_custom
                    },
                )
            },
            onSelect = { mode ->
                val custom = if (mode == TextColorMode.CUSTOM) spec.typography.customTextColor ?: "#FFFFFF" else spec.typography.customTextColor
                onSpecChange(spec.copy(typography = spec.typography.copy(textColorMode = mode, customTextColor = custom)))
            },
        )
        when (spec.typography.textColorMode) {
            TextColorMode.PRESET -> ChoiceChips(
                values = listOf(TextColorPreset.WHITE, TextColorPreset.BLACK, TextColorPreset.WARM_WHITE, TextColorPreset.CREAM),
                selected = spec.typography.textColorPreset,
                label = ::textColorPresetLabel,
                onSelect = { onSpecChange(spec.copy(typography = spec.typography.copy(textColorPreset = it))) },
            )
            TextColorMode.CUSTOM -> ColorField(stringResource(R.string.editor_custom_text_color), spec.typography.customTextColor ?: "#FFFFFF") {
                onSpecChange(spec.copy(typography = spec.typography.copy(customTextColor = it)))
            }
            TextColorMode.AUTO -> Unit
        }
    }
}

@Composable
private fun BrandingPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_platform_attribution))
        SettingSwitch(stringResource(R.string.editor_show_platform_logo), spec.visibility.showPlatformBadge, enabled = spec.branding.platform != SongSource.UNKNOWN) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showPlatformBadge = it)))
        }
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.branding.platform,
            label = ::songSourceLabel,
            onSelect = { onSpecChange(spec.copy(branding = spec.branding.copy(platform = it))) },
        )
        SettingSwitch(
            stringResource(R.string.editor_show_shared_by, stringResource(R.string.brand_shared_by)),
            spec.visibility.showSharedBy,
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSharedBy = it)))
        }
        if (spec.visibility.showSharedBy) {
            OutlinedTextField(
                value = spec.branding.sharedByName,
                onValueChange = { onSpecChange(spec.copy(branding = spec.branding.copy(sharedByName = it.take(240)))) },
                label = { Text(stringResource(R.string.editor_sharer_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        SettingSwitch(
            stringResource(
                R.string.editor_show_generated_watermark,
                stringResource(R.string.brand_generated_watermark),
            ),
            spec.visibility.showGeneratedWatermark,
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showGeneratedWatermark = it)))
        }
    }
}

@Composable
private fun ExportStepPanel(project: Project) {
    val spec = project.spec
    val songReady = spec.song.title.isNotBlank() || spec.song.artist.isNotBlank()
    val contentReady = spec.content.mode == ContentMode.INSTRUMENTAL || spec.content.lyrics.isNotBlank()
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_pre_export_check))
        Text(project.name, style = MaterialTheme.typography.titleLarge)
        ReadinessRow(
            stringResource(R.string.editor_song_info),
            songReady,
            stringResource(
                if (songReady) R.string.editor_filled else R.string.editor_return_to_first_step,
            ),
        )
        ReadinessRow(
            stringResource(R.string.editor_card_content),
            contentReady,
            stringResource(if (contentReady) R.string.editor_ready else R.string.editor_lyrics_empty),
        )
        ReadinessRow(
            stringResource(R.string.editor_canvas),
            true,
            stringResource(
                R.string.editor_canvas_summary,
                spec.canvas.width,
                spec.canvas.height,
                stringResource(
                    if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                        R.string.common_portrait
                    } else {
                        R.string.common_landscape
                    },
                ),
            ),
        )
        Text(
            stringResource(R.string.editor_export_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (ready) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(displayValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                leadingIcon = if (selected == value) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    validRange: IntRange,
    modifier: Modifier,
    onValidValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in validRange }?.let(onValidValue)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ColorField(label: String, value: String, onValidValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.take(9)
            if (HEX_COLOR.matches(text)) onValidValue(text.uppercase())
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = MaterialTheme.shapes.small,
                color = runCatching { cssHexColor(value) }
                    .getOrDefault(MaterialTheme.colorScheme.surfaceVariant),
            ) {}
        },
    )
}

@Composable
private fun songSourceLabel(source: SongSource) = stringResource(when (source) {
    SongSource.UNKNOWN -> R.string.common_unknown
    SongSource.QQ -> R.string.brand_qq_music
    SongSource.NETEASE -> R.string.brand_netease
    SongSource.APPLE -> R.string.brand_apple_music
    SongSource.SPOTIFY -> R.string.brand_spotify
})

@Composable
private fun textColorPresetLabel(preset: TextColorPreset) = stringResource(when (preset) {
    TextColorPreset.WHITE -> R.string.editor_color_white
    TextColorPreset.BLACK -> R.string.editor_color_black
    TextColorPreset.WARM_WHITE -> R.string.editor_color_warm_white
    TextColorPreset.CREAM -> R.string.editor_color_cream
    TextColorPreset.CHARCOAL -> R.string.editor_color_charcoal
    TextColorPreset.SOFT_BLUE -> R.string.editor_color_soft_blue
    TextColorPreset.SOFT_GOLD -> R.string.editor_color_soft_gold
})

@Composable
private fun ratioLabel(ratio: CanvasRatio) = when (ratio) {
    CanvasRatio.CUSTOM -> stringResource(R.string.common_custom)
    else -> ratio.contractLabel()
}

private fun CanvasRatio.contractLabel() = when (this) {
    CanvasRatio.SQUARE -> "1:1"
    CanvasRatio.PORTRAIT_4_5 -> "4:5"
    CanvasRatio.PORTRAIT_9_16 -> "9:16"
    CanvasRatio.LANDSCAPE_16_9 -> "16:9"
    CanvasRatio.LANDSCAPE_21_9 -> "21:9"
    CanvasRatio.LANDSCAPE_3_2 -> "3:2"
    CanvasRatio.CUSTOM -> error("Custom ratio has a localized label")
}

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")

private fun cssHexColor(value: String): androidx.compose.ui.graphics.Color {
    val hex = value.removePrefix("#")
    require(hex.length == 6 || hex.length == 8)
    val red = hex.substring(0, 2).toInt(16)
    val green = hex.substring(2, 4).toInt(16)
    val blue = hex.substring(4, 6).toInt(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
    return androidx.compose.ui.graphics.Color(red, green, blue, alpha)
}
