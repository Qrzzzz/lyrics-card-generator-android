package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.webkit.WebResourceResponse
import com.qrzzzz.lyricscard.data.ProjectFileReconcileResult
import com.qrzzzz.lyricscard.data.ProjectStorageFileStore
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Private, logical-ID based storage for project cover images.
 *
 * Android URIs never enter RenderSpec. The local renderer can request only one UUID-shaped ID,
 * and the WebView handler resolves it to a private file without exposing a filesystem path.
 */
class ProjectAssetStore(
    context: Context,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProjectStorageFileStore {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "project-assets")
    private val thumbnailRoot = File(appContext.filesDir, "thumbnails")
    private val exportRoot = File(appContext.cacheDir, "exports")
    private val storageSessionStartedAt = clock()
    private val fileMutex = Mutex()
    private val pendingAssetIds = mutableSetOf<String>()

    suspend fun importCover(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            fileMutex.withLock { importCoverLocked(input) }
        }
            ?: error("无法打开所选图片")
    }

    suspend fun importCover(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(bytes.isNotEmpty()) { "无法读取空图片" }
        require(bytes.size <= MAX_COVER_BYTES) { "封面图片不能超过 25 MB" }
        ByteArrayInputStream(bytes).use { input ->
            fileMutex.withLock { importCoverLocked(input) }
        }
    }

    private fun importCoverLocked(input: InputStream): String {
        check(root.isDirectory || root.mkdirs()) { "无法创建封面存储目录" }
        val id = UUID.randomUUID().toString()
        val dataFile = dataFile(id)
        val mimeFile = mimeFile(id)
        val importFile = File(root, "$id.import")
        val dataTemp = File(root, "$id.image.tmp")
        val mimeTemp = File(root, "$id.mime.tmp")

        return try {
            importFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_COVER_BYTES) { "封面图片不能超过 25 MB" }
                    output.write(buffer, 0, read)
                }
                require(total > 0) { "无法读取空图片" }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(importFile.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选文件不是可解码的图片" }
            require(bounds.outWidth <= MAX_SOURCE_EDGE && bounds.outHeight <= MAX_SOURCE_EDGE) {
                "封面原图的长边不能超过 $MAX_SOURCE_EDGE 像素"
            }
            require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_SOURCE_PIXELS) {
                "封面原图像素过大"
            }

            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > STORED_COVER_EDGE) {
                sampleSize *= 2
            }
            var working: Bitmap? = BitmapFactory.decodeFile(
                importFile.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            ) ?: error("无法解码封面图片")
            try {
                val oriented = applyExifOrientation(checkNotNull(working), importFile)
                if (oriented !== working) {
                    working?.recycle()
                    working = oriented
                }
                val scale = minOf(
                    1f,
                    STORED_COVER_EDGE.toFloat() / maxOf(oriented.width, oriented.height).toFloat(),
                )
                val width = (oriented.width * scale).toInt().coerceAtLeast(1)
                val height = (oriented.height * scale).toInt().coerceAtLeast(1)
                val normalized = if (width == oriented.width && height == oriented.height) {
                    oriented
                } else {
                    Bitmap.createScaledBitmap(oriented, width, height, true).also {
                        oriented.recycle()
                        working = it
                    }
                }
                val hasAlpha = normalized.hasAlpha()
                val mimeType = if (hasAlpha) "image/png" else "image/jpeg"
                dataTemp.outputStream().use { output ->
                    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    check(normalized.compress(format, 92, output)) { "封面图片编码失败" }
                }
                mimeTemp.writeText(mimeType, Charsets.UTF_8)
                check(mimeTemp.renameTo(mimeFile)) { "无法保存封面类型" }
                check(dataTemp.renameTo(dataFile)) { "无法保存封面图片" }
            } finally {
                working?.recycle()
            }
            id.also(pendingAssetIds::add)
        } catch (cause: Throwable) {
            dataFile.delete()
            mimeFile.delete()
            dataTemp.delete()
            mimeTemp.delete()
            throw cause
        } finally {
            importFile.delete()
        }
    }

    fun openForWebView(path: String): WebResourceResponse? {
        val id = path.substringBefore('?').substringBefore('#')
        val file = safeCoverDataFile(id) ?: return null
        val mime = detectStoredMime(file) ?: return null
        return try {
            WebResourceResponse(mime, null, FileInputStream(file)).also {
                it.responseHeaders = mapOf(
                    "Cache-Control" to "public, max-age=31536000, immutable",
                    "X-Content-Type-Options" to "nosniff",
                )
            }
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun markReferenced(id: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            pendingAssetIds.remove(id)
            Unit
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            pendingAssetIds.remove(id)
            deleteFiles(id)
            Unit
        }
    }

    /**
     * Creates or replaces one project thumbnail without exposing a partially written target.
     *
     * The source is decoded and resized off the main thread, then an fsynced same-directory temp
     * file is atomically moved over the previous thumbnail. Any failure leaves the old target in
     * place and removes the temp file.
     */
    suspend fun createThumbnailAtomically(projectId: String, source: File): File =
        createThumbnailAtomically(projectId, source, ::moveThumbnailAtomically)

    internal suspend fun createThumbnailAtomically(
        projectId: String,
        source: File,
        atomicMove: (File, File) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(PROJECT_ID.matches(projectId)) { "项目 ID 无效" }
        fileMutex.withLock {
            check(thumbnailRoot.isDirectory || thumbnailRoot.mkdirs()) { "无法创建缩略图存储目录" }
            val target = File(thumbnailRoot, "$projectId.png")
            val temporary = File(thumbnailRoot, ".$projectId-${UUID.randomUUID()}.png.tmp")
            try {
                writeThumbnail(source, temporary)
                check(isUsableThumbnail(temporary)) { "缩略图写入校验失败" }
                atomicMove(temporary, target)
                target
            } finally {
                temporary.delete()
            }
        }
    }

    override suspend fun deleteUnreferenced(referencedIds: Set<String>) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            reconcileCoverFilesLocked(referencedIds)
            Unit
        }
    }

    override suspend fun deleteThumbnail(path: String) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            thumbnailFile(path)?.delete()
            Unit
        }
    }

    override suspend fun reconcileProjectFiles(
        referencedCoverAssetIds: Set<String>,
        referencedThumbnailPaths: Set<String>,
    ): ProjectFileReconcileResult = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val covers = reconcileCoverFilesLocked(referencedCoverAssetIds)
            val thumbnails = reconcileThumbnailFilesLocked(referencedThumbnailPaths)
            val exports = reconcileExportFilesLocked()
            ProjectFileReconcileResult(
                missingCoverAssetIds = covers.missingIds,
                missingThumbnailPaths = thumbnails.missingPaths,
                deletedOrphanCoverCount = covers.deletedOrphanCount,
                deletedOrphanThumbnailCount = thumbnails.deletedOrphanCount,
                deletedPartialExportCount = exports.deletedPartialCount,
                prunedExportCount = exports.prunedCount,
            )
        }
    }

    private fun reconcileCoverFilesLocked(referencedIds: Set<String>): CoverReconcileOutcome {
        if (!root.isDirectory && !root.mkdirs()) {
            return CoverReconcileOutcome(missingIds = referencedIds)
        }
        pendingAssetIds.removeAll(referencedIds)
        val storedIds = root.listFiles()
            .orEmpty()
            .mapNotNull { file ->
                when {
                    file.name.endsWith(DATA_SUFFIX) -> file.name.removeSuffix(DATA_SUFFIX)
                    file.name.endsWith(MIME_SUFFIX) -> file.name.removeSuffix(MIME_SUFFIX)
                    else -> null
                }
            }
            .filter(ASSET_ID::matches)
            .toSet()
        var deletedOrphanCount = 0
        storedIds
            .filterNot { it in referencedIds || it in pendingAssetIds }
            .forEach { id ->
                if (deleteFiles(id)) deletedOrphanCount += 1
            }
        root.listFiles()
            .orEmpty()
            .filter { it.name.endsWith(".tmp") || it.name.endsWith(".import") }
            .forEach(File::delete)
        val missingIds = referencedIds
            .filterNot(::ensureUsableCover)
            .toSet()
        return CoverReconcileOutcome(
            missingIds = missingIds,
            deletedOrphanCount = deletedOrphanCount,
        )
    }

    private fun ensureUsableCover(id: String): Boolean {
        val data = safeCoverDataFile(id) ?: return false
        if (data.length() <= 0L) return false
        val detectedMime = detectStoredMime(data) ?: return false
        val metadata = safeCoverMetadataFile(id) ?: return false
        val storedMime = runCatching { metadata.takeIf(File::isFile)?.readText(Charsets.UTF_8) }.getOrNull()
        if (storedMime != detectedMime) {
            if (runCatching { metadata.writeText(detectedMime, Charsets.UTF_8) }.isFailure) return false
        }
        return true
    }

    private fun safeCoverDataFile(id: String): File? {
        if (!ASSET_ID.matches(id)) return null
        return safeFlatFile(dataFile(id), requireExistingRegularFile = true)
    }

    private fun safeCoverMetadataFile(id: String): File? {
        if (!ASSET_ID.matches(id)) return null
        return safeFlatFile(mimeFile(id), requireExistingRegularFile = false)
    }

    private fun safeFlatFile(candidate: File, requireExistingRegularFile: Boolean): File? = runCatching {
        if (Files.isSymbolicLink(candidate.toPath())) return@runCatching null
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        candidate.takeIf {
            canonicalCandidate.parentFile == canonicalRoot &&
                if (requireExistingRegularFile) it.isFile else !it.exists() || it.isFile
        }
    }.getOrNull()

    private fun reconcileThumbnailFilesLocked(referencedPaths: Set<String>): ThumbnailReconcileOutcome {
        if (!thumbnailRoot.isDirectory && !thumbnailRoot.mkdirs()) {
            return ThumbnailReconcileOutcome(missingPaths = referencedPaths)
        }
        val safeReferences = referencedPaths.mapNotNull(::thumbnailFile).toSet()
        val missingPaths = referencedPaths
            .filter { path -> thumbnailFile(path)?.let(::isUsableThumbnail) != true }
            .toSet()
        var deletedOrphanCount = 0
        thumbnailRoot.listFiles().orEmpty()
            .filterNot { candidate -> candidate.canonicalFile in safeReferences }
            .filter { it.lastModified() < storageSessionStartedAt }
            .forEach { orphan ->
                if (orphan.delete()) deletedOrphanCount += 1
            }
        return ThumbnailReconcileOutcome(
            missingPaths = missingPaths,
            deletedOrphanCount = deletedOrphanCount,
        )
    }

    private fun reconcileExportFilesLocked(): ExportReconcileOutcome {
        if (!exportRoot.isDirectory) return ExportReconcileOutcome()
        val previousSessionFiles = exportRoot.listFiles().orEmpty()
            .filter { it.lastModified() < storageSessionStartedAt }
        var deletedPartialCount = 0
        previousSessionFiles
            .filter { it.name.endsWith(".part") || it.name.endsWith(".tmp") }
            .forEach { file ->
                if (file.delete()) deletedPartialCount += 1
            }

        var prunedCount = 0
        val now = maxOf(clock(), storageSessionStartedAt)
        val completed = previousSessionFiles
            .filter { it.isFile && it.name.endsWith(".png", ignoreCase = true) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenBy { it.name })
        var keptCount = 0
        var keptBytes = 0L
        for (file in completed) {
            val age = (now - file.lastModified()).coerceAtLeast(0L)
            val keep = file.length() > 0L &&
                age <= MAX_EXPORT_AGE_MS &&
                keptCount < MAX_RETAINED_EXPORTS &&
                keptBytes <= MAX_RETAINED_EXPORT_BYTES - file.length()
            if (keep) {
                keptCount += 1
                keptBytes += file.length()
            } else if (file.delete()) {
                prunedCount += 1
            }
        }
        previousSessionFiles
            .filterNot { it.name.endsWith(".part") || it.name.endsWith(".tmp") }
            .filterNot { it.name.endsWith(".png", ignoreCase = true) }
            .forEach { file ->
                if (file.delete()) prunedCount += 1
            }
        return ExportReconcileOutcome(
            deletedPartialCount = deletedPartialCount,
            prunedCount = prunedCount,
        )
    }

    private fun thumbnailFile(path: String): File? = runCatching {
        val candidate = File(path).canonicalFile
        val canonicalRoot = thumbnailRoot.canonicalFile
        candidate.takeIf {
            it.parentFile == canonicalRoot && it.name.endsWith(".png", ignoreCase = true)
        }
    }.getOrNull()

    private fun writeThumbnail(source: File, target: File) {
        require(source.isFile) { "无法读取导出图片" }
        val bounds = decodeImageBounds(source)
        require(bounds != null) { "无法读取导出图片" }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > THUMBNAIL_EDGE * 2) {
            sampleSize *= 2
        }
        val bitmap = try {
            BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } catch (_: RuntimeException) {
            null
        } ?: throw IllegalArgumentException("无法生成项目缩略图")
        var scaled: Bitmap? = null
        try {
            val scale = minOf(
                1f,
                THUMBNAIL_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat(),
            )
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            scaled = if (width == bitmap.width && height == bitmap.height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
            FileOutputStream(target).use { output ->
                check(checkNotNull(scaled).compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "缩略图编码失败"
                }
                output.flush()
                output.fd.sync()
            }
        } finally {
            scaled?.takeIf { it !== bitmap }?.recycle()
            bitmap.recycle()
        }
    }

    private fun isUsableThumbnail(file: File): Boolean {
        if (!file.isFile || file.length() <= PNG_SIGNATURE.size) return false
        if (detectStoredMime(file) != "image/png") return false
        return decodeImageBounds(file) != null
    }

    private fun decodeImageBounds(file: File): BitmapFactory.Options? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun moveThumbnailAtomically(source: File, target: File) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private fun deleteFiles(id: String): Boolean {
        if (!ASSET_ID.matches(id)) return false
        val data = dataFile(id)
        val mime = mimeFile(id)
        val existed = data.exists() || mime.exists()
        data.delete()
        mime.delete()
        return existed && !data.exists() && !mime.exists()
    }

    private fun dataFile(id: String) = File(root, "$id$DATA_SUFFIX")
    private fun mimeFile(id: String) = File(root, "$id$MIME_SUFFIX")

    private fun detectStoredMime(file: File): String? = runCatching {
        val header = ByteArray(12)
        val count = FileInputStream(file).use { it.read(header) }
        when {
            count >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { index ->
                (header[index].toInt() and 0xff) == PNG_SIGNATURE[index]
            } -> "image/png"
            count >= 3 &&
                (header[0].toInt() and 0xff) == 0xff &&
                (header[1].toInt() and 0xff) == 0xd8 &&
                (header[2].toInt() and 0xff) == 0xff -> "image/jpeg"
            else -> null
        }
    }.getOrNull()

    private fun applyExifOrientation(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private data class CoverReconcileOutcome(
        val missingIds: Set<String> = emptySet(),
        val deletedOrphanCount: Int = 0,
    )

    private data class ThumbnailReconcileOutcome(
        val missingPaths: Set<String> = emptySet(),
        val deletedOrphanCount: Int = 0,
    )

    private data class ExportReconcileOutcome(
        val deletedPartialCount: Int = 0,
        val prunedCount: Int = 0,
    )

    private companion object {
        val ASSET_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        val PROJECT_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
        val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        const val MAX_COVER_BYTES = 25L * 1024L * 1024L
        const val MAX_SOURCE_EDGE = 20_000
        const val MAX_SOURCE_PIXELS = 160_000_000L
        const val STORED_COVER_EDGE = 2_048
        const val THUMBNAIL_EDGE = 480
        const val DATA_SUFFIX = ".image"
        const val MIME_SUFFIX = ".mime"
        const val MAX_EXPORT_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
        const val MAX_RETAINED_EXPORTS = 32
        const val MAX_RETAINED_EXPORT_BYTES = 256L * 1024L * 1024L
    }
}
