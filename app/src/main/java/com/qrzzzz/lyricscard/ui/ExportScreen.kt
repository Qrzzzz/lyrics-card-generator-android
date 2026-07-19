package com.qrzzzz.lyricscard.ui

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    project: Project,
    renderer: RendererController,
    defaultMultiplier: Int,
    onBack: () -> Unit,
    onExportRecorded: suspend (ExportedImage) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var multiplier by remember { mutableIntStateOf(defaultMultiplier.coerceIn(1, 2)) }
    var measuredHeight by remember(project.id) { mutableIntStateOf(project.spec.canvas.height) }
    var exported by remember { mutableStateOf<ExportedImage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("准备导出") }
    var fileName by remember(project.id) {
        mutableStateOf(defaultFileName(project.spec.song.title))
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val image = exported
        if (uri != null && image != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.use { sink ->
                            image.file.inputStream().use { source -> source.copyTo(sink) }
                        } ?: error("无法写入所选位置")
                    }
                }.onSuccess { status = "已保存到所选位置" }
                    .onFailure { error = it.message ?: "保存失败" }
            }
        }
    }

    fun runExport(onReady: (ExportedImage) -> Unit) {
        if (exportJob?.isActive == true) return
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            busy = true
            error = null
            status = "正在生成 ${multiplier}× PNG…"
            try {
                val image = renderer.exportPng(project.spec, multiplier)
                runCatching { onExportRecorded(image) }
                exported = image
                status = "已生成 ${image.width} × ${image.height} PNG"
                onReady(image)
            } catch (cause: CancellationException) {
                error = null
                status = "导出已取消，可立即重试"
                throw cause
            } catch (cause: Throwable) {
                error = cause.message ?: "导出失败"
            } finally {
                if (exportJob === launched) {
                    exportJob = null
                    busy = false
                }
            }
        }
        exportJob = launched
        launched.start()
    }

    fun cancelExport() {
        if (exportJob?.isActive != true) return
        error = null
        status = "正在取消并恢复渲染器…"
        exportJob?.cancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出图片", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
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
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    RendererPreview(
                        spec = project.spec.copy(canvas = project.spec.canvas.copy(pixelRatio = multiplier)),
                        controller = renderer,
                        onMeasuredHeight = { measuredHeight = it },
                        modifier = Modifier.weight(1f),
                    )
                    ExportControls(
                        project = project,
                        resolvedHeight = measuredHeight,
                        multiplier = multiplier,
                        onMultiplier = { multiplier = it; exported = null },
                        fileName = fileName,
                        onFileName = { fileName = it },
                        exported = exported,
                        busy = busy,
                        status = status,
                        error = error,
                        onSave = {
                            val current = exported
                            if (current != null) saveLauncher.launch(ensurePng(fileName))
                            else runExport { saveLauncher.launch(ensurePng(fileName)) }
                        },
                        onShare = {
                            val current = exported
                            if (current != null) shareImage(context, current)
                            else runExport { shareImage(context, it) }
                        },
                        onCancel = ::cancelExport,
                        onRetry = { exported = null; runExport {} },
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
                        spec = project.spec.copy(canvas = project.spec.canvas.copy(pixelRatio = multiplier)),
                        controller = renderer,
                        onMeasuredHeight = { measuredHeight = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.46f),
                    )
                    ExportControls(
                        project = project,
                        resolvedHeight = measuredHeight,
                        multiplier = multiplier,
                        onMultiplier = { multiplier = it; exported = null },
                        fileName = fileName,
                        onFileName = { fileName = it },
                        exported = exported,
                        busy = busy,
                        status = status,
                        error = error,
                        onSave = {
                            val current = exported
                            if (current != null) saveLauncher.launch(ensurePng(fileName))
                            else runExport { saveLauncher.launch(ensurePng(fileName)) }
                        },
                        onShare = {
                            val current = exported
                            if (current != null) shareImage(context, current)
                            else runExport { shareImage(context, it) }
                        },
                        onCancel = ::cancelExport,
                        onRetry = { exported = null; runExport {} },
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
    status: String,
    error: String?,
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
            Text("输出设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 2).forEach { value ->
                    FilterChip(
                        selected = multiplier == value,
                        onClick = { onMultiplier(value) },
                        label = { Text("${value}× ${if (value == 1) "标准" else "高清"}") },
                        enabled = !busy,
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("最终尺寸", style = MaterialTheme.typography.labelLarge)
                    Text("$finalWidth × $finalHeight px", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("预计解码内存 ${"%.1f".format(estimateMb)} MB", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = fileName,
                onValueChange = { onFileName(it.take(80)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("文件名") },
                suffix = { if (!fileName.endsWith(".png", true)) Text(".png") },
                singleLine = true,
                enabled = !busy,
            )
        }
        if (exported != null) {
            item {
                val bitmap = remember(exported.file.absolutePath) {
                    decodePreviewBitmap(exported.file.absolutePath)
                }
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("导出结果预览", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "导出的图片",
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
                error ?: status,
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        if (error != null) {
            item {
                OutlinedButton(onClick = onRetry, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("重试导出", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f), enabled = !busy) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Text(if (busy) "生成中…" else "保存", modifier = Modifier.padding(start = 6.dp))
                }
                if (busy) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Text("取消", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Text("分享", modifier = Modifier.padding(start = 6.dp))
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
        clipData = ClipData.newUri(context.contentResolver, "Lyrics Card PNG", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享歌词卡片"))
}

private fun defaultFileName(title: String): String {
    val safe = title.ifBlank { "lyrics-card" }.replace(INVALID_FILE_CHARS, "-").take(48)
    val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "$safe-$date.png"
}

private fun ensurePng(value: String): String {
    val clean = value.ifBlank { "lyrics-card.png" }.replace(INVALID_FILE_CHARS, "-")
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
