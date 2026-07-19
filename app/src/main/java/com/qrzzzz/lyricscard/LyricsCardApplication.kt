package com.qrzzzz.lyricscard

import android.app.Application
import androidx.room.Room
import com.qrzzzz.lyricscard.data.AppDatabase
import com.qrzzzz.lyricscard.data.ProjectRepository
import com.qrzzzz.lyricscard.data.UserPreferencesRepository
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore

class LyricsCardApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(
            projectDao = database.projectDao(),
            assetFiles = assetStore,
        )
    }

    val assetStore: ProjectAssetStore by lazy {
        ProjectAssetStore(applicationContext)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }
}
