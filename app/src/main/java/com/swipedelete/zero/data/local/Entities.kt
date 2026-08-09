package com.swipedelete.zero.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A file the user swiped left — queued for deletion but NOT yet purged. This is
 * tier 2 of the safety pipeline (the Staging Review Drawer). Rows survive process
 * death so a queue is never silently lost.
 */
@Entity(tableName = "staged_files")
data class StagedFileEntity(
    /** Canonical content uri string — primary key, so re-staging is idempotent. */
    @PrimaryKey val contentUri: String,
    val displayName: String,
    val mimeType: String,
    /** [com.swipedelete.zero.domain.model.MediaType] name. */
    val mediaType: String,
    val sizeBytes: Long,
    val relativePath: String?,
    val stagedAtMillis: Long,
    /** The deck the file came from, for restore-into-deck flows. */
    val sourceDeckId: String?,
)

/**
 * A file the user decided to protect — swiped right (keep) or up (star).
 * This is the source set for the opt-in cloud backup: each row is backed up
 * exactly once (see [BackedUpFileEntity]) and re-keeping a file is idempotent.
 */
@Entity(tableName = "kept_files")
data class KeptFileEntity(
    @PrimaryKey val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val keptAtMillis: Long,
    /** True when the keep came from an up-swipe (star). */
    val starred: Boolean,
)

/**
 * Ledger of files already uploaded to the backup destination. A kept file is
 * pending backup iff it has no row here — that is what makes backup runs
 * incremental instead of re-uploading everything.
 */
@Entity(tableName = "backed_up_files")
data class BackedUpFileEntity(
    @PrimaryKey val contentUri: String,
    val sizeBytes: Long,
    /** Drive file id, or the Google Photos mediaItemId. */
    val remoteId: String,
    val uploadedAtMillis: Long,
    /**
     * Which provider holds this copy. Without it the app can say "backed up"
     * but not where, and a Drive copy is not a Photos copy — only the latter
     * appears in the user's photo library.
     */
    val destination: String = "DRIVE",
    /** Display name, so the monitor can list uploads without re-querying MediaStore. */
    val displayName: String = "",
    /** RECORDED / CONFIRMED / MISSING / UNKNOWN — see RemoteState. */
    val remoteState: String = "RECORDED",
    /** When the copy was last read back from the provider, 0 if never. */
    val verifiedAtMillis: Long = 0,
    /** Why a verification failed, when it did. */
    val lastError: String? = null,
)

/**
 * Persisted progress for a deck so a session can resume mid-way
 * ("24/50 swiped in July 2024").
 */
@Entity(tableName = "deck_sessions")
data class DeckSessionEntity(
    @PrimaryKey val deckId: String,
    val kind: String,
    val title: String,
    /** Index of the next un-swiped card. */
    val cursor: Int,
    val totalCount: Int,
    val updatedAtMillis: Long,
)

/**
 * The Exclusion Vault (tier of the persistence protocol). Either a specific
 * starred file (by [perceptualHash] and/or [uri]) or an excluded folder path.
 * Anything matched here is filtered out of all future scans.
 */
@Entity(
    tableName = "exclusions",
    indices = [Index(value = ["perceptualHash"]), Index(value = ["folderPath"])],
)
data class ExclusionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** STARRED_FILE or EXCLUDED_FOLDER. */
    val type: String,
    val uri: String?,
    val perceptualHash: Long?,
    val folderPath: String?,
    val label: String,
    val createdAtMillis: Long,
) {
    companion object {
        const val TYPE_STARRED_FILE = "STARRED_FILE"
        const val TYPE_EXCLUDED_FOLDER = "EXCLUDED_FOLDER"
    }
}

/**
 * Cache of computed perceptual hashes + sharpness scores, keyed by MediaStore id.
 * Lets the WorkManager job compute once and lets deck-building group duplicates
 * / find blurry shots without re-decoding bitmaps.
 *
 * Video rows carry only the metadata columns — hashes/blur stay null so a
 * metadata-only row can never be mistaken for a hashed image (a fake dHash of 0
 * would cluster every video as a "duplicate" of every other).
 */
@Entity(tableName = "media_analysis")
data class MediaAnalysisEntity(
    @PrimaryKey val mediaId: Long,
    val contentUri: String,
    val dHash: Long?,
    val pHash: Long?,
    val sharpnessVariance: Double?,
    val meanLuma: Double?,
    val isBlurry: Boolean?,
    /** File size at analysis time — invalidate the row if the file changed. */
    val sizeBytes: Long,
    val analyzedAtMillis: Long,
    /** Display codec name for videos ("HEVC", "H.264"…), null for images. */
    val videoCodec: String? = null,
    /** Video frame rate (fps), null when unknown or for images. */
    val frameRate: Float? = null,
    /** Video bitrate in bits/second, null when unknown or for images. */
    val bitrateBps: Long? = null,
    /**
     * Luminance bimodality (0..1) from [com.swipedelete.zero.domain.algorithm.TextDetector].
     * High values mean the image is predominantly text, which is how a chat
     * screenshot gets classified as a document rather than a photograph.
     */
    val bimodality: Double? = null,
)

/**
 * One queued/in-flight Google Photos upload (cloud flavor). Rows persist the
 * resumable-session URL and byte offset so a 20 GB upload survives process
 * death and resumes instead of restarting. A row only reaches VERIFIED after
 * the mediaItems:batchCreate handshake returned a non-empty mediaItemId — the
 * precondition for ever staging the local copy for deletion.
 */
@Entity(tableName = "cloud_uploads")
data class CloudUploadEntity(
    @PrimaryKey val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** One of [STATE_QUEUED], [STATE_UPLOADING], [STATE_VERIFYING], [STATE_VERIFIED], [STATE_FAILED]. */
    val state: String,
    /** Resumable upload session URL from the `start` handshake. */
    val uploadUrl: String? = null,
    val bytesUploaded: Long = 0,
    /** Upload token returned by the finalize chunk, consumed by batchCreate. */
    val uploadToken: String? = null,
    /** Set only after batchCreate verification — never null in VERIFIED state. */
    val mediaItemId: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    val enqueuedAtMillis: Long,
    val updatedAtMillis: Long,
) {
    companion object {
        const val STATE_QUEUED = "QUEUED"
        const val STATE_UPLOADING = "UPLOADING"
        const val STATE_VERIFYING = "VERIFYING"
        const val STATE_VERIFIED = "VERIFIED"
        const val STATE_FAILED = "FAILED"
    }
}
