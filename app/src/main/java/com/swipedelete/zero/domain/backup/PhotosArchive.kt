package com.swipedelete.zero.domain.backup

import android.content.Intent
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Comprehensive stats for real-time cloud upload tracking & speed measurement. */
data class CloudUploadStats(
    val totalCount: Int = 0,
    val queuedCount: Int = 0,
    val uploadingCount: Int = 0,
    val verifyingCount: Int = 0,
    val verifiedCount: Int = 0,
    val failedCount: Int = 0,
    val totalBytes: Long = 0L,
    val uploadedBytes: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val etaSeconds: Long? = null,
    val activeFileName: String? = null,
    val activeFileProgress: Float? = null,
) {
    val overallProgress: Float
        get() = if (totalBytes <= 0) 0f else (uploadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    val isIdle: Boolean
        get() = queuedCount == 0 && uploadingCount == 0 && verifyingCount == 0
}

/** Lifecycle of one up-swiped file on its way into Google Photos. */
sealed interface ArchiveItemState {
    data object Queued : ArchiveItemState
    data class Uploading(val bytesSent: Long, val totalBytes: Long) : ArchiveItemState {
        val progress: Float
            get() = if (totalBytes <= 0) 0f else (bytesSent.toFloat() / totalBytes).coerceIn(0f, 1f)
    }
    data object Verifying : ArchiveItemState
    data class Verified(val mediaItemId: String) : ArchiveItemState
    data class Failed(val reason: String, val retryable: Boolean) : ArchiveItemState
}

/**
 * Flavor seam for the up-swipe "archive to Google Photos" flow.
 *
 * The fdroid/play flavors bind [NoOpPhotosArchive] — up-swipe keeps its Star
 * semantics and no network code is compiled in. The cloud flavor binds a
 * Google Photos implementation whose contract is strict: a file may only be
 * staged for local deletion after the upload was VERIFIED (batchCreate
 * returned a valid mediaItemId).
 */
interface PhotosArchive {
    /** True only in the cloud flavor — gates the swipe-up semantics switch. */
    val isAvailable: Boolean

    /** Live upload queue keyed by contentUri string. Empty flow when no-op. */
    val queue: Flow<Map<String, ArchiveItemState>>

    /** Live upload transfer speed, progress, and queue statistics. */
    val uploadStats: Flow<CloudUploadStats>

    /** Queue an up-swiped file for upload; idempotent per uri. */
    suspend fun enqueue(item: MediaItem)

    /** Undo an up-swipe: drop the row only if the upload hasn't started. */
    suspend fun cancelIfQueued(contentUri: String)

    /** Force cancel an upload item regardless of state. */
    suspend fun cancel(contentUri: String)

    /** Retry a failed upload. */
    fun retry(contentUri: String)

    /** Retry all failed uploads in the queue. */
    fun retryAllFailed()

    /** Clear verified/finished items from the queue history view. */
    fun clearFinished()

    /** Re-upload a file from scratch (e.g. after remote deletion or ledger reset). */
    suspend fun rebackup(item: MediaItem)

    /** Deep-link into the Google Photos app (manual-backup fallback), or null. */
    fun openInPhotosIntent(): Intent?

    companion object {
        /** sourceDeckId marking staged rows that came from a VERIFIED upload. */
        const val VERIFIED_SOURCE_DECK = "photos:verified"
    }
}

@Singleton
class NoOpPhotosArchive @Inject constructor() : PhotosArchive {
    override val isAvailable: Boolean = false
    override val queue: Flow<Map<String, ArchiveItemState>> = MutableStateFlow(emptyMap())
    override val uploadStats: Flow<CloudUploadStats> = MutableStateFlow(CloudUploadStats())
    override suspend fun enqueue(item: MediaItem) = Unit
    override suspend fun cancelIfQueued(contentUri: String) = Unit
    override suspend fun cancel(contentUri: String) = Unit
    override fun retry(contentUri: String) = Unit
    override fun retryAllFailed() = Unit
    override fun clearFinished() = Unit
    override suspend fun rebackup(item: MediaItem) = Unit
    override fun openInPhotosIntent(): Intent? = null
}
