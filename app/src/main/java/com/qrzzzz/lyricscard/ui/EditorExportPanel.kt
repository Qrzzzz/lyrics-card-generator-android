package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.ContentMode
import com.qrzzzz.lyricscard.model.LayoutMode
import com.qrzzzz.lyricscard.model.Project

@Composable
internal fun ExportStepPanel(project: Project) {
    val spec = project.spec
    val songReady = spec.song.title.isNotBlank() || spec.song.artist.isNotBlank()
    val contentReady = spec.content.mode == ContentMode.INSTRUMENTAL || spec.content.lyrics.isNotBlank()
    PanelColumn {
        SectionTitle(stringResource(R.string.editor_pre_export_check))
        Text(project.name, style = MaterialTheme.typography.titleLarge)
        ReadinessRow(
            label = stringResource(R.string.editor_song_info),
            ready = songReady,
            detail = stringResource(
                if (songReady) R.string.editor_filled else R.string.editor_return_to_first_step,
            ),
        )
        ReadinessRow(
            label = stringResource(R.string.editor_card_content),
            ready = contentReady,
            detail = stringResource(
                if (contentReady) R.string.editor_ready else R.string.editor_lyrics_empty,
            ),
        )
        ReadinessRow(
            label = stringResource(R.string.editor_canvas),
            ready = true,
            detail = stringResource(
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
    val state = stringResource(if (ready) R.string.editor_ready else R.string.editor_not_ready)
    val accessibilityDescription = listOf(label, detail)
        .filterNot { it == state }
        .joinToString(separator = ", ")
    Surface(
        color = if (ready) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = accessibilityDescription
                    stateDescription = state
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (ready) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ready) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                )
            }
        }
    }
}
