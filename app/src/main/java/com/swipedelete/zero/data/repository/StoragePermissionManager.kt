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
 * The fdroid and play editions use scoped media access. Non-media files need
 * document access through the Storage Access Framework.
 *
 * Media (image/video/audio) is always handled through granular READ_MEDIA_*
 * permissions + MediaStore, regardless of flavor.
 */
@Singleton
class StoragePermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal var sdkInt: Int = Build.VERSION.SDK_INT
    internal var permissionChecker: (String) -> Boolean = { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    enum class UriMediaType {
        IMAGE,
        VIDEO,
        AUDIO,
        UNKNOWN
    }

    fun detectMediaType(uri: android.net.Uri): UriMediaType {
        val s = uri.toString().lowercase()
        return when {
            s.contains("/images") || s.contains("image") -> UriMediaType.IMAGE
            s.contains("/video") || s.contains("video") -> UriMediaType.VIDEO
            s.contains("/audio") || s.contains("audio") -> UriMediaType.AUDIO
            else -> UriMediaType.UNKNOWN
        }
    }

    /** READ_MEDIA_* (33+) or READ_EXTERNAL_STORAGE (≤32) needed to scan media. */
    val mediaPermissions: Array<String>
        get() = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
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
        if (sdkInt >= 34 &&
            isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        ) {
            return true
        }
        return mediaPermissions.any { isGranted(it) }
    }

    /**
     * Determines whether access is granted specifically for the given media URI.
     * M0-V2-01: An unrelated media permission (such as audio-only) must NEVER establish visibility
     * for image or video URIs.
     */
    fun hasAccessFor(uri: android.net.Uri): Boolean {
        if (hasAllFilesAccess()) return true
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            return isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val type = detectMediaType(uri)
        return when (type) {
            UriMediaType.IMAGE -> {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    (sdkInt >= 34 && isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
            }
            UriMediaType.VIDEO -> {
                isGranted(Manifest.permission.READ_MEDIA_VIDEO) ||
                    (sdkInt >= 34 && isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
            }
            UriMediaType.AUDIO -> {
                isGranted(Manifest.permission.READ_MEDIA_AUDIO)
            }
            UriMediaType.UNKNOWN -> {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                    isGranted(Manifest.permission.READ_MEDIA_VIDEO) &&
                    isGranted(Manifest.permission.READ_MEDIA_AUDIO)
            }
        }
    }

    /**
     * True on Android 14+ (API 34+) when user granted partial access (READ_MEDIA_VISUAL_USER_SELECTED)
     * without full media permissions or all-files access for this specific media URI.
     * Under this access model, an empty query cursor cannot distinguish deletion from lack of visibility.
     */
    fun hasLimitedAccessOnlyFor(uri: android.net.Uri): Boolean {
        if (sdkInt < 34) return false
        if (hasAllFilesAccess()) return false
        val hasSelected = isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        val type = detectMediaType(uri)
        return when (type) {
            UriMediaType.IMAGE -> hasSelected && !isGranted(Manifest.permission.READ_MEDIA_IMAGES)
            UriMediaType.VIDEO -> hasSelected && !isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            UriMediaType.AUDIO -> false // READ_MEDIA_VISUAL_USER_SELECTED never grants audio access
            UriMediaType.UNKNOWN -> hasSelected && !(
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) &&
                isGranted(Manifest.permission.READ_MEDIA_VIDEO)
            )
        }
    }

    /**
     * True on Android 14+ (API 34+) when user granted partial access (READ_MEDIA_VISUAL_USER_SELECTED)
     * without full visual media permissions or all-files access.
     */
    fun hasLimitedMediaAccessOnly(): Boolean {
        if (sdkInt < 34) return false
        val hasSelected = isGranted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        val hasFullVisual = (isGranted(Manifest.permission.READ_MEDIA_IMAGES) && isGranted(Manifest.permission.READ_MEDIA_VIDEO)) || hasAllFilesAccess()
        return hasSelected && !hasFullVisual
    }

    /** True only on the `play` flavor when the user granted all-files access. */
    fun hasAllFilesAccess(): Boolean {
        if (!BuildConfig.ALLOW_MANAGE_STORAGE) return false
        return sdkInt >= Build.VERSION_CODES.R &&
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

    private fun isGranted(permission: String): Boolean = permissionChecker(permission)

    enum class NonMediaStrategy {
        /** Delete via java.io.File — requires MANAGE_EXTERNAL_STORAGE (play). */
        DIRECT_FILE,

        /** Delete via a user-granted SAF tree uri (fdroid + default). */
        SAF_DOCUMENT_TREE,
    }
}
