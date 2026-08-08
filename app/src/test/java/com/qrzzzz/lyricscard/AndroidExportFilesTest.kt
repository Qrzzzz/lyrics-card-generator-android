package com.qrzzzz.lyricscard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidExportFilesTest {
    private lateinit var context: Context
    private lateinit var exportDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        exportDirectory = File(context.cacheDir, "exports")
        exportDirectory.deleteRecursively()
    }

    @Test
    fun `clear export cache runs against real files and reports the removed byte count`() = runTest {
        val nested = File(exportDirectory, "nested").apply { check(mkdirs()) }
        File(exportDirectory, "first.png").writeBytes(ByteArray(512))
        File(nested, "second.part").writeBytes(ByteArray(256))
        val files = AndroidExportFiles(context, ProjectAssetStore(context))

        assertEquals(768L, files.clearExportCache())
        assertFalse(exportDirectory.exists())
    }
}
