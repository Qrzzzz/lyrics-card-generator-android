package com.qrzzzz.lyricscard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand colors for the native Android chrome. Renderer colors remain owned by RenderSpec.
 *
 * Every public ColorScheme role in Material 3 1.3.1 is assigned explicitly so a library
 * default cannot silently introduce an unrelated purple or surface color.
 */
internal val LyricsCardLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF6656C8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DFFF),
    onPrimaryContainer = Color(0xFF21134F),
    inversePrimary = Color(0xFFC9BEFF),
    secondary = Color(0xFF914A36),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD0),
    onSecondaryContainer = Color(0xFF351000),
    tertiary = Color(0xFF376286),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE5FF),
    onTertiaryContainer = Color(0xFF001D32),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF201E1B),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF201E1B),
    surfaceVariant = Color(0xFFEAE3D9),
    onSurfaceVariant = Color(0xFF4C4741),
    surfaceTint = Color(0xFF6656C8),
    inverseSurface = Color(0xFF35322E),
    inverseOnSurface = Color(0xFFF9EFE5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7E776F),
    outlineVariant = Color(0xFFCFC6BC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFBF4),
    surfaceDim = Color(0xFFE0D8CF),
    surfaceContainer = Color(0xFFF3ECE4),
    surfaceContainerHigh = Color(0xFFEDE6DE),
    surfaceContainerHighest = Color(0xFFE7E0D8),
    surfaceContainerLow = Color(0xFFF9F2EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

internal val LyricsCardDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFC9BEFF),
    onPrimary = Color(0xFF33256C),
    primaryContainer = Color(0xFF4B3D8F),
    onPrimaryContainer = Color(0xFFE7DFFF),
    inversePrimary = Color(0xFF6656C8),
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF561F10),
    secondaryContainer = Color(0xFF733522),
    onSecondaryContainer = Color(0xFFFFDAD0),
    tertiary = Color(0xFFA1CCF3),
    onTertiary = Color(0xFF003353),
    tertiaryContainer = Color(0xFF1D4A6C),
    onTertiaryContainer = Color(0xFFCDE5FF),
    background = Color(0xFF171614),
    onBackground = Color(0xFFEAE3D9),
    surface = Color(0xFF171614),
    onSurface = Color(0xFFEAE3D9),
    surfaceVariant = Color(0xFF4C4741),
    onSurfaceVariant = Color(0xFFCFC6BC),
    surfaceTint = Color(0xFFC9BEFF),
    inverseSurface = Color(0xFFEAE3D9),
    inverseOnSurface = Color(0xFF35322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF989087),
    outlineVariant = Color(0xFF4C4741),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3E3A36),
    surfaceDim = Color(0xFF171614),
    surfaceContainer = Color(0xFF24221F),
    surfaceContainerHigh = Color(0xFF2E2C28),
    surfaceContainerHighest = Color(0xFF393733),
    surfaceContainerLow = Color(0xFF201E1B),
    surfaceContainerLowest = Color(0xFF11100F),
)
