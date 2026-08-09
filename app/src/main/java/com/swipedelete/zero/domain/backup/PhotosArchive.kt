package com.swipedelete.zero.domain.backup

import android.content.Intent
import com.swipedelete.zero.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Queue an up-swiped file for upload; idempotent per uri. */
    suspend fun enqueue(item: MediaItem)

    /** Undo an up-swipe: drop the row only if the upload hasn't started. */
    suspend fun cancelIfQueued(contentUri: String)

    /** Retry a failed upload. */
    fun retry(contentUri: String)

    /** Deep-link into the Google Photos app (manual-backup fallback), or null. */
    fun openInPhotosIntent(): Intent?
}

@Singleton
class NoOpPhotosArchive @Inject constructor() : PhotosArchive {
    override val isAvailable: Boolean = false
    override val queue: Flow<Map<String, ArchiveItemState>> = MutableStateFlow(emptyMap())
    override suspend fun enqueue(item: MediaItem) = Unit
    override suspend fun cancelIfQueued(contentUri: String) = Unit
    override fun retry(contentUri: String) = Unit
    override fun openInPhotosIntent(): Intent? = null
}
