package com.qrzzzz.lyricscard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [Index(value = ["updated_at"])],
)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "renderer_version")
    val rendererVersion: String,
    @ColumnInfo(name = "spec_json")
    val specJson: String,
    @ColumnInfo(name = "cover_asset_id")
    val coverAssetId: String?,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "last_exported_at")
    val lastExportedAt: Long?,
)
