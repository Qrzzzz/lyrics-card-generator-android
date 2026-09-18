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
                    if (mode == spec.canvas.layoutMode) return@ChoiceChips
                    val canvas = if (mode == LayoutMode.PORTRAIT) {
                        spec.canvas.copy(
                            layoutMode = mode,
                            ratio = spec.canvas.portrait.ratio,
                            width = spec.canvas.portrait.width,
                            height = spec.canvas.portrait.height,
                            autoHeight = spec.canvas.portrait.autoHeight,
                            autoWidth = spec.canvas.portrait.autoWidth,
                        )
                    } else {
                        spec.canvas.copy(
                            layoutMode = mode,
                            ratio = CanvasRatio.CUSTOM,
                            width = 1920,
                            height = 1080,
                            autoHeight = true,
                            portrait = com.qrzzzz.lyricscard.model.PortraitSettings(spec.canvas.ratio, spec.canvas.width, spec.canvas.height, spec.canvas.autoWidth, spec.canvas.autoHeight),
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
                listOf(CanvasRatio.CUSTOM)
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
                                autoWidth = false,
                            ),
                        ),
                    )
                },
            )
            if (spec.canvas.layoutMode == LayoutMode.LANDSCAPE) {
                val settings = spec.canvas.landscape
                SettingSwitch(stringResource(R.string.v2_auto_lyrics_width), settings.autoLyricsWidth) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(landscape = settings.copy(autoLyricsWidth = it))))
                }
                if (!settings.autoLyricsWidth) NumberField(stringResource(R.string.v2_lyrics_width), settings.lyricsWidth, 520..1280) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(landscape = settings.copy(lyricsWidth = it))))
                }
                SettingSwitch(stringResource(R.string.editor_auto_height), settings.autoHeight) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(landscape = settings.copy(autoHeight = it))))
                }
                if (!settings.autoHeight) NumberField(stringResource(R.string.v2_requested_height), settings.requestedHeight, 720..3600) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(landscape = settings.copy(requestedHeight = it))))
                }
            }
            if (spec.canvas.ratio == CanvasRatio.CUSTOM && spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                SettingSwitch(stringResource(R.string.v2_auto_width), spec.canvas.autoWidth) {
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(autoWidth = it)))
                }
                val widthRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                    720..1440
                } else {
                    1080..3000
                }
                val heightRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                    if (spec.canvas.autoHeight) 640..6400 else 720..3200
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
                        stringResource(R.string.editor_height_request_help)
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
