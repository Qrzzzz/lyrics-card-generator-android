package com.qrzzzz.lyricscard.model

/** Pure helpers used by both text import and editor cleanup actions. */
object LyricTextCleaner {
    /**
     * Removes leading LRC timestamps, enhanced-LRC inline timestamps, and standalone LRC metadata.
     * Section labels such as `[Chorus]` are deliberately preserved.
     */
    fun removeTimestamps(value: String): String = normalizeNewlines(value)
        .lineSequence()
        .mapIndexed { index, sourceLine ->
            val line = if (index == 0) sourceLine.removePrefix(UTF8_BOM) else sourceLine
            when {
                line.matches(LRC_METADATA_LINE) -> ""
                else -> line
                    .replace(LRC_TIMESTAMP_PREFIX, "")
                    .replace(ENHANCED_LRC_TIMESTAMP, "")
                    .trimEnd()
            }
        }
        .joinToString("\n")

    /** Keeps meaningful line breaks while reducing every blank run to a single empty line. */
    fun collapseRepeatedBlankLines(value: String): String {
        val output = mutableListOf<String>()
        var previousWasBlank = false

        normalizeNewlines(value).lineSequence().forEach { sourceLine ->
            val line = sourceLine.trimEnd()
            if (line.isBlank()) {
                if (output.isNotEmpty() && !previousWasBlank) {
                    output += ""
                }
                previousWasBlank = true
            } else {
                output += line
                previousWasBlank = false
            }
        }

        while (output.lastOrNull().isNullOrEmpty()) {
            if (output.isEmpty()) break
            output.removeAt(output.lastIndex)
        }
        return output.joinToString("\n")
    }

    fun clean(value: String): String = collapseRepeatedBlankLines(removeTimestamps(value))

    private fun normalizeNewlines(value: String): String = value
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private const val UTF8_BOM = "\uFEFF"
    private val LRC_TIMESTAMP_PREFIX = Regex(
        pattern = "^\\s*(?:\\[(?:(?:\\d{1,2}:)?\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)])+(?:\\s*)",
    )
    private val ENHANCED_LRC_TIMESTAMP = Regex(
        pattern = "<(?:(?:\\d{1,2}:)?\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?)>",
    )
    private val LRC_METADATA_LINE = Regex(
        pattern = "^\\s*\\[(?:ar|ti|al|by|offset|re|ve|length):[^]]*]\\s*$",
        option = RegexOption.IGNORE_CASE,
    )
}

fun String.withoutLyricTimestamps(): String = LyricTextCleaner.removeTimestamps(this)

fun String.withCollapsedBlankLines(): String = LyricTextCleaner.collapseRepeatedBlankLines(this)

fun String.cleanedLyrics(): String = LyricTextCleaner.clean(this)
