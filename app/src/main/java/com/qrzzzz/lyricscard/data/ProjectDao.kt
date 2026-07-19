package com.qrzzzz.lyricscard.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updated_at DESC, id ASC")
    abstract fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    abstract fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): ProjectEntity?

    @Upsert
    abstract suspend fun upsert(project: ProjectEntity)

    @Query(
        """
        UPDATE projects
        SET name = :name,
            schema_version = :schemaVersion,
            renderer_version = :rendererVersion,
            spec_json = :specJson,
            cover_asset_id = :coverAssetId,
            updated_at = CASE
                WHEN updated_at >= :requestedUpdatedAt AND updated_at < 9223372036854775807
                    THEN updated_at + 1
                WHEN updated_at >= :requestedUpdatedAt THEN updated_at
                ELSE :requestedUpdatedAt
            END
        WHERE id = :id
        """,
    )
    abstract suspend fun updateEditable(
        id: String,
        name: String,
        schemaVersion: Int,
        rendererVersion: String,
        specJson: String,
        coverAssetId: String?,
        requestedUpdatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE projects
        SET name = :name, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    abstract suspend fun rename(id: String, name: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE projects
        SET thumbnail_path = :thumbnailPath, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    abstract suspend fun updateThumbnail(id: String, thumbnailPath: String?, updatedAt: Long): Int

    @Query(
        """
        UPDATE projects
        SET last_exported_at = :exportedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    abstract suspend fun markExported(id: String, exportedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM projects WHERE id = :id")
    abstract suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM projects WHERE cover_asset_id = :id")
    abstract suspend fun countProjectsReferencingAsset(id: String): Int

    @Query("SELECT reference_count FROM cover_assets WHERE id = :id LIMIT 1")
    abstract suspend fun getCoverAssetReferenceCount(id: String): Int?

    @Query("SELECT id FROM cover_assets ORDER BY id ASC")
    abstract suspend fun getTrackedCoverAssetIds(): List<String>

    @Upsert
    protected abstract suspend fun upsertCoverAsset(asset: CoverAssetEntity)

    @Query("DELETE FROM cover_assets WHERE id = :id")
    protected abstract suspend fun deleteCoverAsset(id: String): Int

    @Query("DELETE FROM cover_assets")
    protected abstract suspend fun clearCoverAssets()

    @Query(
        """
        INSERT INTO cover_assets (id, reference_count)
        SELECT cover_asset_id, COUNT(*)
        FROM projects
        WHERE cover_asset_id IS NOT NULL
        GROUP BY cover_asset_id
        """,
    )
    protected abstract suspend fun rebuildCoverAssetsFromProjects()

    @Transaction
    open suspend fun upsertProjectWithAssetReferences(project: ProjectEntity): ProjectWriteResult {
        val previousCoverId = getById(project.id)?.coverAssetId
        upsert(project)
        project.coverAssetId?.let { synchronizeCoverAsset(it) }
        val releasedAssetId = previousCoverId
            ?.takeIf { it != project.coverAssetId }
            ?.let { synchronizeCoverAsset(it) }
        return ProjectWriteResult(
            project = checkNotNull(getById(project.id)),
            releasedAssetId = releasedAssetId,
        )
    }

    @Transaction
    open suspend fun updateEditableWithAssetReferences(
        id: String,
        name: String,
        schemaVersion: Int,
        rendererVersion: String,
        specJson: String,
        coverAssetId: String?,
        requestedUpdatedAt: Long,
    ): ProjectWriteResult? {
        val current = getById(id) ?: return null
        check(
            updateEditable(
                id = id,
                name = name,
                schemaVersion = schemaVersion,
                rendererVersion = rendererVersion,
                specJson = specJson,
                coverAssetId = coverAssetId,
                requestedUpdatedAt = requestedUpdatedAt,
            ) == 1,
        ) { "Project '$id' changed while saving" }
        coverAssetId?.let { synchronizeCoverAsset(it) }
        val releasedAssetId = current.coverAssetId
            ?.takeIf { it != coverAssetId }
            ?.let { synchronizeCoverAsset(it) }
        return ProjectWriteResult(
            project = checkNotNull(getById(id)),
            releasedAssetId = releasedAssetId,
        )
    }

    @Transaction
    open suspend fun deleteProjectWithAssetReferences(id: String): ProjectDeleteResult {
        val current = getById(id) ?: return ProjectDeleteResult(deleted = false)
        check(deleteById(id) == 1) { "Project '$id' changed while deleting" }
        return ProjectDeleteResult(
            deleted = true,
            releasedAssetId = current.coverAssetId?.let { synchronizeCoverAsset(it) },
        )
    }

    @Transaction
    open suspend fun rebuildCoverAssetReferences(): Set<String> {
        clearCoverAssets()
        rebuildCoverAssetsFromProjects()
        return getTrackedCoverAssetIds().toSet()
    }

    private suspend fun synchronizeCoverAsset(id: String): String? {
        val referenceCount = countProjectsReferencingAsset(id)
        return if (referenceCount > 0) {
            upsertCoverAsset(CoverAssetEntity(id = id, referenceCount = referenceCount))
            null
        } else {
            deleteCoverAsset(id)
            id
        }
    }
}

data class ProjectWriteResult(
    val project: ProjectEntity,
    val releasedAssetId: String? = null,
)

data class ProjectDeleteResult(
    val deleted: Boolean,
    val releasedAssetId: String? = null,
)
