package com.swipedelete.zero.domain.backup

/**
 * Where a copy of a file actually lives.
 *
 * Drive backup and the Photos archive previously shared one ledger with a bare
 * `remoteId` and no destination, so the app could say "backed up" but not
 * *where* — and a file sitting safely in Drive was indistinguishable from one
 * in Google Photos. They are not interchangeable: only the Photos copy shows up
 * in the user's photo library.
 */
enum class CloudDestination {
    DRIVE,
    PHOTOS;

    val label: String
        get() = when (this) {
            DRIVE -> "Google Drive"
            PHOTOS -> "Google Photos"
        }

    companion object {
        fun parse(raw: String?): CloudDestination =
            entries.firstOrNull { it.name == raw } ?: DRIVE
    }
}

/**
 * What the *server* says about a copy we believe we uploaded.
 *
 * A 200 at upload time is not proof the item still exists — the user may have
 * deleted it, or a batch may have silently failed. [CONFIRMED] means we read
 * the item back from the API after the fact.
 */
enum class RemoteState {
    /** Uploaded, never re-checked against the server. */
    RECORDED,

    /** Read back from the API and present. */
    CONFIRMED,

    /** The API says it is gone. The local file is NOT safe to delete. */
    MISSING,

    /** The check itself failed (network, auth) — status unknown, assume nothing. */
    UNKNOWN;

    val label: String
        get() = when (this) {
            RECORDED -> "Uploaded"
            CONFIRMED -> "Verified"
            MISSING -> "Missing on server"
            UNKNOWN -> "Unverified"
        }

    companion object {
        fun parse(raw: String?): RemoteState =
            entries.firstOrNull { it.name == raw } ?: RECORDED
    }
}

/**
 * What this app knows about one file's cloud copy. Absence of a [CloudCopy]
 * means "this app did not upload it" — never "it is not backed up", which is
 * not a fact the upload-only Photos scope can establish.
 */
data class CloudCopy(
    val destination: CloudDestination,
    val state: RemoteState,
)

/** Outcome of reconciling the whole ledger against the providers. */
data class ReconcileResult(
    val checked: Int = 0,
    val confirmed: Int = 0,
    val missing: Int = 0,
    val unknown: Int = 0,
    val message: String,
) {
    val allConfirmed: Boolean get() = checked > 0 && confirmed == checked
}
