package com.qrzzzz.lyricscard.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenderSpecContractConsistencyTest {
    @Test
    fun `bundled renderer schema and Kotlin use the same lyric line limit`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schema = context.assets.open("renderer/renderer-schema.json")
            .bufferedReader()
            .use { Json.parseToJsonElement(it.readText()).jsonObject }
        val contentProperties = schema.getValue("properties")
            .jsonObject
            .getValue("content")
            .jsonObject
            .getValue("properties")
            .jsonObject

        val lyricLimit = contentProperties.getValue("lyrics")
            .jsonObject
            .getValue("maxLines")
            .jsonPrimitive
            .int
        val translationLimit = contentProperties.getValue("translation")
            .jsonObject
            .getValue("maxLines")
            .jsonPrimitive
            .int

        assertEquals(LyricTextLimits.MAX_LINES, lyricLimit)
        assertEquals(lyricLimit, translationLimit)
    }
}
