package com.qrzzzz.lyricscard.ui

import androidx.lifecycle.SavedStateHandle
import com.qrzzzz.lyricscard.EditorSessionRegistry
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.ProjectTemplates
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun routeProjectIdLoadsOnlyThatProjectFromRoomBoundary() = runTest(mainDispatcherRule.dispatcher) {
        val first = project("project-1")
        val second = project("project-2")
        val store = FakeProjectStore(listOf(first, second))
        val viewModel = editorViewModel(store, SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to second.id)))

        runCurrent()

        assertEquals(listOf(second.id), store.requestedIds)
        assertEquals(second, viewModel.uiState.value.currentProject)
        assertFalse(viewModel.uiState.value.projectUnavailable)
    }

    @Test
    fun stepAndDraftsRestoreWhileProjectReloadsFromStore() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-restore")
        val store = FakeProjectStore(listOf(stored))
        val firstHandle = SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id))
        val first = editorViewModel(store, firstHandle)
        runCurrent()

        first.selectStep(3)
        first.updateSearchQuery("晴天 周杰伦")
        first.updateLinkInput("https://music.163.com/song?id=1")
        first.updateProjectName("恢复中的项目")
        assertTrue(first.flushAutosave())

        val restoredHandle = SavedStateHandle(
            mapOf(
                EditorViewModel.PROJECT_ID_KEY to stored.id,
                EditorViewModel.STEP_KEY to firstHandle.get<Int>(EditorViewModel.STEP_KEY),
                EditorViewModel.SEARCH_QUERY_KEY to firstHandle.get<String>(EditorViewModel.SEARCH_QUERY_KEY),
                EditorViewModel.LINK_INPUT_KEY to firstHandle.get<String>(EditorViewModel.LINK_INPUT_KEY),
                EditorViewModel.PROJECT_NAME_KEY to firstHandle.get<String>(EditorViewModel.PROJECT_NAME_KEY),
            ),
        )
        val restored = editorViewModel(store, restoredHandle)
        runCurrent()

        assertEquals(3, restored.uiState.value.selectedStep)
        assertEquals("晴天 周杰伦", restored.uiState.value.drafts.searchQuery)
        assertEquals("https://music.163.com/song?id=1", restored.uiState.value.drafts.linkInput)
        assertEquals("恢复中的项目", restored.uiState.value.drafts.projectName)
        assertEquals("恢复中的项目", restored.uiState.value.currentProject?.name)
        assertEquals(stored.id, restored.uiState.value.currentProject?.id)
    }

    @Test
    fun blankProjectNameDraftRestoresWithoutCreatingInvalidProject() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-blank-draft")
        val handle = SavedStateHandle(
            mapOf(
                EditorViewModel.PROJECT_ID_KEY to stored.id,
                EditorViewModel.PROJECT_NAME_KEY to "",
            ),
        )

        val viewModel = editorViewModel(FakeProjectStore(listOf(stored)), handle)
        runCurrent()

        assertEquals("", viewModel.uiState.value.drafts.projectName)
        assertEquals(stored.name, viewModel.uiState.value.currentProject?.name)
        assertEquals(AutosaveStatus.SAVED, viewModel.uiState.value.autosaveStatus)
    }

    @Test
    fun autosaveUsesFiveHundredMillisecondDebounceAndLatestSnapshot() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-autosave")
        val store = FakeProjectStore(listOf(stored))
        val viewModel = editorViewModel(store, SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)))
        runCurrent()

        viewModel.updateSpec { it.copy(song = it.song.copy(title = "first")) }
        advanceTimeBy(300)
        viewModel.updateSpec { it.copy(song = it.song.copy(title = "latest")) }
        advanceTimeBy(499)
        runCurrent()
        assertTrue(store.saved.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, store.saved.size)
        assertEquals("latest", store.saved.single().spec.song.title)
        assertEquals(AutosaveStatus.SAVED, viewModel.uiState.value.autosaveStatus)
    }

    @Test
    fun undoRedoRetainsAtMostFiftyHistoryEntries() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-history")
        val store = FakeProjectStore(listOf(stored))
        val viewModel = editorViewModel(store, SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)))
        runCurrent()

        repeat(51) { index ->
            viewModel.updateSpec { it.copy(song = it.song.copy(title = "edit-${index + 1}")) }
        }
        var undoCount = 0
        while (viewModel.uiState.value.canUndo) {
            viewModel.undo()
            undoCount += 1
        }

        assertEquals(EditorViewModel.MAX_HISTORY, undoCount)
        assertEquals("edit-1", viewModel.uiState.value.currentProject?.spec?.song?.title)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.redo()
        assertEquals("edit-2", viewModel.uiState.value.currentProject?.spec?.song?.title)
        viewModel.updateSpec { it.copy(song = it.song.copy(title = "new-branch")) }
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun failedFlushStaysVisibleAndDoesNotPretendSaved() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-failure")
        val store = FakeProjectStore(listOf(stored))
        val sessions = EditorSessionRegistry()
        val viewModel = editorViewModel(
            store = store,
            handle = SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)),
            sessions = sessions,
        )
        runCurrent()
        store.saveFailure = IllegalStateException("disk full")

        viewModel.updateSpec { it.copy(song = it.song.copy(title = "unsaved")) }

        assertFalse(sessions.flushActive())
        assertEquals(AutosaveStatus.FAILED, viewModel.uiState.value.autosaveStatus)
        assertEquals("disk full", viewModel.uiState.value.errorMessage)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun flushPersistsEditsThatArriveWhileAnEarlierSnapshotIsSaving() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-flush-race")
        val store = FakeProjectStore(listOf(stored))
        val firstSaveStarted = CompletableDeferred<Unit>()
        val releaseFirstSave = CompletableDeferred<Unit>()
        var saveAttempts = 0
        store.beforeSave = {
            saveAttempts += 1
            if (saveAttempts == 1) {
                firstSaveStarted.complete(Unit)
                releaseFirstSave.await()
            }
        }
        val viewModel = editorViewModel(
            store,
            SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)),
        )
        runCurrent()

        viewModel.updateSpec { it.copy(song = it.song.copy(title = "first")) }
        val flushResult = async { viewModel.flushAutosave() }
        runCurrent()
        assertTrue(firstSaveStarted.isCompleted)

        viewModel.updateSpec { it.copy(song = it.song.copy(title = "latest")) }
        releaseFirstSave.complete(Unit)
        runCurrent()

        assertTrue(flushResult.await())
        assertEquals(listOf("first", "latest"), store.saved.map { it.spec.song.title })
        assertEquals(AutosaveStatus.SAVED, viewModel.uiState.value.autosaveStatus)
    }

    @Test
    fun navigationPermitRejectsRepeatedTransitionUntilDestinationReturns() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-navigation-once")
        val store = FakeProjectStore(listOf(stored))
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        store.beforeSave = {
            saveStarted.complete(Unit)
            releaseSave.await()
        }
        val viewModel = editorViewModel(
            store,
            SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)),
        )
        runCurrent()

        viewModel.updateSpec { it.copy(song = it.song.copy(title = "saving before back")) }
        val firstAttempt = async { viewModel.prepareForNavigation() }
        runCurrent()

        assertTrue(saveStarted.isCompleted)
        assertTrue(viewModel.uiState.value.isLeaving)
        val repeatedAttempt = async { viewModel.prepareForNavigation() }
        runCurrent()
        assertFalse(repeatedAttempt.await())

        releaseSave.complete(Unit)
        runCurrent()
        assertTrue(firstAttempt.await())

        viewModel.markNavigationCommitted()
        viewModel.onNavigationResumed()

        assertFalse(viewModel.uiState.value.isLeaving)
        assertTrue(viewModel.prepareForNavigation())
    }

    @Test
    fun exportTransitionWaitsUntilPaletteMutationHasFinished() = runTest(mainDispatcherRule.dispatcher) {
        val stored = project("project-palette-barrier").let { project ->
            project.copy(
                spec = project.spec.copy(
                    song = project.spec.song.copy(
                        coverAssetId = "00000000-0000-4000-8000-000000000001",
                    ),
                ),
            )
        }
        val paletteResult = CompletableDeferred<PaletteSpec>()
        val renderer = FakeRendererOperations().apply {
            paletteBlock = { paletteResult.await() }
        }
        val viewModel = editorViewModel(
            store = FakeProjectStore(listOf(stored)),
            handle = SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to stored.id)),
            renderer = renderer,
        )
        runCurrent()

        viewModel.extractPalette()
        runCurrent()

        assertTrue(viewModel.uiState.value.isExtractingPalette)
        assertFalse(viewModel.prepareForNavigation())

        paletteResult.complete(PaletteSpec())
        runCurrent()

        assertFalse(viewModel.uiState.value.isExtractingPalette)
        assertTrue(viewModel.prepareForNavigation())
    }

    private fun editorViewModel(
        store: FakeProjectStore,
        handle: SavedStateHandle,
        sessions: EditorSessionRegistry = EditorSessionRegistry(),
        renderer: FakeRendererOperations = FakeRendererOperations(),
    ) = EditorViewModel(
        savedStateHandle = handle,
        projects = store,
        projectAssets = FakeProjectAssets(),
        neteaseClient = FakeNeteaseClient(),
        renderer = renderer,
        messages = FakeEditorMessages,
        sessions = sessions,
    )

    private fun project(id: String): Project = ProjectTemplates.blank(id = id, now = 1L)
}
