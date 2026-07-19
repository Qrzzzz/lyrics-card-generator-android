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
    }
}

private class RecordingCoverAssetFileStore : CoverAssetFileStore {
    val stored = mutableSetOf<String>()
    val deleted = mutableListOf<String>()

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
}
