package com.swipedelete.zero.ui.screens.swipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.repository.BackupRepository
import com.swipedelete.zero.data.repository.DeckRepository
import com.swipedelete.zero.data.repository.ExclusionRepository
import com.swipedelete.zero.data.repository.MediaPreloader
import com.swipedelete.zero.data.repository.StatsStore
import com.swipedelete.zero.data.repository.StagingRepository
import com.swipedelete.zero.domain.backup.ArchiveItemState
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeckSortOrder {
    NEWEST_FIRST,
    LARGEST_FIRST,
}

data class SwipeUiState(
    val loading: Boolean = true,
    val deck: Deck? = null,
    val cursor: Int = 0,
    val sortOrder: DeckSortOrder = DeckSortOrder.NEWEST_FIRST,
    val nextDeckId: String? = null,
    val nextDeckTitle: String? = null,
    /** The most recent swipe, kept alive for the 5-second Undo window. */
    val lastAction: SwipeAction? = null,
    /** Bytes queued for reclaim during this sitting — the celebration's figure. */
    val sessionReclaimedBytes: Long = 0,
    /** Files queued for reclaim during this sitting. */
    val sessionReclaimedCount: Int = 0,
    /** True until the user has been shown the gesture coachmark. */
    val showCoachmark: Boolean = false,
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
    private val statsStore: StatsStore,
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

    /** Live Photos upload queue (always empty in fdroid/play). */
    val uploadQueue: StateFlow<Map<String, ArchiveItemState>> =
        photosArchive.queue
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            // Shown once, ever: the gesture mapping needs teaching exactly one time.
            if (!statsStore.coachmarkSeen.first()) {
                _state.update { it.copy(showCoachmark = true) }
            }
        }
        loadDeck(deckId)
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

    fun loadDeck(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val deck = deckRepository.getDeck(id)
            val nextDeck = deck?.let { deckRepository.getNextDeckInGroup(it) }
            _state.update {
                it.copy(
                    loading = false,
                    deck = deck,
                    cursor = deck?.completedCount ?: 0,
                    nextDeckId = nextDeck?.id,
                    nextDeckTitle = nextDeck?.title,
                )
            }
        }
    }

    fun setSortOrder(order: DeckSortOrder) {
        val current = _state.value
        val deck = current.deck ?: return
        if (current.sortOrder == order) return
        val cursor = current.cursor
        val swiped = deck.items.take(cursor)
        val remaining = deck.items.drop(cursor)
        val sortedRemaining = when (order) {
            DeckSortOrder.NEWEST_FIRST -> remaining.sortedByDescending { it.dateAddedMillis }
            DeckSortOrder.LARGEST_FIRST -> remaining.sortedByDescending { it.sizeBytes }
        }
        val reorderedDeck = deck.copy(items = swiped + sortedRemaining)
        _state.update {
            it.copy(
                deck = reorderedDeck,
                sortOrder = order,
            )
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
                    sessionReclaimedBytes = it.sessionReclaimedBytes +
                        if (direction == SwipeDirection.LEFT) item.sizeBytes else 0L,
                    sessionReclaimedCount = it.sessionReclaimedCount +
                        if (direction == SwipeDirection.LEFT) 1 else 0,
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
            _state.update {
                it.copy(
                    cursor = last.deckIndex,
                    lastAction = null,
                    // Undo must also unwind the session tally, or the
                    // celebration would claim space the user just took back.
                    sessionReclaimedBytes = (it.sessionReclaimedBytes -
                        if (last.direction == SwipeDirection.LEFT) last.item.sizeBytes else 0L)
                        .coerceAtLeast(0L),
                    sessionReclaimedCount = (it.sessionReclaimedCount -
                        if (last.direction == SwipeDirection.LEFT) 1 else 0).coerceAtLeast(0),
                )
            }
            deckRepository.saveProgress(deck, last.deckIndex)
        }
    }

    fun dismissUndo() = _state.update { it.copy(lastAction = null) }

    fun dismissCoachmark() {
        _state.update { it.copy(showCoachmark = false) }
        viewModelScope.launch { statsStore.markCoachmarkSeen() }
    }
}
