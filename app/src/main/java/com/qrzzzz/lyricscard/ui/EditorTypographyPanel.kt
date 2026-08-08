package com.qrzzzz.lyricscard.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.FontScheme
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.TextAlignment
import com.qrzzzz.lyricscard.model.TextColorMode
import com.qrzzzz.lyricscard.model.TextColorPreset
import kotlin.math.roundToInt

@Composable
internal fun TypographyPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
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
                val family = if (scheme == FontScheme.SANS_HEAVY) {
                    "Source Han Sans SC"
                } else {
                    "Source Han Serif SC"
                }
                onSpecChange(
                    spec.copy(
                        typography = spec.typography.copy(fontScheme = scheme, fontFamily = family),
                    ),
                )
            },
        )
        LabeledSlider(
            label = stringResource(R.string.editor_lyric_size),
            value = spec.typography.lyricSize.toFloat(),
            range = 36f..72f,
            displayValue = "${spec.typography.lyricSize}",
        ) {
            onSpecChange(spec.copy(typography = spec.typography.copy(lyricSize = it.roundToInt())))
        }
        LabeledSlider(
            label = stringResource(R.string.editor_line_height),
            value = spec.typography.lineHeight.toFloat(),
            range = 1.1f..1.75f,
            displayValue = "%.2f".format(spec.typography.lineHeight),
        ) {
            onSpecChange(spec.copy(typography = spec.typography.copy(lineHeight = it.toDouble())))
        }
        LabeledSlider(
            label = stringResource(R.string.editor_translation_scale),
            value = spec.typography.translationScale.toFloat(),
            range = 0.6f..0.9f,
            displayValue = "${(spec.typography.translationScale * 100).roundToInt()}%",
        ) {
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
            onSelect = {
                onSpecChange(spec.copy(typography = spec.typography.copy(alignment = it)))
            },
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
                val custom = if (mode == TextColorMode.CUSTOM) {
                    spec.typography.customTextColor ?: "#FFFFFF"
                } else {
                    spec.typography.customTextColor
                }
                onSpecChange(
                    spec.copy(
                        typography = spec.typography.copy(
                            textColorMode = mode,
                            customTextColor = custom,
                        ),
                    ),
                )
            },
        )
        when (spec.typography.textColorMode) {
            TextColorMode.PRESET -> ChoiceChips(
                values = listOf(
                    TextColorPreset.WHITE,
                    TextColorPreset.BLACK,
                    TextColorPreset.WARM_WHITE,
                    TextColorPreset.CREAM,
                ),
                selected = spec.typography.textColorPreset,
                label = ::textColorPresetLabel,
                onSelect = {
                    onSpecChange(spec.copy(typography = spec.typography.copy(textColorPreset = it)))
                },
            )
            TextColorMode.CUSTOM -> ColorField(
                label = stringResource(R.string.editor_custom_text_color),
                value = spec.typography.customTextColor ?: "#FFFFFF",
                modifier = Modifier.testTag(EDITOR_CUSTOM_TEXT_COLOR_TAG),
            ) {
                onSpecChange(spec.copy(typography = spec.typography.copy(customTextColor = it)))
            }
            TextColorMode.AUTO -> Unit
        }
    }
}

@Composable
internal fun textColorPresetLabel(preset: TextColorPreset) = stringResource(
    when (preset) {
        TextColorPreset.WHITE -> R.string.editor_color_white
        TextColorPreset.BLACK -> R.string.editor_color_black
        TextColorPreset.WARM_WHITE -> R.string.editor_color_warm_white
        TextColorPreset.CREAM -> R.string.editor_color_cream
        TextColorPreset.CHARCOAL -> R.string.editor_color_charcoal
        TextColorPreset.SOFT_BLUE -> R.string.editor_color_soft_blue
        TextColorPreset.SOFT_GOLD -> R.string.editor_color_soft_gold
    },
)

internal const val EDITOR_CUSTOM_TEXT_COLOR_TAG = "editor-custom-text-color"
