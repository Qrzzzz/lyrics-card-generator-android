package com.qrzzzz.lyricscard.renderer

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RendererRequestPolicyTest {
    @Test
    fun `only exact appassets renderer URLs may navigate`() {
        assertTrue(
            RendererRequestPolicy.mayNavigate(
                Uri.parse("https://appassets.androidplatform.net/renderer/index.html"),
            ),
        )

        listOf(
            "http://appassets.androidplatform.net/renderer/index.html",
            "https://appassets.androidplatform.net:444/renderer/index.html",
            "https://user@appassets.androidplatform.net/renderer/index.html",
            "https://appassets.androidplatform.net.attacker.invalid/renderer/index.html",
            "https://attacker.invalid/renderer/index.html",
            "https://appassets.androidplatform.net/media/cover-id",
            "https://appassets.androidplatform.net/renderer/../media/cover-id",
            "https://appassets.androidplatform.net/renderer/%2e%2e/media/cover-id",
        ).forEach { url ->
            assertFalse(url, RendererRequestPolicy.mayNavigate(Uri.parse(url)))
        }
    }

    @Test
    fun `only GET requests for trusted renderer or media resources may be served`() {
        val renderer = Uri.parse("https://appassets.androidplatform.net/renderer/assets/index.js")
        val media = Uri.parse("https://appassets.androidplatform.net/media/cover-id")

        assertTrue(RendererRequestPolicy.mayServe("GET", renderer))
        assertTrue(RendererRequestPolicy.mayServe("get", media))
        assertFalse(RendererRequestPolicy.mayServe("POST", renderer))
        assertFalse(RendererRequestPolicy.mayServe("GET", Uri.parse("https://example.com/renderer/index.html")))
        assertFalse(RendererRequestPolicy.mayServe("GET", Uri.parse("file:///android_asset/renderer/index.html")))
        assertFalse(RendererRequestPolicy.mayServe("GET", Uri.parse("content://covers/cover-id")))
    }
}
