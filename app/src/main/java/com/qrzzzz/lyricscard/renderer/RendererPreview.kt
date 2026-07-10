package com.qrzzzz.lyricscard.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qrzzzz.lyricscard.model.RenderSpec
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun RendererPreview(
    spec: RenderSpec,
    assetStore: ProjectAssetStore,
    modifier: Modifier = Modifier,
    onController: (RendererController) -> Unit = {},
    onMeasuredHeight: (Int) -> Unit = {},
    showSafeArea: Boolean = false,
) {
    val context = LocalContext.current
    val controller = remember(assetStore) { RendererController(context, assetStore) }
    val status by controller.status.collectAsState()
    val generation by controller.generation.collectAsState()
    val previewKey = if (spec.canvas.autoHeight) {
        spec.copy(canvas = spec.canvas.copy(height = 0, pixelRatio = 1))
    } else {
        spec
    }

    LaunchedEffect(controller) { onController(controller) }
    LaunchedEffect(controller, previewKey) {
        delay(120)
        if (spec.canvas.autoHeight) {
            runCatching { controller.measure(spec) }
                .onSuccess { measured ->
                    controller.updateSpec(spec.copy(canvas = spec.canvas.copy(height = measured.height)))
                    if (abs(measured.height - spec.canvas.height) > 1) onMeasuredHeight(measured.height)
                }
        } else {
            controller.updateSpec(spec)
        }
    }
    DisposableEffect(controller) {
        onDispose(controller::close)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        key(generation) {
            AndroidView(
                factory = controller::createWebView,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showSafeArea && status.phase != RendererStatus.Phase.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                        shape = RoundedCornerShape(14.dp),
                    ),
            )
        }

        when (status.phase) {
            RendererStatus.Phase.STARTING -> PreviewStatus(
                message = status.message,
                showProgress = true,
            )
            RendererStatus.Phase.ERROR -> PreviewStatus(
                message = status.message,
                retry = controller::retry,
            )
            else -> Unit
        }
    }
}

@Composable
private fun PreviewStatus(
    message: String,
    showProgress: Boolean = false,
    retry: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showProgress) CircularProgressIndicator()
            Text(message, style = MaterialTheme.typography.bodyMedium)
            retry?.let {
                FilledTonalButton(onClick = it) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Text("重试", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
