package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.BackgroundMode
import com.qrzzzz.lyricscard.model.GridDensity
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.SongSource
import kotlin.math.roundToInt

@Composable
internal fun StylePanel(
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
            onSelect = {
                onSpecChange(spec.copy(visual = spec.visual.copy(backgroundMode = it)))
            },
        )
        Button(
            onClick = onExtractPalette,
            enabled = spec.song.coverAssetId != null && !isExtractingPalette,
        ) {
            if (isExtractingPalette) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            }
            Text(
                stringResource(
                    if (isExtractingPalette) R.string.editor_extracting_palette else R.string.editor_extract_palette,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        paletteError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        ColorField(
            label = stringResource(R.string.editor_dominant_color),
            value = spec.visual.palette.dominant,
            modifier = Modifier.testTag(EDITOR_DOMINANT_COLOR_TAG),
        ) {
            onSpecChange(
                spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(dominant = it))),
            )
        }
        ColorField(
            label = stringResource(R.string.editor_secondary_color),
            value = spec.visual.palette.secondary,
            modifier = Modifier.testTag(EDITOR_SECONDARY_COLOR_TAG),
        ) {
            onSpecChange(
                spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(secondary = it))),
            )
        }
        ColorField(
            label = stringResource(R.string.editor_accent_color),
            value = spec.visual.palette.accent,
            modifier = Modifier.testTag(EDITOR_ACCENT_COLOR_TAG),
        ) {
            onSpecChange(
                spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(accent = it))),
            )
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
                onSelect = {
                    onSpecChange(spec.copy(visual = spec.visual.copy(gridDensity = it)))
                },
            )
            LabeledSlider(
                label = stringResource(R.string.editor_grid_opacity),
                value = spec.visual.gridOpacity.toFloat(),
                range = 0f..0.5f,
                displayValue = "${(spec.visual.gridOpacity * 100).roundToInt()}%",
            ) {
                onSpecChange(spec.copy(visual = spec.visual.copy(gridOpacity = it.toDouble())))
            }
        }
    }
}

@Composable
internal fun BrandingPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_platform_attribution))
        SettingSwitch(
            label = stringResource(R.string.editor_show_platform_logo),
            checked = spec.visibility.showPlatformBadge,
            enabled = spec.branding.platform != SongSource.UNKNOWN,
            supportingText = if (spec.branding.platform == SongSource.UNKNOWN) {
                stringResource(R.string.editor_platform_required_reason)
            } else {
                null
            },
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showPlatformBadge = it)))
        }
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.branding.platform,
            label = ::songSourceLabel,
            onSelect = {
                onSpecChange(spec.copy(branding = spec.branding.copy(platform = it)))
            },
        )
        SettingSwitch(
            label = stringResource(
                R.string.editor_show_shared_by,
                stringResource(R.string.brand_shared_by),
            ),
            checked = spec.visibility.showSharedBy,
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSharedBy = it)))
        }
        if (spec.visibility.showSharedBy) {
            LimitedSingleLineField(
                label = stringResource(R.string.editor_sharer_name),
                value = spec.branding.sharedByName,
                maxLength = 240,
                imeAction = ImeAction.Done,
                onValueChange = {
                    onSpecChange(spec.copy(branding = spec.branding.copy(sharedByName = it)))
                },
            )
        }
        SettingSwitch(
            label = stringResource(
                R.string.editor_show_generated_watermark,
                stringResource(R.string.brand_generated_watermark),
            ),
            checked = spec.visibility.showGeneratedWatermark,
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showGeneratedWatermark = it)))
        }
    }
}

internal const val EDITOR_DOMINANT_COLOR_TAG = "editor-dominant-color"
internal const val EDITOR_SECONDARY_COLOR_TAG = "editor-secondary-color"
internal const val EDITOR_ACCENT_COLOR_TAG = "editor-accent-color"
