package com.qrzzzz.lyricscard.data

/**
 * File operations used after Room has committed an asset-reference mutation.
 *
 * Imported files may exist briefly before their project reference is committed. Implementations
 * must protect those in-flight files from reconciliation.
 */
interface CoverAssetFileStore {
    suspend fun markReferenced(id: String)

    suspend fun delete(id: String)

    suspend fun deleteUnreferenced(referencedIds: Set<String>)

    data object NoOp : CoverAssetFileStore {
        override suspend fun markReferenced(id: String) = Unit

        override suspend fun delete(id: String) = Unit

        override suspend fun deleteUnreferenced(referencedIds: Set<String>) = Unit
    }
}

/**
 * Private-file lifecycle operations that accompany the persisted cover reference ledger.
 *
 * Cover reference counts remain owned by Room. Implementations reconcile only the files derived
 * from that committed state: cover payloads, project thumbnails, and temporary export artifacts.
 */
interface ProjectStorageFileStore : CoverAssetFileStore {
    suspend fun deleteThumbnail(path: String)

    suspend fun reconcileProjectFiles(
        referencedCoverAssetIds: Set<String>,
        referencedThumbnailPaths: Set<String>,
    ): ProjectFileReconcileResult
}

data class ProjectFileReconcileResult(
    val missingCoverAssetIds: Set<String> = emptySet(),
    val missingThumbnailPaths: Set<String> = emptySet(),
    val deletedOrphanCoverCount: Int = 0,
    val deletedOrphanThumbnailCount: Int = 0,
    val deletedPartialExportCount: Int = 0,
    val prunedExportCount: Int = 0,
)
