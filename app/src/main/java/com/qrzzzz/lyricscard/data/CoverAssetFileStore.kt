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
