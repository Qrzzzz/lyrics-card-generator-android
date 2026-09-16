package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.File
import java.security.MessageDigest

/** App-private, content-addressed fonts. No external URLs or user paths reach WebView. */
object CustomFontStore {
    private val namePattern = Regex("[a-f0-9]{64}\\.(ttf|otf|woff2?)")
    fun import(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                require(output.size() + size <= 32 * 1024 * 1024) { "字体文件不能超过 32 MB" }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        } ?: error("无法读取字体文件")
        require(bytes.size >= 12) { "字体文件不完整" }
        val extension = when (bytes.take(4)) {
            listOf<Byte>(0, 1, 0, 0) -> "ttf"
            "OTTO".toByteArray().toList() -> "otf"
            "wOFF".toByteArray().toList() -> "woff"
            "wOF2".toByteArray().toList() -> "woff2"
            else -> error("请选择 TTF、OTF、WOFF 或 WOFF2 字体")
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "custom-fonts").apply { mkdirs() }
        val name = "$hash.$extension"
        val file = File(directory, name)
        if (!file.exists()) {
            val temp = File.createTempFile("font-", ".part", directory)
            try { temp.writeBytes(bytes); check(temp.renameTo(file)) } finally { temp.delete() }
        }
        return name
    }

    fun open(context: Context, name: String): WebResourceResponse? {
        if (!namePattern.matches(name)) return null
        return runCatching {
            WebResourceResponse("font/${name.substringAfterLast('.')}", null,
                File(File(context.filesDir, "custom-fonts"), name).inputStream())
        }.getOrNull()
    }
}
