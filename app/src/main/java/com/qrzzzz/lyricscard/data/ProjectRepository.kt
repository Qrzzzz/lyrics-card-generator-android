package com.qrzzzz.lyricscard.data

import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.model.RenderSpecJson
import com.qrzzzz.lyricscard.model.requireValid
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val assetFiles: CoverAssetFileStore = CoverAssetFileStore.NoOp,
) {
    private val assetMutationMutex = Mutex()

    fun observeProjects(): Flow<List<ProjectSummary>> = projectDao.observeAll().map { projects ->
        projects.map { it.toSummary() }
    }

    fun observeProject(id: String): Flow<Project?> = projectDao.observeById(id).map { entity ->
        entity?.toProject()
    }

    suspend fun getProject(id: String): Project? = projectDao.getById(id)?.toProject()

    suspend fun create(project: Project): Project = assetMutationMutex.withLock {
        val validated = project.requireValid()
        val result = projectDao.upsertProjectWithAssetReferences(validated.toEntity())
        completeAssetMutation(result)
        result.project.toProject()
    }

    suspend fun createBlank(name: String = ProjectTemplates.DEFAULT_BLANK_NAME): Project {
        val now = clock()
        return create(ProjectTemplates.blank(name = name, id = idGenerator(), now = now))
    }

    suspend fun createSample(name: String = ProjectTemplates.DEFAULT_SAMPLE_NAME): Project {
        val now = clock()
        return create(ProjectTemplates.sample(name = name, id = idGenerator(), now = now))
    }

    /** Persists the full spec JSON and advances updatedAt without allowing time to go backwards. */
    suspend fun save(project: Project): Project = assetMutationMutex.withLock {
        val validated = project.requireValid()
        val current = projectDao.getById(project.id)
            ?: throw IllegalStateException("Project '${project.id}' no longer exists")
        val requestedUpdatedAt = nextUpdatedAt(maxOf(validated.updatedAt, current.updatedAt))
        val result = projectDao.updateEditableWithAssetReferences(
            id = validated.id,
            name = validated.name,
            schemaVersion = validated.spec.schemaVersion,
            rendererVersion = validated.spec.rendererVersion,
            specJson = RenderSpecJson.encode(validated.spec),
            coverAssetId = validated.coverAssetId,
            requestedUpdatedAt = requestedUpdatedAt,
        )
            ?: throw IllegalStateException("Project '${project.id}' no longer exists")
        completeAssetMutation(result)
        result.project.toProject()
    }

    suspend fun rename(id: String, name: String): Boolean {
        val normalizedName = validateProjectName(name)
        val updatedAt = monotonicUpdatedAt(id) ?: return false
        return projectDao.rename(id, normalizedName, updatedAt) > 0
    }

    suspend fun duplicate(id: String, name: String? = null): Project? = assetMutationMutex.withLock {
        val source = getProject(id) ?: return null
        val now = clock()
        val duplicateName = name?.let(::validateProjectName)
            ?: validateProjectName("${source.name} 副本".take(MAX_PROJECT_NAME_LENGTH))
        val duplicate = source.copy(
            id = idGenerator(),
            name = duplicateName,
            thumbnailPath = null,
            createdAt = now,
            updatedAt = now,
            lastExportedAt = null,
        ).requireValid()
        val result = projectDao.upsertProjectWithAssetReferences(duplicate.toEntity())
        completeAssetMutation(result)
        result.project.toProject()
    }

    suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean {
        require(thumbnailPath?.contains('\u0000') != true) { "thumbnailPath must not contain NUL" }
        val updatedAt = monotonicUpdatedAt(id) ?: return false
        return projectDao.updateThumbnail(id, thumbnailPath, updatedAt) > 0
    }

    suspend fun markExported(id: String, exportedAt: Long = clock()): Boolean {
        require(exportedAt >= 0L) { "exportedAt must not be negative" }
        val project = projectDao.getById(id) ?: return false
        require(exportedAt >= project.createdAt) { "exportedAt must not be earlier than createdAt" }
        val updatedAt = maxOf(nextUpdatedAt(project.updatedAt), exportedAt)
        return projectDao.markExported(id, exportedAt, updatedAt) > 0
    }

    suspend fun delete(id: String): Boolean = assetMutationMutex.withLock {
        val result = projectDao.deleteProjectWithAssetReferences(id)
        result.releasedAssetId?.let { deleteIfStillUnreferenced(it) }
        result.deleted
    }

    /**
     * Repairs the derived reference ledger and removes files left orphaned by a prior process
     * interruption. Imported-but-not-yet-committed files are protected by the file-store
     * implementation.
     */
    suspend fun reconcileCoverAssets() = assetMutationMutex.withLock {
        val referencedIds = projectDao.rebuildCoverAssetReferences()
        assetFiles.deleteUnreferenced(referencedIds)
    }

    private suspend fun monotonicUpdatedAt(id: String): Long? {
        val current = projectDao.getById(id) ?: return null
        return nextUpdatedAt(current.updatedAt)
    }

    private fun nextUpdatedAt(current: Long): Long {
        val wallClock = clock()
        return when {
            wallClock > current -> wallClock
            current < Long.MAX_VALUE -> current + 1L
            else -> current
        }
    }

    private suspend fun completeAssetMutation(result: ProjectWriteResult) {
        result.project.coverAssetId?.let { id ->
            runCatching { assetFiles.markReferenced(id) }
        }
        result.releasedAssetId?.let { deleteIfStillUnreferenced(it) }
    }

    private suspend fun deleteIfStillUnreferenced(id: String) {
        if (projectDao.countProjectsReferencingAsset(id) == 0) {
            runCatching { assetFiles.delete(id) }
        }
    }

    private fun Project.toEntity(): ProjectEntity {
        val validated = requireValid()
        return ProjectEntity(
            id = validated.id,
            name = validated.name,
            schemaVersion = validated.spec.schemaVersion,
            rendererVersion = validated.spec.rendererVersion,
            specJson = RenderSpecJson.encode(validated.spec),
            coverAssetId = validated.coverAssetId,
            thumbnailPath = validated.thumbnailPath,
            createdAt = validated.createdAt,
            updatedAt = validated.updatedAt,
            lastExportedAt = validated.lastExportedAt,
        )
    }

    private fun ProjectEntity.toProject(): Project {
        try {
            val spec = RenderSpecJson.decode(specJson)
            check(schemaVersion == spec.schemaVersion) {
                "entity schemaVersion $schemaVersion does not match JSON ${spec.schemaVersion}"
            }
            check(rendererVersion == spec.rendererVersion) {
                "entity rendererVersion $rendererVersion does not match JSON ${spec.rendererVersion}"
            }
            check(coverAssetId == spec.song.coverAssetId) {
                "entity coverAssetId does not match JSON"
            }
            return Project(
                id = id,
                name = name,
                spec = spec,
                thumbnailPath = thumbnailPath,
                createdAt = createdAt,
                updatedAt = updatedAt,
                lastExportedAt = lastExportedAt,
            ).requireValid()
        } catch (cause: Exception) {
            if (cause is CorruptProjectException) throw cause
            throw CorruptProjectException(id, cause)
        }
    }

    private fun ProjectEntity.toSummary(): ProjectSummary = ProjectSummary(
        id = id,
        name = name,
        schemaVersion = schemaVersion,
        rendererVersion = rendererVersion,
        coverAssetId = coverAssetId,
        thumbnailPath = thumbnailPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastExportedAt = lastExportedAt,
    )

    private fun validateProjectName(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty()) { "Project name must not be blank" }
        require(normalized.length <= MAX_PROJECT_NAME_LENGTH) {
            "Project name must be at most $MAX_PROJECT_NAME_LENGTH characters"
        }
        require('\u0000' !in normalized) { "Project name must not contain NUL" }
        return normalized
    }

    private companion object {
        const val MAX_PROJECT_NAME_LENGTH = 120
    }
}

class CorruptProjectException(
    val projectId: String,
    cause: Throwable,
) : IllegalStateException("Stored project '$projectId' is corrupt", cause)
