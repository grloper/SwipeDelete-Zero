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
import kotlinx.coroutines.CoroutineDispatcher
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
class PurgeEngine @Inject constructor(
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
            /** Files that vanished externally prior to purge; unstage without claiming reclaimed bytes. */
            val alreadyMissingUris: List<String> = emptyList(),
            /** Files already in OS trash; handled separately without claiming reclaimed bytes. */
            val alreadyTrashedUris: List<String> = emptyList(),
            /** Files with unknown/unreadable visibility; must remain staged. */
            val blockedUris: List<String> = emptyList(),
            /** Live items exceeding MAX_PURGE_BATCH_SIZE deferred to subsequent user action. */
            val deferredUris: List<String> = emptyList(),
        ) : PurgePlan

        /** Nothing needed a dialog (e.g. only non-media, already-missing, or empty). */
        data class NoConfirmationNeeded(
            val nonMediaResult: NonMediaResult,
            val alreadyMissingUris: List<String> = emptyList(),
            val alreadyTrashedUris: List<String> = emptyList(),
            val blockedUris: List<String> = emptyList(),
            val deferredUris: List<String> = emptyList(),
        ) : PurgePlan

        data class Failed(val reason: String) : PurgePlan
    }

    data class NonMediaResult(
        val purgedUris: List<String> = emptyList(),
        /** SAF tree access required to finish these — surface a picker in UI. */
        val needsSafFor: List<String> = emptyList(),
    )

    /**
     * Typed domain safety check.
     */
    sealed interface DeletionEligibility {
        data object Permitted : DeletionEligibility
        data class Blocked(val reason: String) : DeletionEligibility
    }

    /**
     * Typed domain safety check.
     * Enforces default-deny:
     * - In Play/cloud, local deletion of originals is unconditionally locked in this test build.
     *   Returns immediately without making network calls or touching the backup provider fake.
     * - In F-Droid (offline edition), deletion is permitted.
     */
    suspend fun checkDeletionEligibility(staged: List<StagedFileEntity>): DeletionEligibility {
        if (staged.isEmpty()) return DeletionEligibility.Permitted

        // In Play/cloud, local deletion of originals is unconditionally locked in this test build.
        // Return immediately without calling verifyRemote, ensuring throwing provider fakes are never touched.
        if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE) {
            return DeletionEligibility.Blocked(M0_SAFETY_LOCK_MESSAGE)
        }

        return DeletionEligibility.Permitted
    }

    internal var uriParser: (String) -> Uri = { Uri.parse(it) }
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var sdkInt: Int = Build.VERSION.SDK_INT
    internal var requestBuilder: (List<Uri>, ExecutionMode) -> IntentSender = { uris, mode ->
        buildMediaRequest(uris, mode)
    }

    /**
     * Build a batched purge plan. Splits [staged] into media (MediaStore) and
     * non-media (SAF/direct), applies the existence recheck, and prepares one
     * grouped MediaStore request for all trashable media.
     */
    suspend fun preparePurge(
        staged: List<StagedFileEntity>,
        mode: ExecutionMode,
    ): PurgePlan = withContext(ioDispatcher) {
        if (staged.isEmpty()) return@withContext PurgePlan.NoConfirmationNeeded(NonMediaResult())

        when (val eligibility = checkDeletionEligibility(staged)) {
            is DeletionEligibility.Blocked -> return@withContext PurgePlan.Failed(eligibility.reason)
            DeletionEligibility.Permitted -> Unit
        }

        if (sdkInt == Build.VERSION_CODES.Q && mode == ExecutionMode.OS_TRASH_30_DAY) {
            return@withContext PurgePlan.Failed(
                "Android 10 cannot move this batch to Trash. Choose Permanent delete, or keep it staged."
            )
        }

        val (media, nonMedia) = staged.partition {
            runCatching { MediaType.valueOf(it.mediaType) }
                .getOrDefault(MediaType.DOCUMENT).isMediaStoreTrashable
        }

        // M0-V2-01: Explicit 4-way classification across the staged set:
        // - PRESENT: active media confirmed existing -> liveMedia
        // - ABSENT: confirmed deleted externally -> alreadyMissing (unstage without crediting bytes)
        // - TRASHED: already in OS trash -> alreadyTrashed in trash mode (unstage with 0 bytes credited),
        //            or if permanent purge, candidate for permanent delete
        // - UNKNOWN: query error / restricted / partial access -> blocked (MUST REMAIN STAGED)
        val liveMedia = mutableListOf<StagedFileEntity>()
        val alreadyMissing = mutableListOf<String>()
        val alreadyTrashed = mutableListOf<String>()
        val blocked = mutableListOf<String>()

        for (item in media) {
            val uri = uriParser(item.contentUri)
            when (mediaStore.inspectMediaState(uri)) {
                MediaStoreRepository.MediaItemState.PRESENT -> liveMedia += item
                MediaStoreRepository.MediaItemState.ABSENT -> alreadyMissing += item.contentUri
                MediaStoreRepository.MediaItemState.TRASHED -> {
                    if (mode == ExecutionMode.PERMANENT_PURGE) {
                        // Trashed media can be permanently purged via OS delete request
                        liveMedia += item
                    } else {
                        alreadyTrashed += item.contentUri
                    }
                }
                MediaStoreRepository.MediaItemState.UNKNOWN -> blocked += item.contentUri
            }
        }

        // Bound batch size for live items to avoid OS Binder transaction and provider request limits.
        val batchLiveMedia = liveMedia.take(MAX_PURGE_BATCH_SIZE)
        val deferredUris = liveMedia.drop(MAX_PURGE_BATCH_SIZE).map { it.contentUri }
        val liveMediaUris = batchLiveMedia.map { uriParser(it.contentUri) }

        val nonMediaResult = purgeNonMedia(nonMedia)

        if (liveMediaUris.isEmpty()) {
            return@withContext PurgePlan.NoConfirmationNeeded(
                nonMediaResult = nonMediaResult,
                alreadyMissingUris = alreadyMissing,
                alreadyTrashedUris = alreadyTrashed,
                blockedUris = blocked,
                deferredUris = deferredUris,
            )
        }

        if (sdkInt >= Build.VERSION_CODES.R) {
            val sender = requestBuilder(liveMediaUris, mode)
            PurgePlan.NeedsConfirmation(
                request = sender,
                mediaUris = liveMediaUris,
                nonMediaResult = nonMediaResult,
                alreadyMissingUris = alreadyMissing,
                alreadyTrashedUris = alreadyTrashed,
                blockedUris = blocked,
                deferredUris = deferredUris,
            )
        } else {
            legacyDeleteQ(liveMediaUris, nonMediaResult, alreadyMissing, alreadyTrashed, blocked, deferredUris)
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

    private fun legacyDeleteQ(
        uris: List<Uri>,
        nonMedia: NonMediaResult,
        alreadyMissingUris: List<String>,
        alreadyTrashedUris: List<String>,
        blockedUris: List<String>,
        deferredUris: List<String>,
    ): PurgePlan {
        val purged = mutableListOf<Uri>()
        for (uri in uris) {
            try {
                if (context.contentResolver.delete(uri, null, null) > 0) purged += uri
            } catch (_: Exception) {
            }
        }
        return PurgePlan.NoConfirmationNeeded(
            nonMediaResult = nonMedia.copy(purgedUris = nonMedia.purgedUris + purged.map { it.toString() }),
            alreadyMissingUris = alreadyMissingUris,
            alreadyTrashedUris = alreadyTrashedUris,
            blockedUris = blockedUris,
            deferredUris = deferredUris,
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
    ): List<String> = withContext(ioDispatcher) {
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
            "Cleanup is unavailable in this test build. Your originals stay on this device."
    }
}
