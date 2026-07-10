package com.qrzzzz.lyricscard.data

import android.content.Context
import androidx.room.Room
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
