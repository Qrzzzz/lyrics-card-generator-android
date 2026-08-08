package com.qrzzzz.lyricscard.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview
import com.qrzzzz.lyricscard.ui.theme.LyricsCardLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onSaveDestination: (android.net.Uri) -> Unit,
    onEffectConsumed: (Long) -> Unit,
    onExternalActionError: (UiText) -> Unit,
) {
    val project = checkNotNull(state.project)
    val context = LocalContext.current
    val defaultFileName = stringResource(R.string.export_default_file_name)

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        uri?.let(onSaveDestination)
    }

    LaunchedEffect(state.effect?.id) {
        val effect = state.effect ?: return@LaunchedEffect
        try {
            when (effect.action) {
                ExportPendingAction.SAVE -> saveLauncher.launch(ensurePng(state.fileName, defaultFileName))
                ExportPendingAction.SHARE -> {
                    val image = state.exported
                    if (image != null) shareImage(context, image)
                }
            }
        } catch (cause: Throwable) {
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

    BackHandler(enabled = state.isBusy) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isBusy) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= LyricsCardLayout.wideBreakpoint
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RendererPreview(
                        spec = project.spec.copy(canvas = project.spec.canvas.copy(pixelRatio = state.multiplier)),
                        controller = renderer,
                        onMeasuredHeight = onMeasuredHeight,
                        modifier = Modifier.weight(1f),
                    )
                    ExportControls(
                        project = project,
                        resolvedHeight = state.measuredHeight,
                        multiplier = state.multiplier,
                        onMultiplier = onMultiplier,
                        fileName = state.fileName,
                        onFileName = onFileName,
                        exported = state.exported,
                        busy = state.isBusy,
                        canCancel = state.canCancel,
                        status = state.status,
                        error = state.errorMessage,
                        onSave = onSave,
                        onShare = onShare,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        modifier = Modifier.weight(0.72f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RendererPreview(
                        spec = project.spec.copy(canvas = project.spec.canvas.copy(pixelRatio = state.multiplier)),
                        controller = renderer,
                        onMeasuredHeight = onMeasuredHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.46f),
                    )
                    ExportControls(
                        project = project,
                        resolvedHeight = state.measuredHeight,
                        multiplier = state.multiplier,
                        onMultiplier = onMultiplier,
                        fileName = state.fileName,
                        onFileName = onFileName,
                        exported = state.exported,
                        busy = state.isBusy,
                        canCancel = state.canCancel,
                        status = state.status,
                        error = state.errorMessage,
                        onSave = onSave,
                        onShare = onShare,
                        onCancel = onCancel,
                        onRetry = onRetry,
                        modifier = Modifier.weight(0.54f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportControls(
    project: Project,
    resolvedHeight: Int,
    multiplier: Int,
    onMultiplier: (Int) -> Unit,
    fileName: String,
    onFileName: (String) -> Unit,
    exported: ExportedImage?,
    busy: Boolean,
    canCancel: Boolean,
    status: UiText,
    error: UiText?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finalWidth = project.spec.canvas.width * multiplier
    val finalHeight = resolvedHeight * multiplier
    val estimateMb = finalWidth.toLong() * finalHeight.toLong() * 4.0 / (1024.0 * 1024.0)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(stringResource(R.string.export_output_settings), style = MaterialTheme.typography.titleLarge)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 2).forEach { value ->
                    FilterChip(
                        selected = multiplier == value,
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
                        enabled = !busy,
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                value = fileName,
                onValueChange = { onFileName(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.export_file_name)) },
                suffix = {
                    if (!fileName.endsWith(".png", true)) {
                        Text(stringResource(R.string.file_extension_png))
                    }
                },
                singleLine = true,
                enabled = !busy,
            )
        }
        if (exported != null) {
            item {
                val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = null,
                    key1 = exported.file.absolutePath,
                ) {
                    value = withContext(Dispatchers.IO) {
                        decodePreviewBitmap(exported.file.absolutePath)
                    }
                }
                Card(shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.export_result_preview),
                                modifier = Modifier.padding(start = 8.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        val previewBitmap = bitmap
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = stringResource(R.string.exported_image_description),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                error?.asString() ?: status.asString(),
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (error != null) {
            item {
                OutlinedButton(onClick = onRetry, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text(stringResource(R.string.export_retry), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !busy) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Text(
                        stringResource(if (busy) R.string.export_generating else R.string.common_save),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (busy) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        enabled = canCancel,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text(
                            stringResource(
                                if (canCancel) R.string.common_cancel else R.string.export_finalizing,
                            ),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text(stringResource(R.string.common_share), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

private fun shareImage(context: android.content.Context, image: ExportedImage) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", image.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = image.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(
            context.contentResolver,
            context.getString(R.string.export_clip_label),
            uri,
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_chooser)))
}

private fun ensurePng(value: String, fallbackName: String): String {
    val clean = value.ifBlank { fallbackName }.replace(INVALID_FILE_CHARS, "-")
    return if (clean.endsWith(".png", true)) clean else "$clean.png"
}

private fun decodePreviewBitmap(path: String): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1_024) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|]+")
