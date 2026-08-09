package com.swipedelete.zero.ui.video

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.PlaybackEvent
import com.swipedelete.zero.domain.model.PlaybackReducer
import com.swipedelete.zero.domain.model.PlaybackState

/**
 * Owner of the app's single video decoder path. One ExoPlayer instance lives
 * for the whole swipe-screen composition and is re-targeted at whichever card
 * is on top — re-preparing a media item is cheap, re-allocating a hardware
 * decoder per card is what causes jank, so that never happens.
 *
 * Muted auto-loop by design: `volume = 0f` + REPEAT_MODE_ONE. Seeks resolve to
 * the closest sync frame so filmstrip scrubbing is effectively instant on
 * local files.
 */
@Stable
class TopCardPlayerState internal constructor(val player: ExoPlayer) {

    var playback by mutableStateOf<PlaybackState>(PlaybackState.Idle)
        private set

    /** Gates the PlayerView's alpha so the Coil thumbnail masks surface warm-up. */
    var firstFrameRendered by mutableStateOf(false)
        private set

    private var lastSeekAtMillis = 0L
    private var queuedSeekMs = -1L

    internal fun dispatch(event: PlaybackEvent) {
        playback = PlaybackReducer.reduce(playback, event)
    }

    internal fun markFirstFrame() {
        firstFrameRendered = true
        dispatch(PlaybackEvent.FirstFrameRendered)
    }

    /** Target the player at the top card; null / non-video stops playback. */
    fun showItem(item: MediaItem?) {
        if (item == null || !item.isVideo) {
            player.stop()
            player.clearMediaItems()
            firstFrameRendered = false
            dispatch(PlaybackEvent.ShowNothing)
            return
        }
        if (playback.mediaIdOrNull == item.id) return
        firstFrameRendered = false
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(item.contentUri))
        player.prepare()
        player.playWhenReady = true
        dispatch(PlaybackEvent.ShowVideo(item.id))
    }

    fun togglePause() {
        when (playback) {
            is PlaybackState.Playing -> player.pause()
            is PlaybackState.Paused -> player.play()
            else -> return
        }
        dispatch(PlaybackEvent.TogglePause)
    }

    fun beginScrub() {
        player.pause()
        dispatch(PlaybackEvent.ScrubStart)
    }

    /** Seeks are throttled to one in flight per ~80ms; the last one always lands. */
    fun scrubTo(positionMs: Long) {
        dispatch(PlaybackEvent.ScrubMove(positionMs))
        val now = SystemClock.uptimeMillis()
        if (now - lastSeekAtMillis >= SEEK_THROTTLE_MS) {
            lastSeekAtMillis = now
            queuedSeekMs = -1
            player.seekTo(positionMs)
        } else {
            queuedSeekMs = positionMs
        }
    }

    fun endScrub() {
        if (queuedSeekMs >= 0) {
            player.seekTo(queuedSeekMs)
            queuedSeekMs = -1
        }
        player.play()
        dispatch(PlaybackEvent.ScrubEnd)
    }

    private companion object {
        const val SEEK_THROTTLE_MS = 80L
    }
}

/**
 * Creates the screen-scoped player: released with the composition, paused on
 * ON_STOP, resumed on ON_START. Deliberately not ViewModel-owned — the surface
 * lifecycle maps 1:1 to the screen's, and the ViewModel stays Android-free.
 */
@Composable
fun rememberTopCardPlayer(): TopCardPlayerState {
    val context = LocalContext.current
    val state = remember {
        val player = ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
        TopCardPlayerState(player)
    }

    DisposableEffect(state) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = state.markFirstFrame()
            override fun onPlayerError(error: PlaybackException) {
                state.dispatch(
                    PlaybackEvent.PlayerFailed(error.errorCodeName)
                )
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
            state.player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> state.player.pause()
                Lifecycle.Event.ON_START ->
                    if (state.playback is PlaybackState.Playing ||
                        state.playback is PlaybackState.Loading
                    ) {
                        state.player.play()
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
