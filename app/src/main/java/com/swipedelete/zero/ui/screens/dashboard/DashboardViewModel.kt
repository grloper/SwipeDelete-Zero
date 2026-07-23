package com.swipedelete.zero.ui.screens.dashboard

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.model.Deck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loading: Boolean = true,
    val decks: List<Deck> = emptyList(),
    val stagedCount: Int = 0,
    val stagedBytes: Long = 0,
    val totalStorageBytes: Long = 0,
    val freeStorageBytes: Long = 0,
    val hasMediaAccess: Boolean = false,
) {
    val usedStorageBytes: Long get() = (totalStorageBytes - freeStorageBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalStorageBytes == 0L) 0f else usedStorageBytes.toFloat() / totalStorageBytes
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val stagingRepository: StagingRepository,
) : ViewModel() {

    private val decksState = MutableStateFlow<List<Deck>>(emptyList())
    private val loadingState = MutableStateFlow(true)
    private val accessState = MutableStateFlow(false)

    val uiState: StateFlow<DashboardUiState> =
        combine(
            decksState,
            loadingState,
            accessState,
            stagingRepository.observeCount(),
            stagingRepository.observeStagedBytes(),
        ) { decks, loading, access, count, bytes ->
            val stat = readStorageStats()
            DashboardUiState(
                loading = loading,
                decks = decks,
                stagedCount = count,
                stagedBytes = bytes,
                totalStorageBytes = stat.first,
                freeStorageBytes = stat.second,
                hasMediaAccess = access,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardUiState(),
        )

    fun onPermissionResult(granted: Boolean) {
        accessState.value = granted
        if (granted) loadDecks()
    }

    fun loadDecks(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadingState.value = true
            decksState.value = deckRepository.getDecks(forceRefresh)
            loadingState.value = false
        }
    }

    private fun readStorageStats(): Pair<Long, Long> = try {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        total to free
    } catch (_: Exception) {
        0L to 0L
    }
}
