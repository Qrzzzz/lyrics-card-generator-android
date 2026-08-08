package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qrzzzz.lyricscard.ProjectStore
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
    val errorMessage: String? = null,
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
                            errorMessage = cause.message ?: "无法读取项目",
                        )
                    }
                }
                .collect { values ->
                    _uiState.update { it.copy(projects = values, isLoading = false) }
                }
        }
    }

    suspend fun createBlank(): String? = runNavigationOperation("无法创建项目") {
        projects.createBlank().id
    }

    suspend fun createSample(): String? = runNavigationOperation("无法创建示例") {
        projects.createSample().id
    }

    suspend fun openProject(id: String): String? = runNavigationOperation("无法打开项目") {
        checkNotNull(projects.getProject(id)) { "项目不存在或已被删除" }.id
    }

    suspend fun duplicateProject(id: String): Boolean = runOperation("复制失败") {
        projects.duplicate(id) != null
    } ?: false

    suspend fun renameProject(id: String, name: String): Boolean = runOperation("重命名失败") {
        projects.rename(id, name)
    } ?: false

    suspend fun deleteProject(id: String): Boolean = runOperation("删除失败") {
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

    private suspend fun <T> runNavigationOperation(defaultMessage: String, block: suspend () -> T): T? {
        if (!beginNavigation()) return null
        return try {
            block()
        } catch (cause: CancellationException) {
            releaseOperation()
            throw cause
        } catch (cause: Throwable) {
            _uiState.update { it.copy(errorMessage = cause.message ?: defaultMessage) }
            releaseOperation()
            null
        }
    }

    private suspend fun <T> runOperation(defaultMessage: String, block: suspend () -> T): T? {
        if (!operationInFlight.compareAndSet(false, true)) return null
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        return try {
            block()
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            _uiState.update { it.copy(errorMessage = cause.message ?: defaultMessage) }
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
