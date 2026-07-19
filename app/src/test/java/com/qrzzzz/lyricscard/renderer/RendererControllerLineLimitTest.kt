package com.qrzzzz.lyricscard.renderer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.model.ContentSpec
import com.qrzzzz.lyricscard.model.InvalidRenderSpecException
import com.qrzzzz.lyricscard.model.LyricTextLimits
import com.qrzzzz.lyricscard.model.RenderSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RendererControllerLineLimitTest {
    @Test
    fun `controller rejects over-limit lyrics before a WebView is acquired`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = RendererController(context, ProjectAssetStore(context))
        val invalid = RenderSpec(
            content = ContentSpec(lyrics = "\n".repeat(LyricTextLimits.MAX_LINES)),
        )

        try {
            controller.updateSpec(invalid)
            fail("Expected InvalidRenderSpecException")
        } catch (_: InvalidRenderSpecException) {
            assertEquals(RendererStatus.Phase.STARTING, controller.status.value.phase)
        } finally {
            controller.close()
        }
    }
}
