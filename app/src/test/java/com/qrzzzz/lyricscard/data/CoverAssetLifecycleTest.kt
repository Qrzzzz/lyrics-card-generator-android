package com.qrzzzz.lyricscard.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectTemplates
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoverAssetLifecycleTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProjectDao
    private lateinit var assetFiles: RecordingCoverAssetFileStore
    private var now = 1_000L
    private var generatedId = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.projectDao()
        assetFiles = RecordingCoverAssetFileStore()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `duplicate then replace keeps shared cover until the copy releases it`() = runTest {
        val repository = repository()
        assetFiles.stored += setOf(ASSET_A, ASSET_B)
        val original = repository.create(coveredProject("original", ASSET_A))
        now = 2_000L
        val duplicate = checkNotNull(repository.duplicate(original.id))

        assertEquals(2, dao.getCoverAssetReferenceCount(ASSET_A))

        now = 3_000L
        repository.save(original.withCover(ASSET_B))

        assertEquals(ASSET_A, repository.getProject(duplicate.id)?.coverAssetId)
        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_A))
        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_B))
        assertFalse(ASSET_A in assetFiles.deleted)
        assertTrue(ASSET_A in assetFiles.stored)
    }

    @Test
    fun `duplicate then remove deletes cover only after both projects release it`() = runTest {
        val repository = repository()
        assetFiles.stored += ASSET_A
        val original = repository.create(coveredProject("original", ASSET_A))
        now = 2_000L
        val duplicate = checkNotNull(repository.duplicate(original.id))

        now = 3_000L
        repository.save(original.withCover(null))

        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_A))
        assertFalse(ASSET_A in assetFiles.deleted)

        now = 4_000L
        repository.save(duplicate.withCover(null))

        assertNull(dao.getCoverAssetReferenceCount(ASSET_A))
        assertEquals(listOf(ASSET_A), assetFiles.deleted)
        assertFalse(ASSET_A in assetFiles.stored)
    }

    @Test
    fun `deleting projects removes the file only with the last persisted reference`() = runTest {
        val repository = repository()
        assetFiles.stored += ASSET_A
        val original = repository.create(coveredProject("original", ASSET_A))
        now = 2_000L
        val duplicate = checkNotNull(repository.duplicate(original.id))

        assertTrue(repository.delete(original.id))
        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_A))
        assertFalse(ASSET_A in assetFiles.deleted)

        assertTrue(repository.delete(duplicate.id))
        assertNull(dao.getCoverAssetReferenceCount(ASSET_A))
        assertEquals(listOf(ASSET_A), assetFiles.deleted)
    }

    @Test
    fun `asset ledger failure rolls back project save and does not delete old file`() = runTest {
        val repository = repository()
        assetFiles.stored += setOf(ASSET_A, ASSET_FAIL)
        val original = repository.create(coveredProject("original", ASSET_A))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_cover_asset_insert
            BEFORE INSERT ON cover_assets
            WHEN NEW.id = '$ASSET_FAIL'
            BEGIN
                SELECT RAISE(ABORT, 'forced asset transaction failure');
            END
            """.trimIndent(),
        )

        now = 2_000L
        try {
            repository.save(original.withCover(ASSET_FAIL))
            fail("Expected the forced asset transaction failure")
        } catch (_: Exception) {
            // The assertions below verify that Room rolled back the earlier project update.
        }

        assertEquals(ASSET_A, repository.getProject(original.id)?.coverAssetId)
        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_A))
        assertNull(dao.getCoverAssetReferenceCount(ASSET_FAIL))
        assertTrue(assetFiles.deleted.isEmpty())
        assertTrue(ASSET_A in assetFiles.stored)
    }

    @Test
    fun `reconciliation repairs the ledger and removes only unreferenced files`() = runTest {
        val repository = repository()
        assetFiles.stored += setOf(ASSET_A, ASSET_ORPHAN)
        repository.create(coveredProject("original", ASSET_A))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO cover_assets (id, reference_count) VALUES ('stale-ledger-row', 99)",
        )

        repository.reconcileCoverAssets()

        assertEquals(setOf(ASSET_A), dao.getTrackedCoverAssetIds().toSet())
        assertEquals(1, dao.getCoverAssetReferenceCount(ASSET_A))
        assertEquals(listOf(ASSET_ORPHAN), assetFiles.deleted)
        assertTrue(ASSET_A in assetFiles.stored)
    }

    @Test
    fun `thumbnail replacement removal and project deletion release only unreferenced files`() = runTest {
        val repository = repository()
        val original = repository.createBlank()
        assetFiles.storedThumbnails += setOf(THUMBNAIL_A, THUMBNAIL_B)

        assertTrue(repository.updateThumbnail(original.id, THUMBNAIL_A))
        assertNull(repository.duplicate(original.id)?.thumbnailPath)

        assertTrue(repository.updateThumbnail(original.id, THUMBNAIL_B))
        assertEquals(listOf(THUMBNAIL_A), assetFiles.deletedThumbnails)

        assertTrue(repository.updateThumbnail(original.id, null))
        assertEquals(listOf(THUMBNAIL_A, THUMBNAIL_B), assetFiles.deletedThumbnails)

        assetFiles.storedThumbnails += THUMBNAIL_A
        assertTrue(repository.updateThumbnail(original.id, THUMBNAIL_A))
        assertTrue(repository.delete(original.id))
        assertEquals(listOf(THUMBNAIL_A, THUMBNAIL_B, THUMBNAIL_A), assetFiles.deletedThumbnails)
    }

    @Test
    fun `startup reconciliation reports corrupt and missing cover while repairing thumbnails`() = runTest {
        val repository = repository()
        val project = repository.create(coveredProject("missing-files", ASSET_A))
        assertTrue(repository.updateThumbnail(project.id, THUMBNAIL_MISSING))
        assetFiles.storedThumbnails += THUMBNAIL_ORPHAN
        dao.upsert(
            ProjectEntity(
                id = "corrupt-project",
                name = "Corrupt",
                schemaVersion = 1,
                rendererVersion = "android-alpha-renderer-1",
                specJson = "{not-json",
                coverAssetId = null,
                thumbnailPath = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastExportedAt = null,
            ),
        )

        val report = repository.reconcileStorage()

        assertEquals(setOf("corrupt-project"), report.corruptProjectIds)
        assertEquals(setOf(project.id), report.missingCoverProjectIds)
        assertEquals(setOf(project.id), report.clearedMissingThumbnailProjectIds)
        assertNull(dao.getById(project.id)?.thumbnailPath)
        assertEquals(listOf(THUMBNAIL_ORPHAN, THUMBNAIL_MISSING), assetFiles.deletedThumbnails)
        assertEquals(1, report.cleanup.deletedOrphanThumbnailCount)
    }

    private fun repository() = ProjectRepository(
        projectDao = dao,
        clock = { now },
        idGenerator = { "copy-${++generatedId}" },
        assetFiles = assetFiles,
    )

    private fun coveredProject(id: String, assetId: String): Project =
        ProjectTemplates.blank(id = id, now = now).withCover(assetId)

    private fun Project.withCover(assetId: String?): Project = copy(
        spec = spec.copy(song = spec.song.copy(coverAssetId = assetId)),
    )

    private companion object {
        const val ASSET_A = "asset-a"
        const val ASSET_B = "asset-b"
        const val ASSET_FAIL = "asset-fail"
        const val ASSET_ORPHAN = "asset-orphan"
        const val THUMBNAIL_A = "/private/thumbnails/a.png"
        const val THUMBNAIL_B = "/private/thumbnails/b.png"
        const val THUMBNAIL_MISSING = "/private/thumbnails/missing.png"
        const val THUMBNAIL_ORPHAN = "/private/thumbnails/orphan.png"
    }
}

private class RecordingCoverAssetFileStore : ProjectStorageFileStore {
    val stored = mutableSetOf<String>()
    val deleted = mutableListOf<String>()
    val storedThumbnails = mutableSetOf<String>()
    val deletedThumbnails = mutableListOf<String>()

    override suspend fun markReferenced(id: String) = Unit

    override suspend fun delete(id: String) {
        deleted += id
        stored -= id
    }

    override suspend fun deleteUnreferenced(referencedIds: Set<String>) {
        stored
            .filterNot(referencedIds::contains)
            .sorted()
            .forEach { delete(it) }
    }

    override suspend fun deleteThumbnail(path: String) {
        deletedThumbnails += path
        storedThumbnails -= path
    }

    override suspend fun reconcileProjectFiles(
        referencedCoverAssetIds: Set<String>,
        referencedThumbnailPaths: Set<String>,
    ): ProjectFileReconcileResult {
        val coverOrphans = stored.filterNot(referencedCoverAssetIds::contains).sorted()
        deleteUnreferenced(referencedCoverAssetIds)
        val thumbnailOrphans = storedThumbnails.filterNot(referencedThumbnailPaths::contains).sorted()
        thumbnailOrphans.forEach { deleteThumbnail(it) }
        return ProjectFileReconcileResult(
            missingCoverAssetIds = referencedCoverAssetIds - stored,
            missingThumbnailPaths = referencedThumbnailPaths - storedThumbnails,
            deletedOrphanCoverCount = coverOrphans.size,
            deletedOrphanThumbnailCount = thumbnailOrphans.size,
        )
    }
}
