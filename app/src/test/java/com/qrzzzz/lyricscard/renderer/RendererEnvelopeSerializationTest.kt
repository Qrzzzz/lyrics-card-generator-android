package com.qrzzzz.lyricscard.renderer

import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RendererEnvelopeSerializationTest {
    @Test
    fun `native spec and renderer response preserve protocol and payload across JSON`() {
        val spec = RenderSpec()
        val message = RendererEnvelope(
            requestId = "native-1", type = "setSpec",
            payload = RenderSpecJson.format.encodeToJsonElement(RenderSpec.serializer(), spec),
        )
        val encoded = RenderSpecJson.format.encodeToString(RendererEnvelope.serializer(), message)
        val decoded = RenderSpecJson.format.decodeFromString(RendererEnvelope.serializer(), encoded)
        assertEquals(message, decoded)
        assertEquals(spec, RenderSpecJson.format.decodeFromJsonElement(RenderSpec.serializer(), decoded.payload))
        val response = RenderSpecJson.format.decodeFromString(
            RendererEnvelope.serializer(),
            """{"protocolVersion":1,"requestId":"native-1","type":"measured","payload":{"width":320,"height":640,"future":true},"future":42}""",
        )
        assertEquals(1, response.protocolVersion)
        assertEquals("native-1", response.requestId)
        assertEquals("320", response.payload.jsonObject.getValue("width").jsonPrimitive.content)
    }

    @Test
    fun `malformed and incomplete responses cannot become valid envelopes`() {
        listOf("{broken", "{}", """{"requestId":null,"type":"ready"}""").forEach { payload ->
            assertThrows(SerializationException::class.java) {
                RenderSpecJson.format.decodeFromString(RendererEnvelope.serializer(), payload)
            }
        }
    }
}
