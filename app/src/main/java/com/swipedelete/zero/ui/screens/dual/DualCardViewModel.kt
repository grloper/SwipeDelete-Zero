package com.swipedelete.zero.ui.screens.dual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[Routes.ARG_DECK_ID])

    private val _state = MutableStateFlow(DualCardUiState())
    val state: StateFlow<DualCardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val pairs = deckRepository.getComparisonPairs(deckId)
            _state.update { it.copy(loading = false, pairs = pairs) }
        }
    }

    fun act(action: CompareAction) {
        val pair = _state.value.current ?: return
        viewModelScope.launch {
            when (action) {
                CompareAction.KEEP_A_TRASH_B -> stagingRepository.stage(pair.secondary, deckId)
                CompareAction.KEEP_B_TRASH_A -> stagingRepository.stage(pair.primary, deckId)
                CompareAction.TRASH_BOTH -> {
                    stagingRepository.stage(pair.primary, deckId)
                    stagingRepository.stage(pair.secondary, deckId)
                }
                CompareAction.KEEP_BOTH -> Unit
            }
            _state.update { it.copy(index = it.index + 1) }
        }
    }
}
