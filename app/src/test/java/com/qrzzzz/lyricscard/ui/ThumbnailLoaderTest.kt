package com.qrzzzz.lyricscard.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThumbnailLoaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing and corrupt thumbnails degrade to an empty result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val corrupt = temporaryFolder.newFile("corrupt.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        assertNull(decodeThumbnail(File(temporaryFolder.root, "missing.png").path, 88, 72, dispatcher))
        assertNull(decodeThumbnail(corrupt.path, 88, 72, dispatcher))
    }

    @Test
    fun `replacement at the same path decodes the new pixels without retaining the old bitmap`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val target = temporaryFolder.newFile("replace.png")
        writeSolidPng(target, Color.RED)

        val first = checkNotNull(decodeThumbnail(target.path, 88, 72, dispatcher))
        assertEquals(Color.RED, first.asAndroidBitmap().getPixel(0, 0))

        writeSolidPng(target, Color.BLUE)
        val second = checkNotNull(decodeThumbnail(target.path, 88, 72, dispatcher))
        assertEquals(Color.BLUE, second.asAndroidBitmap().getPixel(0, 0))
    }

    private fun writeSolidPng(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        FileOutputStream(file, false).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}
