package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.CanvasRatio
import com.qrzzzz.lyricscard.model.ContentMode
import com.qrzzzz.lyricscard.model.LayoutMode
import com.qrzzzz.lyricscard.model.RenderSpec

@Composable
internal fun LayoutPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
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
                        spec.canvas.copy(
                            layoutMode = mode,
                            ratio = CanvasRatio.PORTRAIT_4_5,
                            width = 1080,
                            height = 1350,
                            autoHeight = false,
                        )
                    } else {
                        spec.canvas.copy(
                            layoutMode = mode,
                            ratio = CanvasRatio.LANDSCAPE_16_9,
                            width = 1920,
                            height = 1080,
                            autoHeight = false,
                        )
                    }
                    onSpecChange(spec.copy(canvas = canvas))
                },
            )
            Text(stringResource(R.string.editor_ratio), style = MaterialTheme.typography.labelLarge)
            val ratios = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                listOf(
                    CanvasRatio.SQUARE,
                    CanvasRatio.PORTRAIT_4_5,
                    CanvasRatio.PORTRAIT_9_16,
                    CanvasRatio.CUSTOM,
                )
            } else {
                listOf(
                    CanvasRatio.LANDSCAPE_16_9,
                    CanvasRatio.LANDSCAPE_21_9,
                    CanvasRatio.LANDSCAPE_3_2,
                    CanvasRatio.CUSTOM,
                )
            }
            ChoiceChips(
                values = ratios,
                selected = spec.canvas.ratio,
                label = ::ratioLabel,
                onSelect = { ratio ->
                    val width = ratio.width ?: spec.canvas.width
                    val height = ratio.height ?: spec.canvas.height
                    onSpecChange(
                        spec.copy(
                            canvas = spec.canvas.copy(
                                ratio = ratio,
                                width = width,
                                height = height,
                                autoHeight = false,
                            ),
                        ),
                    )
                },
            )
            if (spec.canvas.ratio == CanvasRatio.CUSTOM) {
                val widthRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                    720..1440
                } else {
                    1080..3000
                }
                val heightRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                    720..3200
                } else {
                    720..1600
                }
                NumberField(
                    label = stringResource(R.string.editor_width),
                    value = spec.canvas.width,
                    validRange = widthRange,
                    modifier = Modifier.fillMaxWidth().testTag(EDITOR_WIDTH_FIELD_TAG),
                ) { width ->
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(width = width)))
                }
                NumberField(
                    label = stringResource(R.string.editor_height),
                    value = spec.canvas.height,
                    validRange = heightRange,
                    modifier = Modifier.fillMaxWidth().testTag(EDITOR_HEIGHT_FIELD_TAG),
                ) { height ->
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(height = height)))
                }
                SettingSwitch(
                    label = stringResource(R.string.editor_auto_height),
                    checked = spec.canvas.autoHeight,
                    enabled = spec.canvas.layoutMode == LayoutMode.PORTRAIT,
                    supportingText = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                        null
                    } else {
                        stringResource(R.string.editor_auto_height_disabled_reason)
                    },
                ) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(autoHeight = it)))
                }
            }
        }

        SectionTitle(stringResource(R.string.editor_elements))
        SettingSwitch(
            label = stringResource(R.string.editor_show_cover),
            checked = spec.visibility.showCover,
            enabled = spec.song.coverAssetId != null,
            supportingText = if (spec.song.coverAssetId == null) {
                stringResource(R.string.editor_cover_required_reason)
            } else {
                null
            },
        ) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showCover = it)))
        }
        SettingSwitch(stringResource(R.string.editor_show_song_info), spec.visibility.showSongInfo) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSongInfo = it)))
        }
        SettingSwitch(stringResource(R.string.editor_show_album), spec.visibility.showAlbum) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showAlbum = it)))
        }
        LabeledSlider(
            label = stringResource(R.string.editor_cover_crop_scale),
            value = spec.media.coverCropScale.toFloat(),
            range = 1f..2f,
            displayValue = "%.2f".format(spec.media.coverCropScale),
        ) {
            onSpecChange(spec.copy(media = spec.media.copy(coverCropScale = it.toDouble())))
        }
    }
}

@Composable
internal fun ratioLabel(ratio: CanvasRatio) = when (ratio) {
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

internal const val EDITOR_WIDTH_FIELD_TAG = "editor-width-field"
internal const val EDITOR_HEIGHT_FIELD_TAG = "editor-height-field"
