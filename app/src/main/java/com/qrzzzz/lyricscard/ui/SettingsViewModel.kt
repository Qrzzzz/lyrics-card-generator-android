package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.ExportFiles
import com.qrzzzz.lyricscard.UserPreferencesStore
import com.qrzzzz.lyricscard.data.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val isClearingCache: Boolean = false,
    val cacheStatus: String? = null,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val preferences: UserPreferencesStore,
    private val exportFiles: ExportFiles,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences
                .catch { cause ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = cause.message ?: "无法读取设置")
                    }
                }
                .collect { value ->
                    _uiState.update { it.copy(preferences = value, isLoading = false) }
                }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        updatePreference("无法保存设置") { preferences.setDarkMode(enabled) }
    }

    fun setDefaultExportScale(scale: Int) {
        updatePreference("无法保存设置") { preferences.setDefaultExportScale(scale.coerceIn(1, 2)) }
    }

    fun setShowSafeArea(enabled: Boolean) {
        updatePreference("无法保存设置") { preferences.setShowSafeArea(enabled) }
    }

    fun clearExportCache() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(isClearingCache = true, cacheStatus = null, errorMessage = null) }
        viewModelScope.launch {
            try {
                val bytes = exportFiles.clearExportCache()
                _uiState.update {
                    it.copy(
                        isClearingCache = false,
                        cacheStatus = "已清理 ${"%.1f".format(bytes / 1024.0 / 1024.0)} MB 导出缓存",
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _uiState.update {
                    it.copy(
                        isClearingCache = false,
                        errorMessage = cause.message ?: "无法清理导出缓存",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun updatePreference(defaultMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _uiState.update { it.copy(errorMessage = cause.message ?: defaultMessage) }
            }
        }
    }
}
