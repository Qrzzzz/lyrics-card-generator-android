package com.qrzzzz.lyricscard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent reference ledger for cover files.
 *
 * The project rows remain the source of truth. This table is updated in the same transaction as
 * every project cover mutation so file cleanup can happen only after the last committed reference
 * disappears.
 */
@Entity(tableName = "cover_assets")
data class CoverAssetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "reference_count")
    val referenceCount: Int,
)
