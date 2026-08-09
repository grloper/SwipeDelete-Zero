package com.swipedelete.zero.ui.screens.dashboard

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.scanner.AnalysisRunState
import com.swipedelete.zero.domain.scanner.AnalysisScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val decks: List<Deck> = emptyList(),
    /** De-duplicated bytes across cleanup-candidate decks — safe to show as a headline. */
    val candidateBytes: Long = 0,
    val candidateCount: Int = 0,
    val stagedCount: Int = 0,
    val stagedBytes: Long = 0,
    val totalStorageBytes: Long = 0,
    val freeStorageBytes: Long = 0,
    val hasMediaAccess: Boolean = false,
    val analysisState: AnalysisRunState = AnalysisRunState.IDLE,
    /** True once the analysis pass has produced hash/blur data to build decks from. */
    val hasAnalysis: Boolean = false,
) {
    val usedStorageBytes: Long get() = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalStorageBytes == 0L) 0f else usedStorageBytes.toFloat() / totalStorageBytes

    /**
     * Never claim more than the device physically holds. Even with per-file
     * de-duplication this is a cheap, obvious sanity rail on the one number the
     * user judges the app's honesty by.
     */
    val headlineReclaimableBytes: Long
        get() = if (usedStorageBytes > 0) candidateBytes.coerceAtMost(usedStorageBytes) else candidateBytes

    val isScanning: Boolean get() = analysisState == AnalysisRunState.RUNNING
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val stagingRepository: StagingRepository,
    private val analysisScheduler: AnalysisScheduler,
) : ViewModel() {

    private val decksState = MutableStateFlow<List<Deck>>(emptyList())
    private val candidateBytesState = MutableStateFlow(0L)
    private val candidateCountState = MutableStateFlow(0)
    private val loadingState = MutableStateFlow(true)
    private val accessState = MutableStateFlow(false)
    private val hasAnalysisState = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> =
        combine(
            combine(
                decksState,
                loadingState,
                accessState,
                candidateBytesState,
                candidateCountState,
            ) { decks, loading, access, bytes, count ->
                DashboardUiState(
                    loading = loading,
                    decks = decks,
                    candidateBytes = bytes,
                    candidateCount = count,
                    hasMediaAccess = access,
                )
            },
            stagingRepository.observeCount(),
            stagingRepository.observeStagedBytes(),
            analysisScheduler.observeManualRun(),
            hasAnalysisState,
        ) { base, count, bytes, analysis, hasAnalysis ->
            val stat = readStorageStats()
            base.copy(
                stagedCount = count,
                stagedBytes = bytes,
                totalStorageBytes = stat.first,
                freeStorageBytes = stat.second,
                analysisState = analysis,
                hasAnalysis = hasAnalysis,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(),
        )

    init {
        // A finished manual scan produces new hashes, so rebuild the decks that
        // depend on them instead of leaving the buckets looking empty.
        viewModelScope.launch {
            analysisScheduler.observeManualRun().collect { state ->
                if (state == AnalysisRunState.DONE && !loadingState.value) {
                    loadDecks(forceRefresh = true)
                }
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        accessState.value = granted
        if (granted) loadDecks()
    }

    fun loadDecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadingState.value = true
            val summary = deckRepository.getSummary(forceRefresh)
            decksState.value = summary.decks
            candidateBytesState.value = summary.candidateBytes
            candidateCountState.value = summary.candidateCount
            hasAnalysisState.value = summary.decks.any { deck ->
                deck.items.any { it.perceptualHash != null || it.sharpnessScore != null }
            }
            loadingState.value = false
        }
    }

    /** User asked for the on-device duplicate/blur pass to run right now. */
    fun scanNow() = analysisScheduler.runNow()

    private fun readStorageStats(): Pair<Long, Long> = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        total to free
    } catch (_: Exception) {
        0L to 0L
    }
}
