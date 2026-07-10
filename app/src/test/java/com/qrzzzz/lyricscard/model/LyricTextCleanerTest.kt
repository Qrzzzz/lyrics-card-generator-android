package com.qrzzzz.lyricscard.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTextCleanerTest {
    @Test
    fun `clean removes lrc metadata and timestamps while preserving lyrics`() {
        val source = """
            ﻿[ar:Example Artist]
            [ti:Example Song]
            [00:01.20][00:02.30]First line
            [1:02:03.456]Second <00:03.50>line


            [00:04]Third line
        """.trimIndent()

        assertEquals(
            "First line\nSecond line\n\nThird line",
            LyricTextCleaner.clean(source),
        )
    }

    @Test
    fun `section labels are not mistaken for timestamps`() {
        val source = "[Verse 1]\n[00:01.00]Hello\n[Chorus]\n[00:02.00]World"

        assertEquals(
            "[Verse 1]\nHello\n[Chorus]\nWorld",
            source.cleanedLyrics(),
        )
    }

    @Test
    fun `blank cleanup normalizes newlines and keeps one intentional blank`() {
        val source = "\r\nLine one  \r\n \r\n\r\nLine two\r\n\r\n"

        assertEquals("Line one\n\nLine two", source.withCollapsedBlankLines())
    }

    @Test
    fun `timestamp removal can be used without changing blank line count`() {
        val source = "[00:01.00]One\n\n\n[00:02.00]Two"

        assertEquals("One\n\n\nTwo", source.withoutLyricTimestamps())
    }
}
