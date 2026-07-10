package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Private, logical-ID based storage for project cover images.
 *
 * Android URIs never enter RenderSpec. The local renderer can request only one UUID-shaped ID,
 * and the WebView handler resolves it to a private file without exposing a filesystem path.
 */
class ProjectAssetStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "project-assets").apply { mkdirs() }

    suspend fun importCover(uri: Uri): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dataFile = dataFile(id)
        val mimeFile = mimeFile(id)
        val importFile = File(root, "$id.import")
        val dataTemp = File(root, "$id.image.tmp")
        val mimeTemp = File(root, "$id.mime.tmp")

        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                importFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_COVER_BYTES) { "封面图片不能超过 25 MB" }
                        output.write(buffer, 0, read)
                    }
                    require(total > 0) { "无法读取空图片" }
                }
            } ?: error("无法打开所选图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(importFile.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选文件不是可解码的图片" }
            require(bounds.outWidth <= MAX_SOURCE_EDGE && bounds.outHeight <= MAX_SOURCE_EDGE) {
                "封面原图的长边不能超过 $MAX_SOURCE_EDGE 像素"
            }
            require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_SOURCE_PIXELS) {
                "封面原图像素过大"
            }

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > STORED_COVER_EDGE) {
                sampleSize *= 2
            }
            var working: Bitmap? = BitmapFactory.decodeFile(
                importFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: error("无法解码封面图片")
            try {
                val oriented = applyExifOrientation(checkNotNull(working), importFile)
                if (oriented !== working) {
                    working?.recycle()
                    working = oriented
                }
                val scale = minOf(
                    1f,
                    STORED_COVER_EDGE.toFloat() / maxOf(oriented.width, oriented.height).toFloat(),
                )
                val width = (oriented.width * scale).toInt().coerceAtLeast(1)
                val height = (oriented.height * scale).toInt().coerceAtLeast(1)
                val normalized = if (width == oriented.width && height == oriented.height) {
                    oriented
                } else {
                    Bitmap.createScaledBitmap(oriented, width, height, true).also {
                        oriented.recycle()
                        working = it
                    }
                }
                val hasAlpha = normalized.hasAlpha()
                val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
                dataTemp.outputStream().use { output ->
                    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    check(normalized.compress(format, 92, output)) { "封面图片编码失败" }
                }
                mimeTemp.writeText(mimeType, Charsets.UTF_8)
                check(mimeTemp.renameTo(mimeFile)) { "无法保存封面类型" }
                check(dataTemp.renameTo(dataFile)) { "无法保存封面图片" }
            } finally {
                working?.recycle()
            }
            id
        } catch (cause: Throwable) {
            dataFile.delete()
            mimeFile.delete()
            dataTemp.delete()
            mimeTemp.delete()
            throw cause
        } finally {
            importFile.delete()
        }
    }

    fun openForWebView(path: String): WebResourceResponse? {
        val id = path.substringBefore('?').substringBefore('#')
        if (!ASSET_ID.matches(id)) return null
        val file = dataFile(id)
        if (!file.isFile || !file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
            return null
        }
        val mime = mimeFile(id).takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        return try {
            WebResourceResponse(mime, null, FileInputStream(file)).also {
                it.responseHeaders = mapOf(
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff",
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        if (ASSET_ID.matches(id)) {
            dataFile(id).delete()
            mimeFile(id).delete()
        }
    }

    private fun dataFile(id: String) = File(root, "$id.image")
    private fun mimeFile(id: String) = File(root, "$id.mime")

    private fun applyExifOrientation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        val ASSET_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        const val MAX_COVER_BYTES = 25L * 1024L * 1024L
        const val MAX_SOURCE_EDGE = 20_000
        const val MAX_SOURCE_PIXELS = 160_000_000L
        const val STORED_COVER_EDGE = 2_048
    }
}
