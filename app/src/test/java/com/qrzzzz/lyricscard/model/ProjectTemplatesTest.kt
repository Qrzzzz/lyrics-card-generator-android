package com.qrzzzz.lyricscard.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTemplatesTest {
    @Test
    fun `blank template is immediately persistable`() {
        val project = ProjectTemplates.blank(id = "blank-id", now = 42L)

        assertEquals("blank-id", project.id)
        assertEquals(ProjectTemplates.DEFAULT_BLANK_NAME, project.name)
        assertEquals(42L, project.createdAt)
        assertEquals(42L, project.updatedAt)
        assertEquals(RenderSpec(), project.spec)
        assertNull(project.coverAssetId)
        assertTrue(project.validate().isEmpty())
    }

    @Test
    fun `sample template contains offline renderer content`() {
        val project = ProjectTemplates.sample(id = "sample-id", now = 84L)

        assertTrue(project.spec.song.title.isNotBlank())
        assertTrue(project.spec.content.lyrics.lines().size >= 4)
        assertTrue(project.spec.content.translationEnabled)
        assertFalse(project.spec.visibility.showCover)
        assertEquals(CanvasRatio.PORTRAIT_4_5, project.spec.canvas.ratio)
        assertTrue(project.validate().isEmpty())
    }
}
