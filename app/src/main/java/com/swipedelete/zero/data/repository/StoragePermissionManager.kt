package com.swipedelete.zero.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.swipedelete.zero.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single abstraction over Android's fragmented storage-permission landscape.
 *
 * The app ships in two flavors:
 *  - **fdroid** — never uses MANAGE_EXTERNAL_STORAGE (policy-forbidden on
 *    F-Droid). Non-media purge falls back to the Storage Access Framework.
 *  - **play** — may request MANAGE_EXTERNAL_STORAGE for one-tap non-media purge.
 *
 * Media (image/video/audio) is always handled through granular READ_MEDIA_*
 * permissions + MediaStore, regardless of flavor.
 */
@Singleton
class StoragePermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** READ_MEDIA_* (33+) or READ_EXTERNAL_STORAGE (≤32) needed to scan media. */
    val mediaPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasMediaAccess(): Boolean {
        // On 34+ "partial access" (READ_MEDIA_VISUAL_USER_SELECTED) counts as
        // usable access for our purposes — MediaStore returns the selected subset.
        if (Build.VERSION.SDK_INT >= 34 &&
            isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        ) {
            return true
        }
        return mediaPermissions.any { isGranted(it) }
    }

    /** True only on the `play` flavor when the user granted all-files access. */
    fun hasAllFilesAccess(): Boolean {
        if (!BuildConfig.ALLOW_MANAGE_STORAGE) return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
    }

    /**
     * How non-media clutter (.apk/.zip/downloads) must be purged on this build.
     * The Disk Execution Engine branches on this.
     */
    fun nonMediaStrategy(): NonMediaStrategy = when {
        hasAllFilesAccess() -> NonMediaStrategy.DIRECT_FILE
        else -> NonMediaStrategy.SAF_DOCUMENT_TREE
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    enum class NonMediaStrategy {
        /** Delete via java.io.File — requires MANAGE_EXTERNAL_STORAGE (play). */
        DIRECT_FILE,

        /** Delete via a user-granted SAF tree uri (fdroid + default). */
        SAF_DOCUMENT_TREE,
    }
}
