package com.swipedelete.zero.ui.screens.staging

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.data.repository.StatsStore
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
) {
    val count: Int get() = items.size
}

/** One-shot effects the screen must react to (launch OS dialog / SAF picker). */
sealed interface PurgeEffect {
    data class LaunchConfirmation(val sender: IntentSender) : PurgeEffect
    data class Completed(val freedBytes: Long, val purgedCount: Int) : PurgeEffect
    data class NeedsSafAccess(val uriCount: Int) : PurgeEffect
    data class Message(val text: String) : PurgeEffect
}

@HiltViewModel
class StagingViewModel @Inject constructor(
    private val stagingRepository: StagingRepository,
    private val purgeEngine: PurgeEngine,
    private val statsStore: StatsStore,
) : ViewModel() {

    private val modeState = MutableStateFlow(ExecutionMode.OS_TRASH_30_DAY)
    private val purgingState = MutableStateFlow(false)
    private val sortState = MutableStateFlow(StagingSort.NEWEST)

    private val effects = Channel<PurgeEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    /** URIs awaiting an OS-dialog result, remembered between the two calls. */
    private var pendingMediaUris: List<android.net.Uri> = emptyList()

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
        ) { state, lifetime ->
            state.copy(lifetimeReclaimedBytes = lifetime)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StagingUiState())

    fun setMode(mode: ExecutionMode) { modeState.value = mode }
    fun setSort(sort: StagingSort) { sortState.value = sort }

    fun restore(uri: String) = viewModelScope.launch { stagingRepository.restore(uri) }
    fun clearQueue() = viewModelScope.launch { stagingRepository.clearQueue() }

    /** Kick off a batched purge under the current execution mode. */
    fun purge() {
        viewModelScope.launch {
            purgingState.value = true
            val staged = stagingRepository.getAll()
            pendingSizesByUri = staged.associate { it.contentUri to it.sizeBytes }
            when (val plan = purgeEngine.preparePurge(staged, modeState.value)) {
                is PurgeEngine.PurgePlan.NeedsConfirmation -> {
                    // Non-media already handled; remove its winners now.
                    recordPurged(plan.nonMediaResult.purgedUris)
                    pendingMediaUris = plan.mediaUris
                    effects.send(PurgeEffect.LaunchConfirmation(plan.request))
                    // purging stays true until confirmation result arrives.
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    }
                }
                is PurgeEngine.PurgePlan.NoConfirmationNeeded -> {
                    val freed = recordPurged(plan.nonMediaResult.purgedUris)
                    purgingState.value = false
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    } else {
                        effects.send(
                            PurgeEffect.Completed(freed, plan.nonMediaResult.purgedUris.size)
                        )
                    }
                }
                is PurgeEngine.PurgePlan.Failed -> {
                    purgingState.value = false
                    effects.send(PurgeEffect.Message(plan.reason))
                }
            }
        }
    }

    /** Called by the screen after the OS confirmation dialog returns OK. */
    fun onConfirmationResult(confirmed: Boolean) {
        viewModelScope.launch {
            if (confirmed && pendingMediaUris.isNotEmpty()) {
                val purged = purgeEngine.confirmMediaPurged(pendingMediaUris, modeState.value)
                val freed = recordPurged(purged)
                effects.send(PurgeEffect.Completed(freed, purged.size))
            }
            pendingMediaUris = emptyList()
            purgingState.value = false
        }
    }

    /**
     * Unstage the verified winners, add their (and only their) bytes to the
     * lifetime counter, and return how much was actually freed.
     */
    private suspend fun recordPurged(uris: List<String>): Long {
        if (uris.isEmpty()) return 0L
        stagingRepository.removePurged(uris)
        val freed = uris.sumOf { pendingSizesByUri[it] ?: 0L }
        statsStore.addReclaimed(freed)
        return freed
    }
}
