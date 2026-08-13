@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.CanvasRatio
import com.qrzzzz.lyricscard.model.ContentMode
import com.qrzzzz.lyricscard.model.LayoutMode
import com.qrzzzz.lyricscard.model.LyricTextCleaner
import com.qrzzzz.lyricscard.model.LyricTextLimits
import com.qrzzzz.lyricscard.model.RenderSpec

@Composable
internal fun LyricsPanel(
    spec: RenderSpec,
    onSpecChange: (RenderSpec) -> Unit,
) {
    val instrumental = spec.content.mode == ContentMode.INSTRUMENTAL
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_lyrics_content))
        SettingSwitch(stringResource(R.string.editor_instrumental_mode), instrumental) { enabled ->
            onSpecChange(
                if (enabled) {
                    spec.copy(
                        content = spec.content.copy(
                            mode = ContentMode.INSTRUMENTAL,
                            translationEnabled = false,
                        ),
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
            LimitedSingleLineField(
                label = stringResource(R.string.editor_instrumental_text),
                value = spec.content.instrumentalText,
                maxLength = 240,
                onValueChange = { next ->
                    onSpecChange(spec.copy(content = spec.content.copy(instrumentalText = next)))
                },
            )
        } else {
            var lyricsDraft by remember(spec.content.lyrics) { mutableStateOf(spec.content.lyrics) }
            val lyricLines = LyricTextLimits.countPhysicalLines(lyricsDraft)
            val lyricsValid = lyricLines <= LyricTextLimits.MAX_LINES
            OutlinedTextField(
                value = lyricsDraft,
                onValueChange = { next ->
                    lyricsDraft = next
                    if (LyricTextLimits.countPhysicalLines(next) <= LyricTextLimits.MAX_LINES) {
                        onSpecChange(spec.copy(content = spec.content.copy(lyrics = next)))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 144.dp)
                    .testTag(EDITOR_LYRICS_FIELD_TAG),
                label = { Text(stringResource(R.string.editor_original_lyrics)) },
                supportingText = {
                    LyricLineFeedback(lineCount = lyricLines, valid = lyricsValid)
                },
                isError = !lyricsValid,
                minLines = 6,
                maxLines = 14,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        val cleaned = LyricTextCleaner.removeTimestamps(lyricsDraft)
                        lyricsDraft = cleaned
                        if (LyricTextLimits.countPhysicalLines(cleaned) <= LyricTextLimits.MAX_LINES) {
                            onSpecChange(spec.copy(content = spec.content.copy(lyrics = cleaned)))
                        }
                    },
                ) {
                    Text(stringResource(R.string.editor_clear_timestamps))
                }
                TextButton(
                    onClick = {
                        val cleaned = LyricTextCleaner.collapseRepeatedBlankLines(lyricsDraft)
                        lyricsDraft = cleaned
                        if (LyricTextLimits.countPhysicalLines(cleaned) <= LyricTextLimits.MAX_LINES) {
                            onSpecChange(spec.copy(content = spec.content.copy(lyrics = cleaned)))
                        }
                    },
                ) {
                    Text(stringResource(R.string.editor_merge_blank_lines))
                }
            }
            SettingSwitch(stringResource(R.string.editor_show_translation), spec.content.translationEnabled) {
                onSpecChange(spec.copy(content = spec.content.copy(translationEnabled = it)))
            }
            if (spec.content.translationEnabled) {
                var translationDraft by remember(spec.content.translation) {
                    mutableStateOf(spec.content.translation)
                }
                val translationLines = LyricTextLimits.countPhysicalLines(translationDraft)
                val translationValid = translationLines <= LyricTextLimits.MAX_LINES
                OutlinedTextField(
                    value = translationDraft,
                    onValueChange = { next ->
                        translationDraft = next
                        if (LyricTextLimits.countPhysicalLines(next) <= LyricTextLimits.MAX_LINES) {
                            onSpecChange(spec.copy(content = spec.content.copy(translation = next)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 128.dp)
                        .testTag(EDITOR_TRANSLATION_FIELD_TAG),
                    label = { Text(stringResource(R.string.editor_translation_lyrics)) },
                    supportingText = {
                        LyricLineFeedback(lineCount = translationLines, valid = translationValid)
                    },
                    isError = !translationValid,
                    minLines = 5,
                    maxLines = 12,
                )
            }
        }
    }
}

@Composable
private fun LyricLineFeedback(lineCount: Int, valid: Boolean) {
    Text(
        stringResource(
            if (valid) R.string.editor_lyric_line_count else R.string.editor_lyric_line_limit_inline,
            lineCount,
            LyricTextLimits.MAX_LINES,
        ),
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
    )
}

internal const val EDITOR_LYRICS_FIELD_TAG = "editor-lyrics-field"
internal const val EDITOR_TRANSLATION_FIELD_TAG = "editor-translation-field"
