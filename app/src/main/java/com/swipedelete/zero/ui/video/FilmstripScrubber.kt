package com.swipedelete.zero.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Size
import com.swipedelete.zero.domain.model.Filmstrip
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.PlaybackState
import com.swipedelete.zero.ui.theme.SdzColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Interactive 10-thumbnail filmstrip at the bottom of video cards.
 *
 * The thumbnails are pre-generated Coil video-frame decodes (the frame
 * timestamp is part of Coil's cache key, so each slot caches independently in
 * the existing memory/disk caches — no bespoke cache to manage) and act as the
 * navigation map; the live muted surface above IS the scrub preview, because
 * closest-sync-frame seeks on a local file land faster than a thumbnail swap.
 *
 * The strip owns its horizontal drags (child-first pointer dispatch), so
 * scrubbing never translates the card.
 */
@Composable
fun FilmstripScrubber(
    item: MediaItem,
    playerState: TopCardPlayerState,
    modifier: Modifier = Modifier,
) {
    val duration = item.durationMillis
    if (duration <= 0) return

    val context = LocalContext.current
    val timestamps = remember(item.id) { Filmstrip.timestampsMillis(duration) }

    var stripWidthPx by remember { mutableFloatStateOf(0f) }
    /** Finger fraction while scrubbing, -1 when idle. */
    var scrubFraction by remember(item.id) { mutableFloatStateOf(-1f) }
    /** Playhead fraction, polled from the player while playing. */
    var playheadFraction by remember(item.id) { mutableFloatStateOf(0f) }

    val playing = playerState.playback is PlaybackState.Playing
    LaunchedEffect(item.id, playing) {
        while (playing) {
            playheadFraction =
                (playerState.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SdzColors.PitchBlack.copy(alpha = 0.6f))
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(10.dp))
            .onSizeChanged { stripWidthPx = it.width.toFloat() }
            .pointerInput(item.id) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        playerState.beginScrub()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = fraction
                        playerState.scrubTo(Filmstrip.fractionToPositionMillis(fraction, duration))
                    },
                    onDragEnd = {
                        scrubFraction = -1f
                        playerState.endScrub()
                    },
                    onDragCancel = {
                        scrubFraction = -1f
                        playerState.endScrub()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = fraction
                        playheadFraction = fraction
                        playerState.scrubTo(Filmstrip.fractionToPositionMillis(fraction, duration))
                    },
                )
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            timestamps.forEachIndexed { index, timestamp ->
                val magnified = scrubFraction >= 0f &&
                    Filmstrip.slotIndexFor(scrubFraction) == index
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.contentUri)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .videoFrameMillis(timestamp)
                        .size(Size(Filmstrip.THUMB_WIDTH, Filmstrip.THUMB_HEIGHT))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val scale = if (magnified) 1.25f else 1f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        // Playhead: finger position while scrubbing, playback position otherwise.
        val fraction = if (scrubFraction >= 0f) scrubFraction else playheadFraction
        Box(
            Modifier
                .offset { IntOffset((fraction * (stripWidthPx - 2.dp.toPx())).roundToInt(), 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(SdzColors.PureWhite),
        )
    }
}
