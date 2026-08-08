package com.qrzzzz.lyricscard.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

internal enum class LyricsWindowWidth {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun currentLyricsWindowWidth(
    override: WindowWidthSizeClass? = null,
): LyricsWindowWidth {
    val widthClass = override ?: calculateWindowSizeClass(LocalContext.current.requireActivity()).widthSizeClass
    return when (widthClass) {
        WindowWidthSizeClass.Compact -> LyricsWindowWidth.COMPACT
        WindowWidthSizeClass.Medium -> LyricsWindowWidth.MEDIUM
        else -> LyricsWindowWidth.EXPANDED
    }
}

private tailrec fun Context.requireActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.requireActivity()
    else -> error("Window size class requires an Activity context")
}
