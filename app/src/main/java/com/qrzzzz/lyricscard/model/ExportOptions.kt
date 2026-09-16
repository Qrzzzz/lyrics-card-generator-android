package com.qrzzzz.lyricscard.model

/** Existing integer preference IDs 1 and 2 are retained; 14 identifies medium quality. */
const val MEDIUM_EXPORT_QUALITY = 14
fun exportPixelRatio(quality: Int): Double = when (quality) {
    1 -> 1.0
    MEDIUM_EXPORT_QUALITY -> 1.4
    2 -> 2.0
    else -> throw IllegalArgumentException("Unsupported export quality")
}
fun exportMimeType(format: String): String = when (format) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "jpg" -> "image/jpeg"
    else -> throw IllegalArgumentException("Unsupported export format")
}
