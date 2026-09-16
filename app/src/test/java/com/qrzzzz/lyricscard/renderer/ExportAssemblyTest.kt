package com.qrzzzz.lyricscard.renderer

import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportAssemblyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `cancellation after file creation but before delivery removes the partial`() = runTest {
        val part = temporaryFolder.root.resolve("cancel-before-delivery.png.part")
        val final = temporaryFolder.root.resolve("cancel-before-delivery.png")
        var created = false
        val export = launch(start = CoroutineStart.LAZY) {
            val caller = this
            acquireExportAssembly(StandardTestDispatcher(testScheduler)) {
                ExportAssembly(part, final).also {
                    created = true
                    caller.cancel()
                }
            }
            error("Cancelled acquisition must not return an assembly")
        }
        export.start()
        export.join()
        assertTrue(created)
        assertTrue(export.isCancelled)
        assertFalse(part.exists())
        assertFalse(final.exists())
    }

    @Test
    fun `successful acquisition leaves the file owned by the caller`() = runTest {
        val part = temporaryFolder.root.resolve("acquired.png.part")
        val assembly = acquireExportAssembly(StandardTestDispatcher(testScheduler)) {
            ExportAssembly(part, temporaryFolder.root.resolve("acquired.png"))
        }
        try {
            assertTrue(part.exists())
            val bytes = byteArrayOf(1, 2, 3)
            assembly.accept(0, 1, bytes.size, Base64.getEncoder().encodeToString(bytes))
            assertArrayEquals(bytes, assembly.finish(3L, 1).readBytes())
        } finally {
            assembly.abort()
        }
        assertFalse(part.exists())
    }

    @Test
    fun `numbered chunks are assembled without holding the complete payload`() {
        val source = ByteArray(700_000) { index -> (index % 251).toByte() }
        val part = temporaryFolder.newFile("export.png.part")
        part.delete()
        val final = temporaryFolder.root.resolve("export.png")
        val assembly = ExportAssembly(part, final)
        val chunkSize = 384 * 1024
        val chunks = source.asList().chunked(chunkSize).map { it.toByteArray() }

        chunks.forEachIndexed { index, bytes ->
            assembly.accept(
                index = index,
                total = chunks.size,
                byteLength = bytes.size,
                encoded = Base64.getEncoder().encodeToString(bytes),
            )
        }

        val assembled = assembly.finish(source.size.toLong(), chunks.size)
        assertArrayEquals(source, assembled.readBytes())
        assertFalse(final.exists())
    }

    @Test
    fun `out of order chunk is rejected and partial file can be aborted`() {
        val part = temporaryFolder.root.resolve("bad.png.part")
        val assembly = ExportAssembly(part, temporaryFolder.root.resolve("bad.png"))
        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            assembly.accept(index = 1, total = 2, byteLength = 3, encoded = encoded)
        }
        assembly.abort()

        assertFalse(part.exists())
    }

    @Test
    fun `queued invalid chunk reports failure and abort removes partial without publishing`() = runTest {
        val part = temporaryFolder.root.resolve("queued-invalid.png.part")
        val final = temporaryFolder.root.resolve("queued-invalid.png")
        val assembly = ExportAssembly(part, final)
        val failures = mutableListOf<Throwable>()
        val queue = QueuedExportAssembly(assembly, this) { failures += it }
        assertTrue(queue.offer(1, 2, 1, Base64.getEncoder().encodeToString(byteArrayOf(1))))
        queue.seal()
        val failure = runCatching { queue.awaitDrained() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf(failure), failures)
        assertFalse(queue.offer(0, 1, 1, "AQ=="))
        queue.abortAndJoin()
        assertFalse(part.exists())
        assertFalse(final.exists())
    }

    @Test
    fun `queued chunks preserve order while assembly runs on the io scope`() = runBlocking {
        val source = ByteArray(900_000) { index -> (index % 239).toByte() }
        val part = temporaryFolder.root.resolve("queued.png.part")
        val assembly = ExportAssembly(part, temporaryFolder.root.resolve("queued.png"))
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failures = mutableListOf<Throwable>()
        val queue = QueuedExportAssembly(assembly, ioScope) { cause ->
            synchronized(failures) { failures += cause }
        }
        val chunks = source.asList().chunked(384 * 1024).map { values -> values.toByteArray() }

        try {
            chunks.forEachIndexed { index, bytes ->
                assertTrue(
                    queue.offer(
                        index = index,
                        total = chunks.size,
                        byteLength = bytes.size,
                        encoded = Base64.getEncoder().encodeToString(bytes),
                    ),
                )
            }
            queue.seal()
            queue.awaitDrained()

            assertTrue(synchronized(failures) { failures.isEmpty() })
            assertArrayEquals(source, assembly.finish(source.size.toLong(), chunks.size).readBytes())
        } finally {
            assembly.abort()
            ioScope.cancel()
        }
    }
}
