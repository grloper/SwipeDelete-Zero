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
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.ArchiveItemState
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
) : PhotosArchive {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val isAvailable: Boolean = true

    override val queue: Flow<Map<String, ArchiveItemState>> =
        uploadDao.observeAll().map { rows ->
            rows.associate { it.contentUri to it.toArchiveState() }
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
