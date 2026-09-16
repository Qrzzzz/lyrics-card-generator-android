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

        assertEquals(2, root.getValue("schemaVersion").jsonPrimitive.content.toInt())
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
    fun `old sparse projects are rejected without migration`() {
        listOf("{}", """{"schemaVersion":1,"song":{"title":"旧项目"}}""", """{"schemaVersion":2,"content":{"lyrics":"旧项目"}}""").forEach {
            assertThrows(SerializationException::class.java) { RenderSpecJson.decode(it) }
        }
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
