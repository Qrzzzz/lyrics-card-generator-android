package com.qrzzzz.lyricscard.data

import com.qrzzzz.lyricscard.model.ContentSpec
import com.qrzzzz.lyricscard.model.InvalidRenderSpecException
import com.qrzzzz.lyricscard.model.LyricTextLimits
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.model.RenderSpec
import com.qrzzzz.lyricscard.model.RenderSpecJson
import com.qrzzzz.lyricscard.model.RenderSpecViolation
import com.qrzzzz.lyricscard.model.SongSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectRepositoryTest {
    @Test
    fun `blank project is stored as validated defaulted JSON`() = runTest {
        val dao = FakeProjectDao()
        val repository = ProjectRepository(
            projectDao = dao,
            clock = { 1_000L },
            idGenerator = { "project-1" },
        )

        val created = repository.createBlank()
        val stored = dao.getById(created.id)!!
        val decoded = RenderSpecJson.decode(stored.specJson)

        assertEquals("project-1", created.id)
        assertEquals(RenderSpec.SCHEMA_VERSION, stored.schemaVersion)
        assertEquals(RenderSpec.DEFAULT_RENDERER_VERSION, stored.rendererVersion)
        assertTrue(stored.specJson.contains("\"pixelRatio\":2"))
        assertEquals(created.spec, decoded)
        assertEquals(created, repository.getProject(created.id))
    }

    @Test
    fun `save advances timestamp and persists complete edited spec`() = runTest {
        val dao = FakeProjectDao()
        var now = 1_000L
        val repository = ProjectRepository(dao, clock = { now }, idGenerator = { "project-1" })
        val created = repository.createBlank()
        now = 2_000L

        val saved = repository.save(
            created.copy(
                spec = created.spec.copy(
                    song = SongSpec(title = "Edited", artist = "Artist"),
                ),
            ),
        )

        assertEquals(2_000L, saved.updatedAt)
        assertEquals("Edited", repository.getProject(created.id)?.spec?.song?.title)
        assertEquals("Edited", repository.observeProject(created.id).first()?.spec?.song?.title)
    }

    @Test
    fun `stale editable save preserves newer export metadata`() = runTest {
        val dao = FakeProjectDao()
        var now = 1_000L
        val repository = ProjectRepository(dao, clock = { now }, idGenerator = { "project-1" })
        val staleSnapshot = repository.createBlank()
        now = 2_000L
        assertTrue(repository.updateThumbnail(staleSnapshot.id, "thumb.png"))
        now = 3_000L
        assertTrue(repository.markExported(staleSnapshot.id))

        val saved = repository.save(
            staleSnapshot.copy(spec = staleSnapshot.spec.copy(song = SongSpec(title = "Edited"))),
        )

        assertEquals("thumb.png", saved.thumbnailPath)
        assertEquals(3_000L, saved.lastExportedAt)
        assertEquals("Edited", saved.spec.song.title)
    }

    @Test
    fun `save after deletion fails without resurrecting project`() = runTest {
        val dao = FakeProjectDao()
        val repository = ProjectRepository(dao, clock = { 1_000L }, idGenerator = { "project-1" })
        val created = repository.createBlank()
        assertTrue(repository.delete(created.id))

        try {
            repository.save(created)
            fail("Expected save to fail after deletion")
        } catch (_: IllegalStateException) {
            // Missing rows must never be recreated by autosave.
        }

        assertNull(repository.getProject(created.id))
    }

    @Test
    fun `summary operations rename duplicate export and delete without losing spec`() = runTest {
        val dao = FakeProjectDao()
        var now = 1_000L
        var idCounter = 0
        val repository = ProjectRepository(
            projectDao = dao,
            clock = { now },
            idGenerator = { "project-${++idCounter}" },
        )
        val original = repository.createSample()
        now = 2_000L

        assertTrue(repository.rename(original.id, "  Renamed  "))
        val renamed = repository.getProject(original.id)!!
        assertEquals("Renamed", renamed.name)
        assertEquals(original.spec, renamed.spec)

        now = 3_000L
        val duplicate = repository.duplicate(original.id)!!
        assertNotEquals(original.id, duplicate.id)
        assertEquals("Renamed 副本", duplicate.name)
        assertEquals(renamed.spec, duplicate.spec)
        assertNull(duplicate.thumbnailPath)
        assertNull(duplicate.lastExportedAt)

        now = 4_000L
        assertTrue(repository.markExported(duplicate.id))
        assertEquals(4_000L, repository.getProject(duplicate.id)?.lastExportedAt)

        val summaries = repository.observeProjects().first()
        assertEquals(listOf(duplicate.id, original.id), summaries.map { it.id })

        assertTrue(repository.delete(original.id))
        assertFalse(repository.delete("missing"))
        assertNull(repository.getProject(original.id))
    }

    @Test
    fun `mismatched redundant schema metadata is treated as corruption`() = runTest {
        val dao = FakeProjectDao()
        dao.upsert(
            ProjectEntity(
                id = "corrupt",
                name = "Corrupt",
                schemaVersion = 99,
                rendererVersion = RenderSpec.DEFAULT_RENDERER_VERSION,
                specJson = RenderSpecJson.encode(RenderSpec()),
                coverAssetId = null,
                thumbnailPath = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastExportedAt = null,
            ),
        )
        val repository = ProjectRepository(dao)

        try {
            repository.getProject("corrupt")
            fail("Expected CorruptProjectException")
        } catch (_: CorruptProjectException) {
            // Expected: redundant entity columns must agree with the serialized contract.
        }
    }

    @Test
    fun `stored over-limit lyrics are rejected without modifying the project row`() = runTest {
        val dao = FakeProjectDao()
        val invalidSpec = RenderSpec(
            content = ContentSpec(lyrics = "\n".repeat(LyricTextLimits.MAX_LINES)),
        )
        val rawSpecJson = RenderSpecJson.format.encodeToString(RenderSpec.serializer(), invalidSpec)
        val entity = ProjectEntity(
            id = "over-limit",
            name = "Over limit",
            schemaVersion = RenderSpec.SCHEMA_VERSION,
            rendererVersion = RenderSpec.DEFAULT_RENDERER_VERSION,
            specJson = rawSpecJson,
            coverAssetId = null,
            thumbnailPath = null,
            createdAt = 0L,
            updatedAt = 0L,
            lastExportedAt = null,
        )
        dao.upsert(entity)
        val repository = ProjectRepository(dao)

        val failure = try {
            repository.getProject(entity.id)
            fail("Expected CorruptProjectException")
            null
        } catch (cause: CorruptProjectException) {
            cause
        }
        val lineViolation = generateSequence(failure as Throwable?) { it.cause }
            .filterIsInstance<InvalidRenderSpecException>()
            .flatMap { it.violations.asSequence() }
            .single { it.constraint == RenderSpecViolation.Constraint.MAX_LINES }

        assertEquals(LyricTextLimits.MAX_LINES + 1, lineViolation.actual)
        assertEquals(entity, dao.getById(entity.id))
    }
}

private class FakeProjectDao : ProjectDao() {
    private val entities = MutableStateFlow<List<ProjectEntity>>(emptyList())
    private val coverAssets = mutableMapOf<String, CoverAssetEntity>()

    override fun observeAll(): Flow<List<ProjectEntity>> = entities.map { projects ->
        projects.sortedWith(compareByDescending<ProjectEntity> { it.updatedAt }.thenBy { it.id })
    }

    override fun observeById(id: String): Flow<ProjectEntity?> =
        entities.map { projects -> projects.firstOrNull { it.id == id } }

    override suspend fun getById(id: String): ProjectEntity? =
        entities.value.firstOrNull { it.id == id }

    override suspend fun upsert(project: ProjectEntity) {
        entities.value = entities.value.filterNot { it.id == project.id } + project
    }

    override suspend fun updateEditable(
        id: String,
        name: String,
        schemaVersion: Int,
        rendererVersion: String,
        specJson: String,
        coverAssetId: String?,
        requestedUpdatedAt: Long,
    ): Int = update(id) { current ->
        current.copy(
            name = name,
            schemaVersion = schemaVersion,
            rendererVersion = rendererVersion,
            specJson = specJson,
            coverAssetId = coverAssetId,
            updatedAt = when {
                current.updatedAt >= requestedUpdatedAt && current.updatedAt < Long.MAX_VALUE -> current.updatedAt + 1
                current.updatedAt >= requestedUpdatedAt -> current.updatedAt
                else -> requestedUpdatedAt
            },
        )
    }

    override suspend fun rename(id: String, name: String, updatedAt: Long): Int =
        update(id) { it.copy(name = name, updatedAt = updatedAt) }

    override suspend fun updateThumbnail(id: String, thumbnailPath: String?, updatedAt: Long): Int =
        update(id) { it.copy(thumbnailPath = thumbnailPath, updatedAt = updatedAt) }

    override suspend fun markExported(id: String, exportedAt: Long, updatedAt: Long): Int =
        update(id) { it.copy(lastExportedAt = exportedAt, updatedAt = updatedAt) }

    override suspend fun deleteById(id: String): Int {
        val next = entities.value.filterNot { it.id == id }
        if (next.size == entities.value.size) return 0
        entities.value = next
        return 1
    }

    override suspend fun countProjectsReferencingAsset(id: String): Int =
        entities.value.count { it.coverAssetId == id }

    override suspend fun getCoverAssetReferenceCount(id: String): Int? =
        coverAssets[id]?.referenceCount

    override suspend fun getTrackedCoverAssetIds(): List<String> =
        coverAssets.keys.sorted()

    protected override suspend fun upsertCoverAsset(asset: CoverAssetEntity) {
        coverAssets[asset.id] = asset
    }

    protected override suspend fun deleteCoverAsset(id: String): Int =
        if (coverAssets.remove(id) != null) 1 else 0

    protected override suspend fun clearCoverAssets() {
        coverAssets.clear()
    }

    protected override suspend fun rebuildCoverAssetsFromProjects() {
        entities.value
            .mapNotNull(ProjectEntity::coverAssetId)
            .groupingBy { it }
            .eachCount()
            .forEach { (id, count) ->
                coverAssets[id] = CoverAssetEntity(id = id, referenceCount = count)
            }
    }

    private fun update(id: String, transform: (ProjectEntity) -> ProjectEntity): Int {
        var matched = false
        entities.value = entities.value.map { project ->
            if (project.id == id) {
                matched = true
                transform(project)
            } else {
                project
            }
        }
        return if (matched) 1 else 0
    }
}
