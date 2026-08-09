package com.qrzzzz.lyricscard.quality

import android.os.Build
import android.os.StrictMode
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** Keeps android.os.strictmode.Violation bytecode out of API 26-loadable test classes. */
@RequiresApi(Build.VERSION_CODES.P)
internal object Api28StrictModeViolationListener {
    fun install(
        originalPolicy: StrictMode.ThreadPolicy,
        listenerExecutor: Executor,
        appPackagePrefix: String,
        violations: AtomicInteger,
    ) {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder(originalPolicy)
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyListener(listenerExecutor) { violation ->
                    if (violation.stackTrace.any { frame ->
                            frame.className.startsWith(appPackagePrefix)
                        }
                    ) {
                        violations.incrementAndGet()
                    }
                }
                .build(),
        )
    }
}
