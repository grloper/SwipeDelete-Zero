package com.swipedelete.zero.data.repository

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 3 of the safety pipeline — the **Disk Execution Engine**.
 *
 * Responsibilities:
 *  - Turn a batch of staged files into a SINGLE OS confirmation request, so the
 *    user sees one dialog instead of N (prompt-batching requirement).
 *  - Support both execution modes: [ExecutionMode.OS_TRASH_30_DAY]
 *    (`createTrashRequest`, recoverable 30 days) and
 *    [ExecutionMode.PERMANENT_PURGE] (`createDeleteRequest`).
 *  - Re-check existence just before acting (data-drift guard).
 *
 * The engine never launches the UI itself: on API 30+ it returns an
 * [IntentSender] the Activity launches via `ActivityResultContracts
 * .StartIntentSenderForResult`. This keeps the repository free of Activity refs.
 */
@Singleton
open class PurgeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStoreRepository,
    private val safBridge: SafStorageBridge,
    private val permissions: StoragePermissionManager,
    private val photosArchive: PhotosArchive,
) {

    /** Outcome of preparing a purge batch. */
    sealed interface PurgePlan {
        /**
         * Media items requiring an OS confirmation dialog. Launch [request];
         * on RESULT_OK call [PurgeEngine.confirmMediaPurged] with [mediaUris].
         */
        data class NeedsConfirmation(
            val request: IntentSender,
            val mediaUris: List<Uri>,
            /** Non-media handled out-of-band (already purged or needs SAF). */
            val nonMediaResult: NonMediaResult,
        ) : PurgePlan

        /** Nothing needed a dialog (e.g. only non-media, or empty). */
        data class NoConfirmationNeeded(val nonMediaResult: NonMediaResult) : PurgePlan

        data class Failed(val reason: String) : PurgePlan
    }

    data class NonMediaResult(
        val purgedUris: List<String> = emptyList(),
        /** SAF tree access required to finish these — surface a picker in UI. */
        val needsSafFor: List<String> = emptyList(),
    )

    /**
     * Build a batched purge plan. Splits [staged] into media (MediaStore) and
     * non-media (SAF/direct), applies the existence recheck, and prepares one
     * grouped MediaStore request for all trashable media.
     */
    sealed interface DeletionEligibility {
        data object Permitted : DeletionEligibility
        data class Blocked(val reason: String) : DeletionEligibility
    }

    /**
     * Typed domain safety check.
     * Enforces default-deny:
     * - In Play/cloud, unavailable provider, unverified files, or metadata-only evidence blocks deletion.
     * - In M0, the safety lock centrally prevents destructive operations because original-byte restore proof does not exist yet.
     */
    open suspend fun checkDeletionEligibility(staged: List<StagedFileEntity>): DeletionEligibility {
        if (staged.isEmpty()) return DeletionEligibility.Permitted

        // In Play/cloud, provider availability is mandatory.
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE && !photosArchive.isAvailable) {
            return DeletionEligibility.Blocked(
                "Google Photos backup provider is unavailable. Local deletion requires a connected and verified backup provider."
            )
        }

        if (photosArchive.isAvailable) {
            // Check every file before executing any deletion. A partial batch
            // must never silently delete the backed-up subset while leaving
            // unprotected items in the queue.
            val unverified = staged.firstOrNull { !photosArchive.verifyRemote(it) }
            if (unverified != null) {
                return DeletionEligibility.Blocked(
                    "${unverified.displayName} is not confirmed in Google Photos. " +
                        "Back up the staged files and wait for verification before deleting."
                )
            }

            // Central M0 Non-Destructive Safety Lock:
            // Google Photos evidence is metadata-only and does not verify original-byte restoration.
            // In M0, deleting local originals is centrally locked in the domain layer.
            return DeletionEligibility.Blocked(M0_SAFETY_LOCK_MESSAGE)
        }

        return DeletionEligibility.Permitted
    }

    internal var uriParser: (String) -> Uri = { Uri.parse(it) }

    /**
     * Build a batched purge plan. Splits [staged] into media (MediaStore) and
     * non-media (SAF/direct), applies the existence recheck, and prepares one
     * grouped MediaStore request for all trashable media.
     */
    suspend fun preparePurge(
        staged: List<StagedFileEntity>,
        mode: ExecutionMode,
    ): PurgePlan = withContext(Dispatchers.IO) {
        if (staged.isEmpty()) return@withContext PurgePlan.NoConfirmationNeeded(NonMediaResult())

        when (val eligibility = checkDeletionEligibility(staged)) {
            is DeletionEligibility.Blocked -> return@withContext PurgePlan.Failed(eligibility.reason)
            DeletionEligibility.Permitted -> Unit
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && mode == ExecutionMode.OS_TRASH_30_DAY) {
            return@withContext PurgePlan.Failed(
                "Android 10 cannot move this batch to Trash. Choose Permanent delete, or keep it staged."
            )
        }

        // Bound batch size to avoid OS Binder transaction and provider request limits.
        val batch = staged.take(MAX_PURGE_BATCH_SIZE)

        val (media, nonMedia) = batch.partition {
            runCatching { MediaType.valueOf(it.mediaType) }
                .getOrDefault(MediaType.DOCUMENT).isMediaStoreTrashable
        }

        // Data-drift guard: drop rows whose files vanished/changed externally.
        val liveMediaUris = media
            .map { uriParser(it.contentUri) }
            .filter { mediaStore.stillExists(it) }

        val nonMediaResult = purgeNonMedia(nonMedia)

        if (liveMediaUris.isEmpty()) {
            return@withContext PurgePlan.NoConfirmationNeeded(nonMediaResult)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = buildMediaRequest(liveMediaUris, mode)
            PurgePlan.NeedsConfirmation(sender, liveMediaUris, nonMediaResult)
        } else {
            // API 29: no batch request API. Attempt direct delete; a
            // RecoverableSecurityException surfaces a per-item consent intent.
            legacyDeleteQ(liveMediaUris, nonMediaResult)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildMediaRequest(uris: List<Uri>, mode: ExecutionMode): IntentSender =
        when (mode) {
            ExecutionMode.OS_TRASH_30_DAY ->
                MediaStore.createTrashRequest(context.contentResolver, uris, true)
                    .intentSender
            ExecutionMode.PERMANENT_PURGE ->
                MediaStore.createDeleteRequest(context.contentResolver, uris)
                    .intentSender
        }

    private fun legacyDeleteQ(uris: List<Uri>, nonMedia: NonMediaResult): PurgePlan {
        // On Q we can only try; recoverable exceptions must be caught per-uri by
        // the caller. Here we best-effort delete and report what succeeded.
        val purged = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) purged += uri
            } catch (_: Exception) {
                // Left in queue; user can retry. Avoid crashing the batch.
            }
        }
        // No IntentSender path on Q here; treat as immediate.
        return PurgePlan.NoConfirmationNeeded(
            nonMedia.copy(purgedUris = nonMedia.purgedUris + purged.map { it.toString() }),
        )
    }

    private fun purgeNonMedia(nonMedia: List<StagedFileEntity>): NonMediaResult {
        if (nonMedia.isEmpty()) return NonMediaResult()
        return when (permissions.nonMediaStrategy()) {
            StoragePermissionManager.NonMediaStrategy.DIRECT_FILE -> {
                val purged = nonMedia.filter { safBridge.deleteDirectFile(it) }
                NonMediaResult(purgedUris = purged.map { it.contentUri })
            }
            StoragePermissionManager.NonMediaStrategy.SAF_DOCUMENT_TREE -> {
                val purged = mutableListOf<String>()
                val needsSaf = mutableListOf<String>()
                for (f in nonMedia) {
                    if (safBridge.deleteViaSaf(Uri.parse(f.contentUri))) {
                        purged += f.contentUri
                    } else {
                        needsSaf += f.contentUri
                    }
                }
                NonMediaResult(purgedUris = purged, needsSafFor = needsSaf)
            }
        }
    }

    /**
     * After the OS dialog returns RESULT_OK, verify each media uri is really
     * gone (permanent) or trashed, and report which succeeded. Only the winners
     * are removed from the staging queue by the caller — partial-success safe.
     *
     * Safety rules:
     * - Unreadable != deleted: query errors and permission losses result in UNKNOWN, never success.
     * - Mode OS_TRASH_30_DAY verifies MediaItemState.TRASHED (IS_TRASHED == 1 on API 30+).
     * - Mode PERMANENT_PURGE verifies MediaItemState.ABSENT.
     */
    suspend fun confirmMediaPurged(
        uris: List<Uri>,
        mode: ExecutionMode,
    ): List<String> = withContext(Dispatchers.IO) {
        uris.filter { uri ->
            val state = mediaStore.inspectMediaState(uri)
            when (mode) {
                ExecutionMode.PERMANENT_PURGE -> state == MediaStoreRepository.MediaItemState.ABSENT
                ExecutionMode.OS_TRASH_30_DAY -> state == MediaStoreRepository.MediaItemState.TRASHED
            }
        }.map { it.toString() }
    }

    companion object {
        const val MAX_PURGE_BATCH_SIZE = 100
        const val M0_SAFETY_LOCK_MESSAGE =
            "Deletion locked (M0 safety containment): Google Photos evidence is metadata-only " +
                "and does not prove original-byte restoration. Removing local originals requires " +
                "verified original-file restore proof (M1). Local review, staging, and undo remain active."
    }
}
