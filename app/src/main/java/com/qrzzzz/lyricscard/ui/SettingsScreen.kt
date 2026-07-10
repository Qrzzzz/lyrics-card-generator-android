package com.qrzzzz.lyricscard.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.BuildConfig
import com.qrzzzz.lyricscard.data.UserPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onBack: () -> Unit,
    onDarkMode: (Boolean) -> Unit,
    onDefaultExportScale: (Int) -> Unit,
    onShowSafeArea: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheStatus by remember { mutableStateOf<String?>(null) }
    val webViewPackage = remember {
        WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } ?: "不可用"
    }
    val rendererManifest = remember {
        runCatching {
            context.assets.open("renderer/renderer-manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrElse { "renderer manifest 尚未生成" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置与诊断", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionHeader("常规") }
            item {
                SettingRow("深色模式", "只影响原生编辑界面", preferences.darkMode, onDarkMode)
            }
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("默认导出质量", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 2).forEach { value ->
                                FilterChip(
                                    selected = preferences.defaultExportScale == value,
                                    onClick = { onDefaultExportScale(value) },
                                    label = { Text(if (value == 1) "1× 标准" else "2× 高清") },
                                )
                            }
                        }
                    }
                }
            }
            item {
                SettingRow("预览安全区域", "为后续裁切提示保留", preferences.showSafeArea, onShowSafeArea)
            }

            item { SectionHeader("本地存储") }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val bytes = withContext(Dispatchers.IO) {
                                val dir = File(context.cacheDir, "exports")
                                val size = dir.walkTopDown().filter(File::isFile).sumOf(File::length)
                                dir.deleteRecursively()
                                size
                            }
                            cacheStatus = "已清理 ${"%.1f".format(bytes / 1024.0 / 1024.0)} MB 导出缓存"
                        }
                    },
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                    Text("清理导出缓存", modifier = Modifier.padding(start = 8.dp))
                }
            }
            cacheStatus?.let { value -> item { Text(value, color = MaterialTheme.colorScheme.primary) } }

            item { SectionHeader("诊断信息") }
            item {
                DiagnosticCard(
                    rows = listOf(
                        "应用版本" to BuildConfig.VERSION_NAME,
                        "Renderer" to BuildConfig.RENDERER_VERSION,
                        "RenderSpec Schema" to BuildConfig.RENDERER_SCHEMA_VERSION.toString(),
                        "Windows 基准" to BuildConfig.BASELINE_COMMIT.take(12),
                        "System WebView" to webViewPackage,
                    ),
                )
            }
            item {
                Text("Renderer manifest", style = MaterialTheme.typography.labelLarge)
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        rendererManifest,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            item {
                Text(
                    "诊断信息不包含歌词正文、封面数据或导出图片。Alpha 默认不声明 INTERNET 权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(value: String) {
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun DiagnosticCard(rows: List<Pair<String, String>>) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

