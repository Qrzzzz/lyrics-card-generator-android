package com.qrzzzz.lyricscard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.TextUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContractTest {
    @Test
    fun `light and dark schemes explicitly provide every Material 3 role`() {
        assertCompleteScheme("light", LyricsCardLightColorScheme)
        assertCompleteScheme("dark", LyricsCardDarkColorScheme)

        assertNotEquals(LyricsCardLightColorScheme.background, LyricsCardDarkColorScheme.background)
        assertTrue(LyricsCardLightColorScheme.background.luminance() > LyricsCardDarkColorScheme.background.luminance())
    }

    @Test
    fun `essential content pairs meet WCAG normal-text contrast`() {
        val schemes = listOf(LyricsCardLightColorScheme, LyricsCardDarkColorScheme)
        schemes.forEach { colors ->
            listOf(
                "primary" to (colors.onPrimary to colors.primary),
                "primaryContainer" to (colors.onPrimaryContainer to colors.primaryContainer),
                "secondary" to (colors.onSecondary to colors.secondary),
                "secondaryContainer" to (colors.onSecondaryContainer to colors.secondaryContainer),
                "tertiary" to (colors.onTertiary to colors.tertiary),
                "tertiaryContainer" to (colors.onTertiaryContainer to colors.tertiaryContainer),
                "surface" to (colors.onSurface to colors.surface),
                "surfaceVariant" to (colors.onSurfaceVariant to colors.surfaceVariant),
                "error" to (colors.onError to colors.error),
                "errorContainer" to (colors.onErrorContainer to colors.errorContainer),
                "inverseSurface" to (colors.inverseOnSurface to colors.inverseSurface),
            ).forEach { (name, pair) ->
                assertTrue("$name contrast was ${contrast(pair.first, pair.second)}", contrast(pair.first, pair.second) >= 4.5f)
            }
        }
    }

    @Test
    fun `typography defines all fifteen Material roles with usable line heights`() {
        val styles = listOf(
            LyricsCardTypography.displayLarge,
            LyricsCardTypography.displayMedium,
            LyricsCardTypography.displaySmall,
            LyricsCardTypography.headlineLarge,
            LyricsCardTypography.headlineMedium,
            LyricsCardTypography.headlineSmall,
            LyricsCardTypography.titleLarge,
            LyricsCardTypography.titleMedium,
            LyricsCardTypography.titleSmall,
            LyricsCardTypography.bodyLarge,
            LyricsCardTypography.bodyMedium,
            LyricsCardTypography.bodySmall,
            LyricsCardTypography.labelLarge,
            LyricsCardTypography.labelMedium,
            LyricsCardTypography.labelSmall,
        )

        assertEquals(15, styles.size)
        styles.forEach { style ->
            assertNotEquals(TextUnit.Unspecified, style.fontSize)
            assertNotEquals(TextUnit.Unspecified, style.lineHeight)
            assertTrue(style.lineHeight.value >= style.fontSize.value)
        }
    }

    @Test
    fun `shape and spacing scales stay small ordered and reusable`() {
        assertNotEquals(LyricsCardShapes.extraSmall, LyricsCardShapes.extraLarge)
        val spacing = listOf(
            LyricsCardSpacing.extraSmall,
            LyricsCardSpacing.small,
            LyricsCardSpacing.medium,
            LyricsCardSpacing.large,
            LyricsCardSpacing.comfortable,
            LyricsCardSpacing.extraLarge,
            LyricsCardSpacing.section,
        )
        assertEquals(spacing.sortedBy { it.value }, spacing)
        assertEquals(840f, LyricsCardLayout.wideBreakpoint.value)
        assertEquals(420f, LyricsCardLayout.propertiesPaneWidth.value)
    }

    private fun assertCompleteScheme(name: String, colors: androidx.compose.material3.ColorScheme) {
        val roles = listOf(
            "primary" to colors.primary,
            "onPrimary" to colors.onPrimary,
            "primaryContainer" to colors.primaryContainer,
            "onPrimaryContainer" to colors.onPrimaryContainer,
            "inversePrimary" to colors.inversePrimary,
            "secondary" to colors.secondary,
            "onSecondary" to colors.onSecondary,
            "secondaryContainer" to colors.secondaryContainer,
            "onSecondaryContainer" to colors.onSecondaryContainer,
            "tertiary" to colors.tertiary,
            "onTertiary" to colors.onTertiary,
            "tertiaryContainer" to colors.tertiaryContainer,
            "onTertiaryContainer" to colors.onTertiaryContainer,
            "background" to colors.background,
            "onBackground" to colors.onBackground,
            "surface" to colors.surface,
            "onSurface" to colors.onSurface,
            "surfaceVariant" to colors.surfaceVariant,
            "onSurfaceVariant" to colors.onSurfaceVariant,
            "surfaceTint" to colors.surfaceTint,
            "inverseSurface" to colors.inverseSurface,
            "inverseOnSurface" to colors.inverseOnSurface,
            "error" to colors.error,
            "onError" to colors.onError,
            "errorContainer" to colors.errorContainer,
            "onErrorContainer" to colors.onErrorContainer,
            "outline" to colors.outline,
            "outlineVariant" to colors.outlineVariant,
            "scrim" to colors.scrim,
            "surfaceBright" to colors.surfaceBright,
            "surfaceDim" to colors.surfaceDim,
            "surfaceContainer" to colors.surfaceContainer,
            "surfaceContainerHigh" to colors.surfaceContainerHigh,
            "surfaceContainerHighest" to colors.surfaceContainerHighest,
            "surfaceContainerLow" to colors.surfaceContainerLow,
            "surfaceContainerLowest" to colors.surfaceContainerLowest,
        )

        assertEquals(36, roles.size)
        roles.forEach { (role, color) ->
            assertNotEquals("$name.$role", Color.Unspecified, color)
            assertTrue("$name.$role must be opaque", color.alpha == 1f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
