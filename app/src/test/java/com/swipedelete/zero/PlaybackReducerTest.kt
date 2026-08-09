package com.swipedelete.zero

import com.swipedelete.zero.domain.model.PlaybackEvent
import com.swipedelete.zero.domain.model.PlaybackReducer
import com.swipedelete.zero.domain.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackReducerTest {

    private fun reduce(state: PlaybackState, vararg events: PlaybackEvent): PlaybackState =
        events.fold(state) { s, e -> PlaybackReducer.reduce(s, e) }

    @Test
    fun `show video then first frame plays`() {
        assertEquals(
            PlaybackState.Playing(7),
            reduce(PlaybackState.Idle, PlaybackEvent.ShowVideo(7), PlaybackEvent.FirstFrameRendered),
        )
    }

    @Test
    fun `show nothing resets to idle from anywhere`() {
        assertEquals(
            PlaybackState.Idle,
            reduce(PlaybackState.Scrubbing(7, 100), PlaybackEvent.ShowNothing),
        )
    }

    @Test
    fun `scrub round trip returns to playing`() {
        val end = reduce(
            PlaybackState.Playing(7),
            PlaybackEvent.ScrubStart,
            PlaybackEvent.ScrubMove(1500),
            PlaybackEvent.ScrubMove(2500),
            PlaybackEvent.ScrubEnd,
        )
        assertEquals(PlaybackState.Playing(7), end)
    }

    @Test
    fun `scrub move updates position`() {
        assertEquals(
            PlaybackState.Scrubbing(7, 2500),
            reduce(PlaybackState.Playing(7), PlaybackEvent.ScrubStart, PlaybackEvent.ScrubMove(2500)),
        )
    }

    @Test
    fun `scrubbing while still loading is allowed`() {
        assertEquals(
            PlaybackState.Scrubbing(7, 0),
            reduce(PlaybackState.Loading(7), PlaybackEvent.ScrubStart),
        )
    }

    @Test
    fun `card swap mid scrub retargets the machine`() {
        assertEquals(
            PlaybackState.Loading(8),
            reduce(PlaybackState.Scrubbing(7, 900), PlaybackEvent.ShowVideo(8)),
        )
    }

    @Test
    fun `toggle pause round trip`() {
        val paused = reduce(PlaybackState.Playing(7), PlaybackEvent.TogglePause)
        assertEquals(PlaybackState.Paused(7), paused)
        assertEquals(PlaybackState.Playing(7), reduce(paused, PlaybackEvent.TogglePause))
    }

    @Test
    fun `player failure carries the media id`() {
        assertEquals(
            PlaybackState.Error(7, "decode"),
            reduce(PlaybackState.Playing(7), PlaybackEvent.PlayerFailed("decode")),
        )
    }

    @Test
    fun `failure while idle stays idle`() {
        assertEquals(
            PlaybackState.Idle,
            reduce(PlaybackState.Idle, PlaybackEvent.PlayerFailed("decode")),
        )
    }

    @Test
    fun `error recovers by showing a new video`() {
        assertEquals(
            PlaybackState.Loading(9),
            reduce(PlaybackState.Error(7, "decode"), PlaybackEvent.ShowVideo(9)),
        )
    }
}
