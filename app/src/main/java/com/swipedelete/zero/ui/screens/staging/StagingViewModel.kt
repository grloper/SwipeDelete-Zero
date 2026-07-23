package com.swipedelete.zero.ui.screens.staging

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.data.repository.PurgeEngine
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.model.ExecutionMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val modeState = MutableStateFlow(ExecutionMode.OS_TRASH_30_DAY)
    private val purgingState = MutableStateFlow(false)
    private val sortState = MutableStateFlow(StagingSort.NEWEST)

    private val effects = Channel<PurgeEffect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    /** URIs awaiting an OS-dialog result, remembered between the two calls. */
    private var pendingMediaUris: List<android.net.Uri> = emptyList()
    private var pendingBytes: Long = 0

    val uiState: StateFlow<StagingUiState> =
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
            pendingBytes = staged.sumOf { it.sizeBytes }
            when (val plan = purgeEngine.preparePurge(staged, modeState.value)) {
                is PurgeEngine.PurgePlan.NeedsConfirmation -> {
                    // Non-media already handled; remove its winners now.
                    stagingRepository.removePurged(plan.nonMediaResult.purgedUris)
                    pendingMediaUris = plan.mediaUris
                    effects.send(PurgeEffect.LaunchConfirmation(plan.request))
                    // purging stays true until confirmation result arrives.
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    }
                }
                is PurgeEngine.PurgePlan.NoConfirmationNeeded -> {
                    stagingRepository.removePurged(plan.nonMediaResult.purgedUris)
                    purgingState.value = false
                    if (plan.nonMediaResult.needsSafFor.isNotEmpty()) {
                        effects.send(PurgeEffect.NeedsSafAccess(plan.nonMediaResult.needsSafFor.size))
                    } else {
                        effects.send(
                            PurgeEffect.Completed(pendingBytes, plan.nonMediaResult.purgedUris.size)
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
                stagingRepository.removePurged(purged)
                effects.send(PurgeEffect.Completed(pendingBytes, purged.size))
            }
            pendingMediaUris = emptyList()
            purgingState.value = false
        }
    }
}
