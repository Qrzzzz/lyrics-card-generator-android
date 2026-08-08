package com.qrzzzz.lyricscard.renderer

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.data.AppDatabase
import com.qrzzzz.lyricscard.data.ProjectRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectAssetStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var coverRoot: File
    private lateinit var thumbnailRoot: File
    private lateinit var exportRoot: File
    private val now = 2_000_000_000_000L

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val privateFiles = temporaryFolder.newFolder("files")
        val privateCache = temporaryFolder.newFolder("cache")
        context = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = privateFiles
            override fun getCacheDir(): File = privateCache
        }
        coverRoot = File(context.filesDir, "project-assets")
        thumbnailRoot = File(context.filesDir, "thumbnails")
        exportRoot = File(context.cacheDir, "exports")
        listOf(coverRoot, thumbnailRoot, exportRoot).forEach {
            it.deleteRecursively()
            check(it.mkdirs())
        }
    }

    @After
    fun tearDown() {
        listOf(coverRoot, thumbnailRoot, exportRoot).forEach(File::deleteRecursively)
    }

    @Test
    fun `reconcile repairs cover metadata and removes prior-session orphan files`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        writeCover(REFERENCED_COVER, withMime = false)
        writeCover(ORPHAN_COVER, withMime = true)
        val nestedMissingCover = File(coverRoot, "$MISSING_COVER.image").apply { check(mkdirs()) }
        val nestedSentinel = File(nestedMissingCover, "keep.txt").apply { writeText("keep") }
        File(coverRoot, "$ORPHAN_COVER.image.tmp").writeBytes(byteArrayOf(1))
        makePreviousSession(coverRoot.listFiles().orEmpty().toList())

        val result = store.reconcileProjectFiles(
            referencedCoverAssetIds = setOf(REFERENCED_COVER, MISSING_COVER),
            referencedThumbnailPaths = emptySet(),
        )

        assertEquals(setOf(MISSING_COVER), result.missingCoverAssetIds)
        assertEquals(1, result.deletedOrphanCoverCount)
        assertTrue(File(coverRoot, "$REFERENCED_COVER.image").isFile)
        assertEquals("image/png", File(coverRoot, "$REFERENCED_COVER.mime").readText())
        assertFalse(File(coverRoot, "$ORPHAN_COVER.image").exists())
        assertFalse(File(coverRoot, "$ORPHAN_COVER.mime").exists())
        assertTrue("flat cleanup must not traverse cover directories", nestedSentinel.isFile)
        assertTrue(store.openForWebView(REFERENCED_COVER)?.data?.use { it.read() } == 0x89)
    }

    @Test
    fun `reconcile reports missing thumbnails deletes old orphans and protects current-session files`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        val referenced = File(thumbnailRoot, "project-a.png").also(::writePng)
        val missing = File(thumbnailRoot, "missing.png")
        val oldOrphan = File(thumbnailRoot, "orphan.png").apply { writeBytes(PNG_BYTES) }
        val currentSessionOrphan = File(thumbnailRoot, "in-flight.png").apply { writeBytes(PNG_BYTES) }
        val nestedOrphan = File(thumbnailRoot, "nested-orphan").apply { check(mkdirs()) }
        val nestedSentinel = File(nestedOrphan, "keep.txt").apply { writeText("keep") }
        makePreviousSession(listOf(referenced, oldOrphan, nestedOrphan))
        check(currentSessionOrphan.setLastModified(now + 1L))

        val result = store.reconcileProjectFiles(
            referencedCoverAssetIds = emptySet(),
            referencedThumbnailPaths = setOf(referenced.absolutePath, missing.absolutePath),
        )

        assertEquals(setOf(missing.absolutePath), result.missingThumbnailPaths)
        assertEquals(1, result.deletedOrphanThumbnailCount)
        assertTrue(referenced.isFile)
        assertFalse(oldOrphan.exists())
        assertTrue(currentSessionOrphan.isFile)
        assertTrue("flat cleanup must not traverse nested directories", nestedSentinel.isFile)

        val outside = File(context.filesDir, "outside-thumbnail.png").apply { writeBytes(PNG_BYTES) }
        try {
            store.deleteThumbnail(outside.absolutePath)
            assertTrue("storage cleanup must stay inside the thumbnail root", outside.isFile)
        } finally {
            outside.delete()
        }
    }

    @Test
    fun `reconcile bounds completed export cache and removes stale partial files`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        repeat(35) { index ->
            File(exportRoot, "export-${index.toString().padStart(2, '0')}.png").apply {
                writeBytes(PNG_BYTES)
                check(setLastModified(now - 1_000L - index))
            }
        }
        File(exportRoot, "expired.png").apply {
            writeBytes(PNG_BYTES)
            check(setLastModified(now - EIGHT_DAYS_MS))
        }
        File(exportRoot, "empty.png").apply {
            writeBytes(byteArrayOf())
            check(setLastModified(now - 500L))
        }
        val partial = File(exportRoot, ".interrupted.png.part").apply {
            writeBytes(byteArrayOf(1))
            check(setLastModified(now - 500L))
        }
        File(exportRoot, "unexpected.bin").apply {
            writeBytes(byteArrayOf(1))
            check(setLastModified(now - 500L))
        }
        val nestedPartial = File(exportRoot, "nested.part").apply { check(mkdirs()) }
        val nestedSentinel = File(nestedPartial, "keep.txt").apply { writeText("keep") }
        check(nestedPartial.setLastModified(now - 500L))

        val result = store.reconcileProjectFiles(emptySet(), emptySet())

        assertEquals(1, result.deletedPartialExportCount)
        assertEquals(6, result.prunedExportCount)
        assertFalse(partial.exists())
        assertTrue("flat cleanup must not traverse partial directories", nestedSentinel.isFile)
        assertEquals(
            32,
            exportRoot.listFiles().orEmpty().count { it.isFile && it.extension.equals("png", true) },
        )
    }

    @Test
    fun `repository reconcile clears a referenced non-decodable PNG thumbnail`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = ProjectRepository(
                projectDao = database.projectDao(),
                clock = { now },
                idGenerator = { "invalid-thumbnail-project" },
                assetFiles = store,
            )
            val project = repository.createBlank()
            val invalid = File(thumbnailRoot, "${project.id}.png").apply { writeBytes(PNG_BYTES) }
            assertTrue(invalid.length() > 0L)
            assertTrue(repository.updateThumbnail(project.id, invalid.absolutePath))

            val report = repository.reconcileStorage()

            assertEquals(setOf(project.id), report.clearedMissingThumbnailProjectIds)
            assertEquals(null, database.projectDao().getById(project.id)?.thumbnailPath)
            assertFalse(invalid.exists())
        } finally {
            database.close()
        }
    }

    @Test
    fun `atomic thumbnail replacement publishes complete PNG and rejects unsafe project ids`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        val source = File(context.cacheDir, "source.png")
        writePng(source, width = 960, height = 480)
        val target = File(thumbnailRoot, "project-atomic.png")
        writePng(target, width = 1, height = 1)
        val previousBytes = target.readBytes()

        val written = store.createThumbnailAtomically("project-atomic", source)

        assertEquals(target.canonicalFile, written.canonicalFile)
        assertNotEquals(previousBytes.toList(), written.readBytes().toList())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(written.absolutePath, bounds)
        assertEquals(480, bounds.outWidth)
        assertEquals(240, bounds.outHeight)
        assertTrue(thumbnailRoot.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

        try {
            store.createThumbnailAtomically("../escape", source)
            fail("Expected unsafe project ID rejection")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertFalse(File(context.filesDir, "escape.png").exists())
    }

    @Test
    fun `atomic thumbnail commit and source failures preserve previous target`() = runTest {
        val store = ProjectAssetStore(context, clock = { now })
        val source = File(context.cacheDir, "source.png")
        writePng(source, width = 16, height = 8)
        val target = File(thumbnailRoot, "project-failure.png")
        writePng(target, width = 2, height = 2)
        val previousBytes = target.readBytes()

        try {
            store.createThumbnailAtomically("project-failure", source) { _, _ ->
                throw IOException("forced commit failure")
            }
            fail("Expected forced commit failure")
        } catch (_: IOException) {
            // Expected.
        }
        assertTrue(previousBytes.contentEquals(target.readBytes()))
        assertTrue(thumbnailRoot.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

        val invalidSource = File(context.cacheDir, "invalid-source.png").apply { writeBytes(PNG_BYTES) }
        try {
            store.createThumbnailAtomically("project-failure", invalidSource)
            fail("Expected invalid source rejection")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertTrue(previousBytes.contentEquals(target.readBytes()))
        assertTrue(thumbnailRoot.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    private fun writeCover(id: String, withMime: Boolean) {
        File(coverRoot, "$id.image").writeBytes(PNG_BYTES)
        if (withMime) File(coverRoot, "$id.mime").writeText("image/png")
    }

    private fun makePreviousSession(files: List<File>) {
        files.forEach { check(it.setLastModified(now - 1_000L)) }
    }

    private fun writePng(file: File, width: Int = 2, height: Int = 2) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xff336699.toInt())
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val REFERENCED_COVER = "11111111-1111-4111-8111-111111111111"
        const val ORPHAN_COVER = "22222222-2222-4222-8222-222222222222"
        const val MISSING_COVER = "33333333-3333-4333-8333-333333333333"
        const val EIGHT_DAYS_MS = 8L * 24L * 60L * 60L * 1_000L
        val PNG_BYTES = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00,
        )
    }
}
