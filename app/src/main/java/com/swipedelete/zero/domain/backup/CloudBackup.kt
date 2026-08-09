package com.swipedelete.zero.domain.backup

import android.content.Intent
import com.swipedelete.zero.domain.setup.AuthDiagnostic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Auth + upload status of the cloud backup engine. */
sealed interface BackupState {
    /** Backup exists only in the cloud flavor; fdroid/play always report this. */
    data object Unsupported : BackupState

    /**
     * Not connected. [diagnostic] is non-null after a *failed* attempt and
     * carries the decoded cause and the exact fix, so the UI never has to show
     * a bare status code.
     */
    data class SignedOut(
        val message: String? = null,
        val diagnostic: AuthDiagnostic? = null,
    ) : BackupState

    data class Ready(val accountEmail: String, val message: String? = null) : BackupState

    data class Running(val done: Int, val total: Int) : BackupState
}

/** Result of actively probing the connection rather than trusting cached state. */
data class ConnectionCheck(
    val signedIn: Boolean,
    val accountEmail: String? = null,
    /** Null when not probed; true/false once the API answered. */
    val driveOk: Boolean? = null,
    val photosOk: Boolean? = null,
    val diagnostic: AuthDiagnostic? = null,
    val message: String,
) {
    val allGood: Boolean get() = signedIn && driveOk == true && photosOk == true
}

/**
 * Flavor seam for the opt-in cloud features (Drive backup of kept files and the
 * swipe-up Google Photos archive).
 *
 * The fdroid/play flavors bind [NoOpCloudBackup] — no network code is even
 * compiled into those builds. The cloud flavor binds a Google implementation.
 * UI code talks only to this interface.
 */
interface CloudBackup {
    val state: StateFlow<BackupState>

    /** Intent that starts the provider's sign-in flow, or null when unsupported. */
    fun signInIntent(): Intent?

    /** Handle the activity result of [signInIntent]. */
    fun onSignInResult(data: Intent?)

    /** Upload every kept/starred file not yet in the backup ledger. */
    fun backupNow()

    fun signOut()

    /**
     * Actively call the APIs and report exactly what works. Used by the setup
     * wizard's "Verify connection" step so success is proven, not assumed.
     */
    suspend fun verifyConnection(): ConnectionCheck

    /**
     * Read every ledger row back from its provider and record whether the copy
     * is still there.
     *
     * A 200 at upload time proves the request was accepted, not that the item
     * survived — the user may have deleted it since, and a batch can fail
     * silently. Nothing should be described to the user as "safe in the cloud"
     * on the strength of a months-old write acknowledgement alone.
     */
    suspend fun reconcile(): ReconcileResult
}

@Singleton
class NoOpCloudBackup @Inject constructor() : CloudBackup {
    override val state: StateFlow<BackupState> = MutableStateFlow(BackupState.Unsupported)
    override fun signInIntent(): Intent? = null
    override fun onSignInResult(data: Intent?) = Unit
    override fun backupNow() = Unit
    override fun signOut() = Unit
    override suspend fun verifyConnection() = ConnectionCheck(
        signedIn = false,
        message = "This build has no network access by design.",
    )
    override suspend fun reconcile() = ReconcileResult(
        message = "This build has no network access by design.",
    )
}
