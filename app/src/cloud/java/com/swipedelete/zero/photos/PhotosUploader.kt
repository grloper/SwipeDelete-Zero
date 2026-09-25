package com.swipedelete.zero.photos

import com.swipedelete.zero.domain.backup.PhotosMediaReadiness
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Photos Library API client shared by Play and cloud builds.
 *
 * Resumable upload and batchCreate establish an app-created media item.
 * Readback additionally requires usable media, including READY for videos.
 * Neither metadata nor readiness proves original-byte integrity or restore.
 *
 * Post-March-2025 scopes: appendonly uploads; readonly.appcreateddata reads
 * only app-created items, not the user's entire pre-existing photo library.
 */
@Singleton
class PhotosUploader @Inject constructor() {

    class HttpStatusException(val code: Int, message: String) : Exception(message)
    class MediaNotReadyException(message: String) : IOException(message)
    class MediaRejectedException(message: String) : Exception(message)

    data class Session(val uploadUrl: String, val chunkGranularityBytes: Long)

    /** Start a resumable session; the true byte size is mandatory up front. */
    fun startSession(authToken: String, mimeType: String, rawSizeBytes: Long): Session {
        val connection = open(UPLOADS_URL, authToken).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Length", "0")
            setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            setRequestProperty("X-Goog-Upload-Command", "start")
            setRequestProperty("X-Goog-Upload-Content-Type", mimeType)
            setRequestProperty("X-Goog-Upload-Raw-Size", rawSizeBytes.toString())
            doOutput = true
        }
        connection.outputStream.use { /* empty body */ }
        checkSuccess(connection)
        val url = connection.getHeaderField("X-Goog-Upload-URL")
            ?: throw HttpStatusException(500, "start: missing X-Goog-Upload-URL")
        val granularity = connection.getHeaderField("X-Goog-Upload-Chunk-Granularity")
            ?.toLongOrNull() ?: 0L
        connection.disconnect()
        return Session(url, granularity)
    }

    /** How many bytes the server has already received (resume after death). */
    fun queryOffset(authToken: String, uploadUrl: String): Long {
        val connection = open(uploadUrl, authToken).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Length", "0")
            setRequestProperty("X-Goog-Upload-Command", "query")
            doOutput = true
        }
        connection.outputStream.use { }
        checkSuccess(connection)
        val received = connection.getHeaderField("X-Goog-Upload-Size-Received")?.toLongOrNull() ?: 0L
        connection.disconnect()
        return received
    }

    /**
     * Upload one chunk at [offset]. Returns the upload token when [isLast]
     * finalizes the session, null otherwise.
     */
    fun uploadChunk(
        authToken: String,
        uploadUrl: String,
        chunk: ByteArray,
        length: Int,
        offset: Long,
        isLast: Boolean,
    ): String? {
        val connection = open(uploadUrl, authToken).apply {
            requestMethod = "POST"
            setRequestProperty("X-Goog-Upload-Command", if (isLast) "upload, finalize" else "upload")
            setRequestProperty("X-Goog-Upload-Offset", offset.toString())
            setFixedLengthStreamingMode(length)
            doOutput = true
            readTimeout = 120_000
        }
        connection.outputStream.use { it.write(chunk, 0, length) }
        checkSuccess(connection)
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return if (isLast) body.trim().ifEmpty {
            throw HttpStatusException(500, "finalize returned an empty upload token")
        } else null
    }

    /** Create an item; the returned ID alone is not a verified backup. */
    fun batchCreate(authToken: String, uploadToken: String, fileName: String): String {
        val body = JSONObject()
            .put(
                "newMediaItems",
                JSONArray().put(
                    JSONObject().put(
                        "simpleMediaItem",
                        JSONObject()
                            .put("uploadToken", uploadToken)
                            .put("fileName", fileName),
                    )
                ),
            )
            .toString()
        val connection = open(BATCH_CREATE_URL, authToken).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        checkSuccess(connection)
        val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        connection.disconnect()
        val result = response.optJSONArray("newMediaItemResults")?.optJSONObject(0)
            ?: throw HttpStatusException(500, "batchCreate: empty newMediaItemResults")
        val status = result.optJSONObject("status")
        val code = status?.optInt("code", 0) ?: 0
        if (code != 0) {
            throw HttpStatusException(500, "batchCreate item status: ${status?.optString("message")}")
        }
        return result.optJSONObject("mediaItem")?.optString("id").orEmpty()
    }

    /**
     * Live readback for an app-created item. Both upload completion and the
     * pre-delete check use this method, so neither can accept an unavailable
     * video. Processing retries through the worker's bounded backoff; failed
     * or unknown states fail closed. No media bytes are restored here.
     */
    fun getMediaItem(authToken: String, mediaItemId: String): RemoteItem {
        require(mediaItemId.isNotBlank())
        val encoded = URLEncoder.encode(mediaItemId, "UTF-8")
        val connection = open("$MEDIA_ITEMS_URL/$encoded", authToken).apply {
            requestMethod = "GET"
        }
        try {
            checkSuccess(connection)
            val item = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val mimeType = item.optString("mimeType")
            val videoStatus = item.optJSONObject("mediaMetadata")
                ?.optJSONObject("video")?.optString("status")
            when (PhotosMediaReadiness.evaluate(mimeType, item.optString("baseUrl"), videoStatus)) {
                PhotosMediaReadiness.Result.AVAILABLE -> Unit
                PhotosMediaReadiness.Result.WAITING -> throw MediaNotReadyException(
                    "Google Photos is still processing this media or has not provided downloadable content. Local deletion remains blocked."
                )
                PhotosMediaReadiness.Result.REJECTED -> throw MediaRejectedException(
                    "Google Photos returned failed, unsupported, or invalid media. Local deletion remains blocked."
                )
            }
            return RemoteItem(
                id = item.optString("id"),
                filename = item.optString("filename"),
                mimeType = mimeType,
                productUrl = item.optString("productUrl"),
            )
        } finally {
            connection.disconnect()
        }
    }

    data class RemoteItem(val id: String, val filename: String, val mimeType: String, val productUrl: String)

    private fun open(urlString: String, authToken: String): HttpURLConnection =
        (URL(urlString).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $authToken")
            connectTimeout = 30_000
            readTimeout = 60_000
        }

    private fun checkSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            connection.disconnect()
            throw HttpStatusException(code, error ?: "HTTP $code")
        }
    }

    companion object {
        const val PHOTOS_APPEND_SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly"
        const val PHOTOS_READ_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"
        private const val UPLOADS_URL = "https://photoslibrary.googleapis.com/v1/uploads"
        private const val BATCH_CREATE_URL = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate"
        private const val MEDIA_ITEMS_URL = "https://photoslibrary.googleapis.com/v1/mediaItems"
    }
}
