package com.qrzzzz.lyricscard.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        const val VERSION: Int = 1
        const val DATABASE_NAME: String = "lyrics-card.db"
    }
}
