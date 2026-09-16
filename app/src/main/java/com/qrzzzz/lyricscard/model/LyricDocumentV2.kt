package com.qrzzzz.lyricscard.model

import java.util.UUID
import kotlinx.serialization.Serializable

/** Desktop 4f2b411 LyricDocumentV2 wire model. Formatting belongs to each track. */
@Serializable
data class LyricDocumentV2(
    val schemaVersion: Int = 2,
    val id: String = lyricId("document"),
    val revision: Long = 0,
    val blocks: List<LyricBlock> = emptyList(),
    val formatting: LyricDocumentFormatting = LyricDocumentFormatting(),
) {
    fun text(translation: Boolean = false): String = buildString {
        append(if (translation) formatting.translationPrefix else formatting.sourcePrefix)
        blocks.forEach { block ->
            val f = block.formatting
            if (if (translation) f.translationPresent else f.sourcePresent) {
                append(block.units.flatMap { if (translation) it.translation.orEmpty() else it.source }.joinToString("\n"))
                append(if (translation) f.translationSeparatorAfter else f.sourceSeparatorAfter)
            }
        }
    }

    fun reconcile(source: String, translation: String): LyricDocumentV2 {
        val s = normalize(source)
        val t = normalize(translation)
        if (text() == s && text(true) == t) return this
        val next = create(s, t)
        val matches = matchSequence(blocks, next.blocks) { it.units.joinToString("\u001e") { unit -> unit.signature() } }
        return next.copy(id = id, revision = revision + 1, blocks = next.blocks.mapIndexed { index, block ->
            val old = matches[index]
            val units = matchSequence(old?.units.orEmpty(), block.units) { it.signature() }
            block.copy(id = old?.id ?: block.id, units = block.units.mapIndexed { i, unit -> unit.copy(id = units[i]?.id ?: unit.id) })
        })
    }

    fun isValid(): Boolean {
        val ids = listOf(id) + blocks.flatMap { listOf(it.id) + it.units.map(LyricUnit::id) }
        return schemaVersion == 2 && revision >= 0 && ids.all { it.isNotBlank() } &&
            ids.distinct().size == ids.size && blocks.size <= 400 &&
            blocks.sumOf { it.units.size } <= 800 && blocks.all { block ->
                block.units.all { unit -> (unit.source + unit.translation.orEmpty()).all { '\n' !in it && '\r' !in it && '\u0000' !in it } }
            }
    }

    companion object {
        fun create(source: String = "", translation: String = ""): LyricDocumentV2 {
            val s = parseTrack(source)
            val t = parseTrack(translation)
            return LyricDocumentV2(formatting = LyricDocumentFormatting(s.first, t.first),
                blocks = (0 until maxOf(s.second.size, t.second.size)).map { index ->
                    val sb = s.second.getOrNull(index)
                    val tb = t.second.getOrNull(index)
                    val sl = sb?.first.orEmpty()
                    val tl = tb?.first.orEmpty()
                    val units = mutableListOf<LyricUnit>()
                    var si = 0
                    var ti = 0
                    while (si < sl.size || ti < tl.size) {
                        val a = sl.getOrNull(si)
                        val b = tl.getOrNull(ti)
                        val sa = a?.trim() == "<separator />"
                        val ta = b?.trim() == "<separator />"
                        if (sa || ta) {
                            units += LyricUnit(source = if (sa) listOf(a!!) else emptyList(), translation = if (ta) listOf(b!!) else null)
                            if (sa) si++
                            if (ta) ti++
                        } else {
                            units += LyricUnit(source = listOfNotNull(a), translation = b?.let(::listOf))
                            if (a != null) si++
                            if (b != null) ti++
                        }
                    }
                    LyricBlock(units = units, formatting = LyricBlockFormatting(sb != null, tb != null, sb?.second.orEmpty(), tb?.second.orEmpty()))
                })
        }
    }
}

@Serializable
data class LyricBlock(val id: String = lyricId("block"), val units: List<LyricUnit>, val formatting: LyricBlockFormatting)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class LyricUnit(val id: String = lyricId("unit"), val source: List<String>,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val translation: List<String>? = null) {
    fun signature() = source.joinToString("\n") + "\u001f" + translation.orEmpty().joinToString("\n")
}
@Serializable
data class LyricDocumentFormatting(val sourcePrefix: String = "", val translationPrefix: String = "")
@Serializable
data class LyricBlockFormatting(val sourcePresent: Boolean, val translationPresent: Boolean, val sourceSeparatorAfter: String = "", val translationSeparatorAfter: String = "")

private fun lyricId(prefix: String) = "$prefix-${UUID.randomUUID()}"
private fun normalize(value: String) = value.replace("\r\n", "\n").replace('\r', '\n')

private fun parseTrack(value: String): Pair<String, List<Pair<List<String>, String>>> {
    val text = normalize(value)
    val tokens = Regex("[^\n]*\n|[^\n]+$").findAll(text).map { match ->
        val token = match.value
        Pair(token.removeSuffix("\n"), if (token.endsWith('\n')) "\n" else "")
    }.toList()
    var i = 0
    var prefix = ""
    while (i < tokens.size && tokens[i].first.isBlank()) { prefix += tokens[i].first + tokens[i].second; i++ }
    val blocks = mutableListOf<Pair<List<String>, String>>()
    while (i < tokens.size) {
        val lines = mutableListOf<String>()
        var ending = ""
        while (i < tokens.size && tokens[i].first.isNotBlank()) { lines += tokens[i].first; ending = tokens[i].second; i++ }
        while (i < tokens.size && tokens[i].first.isBlank()) { ending += tokens[i].first + tokens[i].second; i++ }
        blocks += Pair(lines, ending)
    }
    return Pair(prefix, blocks)
}

private fun <T> matchSequence(previous: List<T>, next: List<T>, signature: (T) -> String): List<T?> {
    val result = MutableList<T?>(next.size) { null }
    val used = mutableSetOf<Int>()
    next.forEachIndexed { i, item ->
        val match = previous.indices.firstOrNull { it !in used && signature(previous[it]) == signature(item) }
        if (match != null) { result[i] = previous[match]; used += match }
    }
    next.indices.forEach { i ->
        if (result[i] == null) {
            val match = if (i < previous.size && i !in used) i else previous.indices.firstOrNull { it !in used }
            if (match != null) { result[i] = previous[match]; used += match }
        }
    }
    return result
}
