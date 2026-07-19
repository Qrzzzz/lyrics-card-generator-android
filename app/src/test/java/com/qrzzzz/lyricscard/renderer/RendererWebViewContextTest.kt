package com.qrzzzz.lyricscard.renderer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RendererWebViewContextTest {
    @Test
    fun `controller close is idempotent and rejects another WebView owner`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val controller = RendererController(appContext, ProjectAssetStore(appContext))

        controller.close()
        controller.close()

        assertThrows(IllegalStateException::class.java) {
            controller.acquireWebView(appContext, Any())
        }
    }

    @Test
    fun `release and rebind replace Activity ownership while close stays idempotent`() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val firstController = Robolectric.buildActivity(Activity::class.java).setup()
        val secondController = Robolectric.buildActivity(Activity::class.java).setup()
        val firstActivity = firstController.get()
        val secondActivity = secondController.get()
        val binding = RendererWebViewContext(appContext)
        val firstOwner = Any()
        val reenteredOwner = Any()
        val recreatedOwner = Any()

        try {
            binding.bind(firstOwner, firstActivity)
            assertSame(firstActivity, binding.context.baseContext)

            assertTrue(binding.release(firstOwner))
            assertSame(appContext, binding.context.baseContext)
            assertFalse(binding.context.contains(firstActivity))

            binding.bind(reenteredOwner, firstActivity)
            assertSame(firstActivity, binding.context.baseContext)

            binding.bind(recreatedOwner, secondActivity)
            assertSame(secondActivity, binding.context.baseContext)
            assertFalse(binding.context.contains(firstActivity))

            assertFalse(binding.release(reenteredOwner))
            assertSame(secondActivity, binding.context.baseContext)

            assertTrue(binding.release(recreatedOwner))
            assertSame(appContext, binding.context.baseContext)

            binding.bind(Any(), secondActivity)
            binding.close()
            binding.close()
            assertSame(appContext, binding.context.baseContext)
            assertFalse(binding.context.contains(firstActivity))
            assertFalse(binding.context.contains(secondActivity))
            assertThrows(IllegalStateException::class.java) {
                binding.bind(Any(), secondActivity)
            }
        } finally {
            secondController.pause().stop().destroy()
            firstController.pause().stop().destroy()
        }
    }
}

private fun Context.contains(target: Context): Boolean {
    var current: Context? = this
    val visited = mutableSetOf<Context>()
    while (current != null && visited.add(current)) {
        if (current === target) return true
        current = (current as? ContextWrapper)?.baseContext
    }
    return false
}
