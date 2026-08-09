package com.swipedelete.zero.ui.screens.swipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.ExclusionRepository
import com.swipedelete.zero.data.repository.MediaPreloader
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.SwipeAction
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.domain.scanner.VideoMeta
import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor
import com.swipedelete.zero.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val topItem: MediaItem? get() = deck?.items?.getOrNull(cursor)
    val nextItem: MediaItem? get() = deck?.items?.getOrNull(cursor + 1)
}

@HiltViewModel
class SwipeEngineViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val stagingRepository: StagingRepository,
    private val exclusionRepository: ExclusionRepository,
    private val backupRepository: BackupRepository,
    private val photosArchive: PhotosArchive,
    private val videoMetadataExtractor: VideoMetadataExtractor,
    private val analysisDao: MediaAnalysisDao,
    private val mediaPreloader: MediaPreloader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckId: String = checkNotNull(savedStateHandle[Routes.ARG_DECK_ID])

    private val _state = MutableStateFlow(SwipeUiState())
    val state: StateFlow<SwipeUiState> = _state.asStateFlow()

    private val _topVideoMeta = MutableStateFlow<VideoMeta?>(null)
    /** Codec/fps/bitrate of the top card when it is a video, null otherwise. */
    val topVideoMeta: StateFlow<VideoMeta?> = _topVideoMeta.asStateFlow()

    /** True when the up-swipe archives to Google Photos (cloud flavor only). */
    val cloudArchiveEnabled: Boolean get() = photosArchive.isAvailable

    /** URIs already verified in the backup ledger — drives the cloud chip. */
    val backedUpUris: StateFlow<Set<String>> =
        backupRepository.observeBackedUpUris()
            .map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            val deck = deckRepository.getDeck(deckId)
            _state.update {
                it.copy(loading = false, deck = deck, cursor = deck?.completedCount ?: 0)
            }
        }
        // Keep the N±2 window warm in Coil's caches as the cursor advances.
        viewModelScope.launch {
            _state.map { it.deck to it.cursor }.distinctUntilChanged().collect { (deck, cursor) ->
                deck?.let { mediaPreloader.preloadAround(it.items, cursor) }
            }
        }
        // Refresh the video spec sheet whenever the top card changes: Room's
        // analysis cache first, header parse as the fallback.
        viewModelScope.launch {
            _state.map { it.topItem }.distinctUntilChanged().collect { item ->
                _topVideoMeta.value = null
                if (item == null || !item.isVideo) return@collect
                val cached = analysisDao.get(item.id)
                _topVideoMeta.value =
                    if (cached != null && (cached.videoCodec != null || cached.bitrateBps != null)) {
                        VideoMeta(cached.videoCodec, cached.frameRate, cached.bitrateBps)
                    } else {
                        videoMetadataExtractor.extract(item)
                    }
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
                SwipeDirection.UP -> {
                    if (photosArchive.isAvailable) {
                        // Cloud flavor: queue the Photos upload. The local file
                        // is only ever staged for deletion after verification.
                        photosArchive.enqueue(item)
                        backupRepository.recordKept(item, starred = true)
                    } else {
                        exclusionRepository.starItem(item)
                        backupRepository.recordKept(item, starred = true)
                    }
                }
                SwipeDirection.RIGHT -> backupRepository.recordKept(item, starred = false)
                SwipeDirection.NONE -> Unit
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
                SwipeDirection.UP -> {
                    photosArchive.cancelIfQueued(last.item.contentUri.toString())
                    backupRepository.removeKept(last.item.contentUri.toString())
                }
                SwipeDirection.RIGHT -> backupRepository.removeKept(last.item.contentUri.toString())
                SwipeDirection.NONE -> Unit
            }
            _state.update { it.copy(cursor = last.deckIndex, lastAction = null) }
            deckRepository.saveProgress(deck, last.deckIndex)
        }
    }

    fun dismissUndo() = _state.update { it.copy(lastAction = null) }
}
