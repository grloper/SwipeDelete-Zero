package com.swipedelete.zero.ui.screens.dual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four single-tap outcomes on the comparison action bar. */
enum class CompareAction { KEEP_A_TRASH_B, KEEP_B_TRASH_A, KEEP_BOTH, TRASH_BOTH }

data class DualCardUiState(
    val loading: Boolean = true,
    val actionError: String? = null,
    val pairs: List<ComparisonPair> = emptyList(),
    val index: Int = 0,
) {
    val current: ComparisonPair? get() = pairs.getOrNull(index)
    val isComplete: Boolean get() = !loading && index >= pairs.size
    val total: Int get() = pairs.size
}

@HiltViewModel
class DualCardViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val stagingRepository: StagingRepository,
    private val backupRepository: BackupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val actionGate = com.swipedelete.zero.domain.feedback.ReviewActionGate()

    private val deckId: String = checkNotNull(savedStateHandle[Routes.ARG_DECK_ID])

    private val _state = MutableStateFlow(DualCardUiState())
    val state: StateFlow<DualCardUiState> = _state.asStateFlow()

    init { retryLoad() }

    fun retryLoad() {
        if (!actionGate.enter()) return
        _state.update { it.copy(loading = true, actionError = null) }
        viewModelScope.launch {
            try {
                val pairs = deckRepository.getComparisonPairs(deckId)
                _state.update { it.copy(loading = false, pairs = pairs, index = 0) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(loading = false, pairs = emptyList(),
                actionError = "Could not load comparisons. Check media access and retry.") } }
            finally { actionGate.leave() }
        }
    }

    fun act(action: CompareAction) {
        val currentIndex = _state.value.index
        val pair = _state.value.current ?: return
        if (!actionGate.enter()) return
        viewModelScope.launch {
            try {
            _state.update { it.copy(actionError = null) }
            when (action) {
                CompareAction.KEEP_A_TRASH_B -> {
                    stagingRepository.stage(pair.secondary, deckId)
                    backupRepository.recordKept(pair.primary, starred = false)
                }
                CompareAction.KEEP_B_TRASH_A -> {
                    stagingRepository.stage(pair.primary, deckId)
                    backupRepository.recordKept(pair.secondary, starred = false)
                }
                CompareAction.TRASH_BOTH -> {
                    stagingRepository.stage(pair.primary, deckId)
                    stagingRepository.stage(pair.secondary, deckId)
                }
                CompareAction.KEEP_BOTH -> {
                    backupRepository.recordKept(pair.primary, starred = false)
                    backupRepository.recordKept(pair.secondary, starred = false)
                }
            }
            _state.update { it.copy(index = currentIndex + 1) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(actionError = "Could not finish this comparison. Nothing was deleted. Retry this pair or return to your library.") } }
            finally { actionGate.leave() }
        }
    }
}
