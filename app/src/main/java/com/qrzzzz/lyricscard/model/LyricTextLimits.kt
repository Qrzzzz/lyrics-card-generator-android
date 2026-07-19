package com.qrzzzz.lyricscard.model

/**
 * Cross-boundary limits for lyric-shaped text.
 *
 * A physical line is separated by CRLF, a lone CR, or a lone LF. Empty input is one line, and a
 * trailing separator starts one final empty line. Count without splitting so hostile input does
 * not allocate one object per line before it can be rejected.
 */
object LyricTextLimits {
    const val MAX_CHARACTERS = 200_000
    const val MAX_LINES = 400

    fun countPhysicalLines(value: String): Int {
        var lines = 1
        var index = 0
        while (index < value.length) {
            when (value[index]) {
                '\r' -> {
                    lines += 1
                    if (index + 1 < value.length && value[index + 1] == '\n') {
                        index += 1
                    }
                }

                '\n' -> lines += 1
            }
            index += 1
        }
        return lines
    }
}
