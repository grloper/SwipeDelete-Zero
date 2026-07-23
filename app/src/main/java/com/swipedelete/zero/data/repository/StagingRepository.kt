package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.StagedFileDao
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Staging Review Drawer's data source (tier 2 of the safety pipeline).
 * Nothing here deletes anything on disk — it only maintains the local queue of
 * URIs the user marked for deletion, with live count/size aggregates.
 */
@Singleton
class StagingRepository @Inject constructor(
    private val dao: StagedFileDao,
) {
    fun observeStaged(): Flow<List<StagedFileEntity>> = dao.observeAll()
    fun observeCount(): Flow<Int> = dao.observeCount()
    fun observeStagedBytes(): Flow<Long> = dao.observeStagedBytes()

    suspend fun stage(item: MediaItem, deckId: String?) {
        dao.stage(
            StagedFileEntity(
                contentUri = item.contentUri.toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                mediaType = item.type.name,
                sizeBytes = item.sizeBytes,
                relativePath = item.relativePath,
                stagedAtMillis = 0L,
                sourceDeckId = deckId,
            )
        )
    }

    suspend fun restore(uri: String) = dao.unstage(uri)
    suspend fun clearQueue() = dao.clear()
    suspend fun getAll(): List<StagedFileEntity> = dao.getAll()

    /** Remove only the successfully-purged URIs — partial-success safe. */
    suspend fun removePurged(uris: List<String>) = dao.removeAll(uris)
}
