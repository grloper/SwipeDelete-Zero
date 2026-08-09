package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.KeptFileEntity
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which files the user chose to protect (keep / star swipes) and which
 * of them have already been backed up. The difference between the two sets is
 * the incremental backup work-list — a file is uploaded exactly once, and only
 * files kept after the last run are picked up by the next one.
 */
@Singleton
class BackupRepository @Inject constructor(
    private val keptFileDao: KeptFileDao,
    private val backedUpFileDao: BackedUpFileDao,
) {

    suspend fun recordKept(item: MediaItem, starred: Boolean) {
        keptFileDao.upsert(
            KeptFileEntity(
                contentUri = item.contentUri.toString(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                keptAtMillis = System.currentTimeMillis(),
                starred = starred,
            )
        )
    }

    /** Undo of a keep/star swipe. */
    suspend fun removeKept(contentUri: String) = keptFileDao.remove(contentUri)

    suspend fun pendingBackup(): List<KeptFileEntity> = keptFileDao.pendingBackup()

    fun observePendingBackupCount(): Flow<Int> = keptFileDao.observePendingBackupCount()

    fun observeBackedUpCount(): Flow<Int> = backedUpFileDao.observeCount()

    /** Every backed-up uri — powers the per-card cloud verification chip. */
    fun observeBackedUpUris(): Flow<List<String>> = backedUpFileDao.observeBackedUpUris()

    suspend fun markBackedUp(file: KeptFileEntity, remoteId: String) {
        backedUpFileDao.insert(
            BackedUpFileEntity(
                contentUri = file.contentUri,
                sizeBytes = file.sizeBytes,
                remoteId = remoteId,
                uploadedAtMillis = System.currentTimeMillis(),
            )
        )
    }
}
