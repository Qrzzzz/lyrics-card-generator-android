package com.qrzzzz.lyricscard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportUiContractTest {
    @Test
    fun fileNameAlwaysUsesPngAndSanitizesPlatformReservedCharacters() {
        assertEquals("lyrics-card.png", ensurePng("", "lyrics-card.png"))
        assertEquals("final.png", ensurePng("final", "fallback.png"))
        assertEquals("artist-song.png", ensurePng("artist/song", "fallback.png"))
        assertEquals("already.PNG", ensurePng("already.PNG", "fallback.png"))
    }

    @Test
    fun exportStateOnlyAllowsCancellationBeforeFinalization() {
        val projectId = "export-state"
        assertEquals(true, ExportUiState(projectId, operation = ExportOperationState.PREPARING).canCancel)
        assertEquals(true, ExportUiState(projectId, operation = ExportOperationState.RENDERING).canCancel)
        assertEquals(false, ExportUiState(projectId, operation = ExportOperationState.FINALIZING).canCancel)
        assertEquals(false, ExportUiState(projectId, operation = ExportOperationState.SUCCESS).canCancel)
    }
}
