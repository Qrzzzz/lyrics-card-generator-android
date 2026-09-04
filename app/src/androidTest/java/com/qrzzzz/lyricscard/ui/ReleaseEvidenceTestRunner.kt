package com.qrzzzz.lyricscard.ui

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Removes the console credential before AndroidJUnitRunner logs or registers its arguments. */
class ReleaseEvidenceTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        EmulatorConsoleCredential.takeFrom(arguments)
        try {
            super.onCreate(arguments)
        } catch (cause: Throwable) {
            EmulatorConsoleCredential.clear()
            throw cause
        }
    }

    override fun onDestroy() {
        try { super.onDestroy() } finally { EmulatorConsoleCredential.clear() }
    }
}

/** Process memory only; one console connection consumes the credential. */
internal object EmulatorConsoleCredential {
    private const val ARGUMENT = "lcgTalkBackConsoleToken"
    private var token: String? = null

    @Synchronized
    fun takeFrom(arguments: Bundle) {
        token = arguments.getString(ARGUMENT)
        arguments.remove(ARGUMENT)
    }

    @Synchronized
    fun consume(): String = checkNotNull(token.also { token = null }) {
        "Missing emulator console credential; use ReleaseEvidenceTestRunner"
    }

    @Synchronized
    fun clear() { token = null }
}
