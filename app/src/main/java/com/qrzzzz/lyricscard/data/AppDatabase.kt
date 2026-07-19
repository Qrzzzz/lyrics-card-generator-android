package com.qrzzzz.lyricscard.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, CoverAssetEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        const val VERSION: Int = 2
        const val DATABASE_NAME: String = "lyrics-card.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cover_assets` (
                        `id` TEXT NOT NULL,
                        `reference_count` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `cover_assets` (`id`, `reference_count`)
                    SELECT `cover_asset_id`, COUNT(*)
                    FROM `projects`
                    WHERE `cover_asset_id` IS NOT NULL
                    GROUP BY `cover_asset_id`
                    """.trimIndent(),
                )
            }
        }
    }
}
