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
 */
@Entity(tableName = "media_analysis")
data class MediaAnalysisEntity(
    @PrimaryKey val mediaId: Long,
    val contentUri: String,
    val dHash: Long,
    val pHash: Long,
    val sharpnessVariance: Double,
    val meanLuma: Double,
    val isBlurry: Boolean,
    /** File size at analysis time — invalidate the row if the file changed. */
    val sizeBytes: Long,
    val analyzedAtMillis: Long,
)
