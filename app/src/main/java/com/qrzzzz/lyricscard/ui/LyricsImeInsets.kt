package com.qrzzzz.lyricscard.ui

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal data class LyricsImeInsets(
    val composeBottomPx: Int,
    val platformBottomPx: Int,
) {
    val effectiveBottomPx: Int = if (
        Build.VERSION.SDK_INT == Build.VERSION_CODES.O && composeBottomPx == 0
    ) {
        platformBottomPx
    } else {
        composeBottomPx
    }
    val isVisible: Boolean = effectiveBottomPx > 0
}

/**
 * Compose's IME inset can remain zero on API 26 even when the platform reports a visible IME.
 * Observe layout changes without replacing the inset listener owned by AndroidComposeView, then
 * use the platform value only as an API 26 fallback for the same Type.ime() inset.
 */
@Composable
internal fun rememberLyricsImeInsets(): LyricsImeInsets {
    val view = LocalView.current
    val density = LocalDensity.current
    val composeBottomPx = WindowInsets.ime.getBottom(density)
    var platformBottomPx by remember(view) { mutableIntStateOf(currentPlatformImeBottom(view)) }

    DisposableEffect(view) {
        var active = true
        fun refreshPlatformInset() {
            if (active) {
                platformBottomPx = currentPlatformImeBottom(view)
            }
        }

        val observer = ViewTreeObserver.OnGlobalLayoutListener(::refreshPlatformInset)
        val initialViewTreeObserver = view.viewTreeObserver
        initialViewTreeObserver.addOnGlobalLayoutListener(observer)
        view.post(::refreshPlatformInset)

        onDispose {
            active = false
            val currentViewTreeObserver = view.viewTreeObserver
            when {
                currentViewTreeObserver.isAlive -> currentViewTreeObserver.removeOnGlobalLayoutListener(observer)
                initialViewTreeObserver.isAlive -> initialViewTreeObserver.removeOnGlobalLayoutListener(observer)
            }
        }
    }

    return remember(composeBottomPx, platformBottomPx) {
        LyricsImeInsets(
            composeBottomPx = composeBottomPx,
            platformBottomPx = platformBottomPx,
        )
    }
}

internal fun currentPlatformImeBottom(view: View): Int {
    val insets = ViewCompat.getRootWindowInsets(view) ?: return 0
    return if (insets.isVisible(WindowInsetsCompat.Type.ime())) {
        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    } else {
        0
    }
}
