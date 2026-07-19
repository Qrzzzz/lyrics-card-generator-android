package com.qrzzzz.lyricscard.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qrzzzz.lyricscard.model.BackgroundMode
import com.qrzzzz.lyricscard.model.CanvasRatio
import com.qrzzzz.lyricscard.model.ContentMode
import com.qrzzzz.lyricscard.model.FontScheme
import com.qrzzzz.lyricscard.model.GridDensity
import com.qrzzzz.lyricscard.model.LayoutMode
import com.qrzzzz.lyricscard.model.LyricTextCleaner
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.SongSource
import com.qrzzzz.lyricscard.model.TextAlignment
import com.qrzzzz.lyricscard.model.TextColorMode
import com.qrzzzz.lyricscard.model.TextColorPreset
import com.qrzzzz.lyricscard.renderer.RendererController
import com.qrzzzz.lyricscard.renderer.RendererPreview
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class EditorStep(val label: String, val description: String) {
    CHOOSE_SONG("选择歌曲", "搜索、链接解析或手动填写歌曲信息"),
    LYRICS("歌词", "整理原文、译文与纯音乐内容"),
    LAYOUT("布局", "设置方向、比例与卡片元素"),
    FONT("字体方案", "调整字体、字号、行高与对齐"),
    VISUAL("视觉", "设置配色、背景、网格与品牌信息"),
    EXPORT("导出", "确认卡片内容并进入 PNG 输出"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    isSaving: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    showSafeArea: Boolean,
    renderer: RendererController,
    netease: NeteaseLookupUiState,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onMeasuredHeight: (Int) -> Unit,
    onPaletteExtracted: (PaletteSpec) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectCover: (Uri) -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    onExport: () -> Unit,
) {
    var selectedStep by remember(project.id) { mutableIntStateOf(0) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onSelectCover)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(project.name, maxLines = 1, fontWeight = FontWeight.Bold)
                        Text(
                            "${selectedStep + 1}/${EditorStep.entries.size} · ${if (isSaving) "正在自动保存…" else "已自动保存"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销")
                    }
                    IconButton(onClick = onRedo, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "重做")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = snackbarHost,
        bottomBar = {
            Surface(shadowElevation = 10.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { selectedStep = (selectedStep - 1).coerceAtLeast(0) },
                        enabled = selectedStep > 0,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        Text("上一步", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = {
                            if (selectedStep == EditorStep.entries.lastIndex) onExport()
                            else selectedStep += 1
                        },
                        modifier = Modifier.weight(1.5f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Icon(
                            if (selectedStep == EditorStep.entries.lastIndex) Icons.Rounded.FileUpload else Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = null,
                        )
                        Text(
                            if (selectedStep == EditorStep.entries.lastIndex) "导出 PNG" else "下一步",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 840.dp
            val showPreview = selectedStep >= EditorStep.LAYOUT.ordinal
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showPreview) {
                        RendererPreview(
                            spec = project.spec,
                            controller = renderer,
                            onMeasuredHeight = onMeasuredHeight,
                            showSafeArea = showSafeArea,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    EditorProperties(
                        project = project,
                        selectedStep = selectedStep,
                        onSelectedStep = { selectedStep = it },
                        netease = netease,
                        onProjectNameChange = onProjectNameChange,
                        onSpecChange = onSpecChange,
                        onPickCover = { coverPicker.launch("image/*") },
                        onRemoveCover = onRemoveCover,
                        renderer = renderer,
                        onPaletteExtracted = onPaletteExtracted,
                        onSearchNetease = onSearchNetease,
                        onResolveNeteaseSong = onResolveNeteaseSong,
                        onResolveNeteaseLink = onResolveNeteaseLink,
                        modifier = if (showPreview) Modifier.width(420.dp) else Modifier.fillMaxSize(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showPreview) {
                        RendererPreview(
                            spec = project.spec,
                            controller = renderer,
                            onMeasuredHeight = onMeasuredHeight,
                            showSafeArea = showSafeArea,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.42f),
                        )
                    }
                    EditorProperties(
                        project = project,
                        selectedStep = selectedStep,
                        onSelectedStep = { selectedStep = it },
                        netease = netease,
                        onProjectNameChange = onProjectNameChange,
                        onSpecChange = onSpecChange,
                        onPickCover = { coverPicker.launch("image/*") },
                        onRemoveCover = onRemoveCover,
                        renderer = renderer,
                        onPaletteExtracted = onPaletteExtracted,
                        onSearchNetease = onSearchNetease,
                        onResolveNeteaseSong = onResolveNeteaseSong,
                        onResolveNeteaseLink = onResolveNeteaseLink,
                        modifier = if (showPreview) {
                            Modifier
                                .fillMaxWidth()
                                .weight(0.58f)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorProperties(
    project: Project,
    selectedStep: Int,
    onSelectedStep: (Int) -> Unit,
    netease: NeteaseLookupUiState,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    renderer: RendererController,
    onPaletteExtracted: (PaletteSpec) -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), tonalElevation = 2.dp) {
        Column {
            ScrollableTabRow(
                selectedTabIndex = selectedStep,
                edgePadding = 8.dp,
                divider = {},
            ) {
                EditorStep.entries.forEachIndexed { index, step ->
                    Tab(
                        selected = index == selectedStep,
                        onClick = { onSelectedStep(index) },
                        text = {
                            Text(
                                "${index + 1}. ${step.label}",
                                fontWeight = if (index == selectedStep) FontWeight.Bold else null,
                            )
                        },
                    )
                }
            }
            Text(
                EditorStep.entries[selectedStep].description,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    when (EditorStep.entries[selectedStep]) {
                        EditorStep.CHOOSE_SONG -> ChooseSongPanel(
                            project = project,
                            netease = netease,
                            onProjectNameChange = onProjectNameChange,
                            onSpecChange = onSpecChange,
                            onPickCover = onPickCover,
                            onRemoveCover = onRemoveCover,
                            onSearchNetease = onSearchNetease,
                            onResolveNeteaseSong = onResolveNeteaseSong,
                            onResolveNeteaseLink = onResolveNeteaseLink,
                        )
                        EditorStep.LYRICS -> LyricsPanel(project.spec, onSpecChange)
                        EditorStep.LAYOUT -> LayoutPanel(project.spec, onSpecChange)
                        EditorStep.FONT -> TypographyPanel(project.spec, onSpecChange)
                        EditorStep.VISUAL -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            StylePanel(project.spec, renderer, onSpecChange, onPaletteExtracted)
                            BrandingPanel(project.spec, onSpecChange)
                        }
                        EditorStep.EXPORT -> ExportStepPanel(project)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooseSongPanel(
    project: Project,
    netease: NeteaseLookupUiState,
    onProjectNameChange: (String) -> Unit,
    onSpecChange: (RenderSpec) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onSearchNetease: (String) -> Unit,
    onResolveNeteaseSong: (String) -> Unit,
    onResolveNeteaseLink: (String) -> Unit,
) {
    val spec = project.spec
    var projectNameDraft by remember(project.id) { mutableStateOf(project.name) }
    var searchQuery by remember(project.id) { mutableStateOf("") }
    var linkInput by remember(project.id) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val lookupBusy = netease.isSearching || netease.isResolving
    PanelColumn {
        SectionTitle("网易云选歌")
        Text(
            "与 Web 版一致，可先按歌名搜索；也可直接贴入网易云分享文本或链接。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("歌曲名或歌手") },
            placeholder = { Text("例如：晴天 周杰伦") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (netease.isSearching) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
            singleLine = true,
            enabled = !netease.isResolving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchNetease(searchQuery) }),
        )
        Button(
            onClick = { onSearchNetease(searchQuery) },
            enabled = searchQuery.isNotBlank() && !lookupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null)
            Text("搜索网易云歌曲", modifier = Modifier.padding(start = 8.dp))
        }
        netease.results.forEach { result ->
            OutlinedButton(
                onClick = { onResolveNeteaseSong(result.id) },
                enabled = !lookupBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        listOf(result.title, result.artist).filter(String::isNotBlank).joinToString(" · "),
                        fontWeight = FontWeight.Bold,
                    )
                    if (result.album.isNotBlank()) {
                        Text(
                            result.album,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = linkInput,
            onValueChange = { linkInput = it.take(8_192) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("网易云分享文本或链接") },
            placeholder = { Text("https://music.163.com/song?id=…") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            minLines = 2,
            maxLines = 4,
            enabled = !lookupBusy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { clipboard.getText()?.text?.let { linkInput = it.take(8_192) } },
                enabled = !lookupBusy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                Text("贴入", modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = { onResolveNeteaseLink(linkInput) },
                enabled = linkInput.isNotBlank() && !lookupBusy,
                modifier = Modifier.weight(1f),
            ) {
                if (netease.isResolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Text("解析", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text(
            netease.message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("手动补充")
        OutlinedTextField(
            value = projectNameDraft,
            onValueChange = { value ->
                projectNameDraft = value.take(120)
                if (projectNameDraft.isNotBlank()) onProjectNameChange(projectNameDraft)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("项目名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = spec.song.title,
            onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(title = it.take(240)))) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("歌曲标题") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = spec.song.artist,
                onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(artist = it.take(240)))) },
                modifier = Modifier.weight(1f),
                label = { Text("艺术家") },
                singleLine = true,
            )
            OutlinedTextField(
                value = spec.song.album,
                onValueChange = { onSpecChange(spec.copy(song = spec.song.copy(album = it.take(240)))) },
                modifier = Modifier.weight(1f),
                label = { Text("专辑") },
                singleLine = true,
            )
        }
        Text("来源平台", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.song.source,
            label = ::songSourceLabel,
            onSelect = { source ->
                onSpecChange(spec.copy(song = spec.song.copy(source = source), branding = spec.branding.copy(platform = source)))
            },
        )
        SettingSwitch("Explicit 标记", spec.song.explicit) {
            onSpecChange(spec.copy(song = spec.song.copy(explicit = it)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onPickCover, modifier = Modifier.weight(1f)) {
                Text(if (spec.song.coverAssetId == null) "选择封面" else "替换封面")
            }
            if (spec.song.coverAssetId != null) {
                TextButton(onClick = onRemoveCover) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Text("移除")
                }
            }
        }

    }
}

@Composable
private fun LyricsPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    val instrumental = spec.content.mode == ContentMode.INSTRUMENTAL
    PanelColumn {
        SectionTitle("歌词内容")
        SettingSwitch("纯音乐模式", instrumental) { enabled ->
            onSpecChange(
                if (enabled) {
                    spec.copy(
                        content = spec.content.copy(mode = ContentMode.INSTRUMENTAL, translationEnabled = false),
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
            OutlinedTextField(
                value = spec.content.instrumentalText,
                onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(instrumentalText = it.take(240)))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("纯音乐提示文字") },
            )
        } else {
            OutlinedTextField(
                value = spec.content.lyrics,
                onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(lyrics = it))) },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                label = { Text("原文歌词") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onSpecChange(spec.copy(content = spec.content.copy(lyrics = LyricTextCleaner.removeTimestamps(spec.content.lyrics))))
                }) { Text("清除时间戳") }
                TextButton(onClick = {
                    onSpecChange(spec.copy(content = spec.content.copy(lyrics = LyricTextCleaner.collapseRepeatedBlankLines(spec.content.lyrics))))
                }) { Text("合并空行") }
            }
            SettingSwitch("显示译文", spec.content.translationEnabled) {
                onSpecChange(spec.copy(content = spec.content.copy(translationEnabled = it)))
            }
            if (spec.content.translationEnabled) {
                OutlinedTextField(
                    value = spec.content.translation,
                    onValueChange = { onSpecChange(spec.copy(content = spec.content.copy(translation = it))) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    label = { Text("译文歌词") },
                )
            }
        }
    }
}

@Composable
private fun LayoutPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle("画布")
        if (spec.content.mode == ContentMode.INSTRUMENTAL) {
            Text(
                "纯音乐模式固定使用竖版 1:1 画布。关闭纯音乐模式后可选择其他比例。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChoiceChips(
                values = LayoutMode.entries,
                selected = spec.canvas.layoutMode,
                label = { if (it == LayoutMode.PORTRAIT) "竖版" else "横版" },
                onSelect = { mode ->
                    val canvas = if (mode == LayoutMode.PORTRAIT) {
                        spec.canvas.copy(layoutMode = mode, ratio = CanvasRatio.PORTRAIT_4_5, width = 1080, height = 1350, autoHeight = false)
                    } else {
                        spec.canvas.copy(layoutMode = mode, ratio = CanvasRatio.LANDSCAPE_16_9, width = 1920, height = 1080, autoHeight = false)
                    }
                    onSpecChange(spec.copy(canvas = canvas))
                },
            )
            Text("比例", style = MaterialTheme.typography.labelLarge)
            val ratios = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) {
                listOf(CanvasRatio.SQUARE, CanvasRatio.PORTRAIT_4_5, CanvasRatio.PORTRAIT_9_16, CanvasRatio.CUSTOM)
            } else {
                listOf(CanvasRatio.LANDSCAPE_16_9, CanvasRatio.LANDSCAPE_21_9, CanvasRatio.LANDSCAPE_3_2, CanvasRatio.CUSTOM)
            }
            ChoiceChips(
                values = ratios,
                selected = spec.canvas.ratio,
                label = ::ratioLabel,
                onSelect = { ratio ->
                    val width = ratio.width ?: spec.canvas.width
                    val height = ratio.height ?: spec.canvas.height
                    onSpecChange(spec.copy(canvas = spec.canvas.copy(ratio = ratio, width = width, height = height, autoHeight = false)))
                },
            )
            if (spec.canvas.ratio == CanvasRatio.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val widthRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) 720..1440 else 1080..3000
                    val heightRange = if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) 720..3200 else 720..1600
                    NumberField("宽度", spec.canvas.width, widthRange, Modifier.weight(1f)) { width ->
                        onSpecChange(spec.copy(canvas = spec.canvas.copy(width = width)))
                    }
                    NumberField("高度", spec.canvas.height, heightRange, Modifier.weight(1f)) { height ->
                        onSpecChange(spec.copy(canvas = spec.canvas.copy(height = height)))
                    }
                }
                SettingSwitch(
                    "竖版自动高度",
                    spec.canvas.autoHeight,
                    enabled = spec.canvas.layoutMode == LayoutMode.PORTRAIT,
                ) { onSpecChange(spec.copy(canvas = spec.canvas.copy(autoHeight = it))) }
            }
        }

        SectionTitle("元素")
        SettingSwitch("显示封面", spec.visibility.showCover, enabled = spec.song.coverAssetId != null) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showCover = it)))
        }
        SettingSwitch("显示歌曲信息", spec.visibility.showSongInfo) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSongInfo = it)))
        }
        SettingSwitch("显示专辑", spec.visibility.showAlbum) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showAlbum = it)))
        }
        LabeledSlider("封面裁切缩放", spec.media.coverCropScale.toFloat(), 1f..2f, "%.2f".format(spec.media.coverCropScale)) {
            onSpecChange(spec.copy(media = spec.media.copy(coverCropScale = it.toDouble())))
        }
    }
}

@Composable
private fun StylePanel(
    spec: RenderSpec,
    renderer: RendererController?,
    onSpecChange: (RenderSpec) -> Unit,
    onPaletteExtracted: (PaletteSpec) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var paletteBusy by remember { mutableStateOf(false) }
    var paletteError by remember { mutableStateOf<String?>(null) }
    PanelColumn {
        SectionTitle("背景")
        ChoiceChips(
            values = BackgroundMode.entries,
            selected = spec.visual.backgroundMode,
            label = { if (it == BackgroundMode.PALETTE) "调色板" else "渐变" },
            onSelect = { onSpecChange(spec.copy(visual = spec.visual.copy(backgroundMode = it))) },
        )
        val coverId = spec.song.coverAssetId
        Button(
            onClick = {
                if (coverId != null && renderer != null) {
                    paletteBusy = true
                    paletteError = null
                    scope.launch {
                        runCatching { renderer.extractPalette(coverId) }
                            .onSuccess(onPaletteExtracted)
                            .onFailure { paletteError = it.message ?: "无法提取颜色" }
                        paletteBusy = false
                    }
                }
            },
            enabled = coverId != null && renderer != null && !paletteBusy,
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Text(if (paletteBusy) "正在提取…" else "从封面提取颜色", modifier = Modifier.padding(start = 8.dp))
        }
        paletteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ColorField("主色", spec.visual.palette.dominant) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(dominant = it))))
        }
        ColorField("辅色", spec.visual.palette.secondary) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(secondary = it))))
        }
        ColorField("强调色", spec.visual.palette.accent) {
            onSpecChange(spec.copy(visual = spec.visual.copy(palette = spec.visual.palette.copy(accent = it))))
        }
        SectionTitle("网格")
        SettingSwitch("显示背景网格", spec.visual.gridEnabled) {
            onSpecChange(spec.copy(visual = spec.visual.copy(gridEnabled = it)))
        }
        if (spec.visual.gridEnabled) {
            ChoiceChips(
                values = GridDensity.entries,
                selected = spec.visual.gridDensity,
                label = { when (it) { GridDensity.SPARSE -> "稀疏"; GridDensity.MEDIUM -> "中等"; GridDensity.DENSE -> "密集" } },
                onSelect = { onSpecChange(spec.copy(visual = spec.visual.copy(gridDensity = it))) },
            )
            LabeledSlider("网格透明度", spec.visual.gridOpacity.toFloat(), 0f..0.5f, "${(spec.visual.gridOpacity * 100).roundToInt()}%") {
                onSpecChange(spec.copy(visual = spec.visual.copy(gridOpacity = it.toDouble())))
            }
        }
    }
}

@Composable
private fun TypographyPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle("字体")
        ChoiceChips(
            values = listOf(FontScheme.SANS_HEAVY, FontScheme.SERIF_HEAVY),
            selected = spec.typography.fontScheme,
            label = { if (it == FontScheme.SANS_HEAVY) "思源黑体" else "思源宋体" },
            onSelect = { scheme ->
                val family = if (scheme == FontScheme.SANS_HEAVY) "Source Han Sans SC" else "Source Han Serif SC"
                onSpecChange(spec.copy(typography = spec.typography.copy(fontScheme = scheme, fontFamily = family)))
            },
        )
        LabeledSlider("歌词字号", spec.typography.lyricSize.toFloat(), 36f..72f, "${spec.typography.lyricSize}") {
            onSpecChange(spec.copy(typography = spec.typography.copy(lyricSize = it.roundToInt())))
        }
        LabeledSlider("行高", spec.typography.lineHeight.toFloat(), 1.1f..1.75f, "%.2f".format(spec.typography.lineHeight)) {
            onSpecChange(spec.copy(typography = spec.typography.copy(lineHeight = it.toDouble())))
        }
        LabeledSlider("译文字号比例", spec.typography.translationScale.toFloat(), 0.6f..0.9f, "${(spec.typography.translationScale * 100).roundToInt()}%") {
            onSpecChange(spec.copy(typography = spec.typography.copy(translationScale = it.toDouble())))
        }
        Text("对齐", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(
            values = TextAlignment.entries,
            selected = spec.typography.alignment,
            label = { when (it) { TextAlignment.LEFT -> "左对齐"; TextAlignment.CENTER -> "居中"; TextAlignment.RIGHT -> "右对齐" } },
            onSelect = { onSpecChange(spec.copy(typography = spec.typography.copy(alignment = it))) },
        )
        SettingSwitch("双行标题", spec.typography.twoLineTitle) {
            onSpecChange(spec.copy(typography = spec.typography.copy(twoLineTitle = it)))
        }
        SectionTitle("文字颜色")
        ChoiceChips(
            values = TextColorMode.entries,
            selected = spec.typography.textColorMode,
            label = { when (it) { TextColorMode.AUTO -> "自动"; TextColorMode.PRESET -> "预设"; TextColorMode.CUSTOM -> "自定义" } },
            onSelect = { mode ->
                val custom = if (mode == TextColorMode.CUSTOM) spec.typography.customTextColor ?: "#FFFFFF" else spec.typography.customTextColor
                onSpecChange(spec.copy(typography = spec.typography.copy(textColorMode = mode, customTextColor = custom)))
            },
        )
        when (spec.typography.textColorMode) {
            TextColorMode.PRESET -> ChoiceChips(
                values = listOf(TextColorPreset.WHITE, TextColorPreset.BLACK, TextColorPreset.WARM_WHITE, TextColorPreset.CREAM),
                selected = spec.typography.textColorPreset,
                label = { preset -> preset.name.lowercase().replace('_', ' ') },
                onSelect = { onSpecChange(spec.copy(typography = spec.typography.copy(textColorPreset = it))) },
            )
            TextColorMode.CUSTOM -> ColorField("自定义文字颜色", spec.typography.customTextColor ?: "#FFFFFF") {
                onSpecChange(spec.copy(typography = spec.typography.copy(customTextColor = it)))
            }
            TextColorMode.AUTO -> Unit
        }
    }
}

@Composable
private fun BrandingPanel(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    PanelColumn {
        SectionTitle("平台与署名")
        SettingSwitch("显示平台 Logo", spec.visibility.showPlatformBadge, enabled = spec.branding.platform != SongSource.UNKNOWN) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showPlatformBadge = it)))
        }
        ChoiceChips(
            values = SongSource.entries,
            selected = spec.branding.platform,
            label = ::songSourceLabel,
            onSelect = { onSpecChange(spec.copy(branding = spec.branding.copy(platform = it))) },
        )
        SettingSwitch("显示 Shared by", spec.visibility.showSharedBy) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showSharedBy = it)))
        }
        if (spec.visibility.showSharedBy) {
            OutlinedTextField(
                value = spec.branding.sharedByName,
                onValueChange = { onSpecChange(spec.copy(branding = spec.branding.copy(sharedByName = it.take(240)))) },
                label = { Text("分享者名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        SettingSwitch("显示 Generated watermark", spec.visibility.showGeneratedWatermark) {
            onSpecChange(spec.copy(visibility = spec.visibility.copy(showGeneratedWatermark = it)))
        }
    }
}

@Composable
private fun ExportStepPanel(project: Project) {
    val spec = project.spec
    val songReady = spec.song.title.isNotBlank() || spec.song.artist.isNotBlank()
    val contentReady = spec.content.mode == ContentMode.INSTRUMENTAL || spec.content.lyrics.isNotBlank()
    PanelColumn {
        SectionTitle("导出前确认")
        Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        ReadinessRow("歌曲信息", songReady, if (songReady) "已填写" else "仍可返回第一步补充")
        ReadinessRow("卡片内容", contentReady, if (contentReady) "已准备" else "歌词为空")
        ReadinessRow(
            "画布",
            true,
            "${spec.canvas.width} × ${spec.canvas.height} · ${if (spec.canvas.layoutMode == LayoutMode.PORTRAIT) "竖版" else "横版"}",
        )
        Text(
            "点击下方“导出 PNG”后，可选择标准/高清倍率、文件名，并保存或分享图片。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (ready) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PanelColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(displayValue, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
                leadingIcon = if (selected == value) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    validRange: IntRange,
    modifier: Modifier,
    onValidValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.filter(Char::isDigit).take(4)
            text.toIntOrNull()?.takeIf { it in validRange }?.let(onValidValue)
        },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun ColorField(label: String, value: String, onValidValue: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.take(9)
            if (HEX_COLOR.matches(text)) onValidValue(text.uppercase())
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = RoundedCornerShape(8.dp),
                color = runCatching { cssHexColor(value) }
                    .getOrDefault(MaterialTheme.colorScheme.surfaceVariant),
            ) {}
        },
    )
}

private fun songSourceLabel(source: SongSource) = when (source) {
    SongSource.UNKNOWN -> "未知"
    SongSource.QQ -> "QQ 音乐"
    SongSource.NETEASE -> "网易云"
    SongSource.APPLE -> "Apple Music"
    SongSource.SPOTIFY -> "Spotify"
}

private fun ratioLabel(ratio: CanvasRatio) = when (ratio) {
    CanvasRatio.SQUARE -> "1:1"
    CanvasRatio.PORTRAIT_4_5 -> "4:5"
    CanvasRatio.PORTRAIT_9_16 -> "9:16"
    CanvasRatio.LANDSCAPE_16_9 -> "16:9"
    CanvasRatio.LANDSCAPE_21_9 -> "21:9"
    CanvasRatio.LANDSCAPE_3_2 -> "3:2"
    CanvasRatio.CUSTOM -> "自定义"
}

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$")

private fun cssHexColor(value: String): androidx.compose.ui.graphics.Color {
    val hex = value.removePrefix("#")
    require(hex.length == 6 || hex.length == 8)
    val red = hex.substring(0, 2).toInt(16)
    val green = hex.substring(2, 4).toInt(16)
    val blue = hex.substring(4, 6).toInt(16)
    val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) else 255
    return androidx.compose.ui.graphics.Color(red, green, blue, alpha)
}
