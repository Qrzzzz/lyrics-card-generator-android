package com.qrzzzz.lyricscard.renderer

import java.io.File
import java.io.RandomAccessFile

/** Check the byte container independently of decoder/platform MIME reporting. */
internal fun hasImageEncoding(file: File, mimeType: String): Boolean = runCatching {
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < 20 || input.length() > 64L * 1024 * 1024) return false
        val header = ByteArray(12).also(input::readFully)
        when (mimeType) {
            "image/png" -> {
                val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)
                val expectedEnd = byteArrayOf(0, 0, 0, 0, 0x49, 0x45, 0x4e, 0x44, 0xae.toByte(), 0x42, 0x60, 0x82.toByte())
                input.seek(input.length() - 12)
                header.copyOf(8).contentEquals(signature) && ByteArray(12).also(input::readFully).contentEquals(expectedEnd)
            }
            "image/jpeg" -> {
                input.seek(input.length() - 2)
                header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte() && input.readUnsignedShort() == 0xffd9
            }
            "image/webp" -> {
                val declared = (4..7).fold(0L) { value, i -> value or ((header[i].toLong() and 255) shl ((i - 4) * 8)) }
                String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" && declared + 8 == input.length()
            }
            else -> false
        }
    }
}.getOrDefault(false)
