package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Minimal local thumbnail boundary: all file access and decoding stays off the main thread. */
fun interface ThumbnailLoader {
    suspend fun load(path: String, targetWidthPx: Int, targetHeightPx: Int): ImageBitmap?
}

object FileThumbnailLoader : ThumbnailLoader {
    override suspend fun load(path: String, targetWidthPx: Int, targetHeightPx: Int): ImageBitmap? =
        decodeThumbnail(path, targetWidthPx, targetHeightPx, Dispatchers.IO)
}

internal suspend fun decodeThumbnail(
    path: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
    dispatcher: CoroutineDispatcher,
): ImageBitmap? = withContext(dispatcher) {
    try {
        val file = File(path)
        if (!file.isFile || !file.hasPngSignature()) return@withContext null
        val width = max(1, targetWidthPx)
        val height = max(1, targetHeightPx)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > width * 2 ||
            bounds.outHeight / sampleSize > height * 2
        ) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return@withContext null
        ensureActive()
        bitmap.asImageBitmap()
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
        null
    }
}

private fun File.hasPngSignature(): Boolean {
    val actual = ByteArray(PNG_SIGNATURE.size)
    val count = inputStream().use { it.read(actual) }
    return count == actual.size && actual.contentEquals(PNG_SIGNATURE)
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
