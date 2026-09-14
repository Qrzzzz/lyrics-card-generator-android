package com.qrzzzz.lyricscard.renderer

import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXPORT_CHUNK_BYTES = 384 * 1024
private const val MAX_BASE64_CHARS_PER_CHUNK = ((EXPORT_CHUNK_BYTES + 2) / 3) * 4
internal const val MAX_PNG_BYTES = 64L * 1024L * 1024L
private val MAX_EXPORT_CHUNKS = ((MAX_PNG_BYTES + EXPORT_CHUNK_BYTES - 1) / EXPORT_CHUNK_BYTES).toInt()

// Owns partial-file acquisition, bounded ordered writes and cancellation cleanup.
// RendererController retains session/request checks, PNG validation and final promotion.
internal suspend fun deleteStaleExport(file: File) {
    withContext(NonCancellable + Dispatchers.IO) {
        file.delete()
    }
}

internal suspend fun acquireExportAssembly(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    create: () -> ExportAssembly,
): ExportAssembly {
    var acquired: ExportAssembly? = null
    try {
        return withContext(dispatcher) { create().also { acquired = it } }
    } catch (cause: Throwable) {
        // withContext can discard a completed IO result when its caller is cancelled.
        // Retain ownership inside the IO block until the caller actually receives it.
        withContext(NonCancellable + dispatcher) { acquired?.abort() }
        throw cause
    }
}

internal class QueuedExportAssembly(
    val assembly: ExportAssembly,
    private val ioScope: CoroutineScope,
    private val onFailure: (Throwable) -> Unit,
) {
    private val chunks = Channel<ExportChunk>(capacity = MAX_EXPORT_CHUNKS)
    @Volatile
    private var failure: Throwable? = null
    private val worker = ioScope.launch {
        try {
            for (chunk in chunks) {
                assembly.accept(
                    index = chunk.index,
                    total = chunk.total,
                    byteLength = chunk.byteLength,
                    encoded = chunk.encoded,
                )
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            failure = cause
            chunks.cancel()
            onFailure(cause)
        }
    }

    fun offer(index: Int, total: Int, byteLength: Int, encoded: String): Boolean =
        chunks.trySend(ExportChunk(index, total, byteLength, encoded)).isSuccess

    fun seal() {
        chunks.close()
    }

    suspend fun awaitDrained() {
        worker.join()
        failure?.let { throw it }
    }

    suspend fun abortAndJoin() {
        chunks.cancel()
        worker.cancelAndJoin()
        assembly.abort()
    }

    fun abortAsync() {
        chunks.cancel()
        ioScope.launch { abortAndJoin() }
    }

    private data class ExportChunk(
        val index: Int,
        val total: Int,
        val byteLength: Int,
        val encoded: String,
    )
}

internal class ExportAssembly(
    val partFile: File,
    val finalFile: File,
) {
    private var sink: FileOutputStream? = FileOutputStream(partFile, false)
    private var nextIndex = 0
    private var expectedTotal: Int? = null
    private var receivedBytes = 0L

    @Synchronized
    fun accept(index: Int, total: Int, byteLength: Int, encoded: String) {
        val output = sink ?: throw RendererException("导出临时文件已关闭")
        require(total in 1..MAX_EXPORT_CHUNKS) { "导出分块总数无效" }
        require(index == nextIndex && index in 0 until total) { "导出分块顺序无效" }
        require(expectedTotal == null || expectedTotal == total) { "导出分块总数不一致" }
        require(byteLength in 0..EXPORT_CHUNK_BYTES) { "导出分块字节数无效" }
        require(encoded.length <= MAX_BASE64_CHARS_PER_CHUNK) { "导出分块超过内存保护上限" }
        val decoded = runCatching { Base64.getDecoder().decode(encoded) }
            .getOrElse { throw RendererException("导出分块 Base64 无效", it) }
        require(decoded.size == byteLength) { "导出分块字节数不一致" }
        require(receivedBytes + decoded.size <= MAX_PNG_BYTES) { "导出图片超过 Alpha 文件大小上限" }
        output.write(decoded)
        expectedTotal = total
        receivedBytes += decoded.size
        nextIndex += 1
    }

    @Synchronized
    fun finish(reportedBytes: Long, reportedChunks: Int): File {
        val output = sink ?: throw RendererException("导出临时文件已关闭")
        val expected = expectedTotal ?: throw RendererException("导出没有收到任何数据分块")
        require(reportedChunks == expected && nextIndex == expected) { "导出分块数量不完整" }
        require(reportedBytes == receivedBytes && reportedBytes in 1..MAX_PNG_BYTES) { "导出总字节数不一致" }
        output.flush()
        output.fd.sync()
        output.close()
        sink = null
        require(partFile.length() == receivedBytes) { "导出临时文件长度不一致" }
        return partFile
    }

    @Synchronized
    fun abort() {
        runCatching { sink?.close() }
        sink = null
        partFile.delete()
    }

}
