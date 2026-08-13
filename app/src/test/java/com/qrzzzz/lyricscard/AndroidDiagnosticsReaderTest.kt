package com.qrzzzz.lyricscard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidDiagnosticsReaderTest {
    @Test
    fun `reader returns build and bundled manifest facts without user content`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = AndroidDiagnosticsReader(context).read()

        assertEquals(BuildConfig.VERSION_NAME, snapshot.appVersionName)
        assertEquals(BuildConfig.VERSION_CODE, snapshot.appVersionCode)
        assertEquals(BuildConfig.RENDERER_VERSION, snapshot.rendererVersion)
        assertEquals(BuildConfig.RENDERER_SCHEMA_VERSION, snapshot.rendererSchemaVersion)
        assertNotNull(snapshot.rendererProtocolVersion)
        assertTrue(snapshot.rendererSourceCommit.orEmpty().matches(Regex("[0-9a-f]{40}")))
        assertTrue(snapshot.rendererFontManifestHash.orEmpty().matches(Regex("[0-9a-f]{64}")))
    }
}
