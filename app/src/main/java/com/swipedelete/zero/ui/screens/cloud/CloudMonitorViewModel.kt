package com.swipedelete.zero.ui.screens.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.CloudUploadDao
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudActivity
import com.swipedelete.zero.domain.backup.CloudActivityMerge
import com.swipedelete.zero.domain.backup.CloudActivitySummary
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.backup.ReconcileResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of a manual "Check my backups" run. */
sealed interface VerifyState {
    data object Idle : VerifyState
    data object Running : VerifyState
    data class Done(val result: ReconcileResult) : VerifyState
}

/**
 * Backing state for the Cloud monitor.
 *
 * Everything here is read from this app's own records. It deliberately makes no
 * claim about Google Photos' built-in auto-backup: the `appendonly` upload scope
 * cannot read the user's library, so a photo backed up by the Photos app itself
 * is invisible to us and must never be reported as "not backed up".
 */
@HiltViewModel
class CloudMonitorViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val cloudBackup: CloudBackup,
    private val photosArchive: PhotosArchive,
    uploadDao: CloudUploadDao,
) : ViewModel() {

    val rows: StateFlow<List<CloudActivity>> =
        combine(backupRepository.observeLedger(), uploadDao.observeAll(), CloudActivityMerge::merge)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<CloudActivitySummary> =
        rows.map(CloudActivityMerge::summarize)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CloudActivitySummary())

    val backupState: StateFlow<BackupState> = cloudBackup.state

    private val _verifyState = MutableStateFlow<VerifyState>(VerifyState.Idle)
    val verifyState: StateFlow<VerifyState> = _verifyState.asStateFlow()

    /** Re-read every ledger row from its provider. */
    fun verifyAll() {
        if (_verifyState.value == VerifyState.Running) return
        _verifyState.update { VerifyState.Running }
        viewModelScope.launch {
            val result = runCatching { cloudBackup.reconcile() }.getOrElse { e ->
                ReconcileResult(message = e.message ?: "Verification failed")
            }
            _verifyState.update { VerifyState.Done(result) }
        }
    }

    fun dismissVerifyResult() = _verifyState.update { VerifyState.Idle }

    /** Re-queue a failed upload. */
    fun retry(contentUri: String) = photosArchive.retry(contentUri)

    /**
     * Drop a ledger row whose remote copy is gone. The file then reappears in
     * the pending-backup work-list instead of being counted as safe forever.
     */
    fun forget(contentUri: String) = viewModelScope.launch {
        backupRepository.forget(contentUri)
    }
}
