package com.swipedelete.zero.data.repository

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Size
import com.swipedelete.zero.domain.model.Filmstrip
import com.swipedelete.zero.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * N±2 media preload window for the swipe deck. The next two cards' full-size
 * previews (and, for videos, all ten filmstrip frames) are enqueued into
 * Coil's shared memory/disk caches so a fling lands on warm pixels; N-1 stays
 * warm automatically because it was just displayed, covering instant Undo.
 */
@Singleton
class MediaPreloader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun preloadAround(items: List<MediaItem>, cursor: Int) {
        for (index in cursor + 1..cursor + 2) {
            val item = items.getOrNull(index) ?: continue
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(item.contentUri)
                    .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder) }
                    .build()
            )
            if (item.isVideo && item.durationMillis > 0) {
                Filmstrip.timestampsMillis(item.durationMillis).forEach { timestamp ->
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(item.contentUri)
                            .decoderFactory(VideoFrameDecoder)
                            .videoFrameMillis(timestamp)
                            .size(Size(Filmstrip.THUMB_WIDTH, Filmstrip.THUMB_HEIGHT))
                            .build()
                    )
                }
            }
        }
    }

    private companion object {
        val VideoFrameDecoder = coil.decode.VideoFrameDecoder.Factory()
    }
}
