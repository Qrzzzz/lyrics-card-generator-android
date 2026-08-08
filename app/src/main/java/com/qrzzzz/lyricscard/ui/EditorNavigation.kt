package com.qrzzzz.lyricscard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.RenderSpec

internal enum class EditorStep(
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

@Immutable
internal data class EditorScreenActions(
    val onSelectedStep: (Int) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onLinkInputChange: (String) -> Unit,
    val onProjectNameChange: (String) -> Unit,
    val onSpecChange: (RenderSpec) -> Unit,
    val onExtractPalette: () -> Unit,
    val onPickCover: () -> Unit,
    val onRemoveCover: () -> Unit,
    val onSearchNetease: (String) -> Unit,
    val onResolveNeteaseSong: (String) -> Unit,
    val onResolveNeteaseLink: (String) -> Unit,
    val onExport: () -> Unit,
)

@Composable
internal fun EditorPanelContent(
    state: EditorUiState,
    actions: EditorScreenActions,
    modifier: Modifier = Modifier,
) {
    val selectedStep = state.selectedStep
    Column(modifier = modifier) {
        EditorStepNavigation(
            selectedStep = selectedStep,
            onSelectedStep = actions.onSelectedStep,
        )
        Text(
            stringResource(EditorStep.entries[selectedStep].description),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        EditorStepContent(
            state = state,
            actions = actions,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        EditorNavigationBar(
            selectedStep = selectedStep,
            enabled = !state.isLeaving,
            onPrevious = {
                actions.onSelectedStep((selectedStep - 1).coerceAtLeast(0))
            },
            onNext = {
                if (selectedStep == EditorStep.entries.lastIndex) {
                    actions.onExport()
                } else {
                    actions.onSelectedStep(selectedStep + 1)
                }
            },
        )
    }
}

@Composable
internal fun EditorStepNavigation(
    selectedStep: Int,
    onSelectedStep: (Int) -> Unit,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedStep,
        modifier = Modifier.semantics { isTraversalGroup = true },
        edgePadding = 8.dp,
        divider = {},
    ) {
        EditorStep.entries.forEachIndexed { index, step ->
            val label = stringResource(step.label)
            val talkBackLabel = stringResource(
                R.string.editor_step_accessibility,
                index + 1,
                EditorStep.entries.size,
                label,
            )
            Tab(
                selected = index == selectedStep,
                onClick = { onSelectedStep(index) },
                modifier = Modifier.semantics {
                    contentDescription = talkBackLabel
                    traversalIndex = index.toFloat()
                },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (index == selectedStep) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.editor_step_number, index + 1, label),
                            fontWeight = if (index == selectedStep) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun EditorStepContent(
    state: EditorUiState,
    actions: EditorScreenActions,
    modifier: Modifier = Modifier,
) {
    val project = checkNotNull(state.currentProject)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = EditorStep.entries[state.selectedStep]) {
            when (EditorStep.entries[state.selectedStep]) {
                EditorStep.CHOOSE_SONG -> ChooseSongPanel(
                    project = project,
                    drafts = state.drafts,
                    netease = state.netease,
                    actions = actions,
                )
                EditorStep.LYRICS -> LyricsPanel(project.spec, actions.onSpecChange)
                EditorStep.LAYOUT -> LayoutPanel(project.spec, actions.onSpecChange)
                EditorStep.FONT -> TypographyPanel(project.spec, actions.onSpecChange)
                EditorStep.VISUAL -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    StylePanel(
                        spec = project.spec,
                        isExtractingPalette = state.isExtractingPalette,
                        paletteError = state.paletteError?.asString(),
                        onSpecChange = actions.onSpecChange,
                        onExtractPalette = actions.onExtractPalette,
                    )
                    BrandingPanel(project.spec, actions.onSpecChange)
                }
                EditorStep.EXPORT -> ExportStepPanel(project)
            }
        }
    }
}

@Composable
internal fun EditorNavigationBar(
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
                    .heightIn(min = 52.dp),
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
                    .heightIn(min = 52.dp),
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

internal const val EDITOR_STEP_COUNT = 6
