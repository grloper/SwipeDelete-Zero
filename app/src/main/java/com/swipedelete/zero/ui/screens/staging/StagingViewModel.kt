package com.swipedelete.zero.ui.screens.staging

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.data.repository.StatsStore
import com.swipedelete.zero.domain.backup.ArchiveItemState
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudBackup
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the staged queue is ordered in the drawer. */
enum class StagingSort {
    /** Most recently swiped first. */
    NEWEST,

    /** Biggest reclaimable files first. */
    LARGEST,
}

data class StagingUiState(
    val items: List<StagedFileEntity> = emptyList(),
    val totalBytes: Long = 0,
    val mode: ExecutionMode = ExecutionMode.OS_TRASH_30_DAY,
    val purging: Boolean = false,
    val sort: StagingSort = StagingSort.NEWEST,
    /** Verified bytes reclaimed across the app's lifetime ("14.2 GB Reclaimed"). */
    val lifetimeReclaimedBytes: Long = 0,
    val backupRequired: Boolean = false,
    val backupConnected: Boolean = false,
    val verifiedCount: Int = 0,
    val pendingBackupCount: Int = 0,
    val failedBackupCount: Int = 0,
    val cleanupAvailable: Boolean = !com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE,
    val cleanupLockExplanation: String? = if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)
        "Cleanup is unavailable in this test build. Your originals stay on this device." else null,
) {
    val count: Int get() = items.size
    val canDelete: Boolean get() = cleanupAvailable && (!backupRequired || (backupConnected && pendingBackupCount == 0))
}

/** One-shot effects the screen must react to (launch OS dialog / SAF picker). */
sealed interface PurgeEffect {
    data class LaunchConfirmation(val sender: IntentSender) : PurgeEffect
    data class Completed(val freedBytes: Long, val purgedCount: Int, val mode: ExecutionMode) : PurgeEffect
    data class NeedsSafAccess(val uriCount: Int) : PurgeEffect
    data class Message(val text: String) : PurgeEffect
}

@HiltViewModel
class StagingViewModel @Inject constructor(
    private val stagingRepository: StagingRepository,
    private val purgeEngine: PurgeEngine,
    private val statsStore: StatsStore,
    private val photosArchive: PhotosArchive,
    private val cloudBackup: CloudBackup,
) : ViewModel() {

    private val modeState = MutableStateFlow(ExecutionMode.OS_TRASH_30_DAY)
    private val purgingState = MutableStateFlow(false)
    private val sortState = MutableStateFlow(StagingSort.NEWEST)

    private val effects = Channel<PurgeEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    /** URIs awaiting an OS-dialog result, remembered between the two calls. */
    private var pendingMediaUris: List<android.net.Uri> = emptyList()
    private var pendingMode: ExecutionMode = ExecutionMode.OS_TRASH_30_DAY

    /** Sizes of everything in the current purge, so only *verified* bytes count. */
    private var pendingSizesByUri: Map<String, Long> = emptyMap()

    val uiState: StateFlow<StagingUiState> =
        combine(
            combine(
                stagingRepository.observeStaged(),
                stagingRepository.observeStagedBytes(),
                modeState,
                purgingState,
                sortState,
            ) { items, bytes, mode, purging, sort ->
                val sorted = when (sort) {
                    StagingSort.NEWEST -> items.sortedByDescending { it.stagedAtMillis }
                    StagingSort.LARGEST -> items.sortedByDescending { it.sizeBytes }
                }
                StagingUiState(items = sorted, totalBytes = bytes, mode = mode, purging = purging, sort = sort)
            },
            statsStore.lifetimeReclaimedBytes,
            photosArchive.queue,
            cloudBackup.state,
        ) { state, lifetime, uploads, backup ->
            val verified = state.items.count { uploads[it.contentUri] is ArchiveItemState.Verified }
            state.copy(
                lifetimeReclaimedBytes = lifetime,
                backupRequired = photosArchive.isAvailable,
                backupConnected = backup is BackupState.Ready || backup is BackupState.Running,
                verifiedCount = verified,
                pendingBackupCount = state.count - verified,
                failedBackupCount = state.items.count { uploads[it.contentUri] is ArchiveItemState.Failed },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StagingUiState())

    fun setMode(mode: ExecutionMode) { modeState.value = mode }
    fun setSort(sort: StagingSort) { sortState.value = sort }

    fun restore(uri: String) = viewModelScope.launch { stagingRepository.restore(uri) }
    fun clearQueue() = viewModelScope.launch { stagingRepository.clearQueue() }

    fun backUpStaged() = viewModelScope.launch {
        if (!photosArchive.isAvailable || cloudBackup.state.value is BackupState.SignedOut ||
            cloudBackup.state.value is BackupState.Unsupported) {
            effects.send(PurgeEffect.Message("Connect your Google account in Settings first."))
            return@launch
        }
        val items = stagingRepository.getAll()
        val unsupported = items.count {
            !it.mimeType.startsWith("image/") && !it.mimeType.startsWith("video/")
        }
        items.forEach { photosArchive.enqueueStaged(it) }
        if (unsupported > 0) effects.send(PurgeEffect.Message(
            "$unsupported file(s) cannot be backed up to Google Photos; remove them from staging."
        ))
    }

    /** Kick off a batched purge under the current execution mode. */
    fun purge() {
        viewModelScope.launch {
            purgingState.value = true
            val staged = stagingRepository.getAll()
            pendingSizesByUri = staged.associate { it.contentUri to it.sizeBytes }
            val selectedMode = modeState.value
            try {
            when (val plan = purgeEngine.preparePurge(staged, selectedMode)) {
                is PurgeEngine.PurgePlan.NeedsConfirmation -> {
                    // M0-R7: Immediately unstage externally missing files without claiming reclaimed bytes.
                    if (plan.alreadyMissingUris.isNotEmpty()) {
                        stagingRepository.removePurged(plan.alreadyMissingUris)
                    }
                    // Non-media already handled; remove its winners now.
                    recordPurged(plan.nonMediaResult.purgedUris, permanentlyDeleted = true)
                    pendingMediaUris = plan.mediaUris
                    pendingMode = selectedMode
                    effects.send(PurgeEffect.LaunchConfirmation(plan.request))
                    // purging stays true until confirmation result arrives.
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    }
                }
                is PurgeEngine.PurgePlan.NoConfirmationNeeded -> {
                    // M0-R7: Immediately unstage externally missing files without claiming reclaimed bytes.
                    if (plan.alreadyMissingUris.isNotEmpty()) {
                        stagingRepository.removePurged(plan.alreadyMissingUris)
                    }
                    val freed = recordPurged(plan.nonMediaResult.purgedUris, permanentlyDeleted = true)
                    purgingState.value = false
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    } else if (plan.nonMediaResult.purgedUris.isNotEmpty()) {
                        effects.send(
                            PurgeEffect.Completed(freed, plan.nonMediaResult.purgedUris.size, ExecutionMode.PERMANENT_PURGE)
                        )
                    }
                }
                is PurgeEngine.PurgePlan.Failed -> {
                    purgingState.value = false
                    effects.send(PurgeEffect.Message(plan.reason))
                }
            }
            } catch (_: Exception) {
                purgingState.value = false
                effects.send(PurgeEffect.Message("Could not finish this batch. Files still in the queue can be retried."))
            }
        }
    }

    /** Called by the screen after the OS confirmation dialog returns OK or was dismissed/cancelled. */
    fun onConfirmationResult(confirmed: Boolean) {
        viewModelScope.launch {
            if (confirmed && pendingMediaUris.isNotEmpty()) {
                val purged = purgeEngine.confirmMediaPurged(pendingMediaUris, pendingMode)
                val freed = recordPurged(purged, permanentlyDeleted = pendingMode == ExecutionMode.PERMANENT_PURGE)
                effects.send(PurgeEffect.Completed(freed, purged.size, pendingMode))
            }
            // M0-R7: Cancellation resets pending URIs and flags, launching no follow-on batches.
            pendingMediaUris = emptyList()
            purgingState.value = false
        }
    }

    /**
     * Unstage the verified winners, add their (and only their) bytes to the
     * lifetime counter, and return how much was actually freed.
     */
    private suspend fun recordPurged(uris: List<String>, permanentlyDeleted: Boolean): Long {
        if (uris.isEmpty()) return 0L
        stagingRepository.removePurged(uris)
        val freed = uris.sumOf { pendingSizesByUri[it] ?: 0L }
        // Android's trash retains the bytes until the OS removes the items.
        // Only permanent deletion represents storage actually reclaimed now.
        if (permanentlyDeleted) statsStore.addReclaimed(freed)
        return freed
    }
}
