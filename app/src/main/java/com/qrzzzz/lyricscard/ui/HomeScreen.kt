package com.qrzzzz.lyricscard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.ui.theme.LyricsCardSpacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.ensureActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<ProjectSummary>,
    isLoading: Boolean = false,
    isWorking: Boolean,
    snackbarHost: @Composable () -> Unit,
    onCreateBlank: () -> Unit,
    onCreateSample: () -> Unit,
    onOpen: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSettings: () -> Unit,
    thumbnailLoader: ThumbnailLoader = FileThumbnailLoader,
) {
    val configuration = LocalConfiguration.current
    val screenTitle = stringResource(R.string.home_title)
    val dateFormatter = remember(configuration.locales) {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT,
            configuration.locales[0],
        )
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = screenTitle },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(screenTitle) },
                    actions = {
                        IconButton(
                            onClick = onSettings,
                            enabled = !isWorking,
                            modifier = Modifier.testTag(HOME_SETTINGS_TAG),
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = stringResource(R.string.home_settings_description),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (isWorking) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(HOME_WORKING_TAG),
                    )
                }
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val layout = homeLayoutClass(maxWidth)
            val horizontalPadding = when (layout) {
                HomeLayoutClass.Compact -> LyricsCardSpacing.large
                HomeLayoutClass.Medium -> LyricsCardSpacing.extraLarge
                HomeLayoutClass.Expanded -> LyricsCardSpacing.section
            }
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = HOME_CONTENT_MAX_WIDTH),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = LyricsCardSpacing.large,
                    end = horizontalPadding,
                    bottom = LyricsCardSpacing.section,
                ),
                verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.large),
            ) {
                item {
                    CreationActions(
                        stacked = layout == HomeLayoutClass.Compact || LocalDensity.current.fontScale >= 1.6f,
                        enabled = !isWorking,
                        onCreateBlank = onCreateBlank,
                        onCreateSample = onCreateSample,
                    )
                }
                item {
                    Spacer(Modifier.height(LyricsCardSpacing.small))
                    Text(
                        stringResource(R.string.home_recent_projects),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                when {
                    isLoading -> item { ProjectsLoading() }
                    projects.isEmpty() -> item { EmptyProjects() }
                    else -> items(projects, key = ProjectSummary::id) { project ->
                        ProjectRow(
                            project = project,
                            updatedAt = stringResource(
                                R.string.home_updated_at,
                                dateFormatter.format(Date(project.updatedAt)),
                            ),
                            enabled = !isWorking,
                            thumbnailLoader = thumbnailLoader,
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
}

@Composable
private fun CreationActions(
    stacked: Boolean,
    enabled: Boolean,
    onCreateBlank: () -> Unit,
    onCreateSample: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.medium)) {
        Text(
            stringResource(R.string.home_create_heading),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.small)) {
                CreateBlankButton(enabled, onCreateBlank, Modifier.fillMaxWidth())
                CreateSampleButton(enabled, onCreateSample, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(LyricsCardSpacing.medium)) {
                CreateBlankButton(enabled, onCreateBlank, Modifier.weight(1f))
                CreateSampleButton(enabled, onCreateSample, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CreateBlankButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .testTag(HOME_CREATE_BLANK_TAG),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Text(stringResource(R.string.home_blank_project), modifier = Modifier.padding(start = LyricsCardSpacing.small))
    }
}

@Composable
private fun CreateSampleButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .testTag(HOME_CREATE_SAMPLE_TAG),
    ) {
        Icon(Icons.Rounded.TipsAndUpdates, contentDescription = null)
        Text(stringResource(R.string.home_open_sample), modifier = Modifier.padding(start = LyricsCardSpacing.small))
    }
}

@Composable
private fun ProjectsLoading() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 112.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .testTag(HOME_PROJECTS_LOADING_TAG),
            )
        }
    }
}

@Composable
private fun EmptyProjects() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 112.dp)
            .testTag(HOME_EMPTY_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(LyricsCardSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.extraSmall),
        ) {
            Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_empty_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ProjectRow(
    project: ProjectSummary,
    updatedAt: String,
    enabled: Boolean,
    thumbnailLoader: ThumbnailLoader,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var renameOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var deleteOpen by rememberSaveable(project.id) { mutableStateOf(false) }
    var name by rememberSaveable(project.id, project.name) { mutableStateOf(project.name) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onOpen)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .testTag("$HOME_PROJECT_ROW_PREFIX${project.id}"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(LyricsCardSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LyricsCardSpacing.medium),
        ) {
            ProjectThumbnail(project, thumbnailLoader)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LyricsCardSpacing.extraSmall),
            ) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    updatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = enabled,
                    modifier = Modifier
                        .sizeIn(minWidth = MIN_TOUCH_TARGET, minHeight = MIN_TOUCH_TARGET)
                        .testTag("$HOME_PROJECT_MENU_PREFIX${project.id}"),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(
                            R.string.home_project_menu_description,
                            project.name,
                        ),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            name = project.name
                            menuOpen = false
                            renameOpen = true
                        },
                        enabled = enabled,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                        enabled = enabled,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            deleteOpen = true
                        },
                        enabled = enabled,
                    )
                }
            }
        }
    }

    if (renameOpen) {
        RenameProjectDialog(
            projectName = project.name,
            name = name,
            enabled = enabled,
            onNameChange = { name = it.take(MAX_PROJECT_NAME_LENGTH) },
            onDismiss = { renameOpen = false },
            onConfirm = {
                onRename(name.trim())
                renameOpen = false
            },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { if (enabled) deleteOpen = false },
            icon = {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.home_delete_project_title, project.name)) },
            text = { Text(stringResource(R.string.home_delete_project_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        deleteOpen = false
                    },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag(HOME_DELETE_CONFIRM_TAG),
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }, enabled = enabled) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProjectThumbnail(project: ProjectSummary, loader: ThumbnailLoader) {
    val density = LocalDensity.current
    val widthPx = with(density) { THUMBNAIL_WIDTH.roundToPx() }
    val heightPx = with(density) { THUMBNAIL_HEIGHT.roundToPx() }
    val requestKey = remember(project.thumbnailPath, project.updatedAt) {
        ThumbnailRequest(project.thumbnailPath, project.updatedAt)
    }
    var result by remember(requestKey, loader) {
        mutableStateOf<ThumbnailResult>(
            if (requestKey.path == null) ThumbnailResult.Missing else ThumbnailResult.Loading,
        )
    }
    LaunchedEffect(requestKey, loader, widthPx, heightPx) {
        result = if (requestKey.path == null) {
            ThumbnailResult.Missing
        } else {
            val bitmap = loader.load(requestKey.path, widthPx, heightPx)
            ensureActive()
            if (bitmap == null) ThumbnailResult.Missing else ThumbnailResult.Ready(bitmap)
        }
    }

    Box(
        modifier = Modifier
            .size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (val value = result) {
            ThumbnailResult.Loading -> CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .clearAndSetSemantics { }
                    .testTag("$HOME_THUMBNAIL_LOADING_PREFIX${project.id}"),
                strokeWidth = 2.dp,
            )
            ThumbnailResult.Missing -> Icon(
                Icons.Rounded.Image,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("$HOME_THUMBNAIL_FALLBACK_PREFIX${project.id}"),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            is ThumbnailResult.Ready -> Image(
                bitmap = value.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("$HOME_THUMBNAIL_IMAGE_PREFIX${project.id}"),
            )
        }
    }
}

@Composable
private fun RenameProjectDialog(
    projectName: String,
    name: String,
    enabled: Boolean,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldPlaced by remember { mutableStateOf(false) }
    val normalizedName = name.trim()
    val isBlank = normalizedName.isEmpty()
    val canConfirm = enabled && !isBlank && normalizedName != projectName
    val submit = {
        if (canConfirm) {
            focusManager.clearFocus()
            onConfirm()
        }
    }

    LaunchedEffect(fieldPlaced) {
        if (fieldPlaced) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(stringResource(R.string.home_rename_project)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onGloballyPositioned { fieldPlaced = true }
                    .testTag(HOME_RENAME_FIELD_TAG),
                singleLine = true,
                label = { Text(stringResource(R.string.home_project_name)) },
                isError = isBlank,
                supportingText = if (isBlank) {
                    { Text(stringResource(R.string.home_project_name_required)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = canConfirm,
                modifier = Modifier.testTag(HOME_RENAME_CONFIRM_TAG),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun homeLayoutClass(width: Dp): HomeLayoutClass = when {
    width < 600.dp -> HomeLayoutClass.Compact
    width < 840.dp -> HomeLayoutClass.Medium
    else -> HomeLayoutClass.Expanded
}

private enum class HomeLayoutClass { Compact, Medium, Expanded }

private data class ThumbnailRequest(val path: String?, val updatedAt: Long)

private sealed interface ThumbnailResult {
    data object Loading : ThumbnailResult
    data object Missing : ThumbnailResult
    data class Ready(val bitmap: ImageBitmap) : ThumbnailResult
}

internal const val HOME_SETTINGS_TAG = "home-settings"
internal const val HOME_WORKING_TAG = "home-working"
internal const val HOME_CREATE_BLANK_TAG = "home-create-blank"
internal const val HOME_CREATE_SAMPLE_TAG = "home-create-sample"
internal const val HOME_PROJECTS_LOADING_TAG = "home-projects-loading"
internal const val HOME_EMPTY_TAG = "home-empty"
internal const val HOME_PROJECT_ROW_PREFIX = "home-project-row-"
internal const val HOME_PROJECT_MENU_PREFIX = "home-project-menu-"
internal const val HOME_THUMBNAIL_LOADING_PREFIX = "home-thumbnail-loading-"
internal const val HOME_THUMBNAIL_FALLBACK_PREFIX = "home-thumbnail-fallback-"
internal const val HOME_THUMBNAIL_IMAGE_PREFIX = "home-thumbnail-image-"
internal const val HOME_RENAME_FIELD_TAG = "home-rename-field"
internal const val HOME_RENAME_CONFIRM_TAG = "home-rename-confirm"
internal const val HOME_DELETE_CONFIRM_TAG = "home-delete-confirm"

private val MIN_TOUCH_TARGET = 48.dp
private val THUMBNAIL_WIDTH = 88.dp
private val THUMBNAIL_HEIGHT = 72.dp
private val HOME_CONTENT_MAX_WIDTH = 960.dp
private const val MAX_PROJECT_NAME_LENGTH = 120
