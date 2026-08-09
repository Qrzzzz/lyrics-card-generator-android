package com.qrzzzz.lyricscard.quality

import android.os.Build
import android.os.StrictMode
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal class StrictModeDiskIoMonitor(
    private val appPackagePrefix: String,
) {
    private val violations = AtomicInteger(0)
    private var previousPolicy: StrictMode.ThreadPolicy? = null
    private var listenerExecutor: ExecutorService? = null

    fun install() {
        check(previousPolicy == null) { "StrictMode disk I/O monitor is already installed" }
        val original = StrictMode.getThreadPolicy()
        previousPolicy = original
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val executor = Executors.newSingleThreadExecutor()
            listenerExecutor = executor
            Api28StrictModeViolationListener.install(
                originalPolicy = original,
                listenerExecutor = executor,
                appPackagePrefix = appPackagePrefix,
                violations = violations,
            )
            Log.i(QUALITY_TAG, "strictmode-listener supported=true api=${Build.VERSION.SDK_INT}")
        } else {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder(original)
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .build(),
            )
            Log.i(
                QUALITY_TAG,
                "strictmode-listener supported=false api=${Build.VERSION.SDK_INT} " +
                    "fallback=penaltyLog workloadGates=export-memory-file-cleanup-renderer-state",
            )
        }
    }

    fun assertNoAppViolations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            check(violations.get() == 0) {
                "app main-thread disk I/O violations: ${violations.get()}"
            }
        } else {
            Log.i(
                QUALITY_TAG,
                "strictmode-listener assertion=not-supported api=${Build.VERSION.SDK_INT} " +
                    "workloadGates=still-enforced",
            )
        }
    }

    fun close() {
        previousPolicy?.let(StrictMode::setThreadPolicy)
        previousPolicy = null
        listenerExecutor?.shutdownNow()
        listenerExecutor = null
    }

    private companion object {
        const val QUALITY_TAG = "LCG_QUALITY"
    }
}
