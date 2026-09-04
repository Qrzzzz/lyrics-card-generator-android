package com.qrzzzz.lyricscard.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewConfiguration
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket

/** Test-only emulator console client. Unlike InputManager injection, these events enter InputReader. */
internal class EmulatorConsoleTouchInput private constructor(private val socket: Socket) : Closeable {
    private val reader = socket.getInputStream().bufferedReader()
    private val writer = socket.getOutputStream().bufferedWriter()
    private var trackingId = 1
    private var touching = false
    private var lastTouchUp = 0L

    fun awaitReadyForGesture() {
        // Keep separate TalkBack gestures outside Android's multi-tap recognition window.
        val remaining = lastTouchUp + ViewConfiguration.getDoubleTapTimeout() + 150L - SystemClock.elapsedRealtime()
        if (remaining > 0) SystemClock.sleep(remaining)
    }

    fun swipe(direction: Int) {
        require(direction == 1 || direction == -1)
        awaitReadyForGesture()
        val start = if (direction > 0) 9_830 else 22_937
        val finish = if (direction > 0) 22_937 else 9_830
        down(start, 16_384)
        try {
            for (step in 1..6) {
                SystemClock.sleep(12)
                command("event send 3:53:${start + (finish - start) * step / 6} 0:0:0", "swipe")
            }
        } finally { up() }
    }

    fun doubleTap() {
        awaitReadyForGesture()
        repeat(2) {
            down(16_384, 16_384)
            try { SystemClock.sleep(30) } finally { up() }
            SystemClock.sleep(60)
        }
    }

    private fun down(x: Int, y: Int) {
        touching = true
        command("event send 3:47:0 3:57:${trackingId++} 3:53:$x 3:54:$y 3:58:512 0:0:0", "touch down")
    }

    private fun up() {
        if (!touching) return
        command("event send 3:57:-1 3:58:0 0:0:0", "touch up")
        touching = false
        lastTouchUp = SystemClock.elapsedRealtime()
    }

    private fun command(value: String, operation: String) {
        writer.write(value)
        writer.newLine()
        writer.flush()
        reply(operation)
    }

    private fun reply(operation: String) {
        repeat(40) {
            val line = checkNotNull(reader.readLine()) { "Emulator console disconnected during $operation" }.trim()
            if (line == "OK") return
            check(!line.startsWith("KO")) { "Emulator console rejected $operation" }
        }
        error("Emulator console reply was incomplete during $operation")
    }

    override fun close() {
        try { up() } finally { socket.close() }
    }

    companion object {
        fun connect(arguments: Bundle): EmulatorConsoleTouchInput {
            check(Build.VERSION.SDK_INT == 33 && Build.HARDWARE in setOf("ranchu", "goldfish")) {
                "Hardware TalkBack gestures require the API 33 emulator"
            }
            val port = checkNotNull(arguments.getString("lcgTalkBackConsolePort")?.toIntOrNull()) {
                "Missing authorized emulator console port"
            }
            require(port in 1024..65534 && port % 2 == 0) { "Invalid emulator console port" }
            val token = EmulatorConsoleCredential.consume()
            require(token.isNotBlank() && token.none(Char::isWhitespace)) { "Invalid emulator console credential" }
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("10.0.2.2", port), 5_000)
                socket.soTimeout = 5_000
                socket.tcpNoDelay = true
                return EmulatorConsoleTouchInput(socket).apply {
                    reply("greeting")
                    // The credential stays in memory and is never included in logs or exceptions.
                    command("auth $token", "authentication")
                }
            } catch (cause: Throwable) {
                socket.close()
                throw cause
            }
        }
    }
}
