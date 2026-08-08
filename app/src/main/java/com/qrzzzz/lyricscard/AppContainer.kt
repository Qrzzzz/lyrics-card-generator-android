package com.qrzzzz.lyricscard

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.qrzzzz.lyricscard.data.AppDatabase
import com.qrzzzz.lyricscard.data.NeteaseMusicService
import com.qrzzzz.lyricscard.data.NeteaseSongSearchResult
import com.qrzzzz.lyricscard.data.ProjectRepository
import com.qrzzzz.lyricscard.data.ResolvedNeteaseSong
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.data.UserPreferencesRepository
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.model.RenderSpecViolation
import com.qrzzzz.lyricscard.renderer.ExportedImage
import com.qrzzzz.lyricscard.renderer.ProjectAssetStore
import com.qrzzzz.lyricscard.renderer.RendererController
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface AppContainer {
    val projects: ProjectStore
    val preferences: UserPreferencesStore
    val projectAssets: ProjectAssets
    val netease: NeteaseClient
    val rendererController: RendererController
    val renderer: RendererOperations
    val exportFiles: ExportFiles
    val editorSessions: EditorSessionRegistry
    val editorMessages: EditorMessageResolver

    fun start()
    fun close()
}

interface ProjectStore {
    fun observeProjects(): Flow<List<ProjectSummary>>
    suspend fun getProject(id: String): Project?
    suspend fun createBlank(): Project
    suspend fun createSample(): Project
    suspend fun save(project: Project): Project
    suspend fun duplicate(id: String): Project?
    suspend fun rename(id: String, name: String): Boolean
    suspend fun delete(id: String): Boolean
    suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean
    suspend fun markExported(id: String): Boolean
    suspend fun recordExport(id: String, thumbnailPath: String): Boolean
    suspend fun reconcileCoverAssets()
}

interface UserPreferencesStore {
    val preferences: Flow<UserPreferences>
    suspend fun setDarkMode(enabled: Boolean)
    suspend fun setDefaultExportScale(scale: Int)
    suspend fun setShowSafeArea(enabled: Boolean)
}

interface ProjectAssets {
    suspend fun importCover(uri: Uri): String
    suspend fun importCover(bytes: ByteArray): String
    suspend fun delete(id: String)
}

interface NeteaseClient {
    suspend fun search(keyword: String): List<NeteaseSongSearchResult>
    suspend fun resolveSong(id: String): ResolvedNeteaseSong
    suspend fun resolveLink(input: String): ResolvedNeteaseSong
    suspend fun downloadCover(url: String): ByteArray
}

interface RendererOperations {
    suspend fun exportPng(project: Project, multiplier: Int): ExportedImage
    suspend fun extractPalette(assetId: String): com.qrzzzz.lyricscard.model.PaletteSpec
    fun retry()
}

interface ExportFiles {
    suspend fun createThumbnail(projectId: String, image: ExportedImage): String
    suspend fun copyTo(image: ExportedImage, destination: Uri)
    suspend fun clearExportCache(): Long
}

interface EditorAutosaveSession {
    suspend fun flushAutosave(): Boolean
}

class EditorSessionRegistry {
    @Volatile
    private var active: EditorAutosaveSession? = null

    fun register(session: EditorAutosaveSession) {
        active = session
    }

    fun unregister(session: EditorAutosaveSession) {
        if (active === session) active = null
    }

    suspend fun flushActive(): Boolean = active?.flushAutosave() ?: true
}

interface EditorMessageResolver {
    fun lineLimit(violation: RenderSpecViolation, loadingStoredProject: Boolean): String
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val databaseDelegate = lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME,
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }
    private val nativeAssetStore = ProjectAssetStore(appContext)
    private val projectRepositoryDelegate = lazy {
        ProjectRepository(
            projectDao = databaseDelegate.value.projectDao(),
            assetFiles = nativeAssetStore,
        )
    }
    private val rendererControllerDelegate = lazy { RendererController(appContext, nativeAssetStore) }

    override val projects: ProjectStore by lazy {
        ProjectRepositoryStore(projectRepositoryDelegate.value)
    }
    override val preferences: UserPreferencesStore by lazy {
        UserPreferencesRepositoryStore(UserPreferencesRepository(appContext))
    }
    override val projectAssets: ProjectAssets = AndroidProjectAssets(nativeAssetStore)
    override val netease: NeteaseClient by lazy { AndroidNeteaseClient(NeteaseMusicService()) }
    override val rendererController: RendererController by rendererControllerDelegate
    override val renderer: RendererOperations by lazy { AndroidRendererOperations(rendererController) }
    override val exportFiles: ExportFiles = AndroidExportFiles(appContext, nativeAssetStore)
    override val editorSessions = EditorSessionRegistry()
    override val editorMessages: EditorMessageResolver = AndroidEditorMessageResolver(appContext)

    override fun start() {
        applicationScope.launch {
            try {
                projects.reconcileCoverAssets()
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                // A later process start can retry orphan cleanup without blocking project loading.
            }
        }
    }

    override fun close() {
        applicationScope.cancel()
        if (rendererControllerDelegate.isInitialized()) rendererControllerDelegate.value.close()
        if (databaseDelegate.isInitialized()) databaseDelegate.value.close()
    }
}

class AndroidEditorMessageResolver(private val context: Context) : EditorMessageResolver {
    override fun lineLimit(violation: RenderSpecViolation, loadingStoredProject: Boolean): String {
        val field = context.getString(
            if (violation.path == "content.translation") {
                R.string.lyric_field_translation
            } else {
                R.string.lyric_field_original
            },
        )
        return context.getString(
            if (loadingStoredProject) {
                R.string.error_loaded_lyric_line_limit
            } else {
                R.string.error_lyric_line_limit
            },
            field,
            violation.limit ?: com.qrzzzz.lyricscard.model.LyricTextLimits.MAX_LINES,
            violation.actual ?: 0,
        )
    }
}

private class ProjectRepositoryStore(
    private val repository: ProjectRepository,
) : ProjectStore {
    override fun observeProjects(): Flow<List<ProjectSummary>> = repository.observeProjects()
    override suspend fun getProject(id: String): Project? = repository.getProject(id)
    override suspend fun createBlank(): Project = repository.createBlank()
    override suspend fun createSample(): Project = repository.createSample()
    override suspend fun save(project: Project): Project = repository.save(project)
    override suspend fun duplicate(id: String): Project? = repository.duplicate(id)
    override suspend fun rename(id: String, name: String): Boolean = repository.rename(id, name)
    override suspend fun delete(id: String): Boolean = repository.delete(id)
    override suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean =
        repository.updateThumbnail(id, thumbnailPath)

    override suspend fun markExported(id: String): Boolean = repository.markExported(id)
    override suspend fun recordExport(id: String, thumbnailPath: String): Boolean =
        repository.recordExport(id, thumbnailPath)

    override suspend fun reconcileCoverAssets() {
        repository.reconcileCoverAssets()
    }
}

private class UserPreferencesRepositoryStore(
    private val repository: UserPreferencesRepository,
) : UserPreferencesStore {
    override val preferences: Flow<UserPreferences> = repository.preferences
    override suspend fun setDarkMode(enabled: Boolean) = repository.setDarkMode(enabled)
    override suspend fun setDefaultExportScale(scale: Int) = repository.setDefaultExportScale(scale)
    override suspend fun setShowSafeArea(enabled: Boolean) = repository.setShowSafeArea(enabled)
}

private class AndroidProjectAssets(
    private val store: ProjectAssetStore,
) : ProjectAssets {
    override suspend fun importCover(uri: Uri): String = store.importCover(uri)
    override suspend fun importCover(bytes: ByteArray): String = store.importCover(bytes)
    override suspend fun delete(id: String) = store.delete(id)
}

private class AndroidNeteaseClient(
    private val service: NeteaseMusicService,
) : NeteaseClient {
    override suspend fun search(keyword: String): List<NeteaseSongSearchResult> = service.search(keyword)
    override suspend fun resolveSong(id: String): ResolvedNeteaseSong = service.resolveSong(id)
    override suspend fun resolveLink(input: String): ResolvedNeteaseSong = service.resolveLink(input)
    override suspend fun downloadCover(url: String): ByteArray = service.downloadCover(url)
}

private class AndroidRendererOperations(
    private val controller: RendererController,
) : RendererOperations {
    override suspend fun exportPng(project: Project, multiplier: Int): ExportedImage =
        controller.exportPng(project.spec, multiplier)

    override suspend fun extractPalette(assetId: String) = controller.extractPalette(assetId)
    override fun retry() = controller.retry()
}

private class AndroidExportFiles(
    context: Context,
    private val assetStore: ProjectAssetStore,
) : ExportFiles {
    private val appContext = context.applicationContext

    override suspend fun createThumbnail(projectId: String, image: ExportedImage): String =
        assetStore.createThumbnailAtomically(projectId, image.file).absolutePath

    override suspend fun copyTo(image: ExportedImage, destination: Uri) = withContext(Dispatchers.IO) {
        appContext.contentResolver.openOutputStream(destination, "w")?.use { sink ->
            image.file.inputStream().use { source -> source.copyTo(sink) }
        } ?: error("无法写入所选位置")
        Unit
    }

    override suspend fun clearExportCache(): Long = withContext(Dispatchers.IO) {
        val directory = File(appContext.cacheDir, "exports")
        val bytes = if (directory.isDirectory) {
            directory.walkTopDown().filter(File::isFile).sumOf(File::length)
        } else {
            0L
        }
        directory.deleteRecursively()
        bytes
    }

}
