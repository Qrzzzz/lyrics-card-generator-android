package com.qrzzzz.lyricscard.renderer

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportAssemblyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
}
