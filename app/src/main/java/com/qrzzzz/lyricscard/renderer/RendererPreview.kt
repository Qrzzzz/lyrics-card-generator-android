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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qrzzzz.lyricscard.model.RenderSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun RendererPreview(
    spec: RenderSpec,
    controller: RendererController,
    modifier: Modifier = Modifier,
    onController: (RendererController) -> Unit = {},
    onMeasuredHeight: (Int) -> Unit = {},
    showSafeArea: Boolean = false,
) {
    val status by controller.status.collectAsState()
    val generation by controller.generation.collectAsState()
    val previewKey = if (spec.canvas.autoHeight) {
        spec.copy(canvas = spec.canvas.copy(height = 0, pixelRatio = 1))
    } else {
        spec
    }

    LaunchedEffect(controller) { onController(controller) }
    LaunchedEffect(controller, spec) {
        controller.updateSpec(spec)
    }
    LaunchedEffect(controller, previewKey) {
        if (!spec.canvas.autoHeight) return@LaunchedEffect
        delay(AUTO_HEIGHT_MEASURE_DEBOUNCE_MS)
        try {
            val measured = controller.measure(spec)
            controller.updateSpec(spec.copy(canvas = spec.canvas.copy(height = measured.height)))
            if (abs(measured.height - spec.canvas.height) > 1) onMeasuredHeight(measured.height)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
            // The controller exposes renderer failures through its status overlay.
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        key(generation) {
            val owner = remember { Any() }
            AndroidView(
                factory = { context -> controller.acquireWebView(context, owner) },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> controller.releaseWebView(owner, view) },
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

private const val AUTO_HEIGHT_MEASURE_DEBOUNCE_MS = 220L

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
