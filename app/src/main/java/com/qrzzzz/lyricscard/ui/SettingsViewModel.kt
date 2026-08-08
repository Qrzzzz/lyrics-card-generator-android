package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.DiagnosticsReader
import com.qrzzzz.lyricscard.DiagnosticsSnapshot
import com.qrzzzz.lyricscard.ExportFiles
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.UserPreferencesStore
import com.qrzzzz.lyricscard.data.UserPreferences
import java.util.concurrent.atomic.AtomicBoolean
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
    val isSavingPreference: Boolean = false,
    val isClearingCache: Boolean = false,
    val cacheStatus: UiText? = null,
    val errorMessage: UiText? = null,
    val diagnostics: DiagnosticsSnapshot? = null,
    val isLoadingDiagnostics: Boolean = true,
    val diagnosticsError: UiText? = null,
)

class SettingsViewModel(
    private val preferences: UserPreferencesStore,
    private val exportFiles: ExportFiles,
    private val diagnostics: DiagnosticsReader,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val preferenceInFlight = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            preferences.preferences
                .catch {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = UiText.resource(R.string.settings_error_read))
                    }
                }
                .collect { value ->
                    _uiState.update { it.copy(preferences = value, isLoading = false) }
                }
        }
        viewModelScope.launch {
            try {
                val snapshot = diagnostics.read()
                _uiState.update {
                    it.copy(
                        diagnostics = snapshot,
                        isLoadingDiagnostics = false,
                        diagnosticsError = null,
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingDiagnostics = false,
                        diagnosticsError = UiText.resource(R.string.settings_diagnostics_unavailable),
                    )
                }
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        updatePreference { preferences.setDarkMode(enabled) }
    }

    fun setDefaultExportScale(scale: Int) {
        updatePreference { preferences.setDefaultExportScale(scale.coerceIn(1, 2)) }
    }

    fun setShowSafeArea(enabled: Boolean) {
        updatePreference { preferences.setShowSafeArea(enabled) }
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
                        cacheStatus = UiText.resource(
                            R.string.settings_cache_cleared,
                            bytes / 1024.0 / 1024.0,
                        ),
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _uiState.update {
                    it.copy(
                        isClearingCache = false,
                        errorMessage = UiText.resource(R.string.settings_error_clear_cache),
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(cacheStatus = null, errorMessage = null) }
    }

    private fun updatePreference(block: suspend () -> Unit) {
        if (!preferenceInFlight.compareAndSet(false, true)) return
        _uiState.update {
            it.copy(
                isSavingPreference = true,
                cacheStatus = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                block()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                _uiState.update { it.copy(errorMessage = UiText.resource(R.string.settings_error_save)) }
            } finally {
                preferenceInFlight.set(false)
                _uiState.update { it.copy(isSavingPreference = false) }
            }
        }
    }
}
