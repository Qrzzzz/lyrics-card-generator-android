package com.qrzzzz.lyricscard.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    state: ExportUiState,
    renderer: RendererController,
    onBack: () -> Unit,
    onMultiplier: (Int) -> Unit,
    onFileName: (String) -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onSaveDestination: (android.net.Uri?) -> Unit,
    onEffectConsumed: (Long) -> Unit,
    onExternalActionError: (UiText) -> Unit,
    onPreviewBitmapReleased: (Bitmap) -> Unit = {},
    windowWidthSizeClass: WindowWidthSizeClass? = null,
) {
    val project = checkNotNull(state.project)
    val context = LocalContext.current
    val windowWidth = currentLyricsWindowWidth(windowWidthSizeClass)
    val defaultFileName = stringResource(R.string.export_default_file_name)
    val screenTitle = stringResource(R.string.export_title)
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        onSaveDestination(uri)
    }

    LaunchedEffect(state.effect?.id) {
        val effect = state.effect ?: return@LaunchedEffect
        try {
            when (effect.action) {
                ExportPendingAction.SAVE -> {
                    saveLauncher.launch(ensurePng(state.fileName, defaultFileName))
                }
                ExportPendingAction.SHARE -> {
                    val readinessError = shareReadinessError(state)
                    if (readinessError != null) {
                        onExternalActionError(readinessError)
                    } else {
                        shareImage(context, checkNotNull(state.exported))?.let(onExternalActionError)
                    }
                }
            }
        } catch (_: Throwable) {
            onExternalActionError(
                UiText.resource(
                    if (effect.action == ExportPendingAction.SAVE) {
                        R.string.export_error_open_file_picker
                    } else {
                        R.string.export_error_open_share_sheet
                    },
                ),
            )
        } finally {
            onEffectConsumed(effect.id)
        }
    }

    BackHandler(enabled = state.isBusy) {
        if (state.canCancel) onCancel()
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = screenTitle },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        screenTitle,
                        modifier = Modifier.semantics { heading() },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        if (windowWidth == LyricsWindowWidth.COMPACT) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
                    .imePadding()
                    .testTag(EXPORT_COMPACT_LAYOUT_TAG),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RendererPreview(
                    spec = project.spec.copy(
                        canvas = project.spec.canvas.copy(pixelRatio = state.multiplier),
                    ),
                    controller = renderer,
                    onMeasuredHeight = onMeasuredHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f)
                        .heightIn(min = 128.dp, max = 360.dp),
                )
                ExportControls(
                    state = state,
                    project = project,
                    onMultiplier = onMultiplier,
                    onFileName = onFileName,
                    onSave = onSave,
                    onShare = onShare,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onPreviewBitmapReleased = onPreviewBitmapReleased,
                    modifier = Modifier.fillMaxWidth().weight(0.58f),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .imePadding()
                    .testTag(
                        if (windowWidth == LyricsWindowWidth.MEDIUM) {
                            EXPORT_MEDIUM_LAYOUT_TAG
                        } else {
                            EXPORT_EXPANDED_LAYOUT_TAG
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                RendererPreview(
                    spec = project.spec.copy(
                        canvas = project.spec.canvas.copy(pixelRatio = state.multiplier),
                    ),
                    controller = renderer,
                    onMeasuredHeight = onMeasuredHeight,
                    modifier = Modifier
                        .weight(if (windowWidth == LyricsWindowWidth.EXPANDED) 1.7f else 1.2f)
                        .fillMaxHeight()
                        .widthIn(min = 240.dp),
                )
                ExportControls(
                    state = state,
                    project = project,
                    onMultiplier = onMultiplier,
                    onFileName = onFileName,
                    onSave = onSave,
                    onShare = onShare,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onPreviewBitmapReleased = onPreviewBitmapReleased,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(
                            min = if (windowWidth == LyricsWindowWidth.EXPANDED) 360.dp else 320.dp,
                            max = if (windowWidth == LyricsWindowWidth.EXPANDED) 560.dp else 480.dp,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun ExportControls(
    state: ExportUiState,
    project: Project,
    onMultiplier: (Int) -> Unit,
    onFileName: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPreviewBitmapReleased: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    val finalWidth = project.spec.canvas.width * state.multiplier
    val finalHeight = state.measuredHeight * state.multiplier
    val estimateMb = finalWidth.toLong() * finalHeight.toLong() * 4.0 / (1024.0 * 1024.0)
    val invalidFileName = INVALID_FILE_CHARS.containsMatchIn(state.fileName)
    val resultPending = state.exported != null && state.preview.phase == ExportPreviewPhase.LOADING
    val actionEnabled = !state.isBusy && !resultPending

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                stringResource(R.string.export_output_settings),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 2).forEach { value ->
                    FilterChip(
                        selected = state.multiplier == value,
                        onClick = { onMultiplier(value) },
                        label = {
                            Text(
                                stringResource(
                                    R.string.export_scale_label,
                                    value,
                                    stringResource(
                                        if (value == 1) {
                                            R.string.common_standard
                                        } else {
                                            R.string.common_high_definition
                                        },
                                    ),
                                ),
                            )
                        },
                        leadingIcon = if (state.multiplier == value) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        enabled = !state.isBusy,
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.export_final_size), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.export_final_dimensions, finalWidth, finalHeight),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        stringResource(R.string.export_memory_estimate, estimateMb),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.fileName,
                onValueChange = onFileName,
                modifier = Modifier.fillMaxWidth().testTag(EXPORT_FILE_NAME_TAG),
                label = { Text(stringResource(R.string.export_file_name)) },
                suffix = {
                    if (!state.fileName.endsWith(".png", true)) {
                        Text(stringResource(R.string.file_extension_png))
                    }
                },
                supportingText = {
                    Text(
                        stringResource(
                            if (invalidFileName) {
                                R.string.export_file_name_sanitized
                            } else {
                                R.string.export_file_name_help
                            },
                        ),
                    )
                },
                isError = invalidFileName,
                singleLine = true,
                enabled = !state.isBusy,
            )
        }
        when (state.preview.phase) {
            ExportPreviewPhase.LOADING -> item {
                ExportPreviewLoading()
            }
            ExportPreviewPhase.READY -> state.preview.bitmap?.let { bitmap ->
                item {
                    DisposableEffect(bitmap) {
                        onDispose { onPreviewBitmapReleased(bitmap) }
                    }
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Card(shape = MaterialTheme.shapes.large) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    stringResource(R.string.export_result_preview),
                                    modifier = Modifier.padding(start = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = stringResource(R.string.exported_image_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp),
                            )
                        }
                    }
                }
            }
            ExportPreviewPhase.ERROR,
            ExportPreviewPhase.EMPTY,
            -> Unit
        }
        item {
            ExportOperationStatus(state)
        }
        if (state.errorMessage != null && !state.isBusy) {
            item {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text(stringResource(R.string.export_retry), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            if (state.isBusy) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        enabled = state.canCancel,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text(
                            stringResource(
                                if (state.canCancel) R.string.common_cancel else R.string.export_finalizing,
                            ),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        enabled = actionEnabled,
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text(
                            stringResource(
                                if (state.isResultReady) R.string.common_save else R.string.export_generate_and_save,
                            ),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        enabled = actionEnabled,
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text(
                            stringResource(
                                if (state.isResultReady) R.string.common_share else R.string.export_generate_and_share,
                            ),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportPreviewLoading() {
    Card(shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.export_preview_loading))
        }
    }
}

@Composable
private fun ExportOperationStatus(state: ExportUiState) {
    val isError = state.errorMessage != null
    val announce = state.operation in setOf(
        ExportOperationState.SUCCESS,
        ExportOperationState.FAILURE,
        ExportOperationState.CANCELLED,
        ExportOperationState.INTERRUPTED,
    )
    Text(
        text = state.errorMessage?.asString() ?: state.status.asString(),
        modifier = Modifier.then(
            if (announce) {
                Modifier.semantics {
                    liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
                }
            } else {
                Modifier
            },
        ),
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun shareReadinessError(state: ExportUiState): UiText? =
    if (state.exported == null || !state.isResultReady) {
        UiText.resource(R.string.export_result_missing_error)
    } else {
        null
    }

internal fun buildShareIntent(
    context: android.content.Context,
    image: ExportedImage,
    uri: android.net.Uri,
): Intent = Intent(Intent.ACTION_SEND).apply {
        type = image.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(
            context.getString(R.string.export_clip_label),
            uri,
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

internal fun shareImage(
    context: android.content.Context,
    image: ExportedImage,
    resolveUri: (android.content.Context, ExportedImage) -> android.net.Uri = { receiver, exported ->
        FileProvider.getUriForFile(receiver, "${receiver.packageName}.files", exported.file)
    },
    launch: (android.content.Context, Intent) -> Unit = { receiver, intent -> receiver.startActivity(intent) },
): UiText? = try {
    val uri = resolveUri(context, image)
    launch(
        context,
        Intent.createChooser(
            buildShareIntent(context, image, uri),
            context.getString(R.string.export_share_chooser),
        ),
    )
    null
} catch (_: Throwable) {
    UiText.resource(R.string.export_error_open_share_sheet)
}

internal fun ensurePng(value: String, fallbackName: String): String {
    val clean = value.ifBlank { fallbackName }.replace(INVALID_FILE_CHARS, "-")
    return if (clean.endsWith(".png", true)) clean else "$clean.png"
}

private val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|]+")

internal const val EXPORT_COMPACT_LAYOUT_TAG = "export-compact-layout"
internal const val EXPORT_MEDIUM_LAYOUT_TAG = "export-medium-layout"
internal const val EXPORT_EXPANDED_LAYOUT_TAG = "export-expanded-layout"
internal const val EXPORT_FILE_NAME_TAG = "export-file-name"
