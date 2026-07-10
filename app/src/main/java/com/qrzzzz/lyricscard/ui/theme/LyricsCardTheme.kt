package com.qrzzzz.lyricscard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF201E1B)
private val Paper = Color(0xFFF7F3EC)
private val PaperElevated = Color(0xFFFFFBF4)
private val Violet = Color(0xFF6656C8)
private val Rust = Color(0xFFB85B3F)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DFFF),
    onPrimaryContainer = Color(0xFF21134F),
    secondary = Rust,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = PaperElevated,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE3D9),
    onSurfaceVariant = Color(0xFF4C4741),
    outline = Color(0xFF7E776F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8B8FF),
    onPrimary = Color(0xFF352274),
    secondary = Color(0xFFFFB59F),
    background = Color(0xFF171614),
    onBackground = Color(0xFFEAE3D9),
    surface = Color(0xFF211F1C),
    onSurface = Color(0xFFF4ECE2),
)

@Composable
fun LyricsCardTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

