package com.qrzzzz.lyricscard.ui

import android.net.Uri
import com.qrzzzz.lyricscard.EditorMessageResolver
import com.qrzzzz.lyricscard.ExportFiles
import com.qrzzzz.lyricscard.NeteaseClient
import com.qrzzzz.lyricscard.ProjectAssets
import com.qrzzzz.lyricscard.ProjectStore
import com.qrzzzz.lyricscard.RendererOperations
import com.qrzzzz.lyricscard.UserPreferencesStore
import com.qrzzzz.lyricscard.data.NeteaseSongSearchResult
import com.qrzzzz.lyricscard.data.ResolvedNeteaseSong
import com.qrzzzz.lyricscard.data.UserPreferences
import com.qrzzzz.lyricscard.model.PaletteSpec
import com.qrzzzz.lyricscard.model.Project
import com.qrzzzz.lyricscard.model.ProjectSummary
import com.qrzzzz.lyricscard.model.ProjectTemplates
import com.qrzzzz.lyricscard.model.RenderSpecViolation
import com.qrzzzz.lyricscard.renderer.ExportedImage
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeProjectStore(initial: List<Project> = emptyList()) : ProjectStore {
    private val values = linkedMapOf<String, Project>().apply {
        initial.forEach { put(it.id, it) }
    }
    private val summaries = MutableStateFlow(values.values.map(Project::toSummary))
    val saved = mutableListOf<Project>()
    val requestedIds = mutableListOf<String>()
    var saveFailure: Throwable? = null
    var recordExportFailure: Throwable? = null
    var beforeSave: suspend (Project) -> Unit = {}
    var beforeRecordExport: suspend (String, String) -> Unit = { _, _ -> }
    var recordExportCalls = 0
    var nextId = 0

    override fun observeProjects(): Flow<List<ProjectSummary>> = summaries

    override suspend fun getProject(id: String): Project? {
        requestedIds += id
        return values[id]
    }

    override suspend fun createBlank(): Project = add(ProjectTemplates.blank(id = "blank-${++nextId}"))
    override suspend fun createSample(): Project = add(ProjectTemplates.sample(id = "sample-${++nextId}"))

    override suspend fun save(project: Project): Project {
        saveFailure?.let { throw it }
        beforeSave(project)
        check(values.containsKey(project.id)) { "项目已被删除" }
        saved += project
        return add(project)
    }

    override suspend fun duplicate(id: String): Project? = values[id]?.let { source ->
        add(source.copy(id = "copy-${++nextId}", name = "${source.name} 副本"))
    }

    override suspend fun rename(id: String, name: String): Boolean {
        val project = values[id] ?: return false
        add(project.copy(name = name))
        return true
    }

    override suspend fun delete(id: String): Boolean {
        val removed = values.remove(id) != null
        emitSummaries()
        return removed
    }

    override suspend fun updateThumbnail(id: String, thumbnailPath: String?): Boolean {
        val project = values[id] ?: return false
        add(project.copy(thumbnailPath = thumbnailPath))
        return true
    }

    override suspend fun markExported(id: String): Boolean {
        val project = values[id] ?: return false
        add(project.copy(lastExportedAt = project.updatedAt + 1))
        return true
    }

    override suspend fun recordExport(id: String, thumbnailPath: String): Boolean {
        recordExportCalls += 1
        beforeRecordExport(id, thumbnailPath)
        recordExportFailure?.let { throw it }
        val project = values[id] ?: return false
        add(
            project.copy(
                thumbnailPath = thumbnailPath,
                lastExportedAt = project.updatedAt + 1,
                updatedAt = project.updatedAt + 1,
            ),
        )
        return true
    }

    override suspend fun reconcileCoverAssets() = Unit

    private fun add(project: Project): Project {
        values[project.id] = project
        emitSummaries()
        return project
    }

    private fun emitSummaries() {
        summaries.value = values.values.map(Project::toSummary)
    }
}

class FakePreferencesStore(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesStore {
    private val values = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = values
    var failure: Throwable? = null

    override suspend fun setDarkMode(enabled: Boolean) {
        failure?.let { throw it }
        values.value = values.value.copy(darkMode = enabled)
    }

    override suspend fun setDefaultExportScale(scale: Int) {
        failure?.let { throw it }
        values.value = values.value.copy(defaultExportScale = scale)
    }

    override suspend fun setShowSafeArea(enabled: Boolean) {
        failure?.let { throw it }
        values.value = values.value.copy(showSafeArea = enabled)
    }
}

class FakeProjectAssets : ProjectAssets {
    val deleted = mutableListOf<String>()
    val deleteContextWasActive = mutableListOf<Boolean>()
    var importUriBlock: suspend (Uri) -> String = { "00000000-0000-4000-8000-000000000001" }
    var importBytesBlock: suspend (ByteArray) -> String = { "00000000-0000-4000-8000-000000000002" }
    var deleteBlock: suspend (String) -> Unit = {}
    override suspend fun importCover(uri: Uri): String = importUriBlock(uri)
    override suspend fun importCover(bytes: ByteArray): String = importBytesBlock(bytes)
    override suspend fun delete(id: String) {
        deleteContextWasActive += currentCoroutineContext().isActive
        deleted += id
        deleteBlock(id)
    }
}

class FakeNeteaseClient : NeteaseClient {
    var searchBlock: suspend (String) -> List<NeteaseSongSearchResult> = { emptyList() }
    var resolveSongBlock: suspend (String) -> ResolvedNeteaseSong = { error("not configured") }
    var resolveLinkBlock: suspend (String) -> ResolvedNeteaseSong = { error("not configured") }
    var downloadCoverBlock: suspend (String) -> ByteArray = { error("not configured") }
    override suspend fun search(keyword: String): List<NeteaseSongSearchResult> = searchBlock(keyword)
    override suspend fun resolveSong(id: String): ResolvedNeteaseSong = resolveSongBlock(id)
    override suspend fun resolveLink(input: String): ResolvedNeteaseSong = resolveLinkBlock(input)
    override suspend fun downloadCover(url: String): ByteArray = downloadCoverBlock(url)
}

class FakeRendererOperations : RendererOperations {
    var exportCalls = 0
    var retryCalls = 0
    var exportBlock: suspend (Project, Int) -> ExportedImage = { _, _ -> error("not configured") }
    var palette = PaletteSpec()
    var paletteBlock: suspend (String) -> PaletteSpec = { palette }

    override suspend fun exportPng(project: Project, multiplier: Int): ExportedImage {
        exportCalls += 1
        return exportBlock(project, multiplier)
    }

    override suspend fun extractPalette(assetId: String): PaletteSpec = paletteBlock(assetId)

    override fun retry() {
        retryCalls += 1
    }
}

class FakeExportFiles : ExportFiles {
    var clearBytes = 0L
    var clearFailure: Throwable? = null
    val copied = mutableListOf<Pair<ExportedImage, Uri>>()
    var thumbnailCalls = 0
    var createThumbnailBlock: suspend (String, ExportedImage) -> String = { projectId, image ->
        File(image.file.parentFile, "$projectId-thumbnail.png").absolutePath
    }

    override suspend fun createThumbnail(projectId: String, image: ExportedImage): String {
        thumbnailCalls += 1
        return createThumbnailBlock(projectId, image)
    }

    override suspend fun copyTo(image: ExportedImage, destination: Uri) {
        copied += image to destination
    }

    override suspend fun clearExportCache(): Long {
        clearFailure?.let { throw it }
        return clearBytes
    }
}

object FakeEditorMessages : EditorMessageResolver {
    override fun lineLimit(violation: RenderSpecViolation, loadingStoredProject: Boolean): String =
        "line limit ${violation.actual}/${violation.limit}"
}

fun Project.toSummary() = ProjectSummary(
    id = id,
    name = name,
    schemaVersion = spec.schemaVersion,
    rendererVersion = spec.rendererVersion,
    coverAssetId = coverAssetId,
    thumbnailPath = thumbnailPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastExportedAt = lastExportedAt,
)
