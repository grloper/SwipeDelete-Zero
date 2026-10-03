package com.swipedelete.zero.data.repository

import androidx.room.withTransaction
import com.swipedelete.zero.data.local.AppDatabase
import com.swipedelete.zero.data.local.BackupReceiptDao
import com.swipedelete.zero.data.local.BackupReceiptEntity
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.KeptFileEntity
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

/**
 * Tracks which files the user chose to protect (keep / star swipes) and which
 * of them have already been backed up. The difference between the two sets is
 * the incremental backup work-list — a file is uploaded exactly once, and only
 * files kept after the last run are picked up by the next one.
 */
@Singleton
class BackupRepository constructor(
    private val keptFileDao: KeptFileDao,
    private val backedUpFileDao: BackedUpFileDao,
    private val cloudUploadDao: CloudUploadDao,
    private val receiptDao: BackupReceiptDao? = null,
    private val database: AppDatabase? = null,
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

    suspend fun pendingDriveBackup(accountId: String): List<KeptFileEntity> =
        if (receiptDao == null) keptFileDao.pendingBackup() else keptFileDao.pendingDriveBackup(accountId)

    fun observePendingDriveBackupCount(accountId: String): Flow<Int> =
        if (receiptDao == null) keptFileDao.observePendingBackupCount()
        else keptFileDao.observePendingDriveBackupCount(accountId)

    fun observePendingBackupCount(): Flow<Int> = keptFileDao.observePendingBackupCount()

    fun observeBackedUpCount(): Flow<Int> = backedUpFileDao.observeCount()

    /** Every backed-up uri — powers the per-card cloud verification chip. */
    fun observeBackedUpUris(): Flow<List<String>> = backedUpFileDao.observeBackedUpUris()

    fun observeBackedUpFiles(): Flow<List<BackedUpFileEntity>> = backedUpFileDao.observeAll()

    fun observeVerifiedReceipts(): Flow<List<BackupReceiptEntity>> =
        receiptDao?.observeAll() ?: kotlinx.coroutines.flow.flowOf(emptyList())

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

    /** Commit both indexes together. A crash must never create a validated
     * receipt without the row that backs the user's visible backup history. */
    suspend fun markVerifiedDriveBackup(
        file: KeptFileEntity, accountId: String, remoteId: String, sha256: String,
    ) {
        require(accountId.isNotBlank() && sha256.matches(Regex("[0-9a-f]{64}")))
        val receipt = BackupReceiptEntity(
            contentUri = file.contentUri,
            provider = "GOOGLE_DRIVE",
            accountId = accountId,
            remoteId = remoteId,
            originalSha256 = sha256,
            originalSizeBytes = file.sizeBytes,
            displayName = file.displayName,
            mimeType = file.mimeType,
            verifiedAtMillis = System.currentTimeMillis(),
        )
        val db = requireNotNull(database) { "Backup receipt database unavailable" }
        val dao = requireNotNull(receiptDao) { "Backup receipt DAO unavailable" }
        db.withTransaction {
            dao.upsert(receipt)
            // Photos uses the legacy URI-keyed row for its live readback gate.
            // Preserve it when both destinations contain the same local file.
            val existing = backedUpFileDao.get(file.contentUri)
            if (existing == null || !existing.remoteId.startsWith("photos:")) {
                backedUpFileDao.insert(BackedUpFileEntity(
                    contentUri = file.contentUri, sizeBytes = file.sizeBytes,
                    remoteId = remoteId, uploadedAtMillis = receipt.verifiedAtMillis,
                ))
            }
        }
    }

    /** Explicitly forget all LOCAL backup metadata for this URI. No remote/original deletion. */
    suspend fun forgetBackedUp(uri: String): Boolean {
        val db = requireNotNull(database) { "Backup database unavailable" }
        val receipts = requireNotNull(receiptDao) { "Backup receipt DAO unavailable" }
        return db.withTransaction {
            val legacy = backedUpFileDao.delete(uri)
            val verified = receipts.removeLocalHistoryForUri(uri)
            cloudUploadDao.delete(uri)
            legacy + verified > 0
        }
    }

    /** Receipts describe previously verified bytes, not a fresh check of today's local bytes.
     * Explicit recheck invalidates only this Drive account and preserves other destinations. */
    suspend fun invalidateDriveReceipts(accountId: String): Int {
        require(accountId.isNotBlank()) { "Connect a Google account before rechecking originals" }
        return requireNotNull(receiptDao).invalidateDriveAccount(accountId)
    }

    /** Queue a new Photos upload authorized for the selected account. Worker rechecks identity. */
    suspend fun rebackup(
        uri: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        accountName: String?,
    ) {
        require(!accountName.isNullOrBlank()) { "Connect a Google account before re-uploading" }
        val db = requireNotNull(database) { "Backup database unavailable" }
        db.withTransaction {
            // A Photos retry must not erase an independent Drive backup index/receipt.
            if (backedUpFileDao.get(uri)?.remoteId?.startsWith("photos:") == true) {
                backedUpFileDao.delete(uri)
            }
            val now = System.currentTimeMillis()
            cloudUploadDao.upsert(CloudUploadEntity(
                contentUri = uri, displayName = displayName, mimeType = mimeType,
                sizeBytes = sizeBytes, state = CloudUploadEntity.STATE_QUEUED,
                uploadUrl = null, bytesUploaded = 0, uploadToken = null,
                mediaItemId = null, attempts = 0, lastError = null,
                enqueuedAtMillis = now, updatedAtMillis = now, accountName = accountName,
            ))
        }
    }
    suspend fun retryAllFailedUploads(): Int = cloudUploadDao.retryAllFailed()

    suspend fun clearCompletedUploads(): Int = cloudUploadDao.clearCompleted()

    suspend fun cancelUpload(uri: String) {
        cloudUploadDao.delete(uri)
    }
}
