package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.R
import com.qrzzzz.lyricscard.model.ProjectSummary
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: UiText? = null,
)

class HomeViewModel(
    private val projects: ProjectStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val operationInFlight = AtomicBoolean(false)
    private val navigationCommitted = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            projects.observeProjects()
                .catch { cause ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = UiText.resource(R.string.home_error_read_projects),
                        )
                    }
                }
                .collect { values ->
                    _uiState.update { it.copy(projects = values, isLoading = false) }
                }
        }
    }

    suspend fun createBlank(): String? = runNavigationOperation(R.string.home_error_create_project) {
        projects.createBlank().id
    }

    suspend fun createSample(): String? = runNavigationOperation(R.string.home_error_create_sample) {
        projects.createSample().id
    }

    suspend fun openProject(id: String): String? = runNavigationOperation(R.string.home_error_open_project) {
        checkNotNull(projects.getProject(id)) { "Project does not exist" }.id
    }

    suspend fun duplicateProject(id: String): Boolean = runOperation(R.string.home_error_duplicate) {
        projects.duplicate(id) != null
    } ?: false

    suspend fun renameProject(id: String, name: String): Boolean = runOperation(R.string.home_error_rename) {
        projects.rename(id, name)
    } ?: false

    suspend fun deleteProject(id: String): Boolean = runOperation(R.string.home_error_delete) {
        projects.delete(id)
    } ?: false

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun beginNavigation(): Boolean {
        if (!operationInFlight.compareAndSet(false, true)) return false
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        return true
    }

    fun markNavigationCommitted() {
        if (operationInFlight.get()) navigationCommitted.set(true)
    }

    fun navigationFailed() {
        releaseOperation()
    }

    fun onNavigationResumed() {
        if (navigationCommitted.compareAndSet(true, false)) releaseOperation()
    }

    private suspend fun <T> runNavigationOperation(defaultMessage: Int, block: suspend () -> T): T? {
        if (!beginNavigation()) return null
        return try {
            block()
        } catch (cause: CancellationException) {
            releaseOperation()
            throw cause
        } catch (cause: Throwable) {
            _uiState.update { it.copy(errorMessage = UiText.resource(defaultMessage)) }
            releaseOperation()
            null
        }
    }

    private suspend fun <T> runOperation(defaultMessage: Int, block: suspend () -> T): T? {
        if (!operationInFlight.compareAndSet(false, true)) return null
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        return try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            _uiState.update { it.copy(errorMessage = UiText.resource(defaultMessage)) }
            null
        } finally {
            releaseOperation()
        }
    }

    private fun releaseOperation() {
        navigationCommitted.set(false)
        operationInFlight.set(false)
        _uiState.update { it.copy(isWorking = false) }
    }
}
