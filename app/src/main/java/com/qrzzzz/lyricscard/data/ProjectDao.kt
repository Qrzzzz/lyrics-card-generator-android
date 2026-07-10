package com.qrzzzz.lyricscard.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updated_at DESC, id ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

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
    suspend fun updateEditable(
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
    suspend fun rename(id: String, name: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE projects
        SET thumbnail_path = :thumbnailPath, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateThumbnail(id: String, thumbnailPath: String?, updatedAt: Long): Int

    @Query(
        """
        UPDATE projects
        SET last_exported_at = :exportedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markExported(id: String, exportedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
