package com.swipedelete.zero.ui.components

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.size.Scale
import com.swipedelete.zero.R
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.video.TopCardPlayerState

/**
 * Renders a card's media preview.
 *
 * Memory & OOM discipline:
 *  - Images decode with Coil, capped to the card's pixel bounds (Coil chooses an
 *    appropriate sample size) and prefer hardware bitmaps.
 *  - Videos always render their Coil first-frame thumbnail as the base layer.
 *    When a [playerState] is supplied (the top card only), a TextureView-backed
 *    PlayerView is layered above it, alpha-gated on onRenderedFirstFrame — the
 *    thumbnail masks surface warm-up so there is never a black flash, and the
 *    single shared ExoPlayer means no per-card decoder re-allocation. Tapping
 *    the surface toggles pause. Cards without a player (peek card, staging,
 *    dual screen) keep the static frame + play badge.
 */
@Composable
fun MediaPreview(
    item: MediaItem,
    modifier: Modifier = Modifier,
    playerState: TopCardPlayerState? = null,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val request = ImageRequest.Builder(LocalContext.current)
            .data(item.contentUri)
            .scale(Scale.FILL)
            .crossfade(true)
            .apply {
                if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory())
            }
            .build()

        AsyncImage(
            model = request,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (item.isVideo && playerState != null) {
            AndroidView(
                factory = { context ->
                    LayoutInflater.from(context)
                        .inflate(R.layout.view_top_card_player, null) as PlayerView
                },
                update = { view -> view.player = playerState.player },
                onRelease = { view -> view.player = null },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (playerState.firstFrameRendered) 1f else 0f
                    }
                    .pointerInput(item.id) {
                        detectTapGestures { playerState.togglePause() }
                    },
            )
        } else if (item.isVideo) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(SdzColors.PitchBlack.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
