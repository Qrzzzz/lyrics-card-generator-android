package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.content.MutableContextWrapper

/**
 * Keeps the reusable renderer WebView independent from an Activity while it has no UI owner.
 *
 * Owner identity prevents a late release from an old composition from detaching a WebView that
 * has already moved to a new Activity.
 */
internal class RendererWebViewContext(context: Context) {
    private val appContext = context.applicationContext
    val context = MutableContextWrapper(appContext)

    private var owner: Any? = null
    private var closed = false

    fun bind(owner: Any, context: Context) {
        check(!closed) { "Renderer WebView context is closed" }
        this.owner = owner
        this.context.baseContext = context
    }

    fun isOwnedBy(owner: Any): Boolean = !closed && this.owner === owner

    fun release(owner: Any): Boolean {
        if (!isOwnedBy(owner)) return false
        this.owner = null
        context.baseContext = appContext
        return true
    }

    fun close() {
        if (closed) return
        closed = true
        owner = null
        context.baseContext = appContext
    }
}
