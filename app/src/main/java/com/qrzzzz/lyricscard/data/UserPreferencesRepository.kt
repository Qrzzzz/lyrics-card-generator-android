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
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val defaultExportScale: Int = 2,
    val showSafeArea: Boolean = true,
)

enum class AppThemeMode(val persistedValue: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    ;

    companion object {
        fun fromPersistedValue(value: Int): AppThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

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
                themeMode = values[THEME_MODE]?.let(AppThemeMode::fromPersistedValue)
                    ?: values[DARK_MODE]?.let { enabled ->
                        if (enabled) AppThemeMode.DARK else AppThemeMode.LIGHT
                    }
                    ?: AppThemeMode.SYSTEM,
                defaultExportScale = (values[DEFAULT_EXPORT_SCALE] ?: 2).coerceIn(1, 2),
                showSafeArea = values[SHOW_SAFE_AREA] ?: true,
            )
        }

    suspend fun setThemeMode(mode: AppThemeMode) {
        dataStore.edit {
            it[THEME_MODE] = mode.persistedValue
            it.remove(DARK_MODE)
        }
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
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DEFAULT_EXPORT_SCALE = intPreferencesKey("default_export_scale")
        val SHOW_SAFE_AREA = booleanPreferencesKey("show_safe_area")
    }
}
