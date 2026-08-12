package com.qrzzzz.lyricscard.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecJson
import com.qrzzzz.lyricscard.model.SongSpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ProjectDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.projectDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `dao persists updates orders projects and deletes by id`() = runTest {
        dao.upsert(entity(id = "older", updatedAt = 10L))
        dao.upsert(entity(id = "newer", updatedAt = 20L))

        assertEquals(listOf("newer", "older"), dao.observeAll().first().map { it.id })

        assertEquals(1, dao.rename("older", "Renamed", 30L))
        assertEquals("Renamed", dao.getById("older")?.name)
        assertEquals("older", dao.observeAll().first().first().id)

        assertEquals(1, dao.updateThumbnail("older", "/private/thumb.png", 31L))
        assertEquals("/private/thumb.png", dao.getById("older")?.thumbnailPath)

        assertEquals(1, dao.markExported("older", 32L, 32L))
        assertEquals(32L, dao.getById("older")?.lastExportedAt)

        val editedSpec = RenderSpec(song = SongSpec(title = "Edited"))
        assertEquals(
            1,
            dao.updateEditable(
                id = "older",
                name = "Edited project",
                schemaVersion = editedSpec.schemaVersion,
                rendererVersion = editedSpec.rendererVersion,
                specJson = RenderSpecJson.encode(editedSpec),
                coverAssetId = null,
                requestedUpdatedAt = 25L,
            ),
        )
        val edited = dao.getById("older")!!
        assertEquals("Edited project", edited.name)
        assertEquals("/private/thumb.png", edited.thumbnailPath)
        assertEquals(32L, edited.lastExportedAt)
        assertEquals(33L, edited.updatedAt)

        assertEquals(1, dao.deleteById("older"))
        assertNull(dao.getById("older"))
    }

    @Test
    fun `migration 1 to 2 preserves projects and rebuilds shared cover references`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-v1.db"
        context.deleteDatabase(databaseName)
        val assetId = "legacy-shared-cover"
        val legacySpec = RenderSpec(song = SongSpec(title = "Legacy", coverAssetId = assetId))
        val legacyJson = RenderSpecJson.encode(legacySpec)
        val legacyDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            legacyDatabase.execSQL(
                """
                CREATE TABLE projects (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    renderer_version TEXT NOT NULL,
                    spec_json TEXT NOT NULL,
                    cover_asset_id TEXT,
                    thumbnail_path TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_exported_at INTEGER,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            legacyDatabase.execSQL(
                "CREATE INDEX index_projects_updated_at ON projects (updated_at)",
            )
            listOf("legacy-a", "legacy-b").forEachIndexed { index, id ->
                legacyDatabase.execSQL(
                    """
                    INSERT INTO projects (
                        id, name, schema_version, renderer_version, spec_json, cover_asset_id,
                        thumbnail_path, created_at, updated_at, last_exported_at
                    ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        id,
                        "Legacy ${index + 1}",
                        legacySpec.schemaVersion,
                        legacySpec.rendererVersion,
                        legacyJson,
                        assetId,
                        100L + index,
                        200L + index,
                    ),
                )
            }
            legacyDatabase.version = 1
        } finally {
            legacyDatabase.close()
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        try {
            val migratedDao = migrated.projectDao()
            assertEquals(assetId, migratedDao.getById("legacy-a")?.coverAssetId)
            assertEquals(assetId, migratedDao.getById("legacy-b")?.coverAssetId)
            assertEquals(2, migratedDao.getCoverAssetReferenceCount(assetId))
            assertEquals(AppDatabase.VERSION, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun `version 2 database opens as current without losing legacy alpha project data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(2, AppDatabase.VERSION)
        val databaseName = "current-v2.db"
        context.deleteDatabase(databaseName)
        val assetId = "legacy-v2-cover"
        val thumbnailPath = "/private/thumbnails/legacy-v2.png"
        val legacySpec = RenderSpec(song = SongSpec(title = "Alpha project", coverAssetId = assetId))
        assertEquals("android-alpha-renderer-1", legacySpec.rendererVersion)
        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        val rawDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            rawDatabase.execSQL(
                """
                CREATE TABLE projects (
                    id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    schema_version INTEGER NOT NULL,
                    renderer_version TEXT NOT NULL,
                    spec_json TEXT NOT NULL,
                    cover_asset_id TEXT,
                    thumbnail_path TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    last_exported_at INTEGER,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            rawDatabase.execSQL("CREATE INDEX index_projects_updated_at ON projects (updated_at)")
            rawDatabase.execSQL(
                """
                CREATE TABLE cover_assets (
                    id TEXT NOT NULL,
                    reference_count INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
                """.trimIndent(),
            )
            rawDatabase.execSQL(
                """
                INSERT INTO projects (
                    id, name, schema_version, renderer_version, spec_json, cover_asset_id,
                    thumbnail_path, created_at, updated_at, last_exported_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy-v2",
                    "Legacy v2",
                    legacySpec.schemaVersion,
                    legacySpec.rendererVersion,
                    RenderSpecJson.encode(legacySpec),
                    assetId,
                    thumbnailPath,
                    100L,
                    200L,
                    190L,
                ),
            )
            rawDatabase.execSQL(
                "INSERT INTO cover_assets (id, reference_count) VALUES (?, ?)",
                arrayOf<Any?>(assetId, 1),
            )
            rawDatabase.version = 2
        } finally {
            rawDatabase.close()
        }

        val current = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .allowMainThreadQueries()
            .build()
        try {
            val currentDao = current.projectDao()
            val entity = checkNotNull(currentDao.getById("legacy-v2"))
            assertEquals(AppDatabase.VERSION, current.openHelper.readableDatabase.version)
            assertEquals("android-alpha-renderer-1", entity.rendererVersion)
            assertEquals(thumbnailPath, entity.thumbnailPath)
            assertEquals(190L, entity.lastExportedAt)
            assertEquals(1, currentDao.getCoverAssetReferenceCount(assetId))
            assertEquals(legacySpec, ProjectRepository(currentDao).getProject(entity.id)?.spec)
        } finally {
            current.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun entity(id: String, updatedAt: Long) = ProjectEntity(
        id = id,
        name = id,
        schemaVersion = RenderSpec.SCHEMA_VERSION,
        rendererVersion = RenderSpec.DEFAULT_RENDERER_VERSION,
        specJson = RenderSpecJson.encode(RenderSpec()),
        coverAssetId = null,
        thumbnailPath = null,
        createdAt = 0L,
        updatedAt = updatedAt,
        lastExportedAt = null,
    )
}
