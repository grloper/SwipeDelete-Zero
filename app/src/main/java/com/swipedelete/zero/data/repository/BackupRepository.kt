package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.KeptFileDao
import com.swipedelete.zero.data.local.KeptFileEntity
import com.swipedelete.zero.domain.backup.CloudCopy
import com.swipedelete.zero.domain.backup.CloudDestination
import com.swipedelete.zero.domain.backup.RemoteState
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks which files the user chose to protect (keep / star swipes), which of
 * them have been uploaded, **where** each copy went, and whether that copy has
 * since been confirmed to still exist on the provider.
 *
 * The ledger is deliberately scoped to this app's own uploads. Google Photos'
 * built-in auto-backup is invisible to us: the `photoslibrary.appendonly` scope
 * is upload-only and the read scopes that survived the 2025 API changes only
 * expose app-created content. So "not in the ledger" means "this app has not
 * uploaded it", never "it is not backed up anywhere" — and the UI must say so
 * in those words rather than implying knowledge it does not have.
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

    fun observeCountFor(destination: CloudDestination): Flow<Int> =
        backedUpFileDao.observeCountFor(destination.name)

    /** Every backed-up uri — powers the per-card cloud chip. */
    fun observeBackedUpUris(): Flow<List<String>> = backedUpFileDao.observeBackedUpUris()

    /**
     * uri -> what we know about that file's copy, so a card can say *where* it
     * lives and whether the provider has actually confirmed it since.
     */
    fun observeCopies(): Flow<Map<String, CloudCopy>> =
        backedUpFileDao.observeAll().map { rows ->
            rows.associate {
                it.contentUri to CloudCopy(
                    destination = CloudDestination.parse(it.destination),
                    state = RemoteState.parse(it.remoteState),
                )
            }
        }

    /** Full ledger for the Cloud monitor. */
    fun observeLedger(): Flow<List<BackedUpFileEntity>> = backedUpFileDao.observeAll()

    suspend fun ledgerRows(): List<BackedUpFileEntity> = backedUpFileDao.getAll()

    suspend fun markBackedUp(
        file: KeptFileEntity,
        remoteId: String,
        destination: CloudDestination,
    ) {
        backedUpFileDao.insert(
            BackedUpFileEntity(
                contentUri = file.contentUri,
                sizeBytes = file.sizeBytes,
                remoteId = remoteId,
                uploadedAtMillis = System.currentTimeMillis(),
                destination = destination.name,
                displayName = file.displayName,
                remoteState = RemoteState.RECORDED.name,
            )
        )
    }

    /** Record a Photos archive upload that already passed its batchCreate check. */
    suspend fun markArchived(
        contentUri: String,
        displayName: String,
        sizeBytes: Long,
        mediaItemId: String,
    ) {
        backedUpFileDao.insert(
            BackedUpFileEntity(
                contentUri = contentUri,
                sizeBytes = sizeBytes,
                remoteId = mediaItemId,
                uploadedAtMillis = System.currentTimeMillis(),
                destination = CloudDestination.PHOTOS.name,
                displayName = displayName,
                // batchCreate returning an id is a real handshake, so this copy
                // starts out confirmed rather than merely recorded.
                remoteState = RemoteState.CONFIRMED.name,
                verifiedAtMillis = System.currentTimeMillis(),
            )
        )
    }

    /** Store the result of reading a copy back from its provider. */
    suspend fun markVerification(uri: String, state: RemoteState, error: String? = null) {
        backedUpFileDao.markVerified(
            uri = uri,
            state = state.name,
            at = System.currentTimeMillis(),
            error = error,
        )
    }

    /**
     * Drop a ledger row whose remote copy is gone, so the file returns to the
     * pending-backup work-list instead of being treated as safe forever.
     */
    suspend fun forget(uri: String) = backedUpFileDao.remove(uri)
}
