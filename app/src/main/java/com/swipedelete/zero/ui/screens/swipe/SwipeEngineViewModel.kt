package com.swipedelete.zero.ui.screens.swipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.ExclusionRepository
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.SwipeAction
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SwipeUiState(
    val loading: Boolean = true,
    val deck: Deck? = null,
    val cursor: Int = 0,
    /** The most recent swipe, kept alive for the 5-second Undo window. */
    val lastAction: SwipeAction? = null,
) {
    val isComplete: Boolean get() = deck != null && cursor >= deck.totalCount
    val remaining: Int get() = deck?.let { it.totalCount - cursor } ?: 0
}

@HiltViewModel
class SwipeEngineViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val stagingRepository: StagingRepository,
    private val exclusionRepository: ExclusionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[Routes.ARG_DECK_ID])

    private val _state = MutableStateFlow(SwipeUiState())
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val deck = deckRepository.getDeck(deckId)
            _state.update {
                it.copy(loading = false, deck = deck, cursor = deck?.completedCount ?: 0)
            }
        }
    }

    fun onSwipe(direction: SwipeDirection) {
        val current = _state.value
        val deck = current.deck ?: return
        val index = current.cursor
        if (index >= deck.totalCount) return
        val item = deck.items[index]

        viewModelScope.launch {
            when (direction) {
                SwipeDirection.LEFT -> stagingRepository.stage(item, deck.id)
                SwipeDirection.UP -> exclusionRepository.starItem(item)
                SwipeDirection.RIGHT, SwipeDirection.NONE -> Unit // keep
            }
            val nextCursor = index + 1
            _state.update {
                it.copy(
                    cursor = nextCursor,
                    lastAction = SwipeAction(item, direction, deck.id, index),
                )
            }
            deckRepository.saveProgress(deck, nextCursor)
        }
    }

    /** Reverse the last swipe within the 5-second window. */
    fun undo() {
        val last = _state.value.lastAction ?: return
        val deck = _state.value.deck ?: return
        viewModelScope.launch {
            when (last.direction) {
                SwipeDirection.LEFT -> stagingRepository.restore(last.item.contentUri.toString())
                SwipeDirection.UP,
                SwipeDirection.RIGHT,
                SwipeDirection.NONE -> Unit
            }
            _state.update { it.copy(cursor = last.deckIndex, lastAction = null) }
            deckRepository.saveProgress(deck, last.deckIndex)
        }
    }

    fun dismissUndo() = _state.update { it.copy(lastAction = null) }
}
