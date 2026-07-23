package com.swipedelete.zero.domain.model

import android.net.Uri

/** The kind of media a [MediaItem] represents. Drives which purge path is legal. */
enum class MediaType {
    IMAGE,
    VIDEO,
    AUDIO,

    /** APKs, zips, raw downloads — cannot use MediaStore trash; needs SAF/MANAGE. */
    DOCUMENT;

    val isMediaStoreTrashable: Boolean
        get() = this == IMAGE || this == VIDEO || this == AUDIO
}

/**
 * A single scanned file surfaced onto a card.
 *
 * [contentUri] is the canonical handle used for previews and deletion. For media
 * this is a `content://media/...` MediaStore uri; for documents it is a SAF
 * `content://` tree/document uri.
 */
data class MediaItem(
    /** MediaStore `_ID` for media, or a stable hash of the SAF uri for documents. */
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val type: MediaType,
    val sizeBytes: Long,
    /** Epoch millis the file was added/taken. */
    val dateAddedMillis: Long,
    val width: Int = 0,
    val height: Int = 0,
    /** Video/audio duration in millis, 0 for stills. */
    val durationMillis: Long = 0,
    /** Absolute filesystem path when known (used for the exclusion vault key). */
    val relativePath: String? = null,
    /** MediaStore `IS_PENDING` / cloud-only tombstone — excluded from decks. */
    val isPending: Boolean = false,
    /** Perceptual hash (dHash), populated lazily by the background worker. */
    val perceptualHash: Long? = null,
    /** Laplacian variance sharpness score; lower = blurrier. Null until computed. */
    val sharpnessScore: Double? = null,
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}×${height}" else "—"

    val megapixels: Double
        get() = (width.toLong() * height.toLong()) / 1_000_000.0

    val isVideo: Boolean get() = type == MediaType.VIDEO
}
