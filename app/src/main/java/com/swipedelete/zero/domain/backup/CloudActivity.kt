package com.swipedelete.zero.domain.backup

import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadEntity

/**
 * What the app is allowed to say about one file's cloud copy.
 *
 * The wording matters more than the colour here. "Uploaded" and "Verified" are
 * different claims: the first means a write was acknowledged, the second means
 * the item was read back afterwards. The old UI collapsed both into "Cloud
 * Backed Up", which is how a file could look safe while its copy no longer
 * existed.
 */
enum class CloudActivityStatus(val label: String, val inFlight: Boolean = false) {
    Queued("Waiting to upload", inFlight = true),
    Uploading("Uploading", inFlight = true),
    Verifying("Finishing upload", inFlight = true),

    /** Write acknowledged, never re-checked against the provider. */
    Uploaded("Uploaded · not re-checked"),

    /** Read back from the provider and present. */
    Verified("Verified on server"),

    /** The provider says it is gone — the local copy is the only copy. */
    Missing("Gone from server"),

    /** The check itself failed, so status is genuinely unknown. */
    Unverified("Could not verify"),

    Failed("Upload failed"),
    ;

    val isProblem: Boolean get() = this == Missing || this == Failed
}

/** One line in the Cloud monitor. */
data class CloudActivity(
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val destination: CloudDestination,
    val status: CloudActivityStatus,
    /** Drive file id or Photos mediaItemId — the receipt the user can check. */
    val remoteId: String? = null,
    /** Upload time for finished rows, enqueue time for in-flight ones. */
    val atMillis: Long = 0,
    /** 0..1 while bytes are moving, null otherwise. */
    val progress: Float? = null,
    val error: String? = null,
    /** True when a manual retry is meaningful. */
    val retryable: Boolean = false,
)

/** Aggregate counters for the monitor header. */
data class CloudActivitySummary(
    val inFlight: Int = 0,
    val verified: Int = 0,
    val uploaded: Int = 0,
    val problems: Int = 0,
    val uploadedBytes: Long = 0,
    val lastVerifiedAtMillis: Long = 0,
) {
    val total: Int get() = inFlight + verified + uploaded + problems
}

/**
 * Merges the durable backup ledger with the live upload queue into the single
 * list the monitor shows.
 *
 * Both sources can hold the same uri — the worker writes a ledger row the
 * moment an upload verifies, while the `cloud_uploads` row lingers. The ledger
 * wins in that case: it is the record that survives, and it carries the
 * verification state that the queue row has no concept of.
 */
object CloudActivityMerge {

    fun merge(
        ledger: List<BackedUpFileEntity>,
        uploads: List<CloudUploadEntity>,
    ): List<CloudActivity> {
        val ledgerRows = ledger.map { it.toActivity() }
        val ledgerUris = ledgerRows.mapTo(HashSet()) { it.contentUri }
        val queueRows = uploads
            .filterNot { it.contentUri in ledgerUris }
            .map { it.toActivity() }

        // In-flight work first — it is the only part the user can still act on —
        // then everything else newest-first.
        return (queueRows + ledgerRows).sortedWith(
            compareByDescending<CloudActivity> { it.status.inFlight }
                .thenByDescending { it.status.isProblem }
                .thenByDescending { it.atMillis }
        )
    }

    fun summarize(rows: List<CloudActivity>): CloudActivitySummary = CloudActivitySummary(
        inFlight = rows.count { it.status.inFlight },
        verified = rows.count { it.status == CloudActivityStatus.Verified },
        uploaded = rows.count {
            it.status == CloudActivityStatus.Uploaded || it.status == CloudActivityStatus.Unverified
        },
        problems = rows.count { it.status.isProblem },
        uploadedBytes = rows.filterNot { it.status.inFlight || it.status.isProblem }
            .sumOf { it.sizeBytes },
        lastVerifiedAtMillis = rows.filter { it.status == CloudActivityStatus.Verified }
            .maxOfOrNull { it.atMillis } ?: 0,
    )

    private fun BackedUpFileEntity.toActivity(): CloudActivity {
        val remote = RemoteState.entries.firstOrNull { it.name == remoteState } ?: RemoteState.RECORDED
        return CloudActivity(
            contentUri = contentUri,
            displayName = displayName.ifBlank { contentUri.substringAfterLast('/') },
            sizeBytes = sizeBytes,
            destination = CloudDestination.parse(destination),
            status = when (remote) {
                RemoteState.RECORDED -> CloudActivityStatus.Uploaded
                RemoteState.CONFIRMED -> CloudActivityStatus.Verified
                RemoteState.MISSING -> CloudActivityStatus.Missing
                RemoteState.UNKNOWN -> CloudActivityStatus.Unverified
            },
            remoteId = remoteId.ifBlank { null },
            // A verified row is dated by its check, not by an upload that may be
            // months old — "verified today" is the fact the user cares about.
            atMillis = if (verifiedAtMillis > 0) verifiedAtMillis else uploadedAtMillis,
            error = lastError,
            // A missing copy is worth re-uploading; the row has to be forgotten
            // first, which is what the monitor's action does.
            retryable = remote == RemoteState.MISSING,
        )
    }

    private fun CloudUploadEntity.toActivity(): CloudActivity = CloudActivity(
        contentUri = contentUri,
        displayName = displayName,
        sizeBytes = sizeBytes,
        // Only the Photos archive uses this queue; Drive backup is synchronous.
        destination = CloudDestination.PHOTOS,
        status = when (state) {
            CloudUploadEntity.STATE_QUEUED -> CloudActivityStatus.Queued
            CloudUploadEntity.STATE_UPLOADING -> CloudActivityStatus.Uploading
            CloudUploadEntity.STATE_VERIFYING -> CloudActivityStatus.Verifying
            CloudUploadEntity.STATE_VERIFIED -> CloudActivityStatus.Verified
            else -> CloudActivityStatus.Failed
        },
        remoteId = mediaItemId?.ifBlank { null },
        atMillis = updatedAtMillis.takeIf { it > 0 } ?: enqueuedAtMillis,
        progress = if (state == CloudUploadEntity.STATE_UPLOADING && sizeBytes > 0) {
            (bytesUploaded.toFloat() / sizeBytes).coerceIn(0f, 1f)
        } else {
            null
        },
        error = lastError,
        retryable = state == CloudUploadEntity.STATE_FAILED,
    )
}
