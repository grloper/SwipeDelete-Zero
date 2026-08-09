package com.swipedelete.zero.domain.scanner

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.swipedelete.zero.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Codec / frame-rate / bitrate triple shown in the card's info pill. */
data class VideoMeta(
    val codec: String? = null,
    val frameRate: Float? = null,
    val bitrateBps: Long? = null,
)

/**
 * Container-level video metadata reader. Only track headers are parsed — no
 * frame is ever decoded — so a call costs single-digit milliseconds even for a
 * 20 GB file. Results are memoised in a small in-process LRU so the swipe
 * screen's N±2 window never re-parses on recomposition; durable caching lives
 * in the media_analysis table (see [MediaAnalysisWorker]).
 */
@Singleton
class VideoMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = LruCache<Long, VideoMeta>(128)

    suspend fun extract(item: MediaItem): VideoMeta {
        if (!item.isVideo) return VideoMeta()
        cache.get(item.id)?.let { return it }
        val meta = withContext(Dispatchers.IO) {
            readMeta(item.contentUri, item.sizeBytes, item.durationMillis)
        }
        cache.put(item.id, meta)
        return meta
    }

    private fun readMeta(uri: Uri, sizeBytes: Long, durationMillis: Long): VideoMeta {
        var codec: String? = null
        var fps: Float? = null
        var bitrate: Long? = null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                codec = codecDisplayName(mime)
                // getInteger throws when the key is absent — always guard.
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = try {
                        format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    } catch (_: Exception) {
                        try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { null }
                    }
                }
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                }
                break
            }
        } catch (_: Exception) {
            // Corrupt / revoked / cloud-only file — degrade to whatever we have.
        } finally {
            extractor.release()
        }

        if (bitrate == null) bitrate = retrieverBitrate(uri)
        if (bitrate == null && durationMillis > 0) {
            bitrate = sizeBytes * 8 * 1000 / durationMillis
        }
        return VideoMeta(codec = codec, frameRate = fps, bitrateBps = bitrate)
    }

    private fun retrieverBitrate(uri: Uri): Long? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        fun codecDisplayName(mime: String): String = when (mime.lowercase()) {
            "video/hevc" -> "HEVC"
            "video/avc" -> "H.264"
            "video/av01" -> "AV1"
            "video/x-vnd.on2.vp9" -> "VP9"
            "video/x-vnd.on2.vp8" -> "VP8"
            "video/mp4v-es" -> "MPEG-4"
            "video/3gpp" -> "3GPP"
            "video/dolby-vision" -> "Dolby Vision"
            else -> mime.substringAfter('/').uppercase()
        }
    }
}
