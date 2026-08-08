@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.SongSource

@Composable
internal fun ChooseSongPanel(
    project: Project,
    drafts: EditorDrafts,
    netease: NeteaseLookupUiState,
    actions: EditorScreenActions,
) {
    val spec = project.spec
    val clipboard = LocalClipboardManager.current
    val lookupBusy = netease.isSearching || netease.isResolving
    var searchDraft by remember(drafts.searchQuery) { mutableStateOf(drafts.searchQuery) }
    val searchTooLong = searchDraft.length > SEARCH_QUERY_MAX_LENGTH

    PanelColumn {
        SectionTitle(stringResource(R.string.editor_netease_section))
        Text(
            stringResource(R.string.editor_netease_help),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = searchDraft,
            onValueChange = { next ->
                searchDraft = next.take(SEARCH_QUERY_MAX_LENGTH + 1)
                if (next.length <= SEARCH_QUERY_MAX_LENGTH) actions.onSearchQueryChange(next)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_netease_query_label)) },
            placeholder = { Text(stringResource(R.string.editor_netease_query_example)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (netease.isSearching) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
            supportingText = {
                Text(
                    if (searchTooLong) {
                        stringResource(R.string.editor_text_too_long, SEARCH_QUERY_MAX_LENGTH)
                    } else {
                        stringResource(
                            R.string.editor_character_count,
                            searchDraft.length,
                            SEARCH_QUERY_MAX_LENGTH,
                        )
                    },
                )
            },
            isError = searchTooLong,
            singleLine = true,
            enabled = !netease.isResolving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (!searchTooLong && searchDraft.isNotBlank() && !lookupBusy) {
                        actions.onSearchNetease(searchDraft)
                    }
                },
            ),
        )
        Button(
            onClick = { actions.onSearchNetease(searchDraft) },
            enabled = searchDraft.isNotBlank() && !searchTooLong && !lookupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text(stringResource(R.string.editor_netease_search), modifier = Modifier.padding(start = 8.dp))
        }

        NeteaseStatus(netease)
        if (netease.results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                netease.results.forEach { result ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !lookupBusy,
                                role = Role.Button,
                                onClick = { actions.onResolveNeteaseSong(result.id) },
                            ),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(result.title, fontWeight = FontWeight.Bold)
                            },
                            supportingContent = {
                                val details = listOf(result.artist, result.album)
                                    .filter(String::isNotBlank)
                                    .joinToString(stringResource(R.string.middle_dot_separator))
                                if (details.isNotBlank()) Text(details)
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                            },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = drafts.linkInput,
            onValueChange = actions.onLinkInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.editor_netease_link_label)) },
            placeholder = { Text(stringResource(R.string.editor_netease_link_example)) },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            supportingText = { Text(stringResource(R.string.editor_link_supporting_text)) },
            minLines = 2,
            maxLines = 4,
            enabled = !lookupBusy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (drafts.linkInput.isNotBlank() && !lookupBusy) {
                        actions.onResolveNeteaseLink(drafts.linkInput)
                    }
                },
            ),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { clipboard.getText()?.text?.let(actions.onLinkInputChange) },
                enabled = !lookupBusy,
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Text(stringResource(R.string.editor_paste), modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = { actions.onResolveNeteaseLink(drafts.linkInput) },
                enabled = drafts.linkInput.isNotBlank() && !lookupBusy,
            ) {
                if (netease.isResolving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                }
                Text(stringResource(R.string.editor_parse), modifier = Modifier.padding(start = 6.dp))
            }
        }

        SectionTitle(stringResource(R.string.editor_manual_section))
        LimitedSingleLineField(
            label = stringResource(R.string.home_project_name),
            value = drafts.projectName,
            maxLength = PROJECT_NAME_MAX_LENGTH,
            required = true,
            onValueChange = actions.onProjectNameChange,
        )
        LimitedSingleLineField(
            label = stringResource(R.string.editor_song_title),
            value = spec.song.title,
            maxLength = SONG_FIELD_MAX_LENGTH,
            onValueChange = { next ->
                actions.onSpecChange(spec.copy(song = spec.song.copy(title = next)))
            },
        )
        LimitedSingleLineField(
            label = stringResource(R.string.editor_artist),
            value = spec.song.artist,
            maxLength = SONG_FIELD_MAX_LENGTH,
            onValueChange = { next ->
                actions.onSpecChange(spec.copy(song = spec.song.copy(artist = next)))
            },
        )
        LimitedSingleLineField(
            label = stringResource(R.string.editor_album),
            value = spec.song.album,
            maxLength = SONG_FIELD_MAX_LENGTH,
            imeAction = ImeAction.Done,
            onValueChange = { next ->
                actions.onSpecChange(spec.copy(song = spec.song.copy(album = next)))
            },
        )
        Text(stringResource(R.string.editor_source_platform), style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.song.source,
            label = ::songSourceLabel,
            onSelect = { source ->
                actions.onSpecChange(
                    spec.copy(
                        song = spec.song.copy(source = source),
                        branding = spec.branding.copy(platform = source),
                    ),
                )
            },
        )
        SettingSwitch(stringResource(R.string.editor_explicit_marker), spec.song.explicit) {
            actions.onSpecChange(spec.copy(song = spec.song.copy(explicit = it)))
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = actions.onPickCover) {
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
                TextButton(onClick = actions.onRemoveCover) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Text(stringResource(R.string.common_remove))
                }
            }
        }
    }
}

@Composable
private fun NeteaseStatus(state: NeteaseLookupUiState) {
    val isError = state.phase == NeteaseLookupPhase.ERROR
    val announce = state.phase in setOf(
        NeteaseLookupPhase.EMPTY,
        NeteaseLookupPhase.SUCCESS,
        NeteaseLookupPhase.ERROR,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (announce) {
                    Modifier.semantics {
                        liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                },
            ),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            state.message.asString(),
            modifier = Modifier.padding(12.dp),
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
internal fun songSourceLabel(source: SongSource) = stringResource(
    when (source) {
        SongSource.UNKNOWN -> R.string.common_unknown
        SongSource.QQ -> R.string.brand_qq_music
        SongSource.NETEASE -> R.string.brand_netease
        SongSource.APPLE -> R.string.brand_apple_music
        SongSource.SPOTIFY -> R.string.brand_spotify
    },
)

private const val SEARCH_QUERY_MAX_LENGTH = 120
private const val PROJECT_NAME_MAX_LENGTH = 120
private const val SONG_FIELD_MAX_LENGTH = 240
