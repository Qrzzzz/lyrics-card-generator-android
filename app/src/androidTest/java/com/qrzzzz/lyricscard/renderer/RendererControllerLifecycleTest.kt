package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RendererControllerLifecycleTest {
    @Test
    fun rendererWebViewReleasesRebindsAndClosesAcrossActivityRecreation() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val controller = RendererController(appContext, ProjectAssetStore(appContext))
        val firstOwner = Any()
        val recreatedOwner = Any()
        val reenteredOwner = Any()
        lateinit var firstActivity: ComponentActivity
        lateinit var rendererView: android.webkit.WebView

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                firstActivity = activity
                val root = FrameLayout(activity)
                activity.setContentView(root)

                rendererView = controller.acquireWebView(activity, firstOwner)
                root.addView(rendererView)
                assertSame(activity, rendererView.mutableContext().baseContext)

                controller.releaseWebView(firstOwner, rendererView)
                assertNull(rendererView.parent)
                assertSame(appContext, rendererView.mutableContext().baseContext)
                assertFalse(rendererView.context.contains(firstActivity))

                val reattached = controller.acquireWebView(activity, firstOwner)
                assertSame(rendererView, reattached)
                root.addView(reattached)
                activity.lifecycle.addObserver(
                    object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            controller.releaseWebView(firstOwner, rendererView)
                        }
                    },
                )
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertTrue(firstActivity.isDestroyed)
                assertSame(appContext, rendererView.mutableContext().baseContext)
                assertFalse(rendererView.context.contains(firstActivity))

                val root = FrameLayout(activity)
                activity.setContentView(root)
                val rebound = controller.acquireWebView(activity, recreatedOwner)
                root.addView(rebound)
                assertSame(rendererView, rebound)
                assertSame(activity, rebound.mutableContext().baseContext)
                assertFalse(rebound.context.contains(firstActivity))

                controller.releaseWebView(firstOwner, rebound)
                assertSame(activity, rebound.mutableContext().baseContext)
                assertSame(root, rebound.parent)

                controller.releaseWebView(recreatedOwner, rebound)
                assertNull(rebound.parent)
                assertSame(appContext, rebound.mutableContext().baseContext)

                val reentered = controller.acquireWebView(activity, reenteredOwner)
                root.addView(reentered)
                assertSame(rendererView, reentered)
                assertSame(activity, reentered.mutableContext().baseContext)

                controller.close()
                controller.close()
                assertNull(reentered.parent)
                assertSame(appContext, reentered.mutableContext().baseContext)
                assertThrows(IllegalStateException::class.java) {
                    controller.acquireWebView(activity, Any())
                }
            }
        }
    }
}

private fun android.webkit.WebView.mutableContext(): MutableContextWrapper =
    context as MutableContextWrapper

private fun Context.contains(target: Context): Boolean {
    var current: Context? = this
    val visited = mutableSetOf<Context>()
    while (current != null && visited.add(current)) {
        if (current === target) return true
        current = (current as? ContextWrapper)?.baseContext
    }
    return false
}
