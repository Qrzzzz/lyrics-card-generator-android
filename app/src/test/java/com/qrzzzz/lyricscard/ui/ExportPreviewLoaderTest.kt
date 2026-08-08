package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportPreviewLoaderTest {
    @Test
    fun missingAndCorruptFilesAreRejectedWithoutAFullDecode() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "export-preview-io")
        }.asCoroutineDispatcher()
        try {
            val decoder = AndroidExportPreviewDecoder(dispatcher)
            val missing = File(System.getProperty("java.io.tmpdir"), "missing-${System.nanoTime()}.png")
            assertEquals(
                ExportPreviewDecodeResult.Missing,
                decoder.decode(ExportedImage(missing, 10, 10)),
            )

            val corrupt = File.createTempFile("lyrics-preview-corrupt-", ".png").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
                deleteOnExit()
            }
            assertEquals(
                ExportPreviewDecodeResult.InvalidPng,
                decoder.decode(ExportedImage(corrupt, 10, 10)),
            )
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun validPngIsSampledOnTheProvidedBackgroundDispatcher() = runBlocking {
        val source = Bitmap.createBitmap(2_048, 1_024, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("lyrics-preview-valid-", ".png").apply { deleteOnExit() }
        file.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        source.recycle()

        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "export-preview-io")
        }.asCoroutineDispatcher()
        try {
            val result = AndroidExportPreviewDecoder(dispatcher).decode(
                ExportedImage(file, 2_048, 1_024),
            )
            assertTrue(result is ExportPreviewDecodeResult.Success)
            val decoded = (result as ExportPreviewDecodeResult.Success).bitmap
            assertTrue(maxOf(decoded.width, decoded.height) <= 1_024)
            decoded.recycle()
        } finally {
            dispatcher.close()
        }
    }
}
