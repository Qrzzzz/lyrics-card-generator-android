package com.qrzzzz.lyricscard.model

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class RenderSpecSerializationTest {
    @Test
    fun `default spec encodes every renderer field and round trips`() {
        val expected = RenderSpec()

        val encoded = RenderSpecJson.encode(expected)
        val root = RenderSpecJson.format.parseToJsonElement(encoded).jsonObject

        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertEquals("android-alpha-renderer-1", root.getValue("rendererVersion").jsonPrimitive.content)
        assertEquals("zh", root.getValue("locale").jsonPrimitive.content)
        assertTrue(root.keys.containsAll(REQUIRED_ROOT_KEYS))
        assertEquals(expected, RenderSpecJson.decode(encoded))
    }

    @Test
    fun `enum values use stable cross platform strings`() {
        val spec = ProjectTemplates.sample(id = "sample", now = 1_000L).spec.copy(
            locale = RenderLocale.ZH_TW,
            song = SongSpec(source = SongSource.NETEASE),
            typography = TypographySpec(
                fontScheme = FontScheme.SERIF_HEAVY,
                alignment = TextAlignment.RIGHT,
                textColorMode = TextColorMode.PRESET,
                textColorPreset = TextColorPreset.WARM_WHITE,
            ),
            visual = VisualSpec(
                backgroundMode = BackgroundMode.GRADIENT,
                gridDensity = GridDensity.DENSE,
            ),
        )

        val encoded = RenderSpecJson.encode(spec)

        assertTrue(encoded.contains("\"locale\":\"zh-TW\""))
        assertTrue(encoded.contains("\"source\":\"netease\""))
        assertTrue(encoded.contains("\"fontScheme\":\"serif-heavy\""))
        assertTrue(encoded.contains("\"alignment\":\"right\""))
        assertTrue(encoded.contains("\"textColorPreset\":\"warmWhite\""))
        assertTrue(encoded.contains("\"backgroundMode\":\"gradient\""))
        assertTrue(encoded.contains("\"gridDensity\":\"dense\""))
        assertEquals(spec, RenderSpecJson.decode(encoded))
    }

    @Test
    fun `missing fields receive v1 defaults and unknown fields are ignored`() {
        val decoded = RenderSpecJson.decode(
            """
            {
              "schemaVersion": 1,
              "song": { "title": "Only a title", "futureSongField": true },
              "futureRootField": { "enabled": true }
            }
            """.trimIndent(),
        )

        assertEquals("Only a title", decoded.song.title)
        assertEquals(SongSource.UNKNOWN, decoded.song.source)
        assertEquals(CanvasRatio.CUSTOM, decoded.canvas.ratio)
        assertEquals(2, decoded.canvas.pixelRatio)
    }

    @Test
    fun `legacy sparse project can be edited saved and reopened with stable defaults`() {
        val legacy = RenderSpecJson.decode("""{"schemaVersion":1,"rendererVersion":"android-alpha-renderer-1","song":{"title":"旧项目","future":42},"future":{"nested":[1,2]}}""")
        val edited = legacy.copy(song = legacy.song.copy(title = "修改后 🎵"))
        val reopened = RenderSpecJson.decode(RenderSpecJson.encode(edited))
        assertEquals(edited, reopened)
        assertEquals("android-alpha-renderer-1", reopened.rendererVersion)
        assertEquals(2, reopened.canvas.pixelRatio)
    }

    @Test
    fun `invalid JSON types and enum values are rejected instead of silently defaulted`() {
        listOf(
            "{broken",
            """{"song":null}""",
            """{"canvas":{"pixelRatio":"invalid"}}""",
            """{"locale":"unsupported-locale"}""",
        ).forEach { payload ->
            assertThrows(SerializationException::class.java) { RenderSpecJson.decode(payload) }
        }
    }

    private companion object {
        val REQUIRED_ROOT_KEYS = setOf(
            "schemaVersion",
            "rendererVersion",
            "locale",
            "song",
            "content",
            "canvas",
            "typography",
            "visual",
            "visibility",
            "branding",
            "media",
        )
    }
}
