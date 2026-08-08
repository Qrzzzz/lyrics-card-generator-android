package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class ExportPreviewPhase {
    EMPTY,
    LOADING,
    READY,
    ERROR,
}

data class ExportPreviewUiState(
    val phase: ExportPreviewPhase = ExportPreviewPhase.EMPTY,
    val bitmap: Bitmap? = null,
    val message: UiText? = null,
)

internal sealed interface ExportPreviewDecodeResult {
    data class Success(val bitmap: Bitmap) : ExportPreviewDecodeResult
    data object Missing : ExportPreviewDecodeResult
    data object InvalidPng : ExportPreviewDecodeResult
    data object DecodeFailed : ExportPreviewDecodeResult
}

internal fun interface ExportPreviewDecoder {
    suspend fun decode(image: ExportedImage): ExportPreviewDecodeResult
}

internal class AndroidExportPreviewDecoder(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExportPreviewDecoder {
    override suspend fun decode(image: ExportedImage): ExportPreviewDecodeResult = withContext(ioDispatcher) {
        val file = image.file
        if (!file.isFile) return@withContext ExportPreviewDecodeResult.Missing

        try {
            file.inputStream().buffered().use { input ->
                val signature = ByteArray(PNG_SIGNATURE.size)
                if (input.read(signature) != signature.size || !signature.contentEquals(PNG_SIGNATURE)) {
                    return@withContext ExportPreviewDecodeResult.InvalidPng
                }
            }
            currentCoroutineContext().ensureActive()

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outMimeType != PNG_MIME_TYPE) {
                return@withContext ExportPreviewDecodeResult.InvalidPng
            }

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > PREVIEW_MAX_EDGE_PX) {
                sample *= 2
            }
            currentCoroutineContext().ensureActive()
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return@withContext ExportPreviewDecodeResult.DecodeFailed
            try {
                currentCoroutineContext().ensureActive()
            } catch (cause: CancellationException) {
                bitmap.recycle()
                throw cause
            }
            ExportPreviewDecodeResult.Success(bitmap)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: FileNotFoundException) {
            ExportPreviewDecodeResult.Missing
        } catch (_: SecurityException) {
            ExportPreviewDecodeResult.Missing
        } catch (_: OutOfMemoryError) {
            ExportPreviewDecodeResult.DecodeFailed
        } catch (_: Throwable) {
            ExportPreviewDecodeResult.InvalidPng
        }
    }

    private companion object {
        const val PREVIEW_MAX_EDGE_PX = 1_024
        const val PNG_MIME_TYPE = "image/png"
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
