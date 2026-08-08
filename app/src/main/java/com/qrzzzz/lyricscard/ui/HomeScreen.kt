package com.qrzzzz.lyricscard.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.ui.theme.LyricsCardSpacing
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<ProjectSummary>,
    isWorking: Boolean,
    snackbarHost: @Composable () -> Unit,
    onCreateBlank: () -> Unit,
    onCreateSample: () -> Unit,
    onOpen: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.home_title))
                        Text(
                            stringResource(R.string.home_alpha_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings, enabled = !isWorking) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.home_settings_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = LyricsCardSpacing.comfortable,
                vertical = LyricsCardSpacing.large,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HeroActions(onCreateBlank, onCreateSample, enabled = !isWorking)
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_recent_projects),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (projects.isEmpty()) {
                item { EmptyProjects() }
            } else {
                items(projects, key = ProjectSummary::id) { project ->
                    ProjectCard(
                        project = project,
                        enabled = !isWorking,
                        onOpen = { onOpen(project.id) },
                        onDuplicate = { onDuplicate(project.id) },
                        onRename = { onRename(project.id, it) },
                        onDelete = { onDelete(project.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroActions(
    onCreateBlank: () -> Unit,
    onCreateSample: () -> Unit,
    enabled: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(LyricsCardSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.large),
        ) {
            Text(
                stringResource(R.string.home_hero_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.home_hero_subtitle),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCreateBlank, enabled = enabled) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(R.string.home_blank_project), modifier = Modifier.padding(start = 6.dp))
                }
                FilledTonalButton(onClick = onCreateSample, enabled = enabled) {
                    Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null)
                    Text(stringResource(R.string.home_open_sample), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyProjects() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var name by remember(project.name) { mutableStateOf(project.name) }
    val preview = remember(project.thumbnailPath, project.updatedAt) {
        project.thumbnailPath
            ?.takeIf { File(it).isFile }
            ?.let(BitmapFactory::decodeFile)
            ?.asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpen),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 68.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF6253C8), Color(0xFF2C6BAA), Color(0xFFDC805B)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        project.name.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    stringResource(
                        R.string.home_updated_at,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(project.updatedAt)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.home_project_menu_description),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; renameOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; deleteOpen = true },
                    )
                }
            }
        }
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.home_rename_project)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_project_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onRename(name.trim()); renameOpen = false },
                    enabled = enabled && name.isNotBlank(),
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text(stringResource(R.string.home_delete_project_title, project.name)) },
            text = { Text(stringResource(R.string.home_delete_project_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); deleteOpen = false },
                    enabled = enabled,
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
