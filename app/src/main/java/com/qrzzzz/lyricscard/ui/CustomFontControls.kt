package com.qrzzzz.lyricscard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.renderer.CustomFontStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CustomFontControls(spec: RenderSpec, onSpecChange: (RenderSpec) -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(spec)
    val change by rememberUpdatedState(onSpecChange)
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val asset = withContext(Dispatchers.IO) { CustomFontStore.import(context, uri) }
                change(current.copy(typography = current.typography.copy(customFontAsset = asset, customFontEnabled = true)))
                message = "字体已导入"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
            } catch (error: Exception) { message = error.message ?: "字体导入失败"
            } finally { importing = false }
        }
    }
    Button(onClick = { picker.launch(arrayOf("font/*", "application/octet-stream")) }, enabled = !importing) { Text(if (importing) "正在导入…" else "导入自定义字体") }
    if (spec.typography.customFontAsset != null) SettingSwitch("使用导入字体", spec.typography.customFontEnabled) {
        onSpecChange(spec.copy(typography = spec.typography.copy(customFontEnabled = it)))
    }
    OutlinedTextField(value = spec.typography.latinFontFamily, onValueChange = {
        if (it.isNotBlank() && it.length <= 200) onSpecChange(spec.copy(typography = spec.typography.copy(latinFontFamily = it)))
    }, label = { Text("西文字体（本机可用字体名称）") }, singleLine = true)
    LabeledSlider("字重", spec.typography.fontWeight.toFloat(), 100f..900f, "${spec.typography.fontWeight}") {
        onSpecChange(spec.copy(typography = spec.typography.copy(fontWeight = it.toInt())))
    }
    SettingSwitch("斜体", spec.typography.fontItalic) { onSpecChange(spec.copy(typography = spec.typography.copy(fontItalic = it))) }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
