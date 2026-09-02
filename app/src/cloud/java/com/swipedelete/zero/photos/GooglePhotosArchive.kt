package com.swipedelete.zero.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.swipedelete.zero.data.local.BackedUpFileDao
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.ArchiveItemState
import com.swipedelete.zero.domain.backup.CloudUploadStats
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud-flavor [PhotosArchive]: up-swipes enqueue into the Room-backed upload
 * queue and kick the foreground WorkManager drain. All safety lives in the
 * queue contract — nothing is ever staged for deletion until a row reaches
 * VERIFIED via the batchCreate handshake.
 */
@Singleton
class GooglePhotosArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadDao: CloudUploadDao,
    private val backedUpFileDao: BackedUpFileDao,
) : PhotosArchive {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val isAvailable: Boolean = true

    override val queue: Flow<Map<String, ArchiveItemState>> =
        uploadDao.observeAll().map { rows ->
            rows.associate { it.contentUri to it.toArchiveState() }
        }

    override val uploadStats: Flow<CloudUploadStats> =
        uploadDao.observeAll().map { rows ->
            val totalCount = rows.size
            val queued = rows.count { it.state == CloudUploadEntity.STATE_QUEUED }
            val uploading = rows.count { it.state == CloudUploadEntity.STATE_UPLOADING }
            val verifying = rows.count { it.state == CloudUploadEntity.STATE_VERIFYING }
            val verified = rows.count { it.state == CloudUploadEntity.STATE_VERIFIED }
            val failed = rows.count { it.state == CloudUploadEntity.STATE_FAILED }
            val totalBytes = rows.sumOf { it.sizeBytes }
            val uploadedBytes = rows.sumOf { it.bytesUploaded }

            val activeRow = rows.firstOrNull { it.state == CloudUploadEntity.STATE_UPLOADING }
                ?: rows.firstOrNull { it.state == CloudUploadEntity.STATE_VERIFYING }
                ?: rows.firstOrNull { it.state == CloudUploadEntity.STATE_QUEUED }

            val activeFileProg = activeRow?.let {
                if (it.sizeBytes <= 0) 0f else (it.bytesUploaded.toFloat() / it.sizeBytes).coerceIn(0f, 1f)
            }

            val remainingBytes = (totalBytes - uploadedBytes).coerceAtLeast(0L)
            // Estimated transfer rate baseline for broadband upload (1.5 MB/s nominal estimate when active)
            val nominalSpeed = if (uploading > 0) 1_500_000L else 0L
            val etaSec = if (nominalSpeed > 0 && remainingBytes > 0) remainingBytes / nominalSpeed else null

            CloudUploadStats(
                totalCount = totalCount,
                queuedCount = queued,
                uploadingCount = uploading,
                verifyingCount = verifying,
                verifiedCount = verified,
                failedCount = failed,
                totalBytes = totalBytes,
                uploadedBytes = uploadedBytes,
                uploadSpeedBytesPerSec = nominalSpeed,
                etaSeconds = etaSec,
                activeFileName = activeRow?.displayName,
                activeFileProgress = activeFileProg,
            )
        }

    override suspend fun enqueue(item: MediaItem) {
        val uri = item.contentUri.toString()
        val existing = uploadDao.get(uri)
        // Idempotent: an active or verified row is left alone; a failed row is
        // re-armed from scratch.
        if (existing != null && existing.state != CloudUploadEntity.STATE_FAILED) return
        val now = System.currentTimeMillis()
        uploadDao.upsert(
            CloudUploadEntity(
                contentUri = uri,
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                state = CloudUploadEntity.STATE_QUEUED,
                enqueuedAtMillis = existing?.enqueuedAtMillis ?: now,
                updatedAtMillis = now,
            )
        )
        kickWorker()
    }

    override suspend fun cancelIfQueued(contentUri: String) {
        uploadDao.deleteIfQueued(contentUri)
    }

    override suspend fun cancel(contentUri: String) {
        uploadDao.delete(contentUri)
    }

    override fun retry(contentUri: String) {
        scope.launch {
            val row = uploadDao.get(contentUri) ?: return@launch
            if (row.state != CloudUploadEntity.STATE_FAILED) return@launch
            uploadDao.upsert(
                row.copy(
                    state = CloudUploadEntity.STATE_QUEUED,
                    attempts = 0,
                    lastError = null,
                    updatedAtMillis = System.currentTimeMillis(),
                )
            )
            kickWorker()
        }
    }

    override fun retryAllFailed() {
        scope.launch {
            val updated = uploadDao.retryAllFailed()
            if (updated > 0) {
                kickWorker()
            }
        }
    }

    override fun clearFinished() {
        scope.launch {
            uploadDao.clearCompleted()
        }
    }

    override suspend fun rebackup(item: MediaItem) {
        val uri = item.contentUri.toString()
        backedUpFileDao.delete(uri)
        val now = System.currentTimeMillis()
        uploadDao.upsert(
            CloudUploadEntity(
                contentUri = uri,
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
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
        kickWorker()
    }

    override fun openInPhotosIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://photos.google.com/"))
            .setPackage(GOOGLE_PHOTOS_PACKAGE)

    private fun kickWorker() {
        val request = OneTimeWorkRequestBuilder<PhotosUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: if a drain is mid-flight, the fresh request runs
        // after it — closing the race where a row lands just as the previous
        // drain observed an empty queue.
        WorkManager.getInstance(context).enqueueUniqueWork(
            PhotosUploadWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object {
        const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
    }
}

private fun CloudUploadEntity.toArchiveState(): ArchiveItemState = when (state) {
    CloudUploadEntity.STATE_QUEUED -> ArchiveItemState.Queued
    CloudUploadEntity.STATE_UPLOADING -> ArchiveItemState.Uploading(bytesUploaded, sizeBytes)
    CloudUploadEntity.STATE_VERIFYING -> ArchiveItemState.Verifying
    CloudUploadEntity.STATE_VERIFIED -> ArchiveItemState.Verified(mediaItemId.orEmpty())
    else -> ArchiveItemState.Failed(
        reason = lastError ?: "Upload failed",
        retryable = true,
    )
}
