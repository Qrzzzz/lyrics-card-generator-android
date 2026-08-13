package com.qrzzzz.lyricscard.ui

import android.content.Context
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity

internal data class ImeRequestResult(
    val requested: Boolean,
    val targetClass: String,
    val attached: Boolean,
    val windowFocused: Boolean,
)

internal fun ComponentActivity.configureImeTestWindow() {
    // Match MainActivity's manifest contract instead of ComponentActivity's adjust-pan default.
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

internal fun ComponentActivity.requestImeForCurrentFocus(): ImeRequestResult {
    val target = currentFocus ?: window.decorView.findFocus()
    if (target == null) {
        return ImeRequestResult(
            requested = false,
            targetClass = "none",
            attached = false,
            windowFocused = window.decorView.hasWindowFocus(),
        )
    }
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return ImeRequestResult(
        requested = inputMethodManager.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT),
        targetClass = target.javaClass.name,
        attached = target.isAttachedToWindow,
        windowFocused = target.hasWindowFocus(),
    )
}

internal fun ComponentActivity.dismissImeFromCurrentFocus(): Boolean {
    val target = currentFocus ?: window.decorView
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return inputMethodManager.hideSoftInputFromWindow(target.windowToken, 0)
}
