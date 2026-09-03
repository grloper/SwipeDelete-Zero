package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
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
    private val cloudUploadDao: CloudUploadDao,
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

    fun observeBackedUpFiles(): Flow<List<BackedUpFileEntity>> = backedUpFileDao.observeAll()

    fun observeCloudUploads(): Flow<List<CloudUploadEntity>> = cloudUploadDao.observeAll()

    suspend fun isBackedUp(uri: String): Boolean = backedUpFileDao.exists(uri)

    suspend fun getBackedUpFile(uri: String): BackedUpFileEntity? = backedUpFileDao.get(uri)

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

    suspend fun markBackedUpDirect(uri: String, sizeBytes: Long, remoteId: String) {
        backedUpFileDao.insert(
            BackedUpFileEntity(
                contentUri = uri,
                sizeBytes = sizeBytes,
                remoteId = remoteId,
                uploadedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    /** Remove from ledger so the app forgets it was previously backed up. */
    suspend fun forgetBackedUp(uri: String): Boolean {
        val deleted = backedUpFileDao.delete(uri) > 0
        cloudUploadDao.delete(uri)
        return deleted
    }

    /**
     * Re-backup: Clears any stale ledger record and resets or creates a fresh QUEUED
     * upload row so the upload worker can cleanly push it to Google Photos.
     */
    suspend fun rebackup(
        uri: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long
    ) {
        backedUpFileDao.delete(uri)
        val now = System.currentTimeMillis()
        cloudUploadDao.upsert(
            CloudUploadEntity(
                contentUri = uri,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                state = CloudUploadEntity.STATE_QUEUED,
                uploadUrl = null,
                bytesUploaded = 0,
                uploadToken = null,
                mediaItemId = null,
                attempts = 0,
                lastError = null,
                enqueuedAtMillis = now,
                updatedAtMillis = now,
            )
        )
    }

    suspend fun retryAllFailedUploads(): Int = cloudUploadDao.retryAllFailed()

    suspend fun clearCompletedUploads(): Int = cloudUploadDao.clearCompleted()

    suspend fun cancelUpload(uri: String) {
        cloudUploadDao.delete(uri)
    }
}
