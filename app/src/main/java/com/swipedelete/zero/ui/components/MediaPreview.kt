package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.size.Scale
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * Renders a card's media preview.
 *
 * Memory & OOM discipline (per PRD hardware constraints):
 *  - Images decode with Coil, capped to the card's pixel bounds (Coil chooses an
 *    appropriate sample size) and prefer hardware bitmaps.
 *  - **Videos never auto-play.** We render a STATIC first-frame thumbnail via
 *    Coil's [VideoFrameDecoder]; a play badge signals that tapping opens the
 *    ExoPlayer sheet. This avoids decoding heavy high-res video on every card.
 */
@Composable
fun MediaPreview(
    item: MediaItem,
    modifier: Modifier = Modifier,
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

        if (item.isVideo) {
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
