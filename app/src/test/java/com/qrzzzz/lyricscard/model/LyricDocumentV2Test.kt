package com.qrzzzz.lyricscard.model

import org.junit.Assert.*
import org.junit.Test

class LyricDocumentV2Test {
    @Test fun `roundtrip preserves independent whitespace and paragraph pairs`() {
        val source = "\n甲\n乙\n\n\n丙\n"
        val translation = "A\nB\n\nC\n"
        val doc = LyricDocumentV2.create(source, translation)
        assertEquals(source, doc.text())
        assertEquals(translation, doc.text(true))
        assertEquals(listOf("C"), doc.blocks[1].units[0].translation)
        val spec = RenderSpec(content = ContentSpec(lyrics = source, translation = translation, lyricDocument = doc))
        assertEquals(spec, RenderSpecJson.decode(RenderSpecJson.encode(spec)))
    }
    @Test fun `insertion preserves identities and separator does not consume translation`() {
        val before = LyricDocumentV2.create("甲\n乙", "A\nB")
        val after = before.reconcile("新\n甲\n乙", "N\nA\nB")
        assertEquals(before.id, after.id)
        assertEquals(before.blocks[0].units[0].id, after.blocks[0].units[1].id)
        assertEquals(1L, after.revision)
        val doc = LyricDocumentV2.create("甲\n<separator />\n乙", "A\nB")
        assertNull(doc.blocks[0].units[1].translation)
        assertEquals(listOf("B"), doc.blocks[0].units[2].translation)
    }
    @Test fun `old project schema and duplicate identity are rejected`() {
        assertThrows(InvalidRenderSpecException::class.java) { RenderSpec(schemaVersion = 1).requireValid() }
        val doc = LyricDocumentV2.create("甲")
        assertFalse(doc.copy(blocks = listOf(doc.blocks[0].copy(id = doc.id))).isValid())
    }
}
