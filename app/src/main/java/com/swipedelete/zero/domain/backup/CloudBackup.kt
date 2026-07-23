package com.swipedelete.zero.domain.backup

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Auth + upload status of the cloud backup engine. */
sealed interface BackupState {
    /** Backup exists only in the cloud flavor; fdroid/play always report this. */
    data object Unsupported : BackupState

    data class SignedOut(val message: String? = null) : BackupState

    data class Ready(val accountEmail: String, val message: String? = null) : BackupState

    data class Running(val done: Int, val total: Int) : BackupState
}

/**
 * Flavor seam for the opt-in cloud backup of kept & starred files.
 *
 * The fdroid/play flavors bind [NoOpCloudBackup] — no network code is even
 * compiled into those builds. The cloud flavor binds a Google Drive
 * implementation. UI code talks only to this interface.
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
}

@Singleton
class NoOpCloudBackup @Inject constructor() : CloudBackup {
    override val state: StateFlow<BackupState> = MutableStateFlow(BackupState.Unsupported)
    override fun signInIntent(): Intent? = null
    override fun onSignInResult(data: Intent?) = Unit
    override fun backupNow() = Unit
    override fun signOut() = Unit
}
