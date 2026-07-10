package com.qrzzzz.lyricscard.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

data class UserPreferences(
    val darkMode: Boolean = false,
    val defaultExportScale: Int = 2,
    val showSafeArea: Boolean = true,
)

private val Context.lyricsCardPreferences by preferencesDataStore(
    name = "lyrics-card-settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.lyricsCardPreferences

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { values ->
            UserPreferences(
                darkMode = values[DARK_MODE] ?: false,
                defaultExportScale = (values[DEFAULT_EXPORT_SCALE] ?: 2).coerceIn(1, 2),
                showSafeArea = values[SHOW_SAFE_AREA] ?: true,
            )
        }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun setDefaultExportScale(scale: Int) {
        require(scale in 1..2)
        dataStore.edit { it[DEFAULT_EXPORT_SCALE] = scale }
    }

    suspend fun setShowSafeArea(enabled: Boolean) {
        dataStore.edit { it[SHOW_SAFE_AREA] = enabled }
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val DEFAULT_EXPORT_SCALE = intPreferencesKey("default_export_scale")
        val SHOW_SAFE_AREA = booleanPreferencesKey("show_safe_area")
    }
}
