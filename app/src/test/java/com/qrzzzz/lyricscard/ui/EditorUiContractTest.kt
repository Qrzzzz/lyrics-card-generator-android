package com.qrzzzz.lyricscard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiContractTest {
    @Test
    fun editorWorkflowHasExactlyTheSixEstablishedSteps() {
        assertEquals(6, EDITOR_STEP_COUNT)
        assertEquals(EDITOR_STEP_COUNT, EditorStep.entries.size)
        assertEquals(
            listOf("CHOOSE_SONG", "LYRICS", "LAYOUT", "FONT", "VISUAL", "EXPORT"),
            EditorStep.entries.map { it.name },
        )
    }

    @Test
    fun colorDraftContractAcceptsOnlyRendererSafeHexValues() {
        assertTrue(HEX_COLOR_PATTERN.matches("#112233"))
        assertTrue(HEX_COLOR_PATTERN.matches("#112233CC"))
        assertFalse(HEX_COLOR_PATTERN.matches("112233"))
        assertFalse(HEX_COLOR_PATTERN.matches("#GGGGGG"))
        assertFalse(HEX_COLOR_PATTERN.matches("#12345"))
    }
}
