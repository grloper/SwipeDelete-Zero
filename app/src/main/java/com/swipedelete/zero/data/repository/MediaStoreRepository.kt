package com.swipedelete.zero.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the device's media library through MediaStore. Everything here is a
 * local content-resolver query — no network, ever.
 *
 * Edge cases handled explicitly (per the PRD hardware/OS constraints):
 *  - **Cloud-only / pending media:** rows with `IS_PENDING = 1` (and, where the
 *    provider exposes it, cloud-tombstoned rows) are skipped so thumbnail
 *    generation never crashes on a not-yet-downloaded file.
 *  - **Restricted app dirs:** queries are wrapped so a `SecurityException` on a
 *    restricted collection degrades to an empty list instead of a crash.
 */
@Singleton
class MediaStoreRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: StoragePermissionManager = StoragePermissionManager(context),
) {
    internal var sdkInt: Int = Build.VERSION.SDK_INT

    private val resolver get() = context.contentResolver

    /** Query all images + videos (the swipe-heavy collections). */
    suspend fun queryVisualMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            addAll(queryCollection(MediaType.IMAGE, imageCollection()))
            addAll(queryCollection(MediaType.VIDEO, videoCollection()))
        }
    }

    suspend fun queryAudio(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryCollection(MediaType.AUDIO, audioCollection())
    }

    private fun imageCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private fun queryCollection(type: MediaType, collection: Uri): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        // Only fully-committed local rows. IS_PENDING guards half-written and
        // cloud-placeholder items that would crash thumbnail decode.
        val selection = "${MediaStore.MediaColumns.IS_PENDING} = 0"
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        return safeQuery(collection, projection, selection, null, sortOrder) { cursor ->
            val out = ArrayList<MediaItem>(cursor.count)
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val wCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val hCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val durCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val size = cursor.getLong(sizeCol)
                if (size <= 0) continue // skip zero-byte / not-yet-downloaded shells
                out += MediaItem(
                    id = id,
                    contentUri = ContentUris.withAppendedId(collection, id),
                    displayName = cursor.getString(nameCol) ?: "Unknown",
                    mimeType = cursor.getString(mimeCol) ?: "application/octet-stream",
                    type = type,
                    sizeBytes = size,
                    dateAddedMillis = cursor.getLong(dateCol) * 1000L,
                    width = if (wCol >= 0) cursor.getInt(wCol) else 0,
                    height = if (hCol >= 0) cursor.getInt(hCol) else 0,
                    durationMillis = if (durCol >= 0) cursor.getLong(durCol) else 0,
                    relativePath = if (pathCol >= 0) cursor.getString(pathCol) else null,
                    isPending = false,
                )
            }
            out
        } ?: emptyList()
    }

    /** Explicit reconciliation states for media deletion verification. */
    enum class MediaItemState {
        /** Row is confirmed absent from MediaStore (permanently deleted). */
        ABSENT,
        /** Row exists and is active (not trashed, not deleted). */
        PRESENT,
        /** Row exists in MediaStore with IS_TRASHED = 1. */
        TRASHED,
        /** Query failed or permission lost; unreadable != deleted. */
        UNKNOWN,
    }

    /**
     * Reconciles the exact state of a media item in MediaStore.
     * Crucial safety rule: query failure or permission revocation yields UNKNOWN, never ABSENT.
     */
    fun inspectMediaState(uri: Uri): MediaItemState = try {
        if (!permissionManager.hasMediaAccess()) {
            MediaItemState.UNKNOWN
        } else if (sdkInt >= Build.VERSION_CODES.R) {
            val bundle = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            val cursor = resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED),
                bundle,
                null
            )
            if (cursor == null) {
                MediaItemState.UNKNOWN
            } else {
                cursor.use { c ->
                    if (!c.moveToFirst()) {
                        if (permissionManager.hasLimitedMediaAccessOnly()) {
                            // Android 14+ selected-media access: empty cursor cannot distinguish
                            // absence from lack of visibility. Fail closed to UNKNOWN.
                            MediaItemState.UNKNOWN
                        } else {
                            MediaItemState.ABSENT
                        }
                    } else {
                        val trashedIdx = c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                        if (trashedIdx >= 0 && c.getInt(trashedIdx) == 1) {
                            MediaItemState.TRASHED
                        } else {
                            MediaItemState.PRESENT
                        }
                    }
                }
            }
        } else {
            val cursor = resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )
            if (cursor == null) {
                MediaItemState.UNKNOWN
            } else {
                cursor.use { c ->
                    if (!c.moveToFirst()) {
                        if (permissionManager.hasLimitedMediaAccessOnly()) {
                            MediaItemState.UNKNOWN
                        } else {
                            MediaItemState.ABSENT
                        }
                    } else {
                        MediaItemState.PRESENT
                    }
                }
            }
        }
    } catch (_: Exception) {
        MediaItemState.UNKNOWN
    }

    /**
     * Strict existence re-check used right before a purge to survive data drift
     * (file edited/deleted externally in Google Photos between staging & purge).
     */
    fun stillExists(uri: Uri): Boolean = inspectMediaState(uri) == MediaItemState.PRESENT

    private inline fun <T> safeQuery(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
        block: (Cursor) -> T,
    ): T? = try {
        resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?.use(block)
    } catch (_: SecurityException) {
        // Restricted collection (e.g. Android/media/com.whatsapp) — degrade
        // gracefully rather than crash. The deck simply won't include it.
        null
    } catch (_: Exception) {
        null
    }
}
