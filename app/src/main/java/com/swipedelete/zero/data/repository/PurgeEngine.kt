package com.swipedelete.zero.data.repository

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.swipedelete.zero.data.local.StagedFileEntity
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
class PurgeEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStore: MediaStoreRepository,
    private val safBridge: SafStorageBridge,
    private val permissions: StoragePermissionManager,
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
    suspend fun preparePurge(
        staged: List<StagedFileEntity>,
        mode: ExecutionMode,
    ): PurgePlan = withContext(Dispatchers.IO) {
        if (staged.isEmpty()) return@withContext PurgePlan.NoConfirmationNeeded(NonMediaResult())

        val (media, nonMedia) = staged.partition {
            runCatching { MediaType.valueOf(it.mediaType) }
                .getOrDefault(MediaType.DOCUMENT).isMediaStoreTrashable
        }

        // Data-drift guard: drop rows whose files vanished/changed externally.
        val liveMediaUris = media
            .map { Uri.parse(it.contentUri) }
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
                context.contentResolver.delete(uri, null, null)
                purged += uri
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
     */
    suspend fun confirmMediaPurged(
        uris: List<Uri>,
        mode: ExecutionMode,
    ): List<String> = withContext(Dispatchers.IO) {
        uris.filter { uri ->
            when (mode) {
                // Permanent: success == no longer present.
                ExecutionMode.PERMANENT_PURGE -> !mediaStore.stillExists(uri)
                // Trash: the row still exists (IS_TRASHED=1) but the user
                // confirmed; treat confirmation as success.
                ExecutionMode.OS_TRASH_30_DAY -> true
            }
        }.map { it.toString() }
    }
}
