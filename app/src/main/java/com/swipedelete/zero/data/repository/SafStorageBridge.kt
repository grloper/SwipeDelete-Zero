package com.swipedelete.zero.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.swipedelete.zero.data.local.StagedFileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the Storage Access Framework and (on `play`) direct file access for
 * NON-media clutter (.apk / .zip / raw downloads) that MediaStore's trash API
 * cannot touch.
 *
 * Persisted SAF tree permissions are re-taken on each grant so document deletion
 * survives reboots. All operations degrade to `false` (never crash) on failure,
 * so a revoked or empty tree simply leaves the file in the staging queue.
 */
@Singleton
class SafStorageBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Persist read/write access to a user-picked tree so we can delete later. */
    fun persistTreePermission(treeUri: Uri) {
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Grant already lost — nothing to persist.
        }
    }

    /** Direct java.io delete — only valid with MANAGE_EXTERNAL_STORAGE (play). */
    fun deleteDirectFile(entity: StagedFileEntity): Boolean = try {
        val path = entity.relativePath ?: return false
        val file = File(path)
        !file.exists() || file.delete()
    } catch (_: Exception) {
        false
    }

    /** Delete a document via a previously-granted SAF tree. */
    fun deleteViaSaf(documentUri: Uri): Boolean = try {
        val doc = DocumentFile.fromSingleUri(context, documentUri)
        doc != null && (!doc.exists() || doc.delete())
    } catch (_: Exception) {
        false
    }

    /** Whether we already hold a persisted grant that covers [uri]. */
    fun hasPersistedAccess(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.isWritePermission && uri.toString().startsWith(it.uri.toString())
        }
}
