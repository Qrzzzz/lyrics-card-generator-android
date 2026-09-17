package com.qrzzzz.lyricscard.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qrzzzz.lyricscard.AndroidProjectAssets
import com.qrzzzz.lyricscard.EditorSessionRegistry
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.data.AppDatabase
import com.qrzzzz.lyricscard.data.ProjectRepository
import com.qrzzzz.lyricscard.data.ResolvedNeteaseSong
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room transactions and temporary image files, with explicitly ordered save failures. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditorDataLifecycleTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val temporary = TemporaryFolder()

    private inner class Fixture(scope: TestScope) : AutoCloseable {
        private val base = ApplicationProvider.getApplicationContext<Context>()
        private val files = temporary.newFolder()
        private val cache = temporary.newFolder()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val assets = ProjectAssetStore(context)
        val repository = ProjectRepository(database.projectDao(), assetFiles = assets)
        val projectAssets = AndroidProjectAssets(assets, repository, scope.backgroundScope)
        var beforeSave: suspend (Project) -> Unit = {}
        val store = object : ProjectStore by FakeProjectStore() {
            override suspend fun getProject(id: String) = repository.getProject(id)
            override suspend fun save(project: Project): Project {
                beforeSave(project)
                return repository.save(project)
            }
        }
        val netease = FakeNeteaseClient().apply {
            resolveSongBlock = { id -> ResolvedNeteaseSong(id, "Imported $id", "Artist", "Album", "Imported line", "cover") }
            downloadCoverBlock = { imageBytes() }
        }
        val models = ViewModelStore()
        lateinit var editor: EditorViewModel
        suspend fun open(cover: String? = null): Project {
            val blank = ProjectTemplates.blank()
            val project = repository.create(blank.copy(spec = blank.spec.copy(song = blank.spec.song.copy(coverAssetId = cover))))
            editor = EditorViewModel(SavedStateHandle(mapOf(EditorViewModel.PROJECT_ID_KEY to project.id)),
                store, projectAssets, netease, FakeRendererOperations(), EditorSessionRegistry())
            models.put("editor", editor)
            editor.uiState.first { !it.isLoading }
            return project
        }
        suspend fun cover(): String = assets.importCover(imageBytes())
        fun file(id: String) = File(files, "project-assets/$id.image")
        fun assertImage(id: String) {
            assertTrue(file(id).isFile)
            val response = assets.openForWebView(id)
            assertNotNull(response)
            response!!.data.use { assertTrue(it.readBytes().isNotEmpty()) }
        }
        override fun close() { models.clear(); database.close() }
    }

    @Test fun `saved replacements survive undo redo then expired history is collected`() = runTest(main.dispatcher) {
        Fixture(this).use { f ->
            val x = f.cover()
            val original = f.open(x)
            val xBytes = f.file(x).readBytes()
            val y = f.cover()
            f.editor.updateSpec { it.copy(song = it.song.copy(coverAssetId = y)) }
            assertTrue(f.editor.flushAutosave())
            val yBytes = f.file(y).readBytes()
            repeat(3) {
                f.editor.undo(); assertTrue(f.editor.flushAutosave())
                assertEquals(x, f.repository.getProject(original.id)!!.coverAssetId)
                assertArrayEquals(xBytes, f.file(x).readBytes())
                f.editor.redo(); assertTrue(f.editor.flushAutosave())
                assertEquals(y, f.repository.getProject(original.id)!!.coverAssetId)
                assertArrayEquals(yBytes, f.file(y).readBytes())
            }
            // X is no longer persisted but is owned by undo history.
            f.repository.reconcileCoverAssets(); f.assertImage(x); f.assertImage(y)
            repeat(EditorViewModel.MAX_HISTORY + 1) { index ->
                f.editor.updateSpec { it.copy(song = it.song.copy(title = "Edit $index")) }
            }
            assertTrue(f.editor.flushAutosave())
            f.repository.reconcileCoverAssets()
            assertFalse(f.file(x).exists()); f.assertImage(y)
            f.editor.removeCover(); assertTrue(f.editor.flushAutosave())
            f.editor.undo(); assertTrue(f.editor.flushAutosave()); f.assertImage(y)
            f.editor.redo(); assertTrue(f.editor.flushAutosave())
            f.models.clear()
            f.repository.reconcileCoverAssets()
            assertFalse(f.file(y).exists())
            assertNull(f.repository.getProject(original.id)!!.coverAssetId)
        }
    }

    @Test fun `redo invalidation releases uncommitted cover and preserves shared persisted files`() = runTest(main.dispatcher) {
        Fixture(this).use { f ->
            val x = f.cover(); val original = f.open(x)
            val duplicate = f.repository.duplicate(original.id)!!
            val y = f.cover()
            f.editor.updateSpec { it.copy(song = it.song.copy(coverAssetId = y)) }
            f.beforeSave = { throw IOException("disk full") }
            assertFalse(f.editor.flushAutosave()); f.assertImage(y)
            f.editor.undo()
            f.editor.updateSpec { it.copy(song = it.song.copy(title = "New branch")) }
            f.beforeSave = {}
            assertTrue(f.editor.flushAutosave())
            f.repository.reconcileCoverAssets()
            assertFalse(f.file(y).exists())
            f.editor.removeCover(); assertTrue(f.editor.flushAutosave())
            f.models.clear(); f.repository.reconcileCoverAssets()
            f.assertImage(x)
            assertEquals(x, f.repository.getProject(duplicate.id)!!.coverAssetId)
            f.repository.delete(duplicate.id)
            assertFalse(f.file(x).exists())
        }
    }

    @Test fun `slow import save then accepted edit then failure retains edits and saves final state`() = runTest(main.dispatcher) {
        Fixture(this).use { f ->
            val x = f.cover(); val original = f.open(x)
            val started = CompletableDeferred<Project>()
            val fail = CompletableDeferred<Unit>()
            f.beforeSave = { snapshot ->
                if (!started.isCompleted) { started.complete(snapshot); fail.await(); throw IOException("forced failure") }
            }
            f.editor.resolveNeteaseSong("first")
            val imported = started.await()
            val failedCover = imported.coverAssetId!!
            f.assertImage(failedCover)
            f.editor.updateSpec { it.copy(content = it.content.copy(lyrics = "Accepted later lyrics"),
                typography = it.typography.copy(lyricSize = 60)) }
            f.editor.updateProjectName("Accepted later name")
            fail.complete(Unit)
            f.editor.uiState.first { !it.netease.isResolving }
            assertEquals("Accepted later lyrics", f.editor.uiState.value.currentProject!!.spec.content.lyrics)
            assertEquals(60, f.editor.uiState.value.currentProject!!.spec.typography.lyricSize)
            assertTrue(f.editor.flushAutosave())
            val saved = f.repository.getProject(original.id)!!
            assertEquals("Accepted later lyrics", saved.spec.content.lyrics)
            assertEquals(60, saved.spec.typography.lyricSize)
            assertEquals("Accepted later name", saved.name)
            assertEquals(original.spec.song, saved.spec.song)
            f.repository.reconcileCoverAssets()
            f.assertImage(x); assertFalse(f.file(failedCover).exists())
            f.editor.undo(); assertTrue(f.editor.flushAutosave()); f.assertImage(x)
            f.editor.redo(); assertTrue(f.editor.flushAutosave())
            assertEquals("Accepted later lyrics", f.repository.getProject(original.id)!!.spec.content.lyrics)
        }
    }

    @Test fun `failed import without later edit restores file and replacement waits for cancelled rollback`() = runTest(main.dispatcher) {
        Fixture(this).use { f ->
            val x = f.cover(); val original = f.open(x)
            val started = CompletableDeferred<Project>()
            val fail = CompletableDeferred<Unit>()
            f.beforeSave = { snapshot -> started.complete(snapshot); fail.await(); throw IOException("forced failure") }
            f.editor.resolveNeteaseSong("fail")
            val failedCover = started.await().coverAssetId!!
            fail.complete(Unit)
            f.editor.uiState.first { !it.netease.isResolving }
            assertEquals(original.spec, f.editor.uiState.value.currentProject!!.spec)
            assertEquals(original.spec, f.repository.getProject(original.id)!!.spec)
            f.repository.reconcileCoverAssets(); f.assertImage(x); assertFalse(f.file(failedCover).exists())

            val cancelledSave = CompletableDeferred<Project>()
            val suspendedSave = CompletableDeferred<Unit>()
            f.beforeSave = { snapshot ->
                if (snapshot.spec.song.title == "Imported old") { cancelledSave.complete(snapshot); suspendedSave.await() }
            }
            f.editor.resolveNeteaseSong("old")
            val oldCover = cancelledSave.await().coverAssetId!!
            f.editor.resolveNeteaseSong("new")
            f.editor.uiState.first { !it.netease.isResolving }
            assertTrue(f.editor.flushAutosave())
            val saved = f.repository.getProject(original.id)!!
            assertEquals("Imported new", saved.spec.song.title)
            f.assertImage(saved.coverAssetId!!)
            f.repository.reconcileCoverAssets()
            assertFalse(f.file(oldCover).exists()); f.assertImage(x)
        }
    }

    private fun imageBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle(); output.toByteArray()
        }
    }
}
