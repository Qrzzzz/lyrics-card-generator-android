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
}
