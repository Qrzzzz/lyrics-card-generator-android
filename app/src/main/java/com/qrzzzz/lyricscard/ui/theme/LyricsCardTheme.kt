package com.qrzzzz.lyricscard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun LyricsCardTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LyricsCardDarkColorScheme else LyricsCardLightColorScheme,
        typography = LyricsCardTypography,
        shapes = LyricsCardShapes,
        content = content,
    )
}
