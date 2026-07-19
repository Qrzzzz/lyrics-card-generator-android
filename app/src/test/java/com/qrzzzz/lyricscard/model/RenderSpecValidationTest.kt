package com.qrzzzz.lyricscard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderSpecValidationTest {
    @Test
    fun `default and sample specs are valid`() {
        assertTrue(RenderSpec().validate().isEmpty())
        assertTrue(ProjectTemplates.sample(id = "sample", now = 0L).spec.validate().isEmpty())
    }

    @Test
    fun `validation reports all invalid paths`() {
        val invalid = RenderSpec(
            schemaVersion = 2,
            canvas = CanvasSpec(
                layoutMode = LayoutMode.LANDSCAPE,
                ratio = CanvasRatio.PORTRAIT_4_5,
                width = 400,
                height = 4_000,
                autoHeight = true,
                pixelRatio = 3,
            ),
            typography = TypographySpec(
                lyricSize = 12,
                lineHeight = Double.NaN,
                translationScale = 1.4,
                textColorMode = TextColorMode.CUSTOM,
                customTextColor = "red",
            ),
            visual = VisualSpec(
                palette = PaletteSpec(dominant = "#12", secondary = "blue"),
                gridOpacity = 2.0,
            ),
            media = MediaSpec(coverCropScale = 0.5),
        )

        val paths = invalid.validate().mapTo(mutableSetOf()) { it.path }

        assertTrue("schemaVersion" in paths)
        assertTrue("canvas.width" in paths)
        assertTrue("canvas.height" in paths)
        assertTrue("canvas.pixelRatio" in paths)
        assertTrue("canvas.ratio" in paths)
        assertTrue("canvas.autoHeight" in paths)
        assertTrue("typography.lyricSize" in paths)
        assertTrue("typography.lineHeight" in paths)
        assertTrue("typography.translationScale" in paths)
        assertTrue("typography.customTextColor" in paths)
        assertTrue("visual.palette.dominant" in paths)
        assertTrue("visual.gridOpacity" in paths)
        assertTrue("media.coverCropScale" in paths)
    }

    @Test(expected = InvalidRenderSpecException::class)
    fun `codec refuses invalid specs before persistence`() {
        RenderSpecJson.encode(RenderSpec(rendererVersion = ""))
    }

    @Test
    fun `renderer version must match the bundled contract`() {
        val paths = RenderSpec(rendererVersion = "some-other-renderer").validate().map { it.path }
        assertTrue("rendererVersion" in paths)
    }

    @Test
    fun `preset ratio requires its canonical dimensions`() {
        val spec = RenderSpec(
            canvas = CanvasSpec(
                ratio = CanvasRatio.PORTRAIT_4_5,
                width = 1080,
                height = 1080,
                autoHeight = false,
            ),
        )

        val violation = spec.validate().single { it.path == "canvas" }

        assertEquals("preset PORTRAIT_4_5 must use 1080x1350", violation.message)
    }

    @Test
    fun `physical line counting handles CRLF lone separators and trailing newlines`() {
        assertEquals(1, LyricTextLimits.countPhysicalLines(""))
        assertEquals(1, LyricTextLimits.countPhysicalLines("one long line"))
        assertEquals(2, LyricTextLimits.countPhysicalLines("line\n"))
        assertEquals(4, LyricTextLimits.countPhysicalLines("a\r\nb\rc\n"))
    }

    @Test
    fun `lyrics accept within and exactly at line limit but reject one extra line`() {
        val withinLimit = List(LyricTextLimits.MAX_LINES - 1) { "line-$it" }.joinToString("\n")
        val exactlyAtLimit = List(LyricTextLimits.MAX_LINES) { "line-$it" }.joinToString("\r\n")
        val overLimit = "$exactlyAtLimit\nextra"

        assertTrue(RenderSpec(content = ContentSpec(lyrics = withinLimit)).validate().isEmpty())
        assertTrue(RenderSpec(content = ContentSpec(lyrics = exactlyAtLimit)).validate().isEmpty())

        val violation = RenderSpec(content = ContentSpec(lyrics = overLimit))
            .validate()
            .single { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }
        assertEquals("content.lyrics", violation.path)
        assertEquals(LyricTextLimits.MAX_LINES, violation.limit)
        assertEquals(LyricTextLimits.MAX_LINES + 1, violation.actual)
    }

    @Test
    fun `trailing newline counts as a physical line at the boundary`() {
        val exactlyAtLimit = "line\n".repeat(LyricTextLimits.MAX_LINES - 1)
        val overLimit = "$exactlyAtLimit\n"

        assertEquals(LyricTextLimits.MAX_LINES, LyricTextLimits.countPhysicalLines(exactlyAtLimit))
        assertTrue(RenderSpec(content = ContentSpec(lyrics = exactlyAtLimit)).validate().isEmpty())
        assertEquals(
            LyricTextLimits.MAX_LINES + 1,
            RenderSpec(content = ContentSpec(lyrics = overLimit))
                .validate()
                .single { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }
                .actual,
        )
    }

    @Test
    fun `newline bomb is rejected without misclassifying a maximum length single line`() {
        val newlineBomb = "\n".repeat(200_000)
        val longSingleLine = "a".repeat(LyricTextLimits.MAX_CHARACTERS)

        val violation = RenderSpec(content = ContentSpec(lyrics = newlineBomb))
            .validate()
            .single { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }
        assertEquals(200_001, violation.actual)
        assertTrue(RenderSpec(content = ContentSpec(lyrics = longSingleLine)).validate().isEmpty())
    }

    @Test
    fun `translation uses the same physical line limit`() {
        val overLimit = List(LyricTextLimits.MAX_LINES + 1) { "translation-$it" }.joinToString("\n")

        val violation = RenderSpec(content = ContentSpec(translation = overLimit))
            .validate()
            .single { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }

        assertEquals("content.translation", violation.path)
        assertEquals(LyricTextLimits.MAX_LINES, violation.limit)
        assertEquals(LyricTextLimits.MAX_LINES + 1, violation.actual)
    }
}
