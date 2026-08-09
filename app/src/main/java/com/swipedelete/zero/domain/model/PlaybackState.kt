package com.swipedelete.zero.domain.model

/** State machine of the single reused top-card video surface. */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Loading(val mediaId: Long) : PlaybackState
    data class Playing(val mediaId: Long) : PlaybackState
    data class Scrubbing(val mediaId: Long, val positionMs: Long) : PlaybackState
    data class Paused(val mediaId: Long) : PlaybackState
    data class Error(val mediaId: Long, val message: String) : PlaybackState

    val mediaIdOrNull: Long?
        get() = when (this) {
            is Idle -> null
            is Loading -> mediaId
            is Playing -> mediaId
            is Scrubbing -> mediaId
            is Paused -> mediaId
            is Error -> mediaId
        }
}

/** Everything that can happen to the top-card player. */
sealed interface PlaybackEvent {
    data class ShowVideo(val mediaId: Long) : PlaybackEvent
    data object ShowNothing : PlaybackEvent
    data object FirstFrameRendered : PlaybackEvent
    data object ScrubStart : PlaybackEvent
    data class ScrubMove(val positionMs: Long) : PlaybackEvent
    data object ScrubEnd : PlaybackEvent
    data object TogglePause : PlaybackEvent
    data class PlayerFailed(val message: String) : PlaybackEvent
}

/**
 * Pure transition table — the composable player state just applies it, which
 * keeps the whole machine unit-testable without ExoPlayer on the classpath.
 */
object PlaybackReducer {

    fun reduce(state: PlaybackState, event: PlaybackEvent): PlaybackState = when (event) {
        is PlaybackEvent.ShowVideo -> PlaybackState.Loading(event.mediaId)
        PlaybackEvent.ShowNothing -> PlaybackState.Idle
        PlaybackEvent.FirstFrameRendered -> when (state) {
            is PlaybackState.Loading -> PlaybackState.Playing(state.mediaId)
            else -> state
        }
        PlaybackEvent.ScrubStart -> when (state) {
            is PlaybackState.Playing -> PlaybackState.Scrubbing(state.mediaId, 0L)
            is PlaybackState.Paused -> PlaybackState.Scrubbing(state.mediaId, 0L)
            is PlaybackState.Loading -> PlaybackState.Scrubbing(state.mediaId, 0L)
            else -> state
        }
        is PlaybackEvent.ScrubMove -> when (state) {
            is PlaybackState.Scrubbing -> state.copy(positionMs = event.positionMs)
            else -> state
        }
        PlaybackEvent.ScrubEnd -> when (state) {
            is PlaybackState.Scrubbing -> PlaybackState.Playing(state.mediaId)
            else -> state
        }
        PlaybackEvent.TogglePause -> when (state) {
            is PlaybackState.Playing -> PlaybackState.Paused(state.mediaId)
            is PlaybackState.Paused -> PlaybackState.Playing(state.mediaId)
            else -> state
        }
        is PlaybackEvent.PlayerFailed -> when (val id = state.mediaIdOrNull) {
            null -> state
            else -> PlaybackState.Error(id, event.message)
        }
    }
}
