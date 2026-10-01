package com.swipedelete.zero.photos

import com.swipedelete.zero.domain.backup.PhotosMediaReadiness
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
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
open class PhotosUploader @Inject constructor() {

    class HttpStatusException(val code: Int, message: String) : Exception(message)
    class MediaNotReadyException(message: String) : IOException(message)
    class MediaRejectedException(message: String) : Exception(message)
    class UnexpectedDestinationException(message: String) : Exception(message)

    data class Session(val uploadUrl: String, val chunkGranularityBytes: Long)

    /** Start a resumable session; the true byte size is mandatory up front. */
    open fun startSession(authToken: String, mimeType: String, rawSizeBytes: Long): Session {
        val connection = open(UPLOADS_URL, authToken).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Length", "0")
            setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            setRequestProperty("X-Goog-Upload-Command", "start")
            setRequestProperty("X-Goog-Upload-Content-Type", mimeType)
            setRequestProperty("X-Goog-Upload-Raw-Size", rawSizeBytes.toString())
            doOutput = true
        }
        try {
            connection.outputStream.use { /* empty body */ }
            checkSuccess(connection)
            val url = connection.getHeaderField("X-Goog-Upload-URL")
                ?: throw HttpStatusException(500, "start: missing X-Goog-Upload-URL")
            validateDestination(url, uploadSession = true)
            val granularity = connection.getHeaderField("X-Goog-Upload-Chunk-Granularity")
                ?.toLongOrNull() ?: 0L
            return Session(url, granularity)
        } finally {
            connection.disconnect()
        }
    }

    data class SessionQueryResult(
        val offset: Long,
        val status: String,
        val uploadToken: String?,
        val isResumable: Boolean,
        val isFinal: Boolean,
    )

    /**
     * Parses the resumable session query response according to the Google Photos Library
     * API resumable upload specification:
     * - "active": Session is live. Size-Received header must be a valid non-negative Long.
     * - "final": Session ended; a lost finalize receipt requires a fresh session.
     * - Any other status (cancelled, terminated, unknown) or invalid offset cannot be resumed.
     */
    fun parseSessionQuery(
        statusHeader: String?,
        sizeReceivedHeader: String?,
        responseBody: String?,
    ): SessionQueryResult {
        val normalizedStatus = statusHeader?.trim()?.lowercase() ?: "unknown"
        val parsedSize = sizeReceivedHeader?.trim()?.toLongOrNull()

        return when (normalizedStatus) {
            "active" -> {
                if (parsedSize != null && parsedSize >= 0L) {
                    SessionQueryResult(
                        offset = parsedSize,
                        status = "active",
                        uploadToken = null,
                        isResumable = true,
                        isFinal = false,
                    )
                } else {
                    SessionQueryResult(
                        offset = 0L,
                        status = "active",
                        uploadToken = null,
                        isResumable = false, // invalid or negative offset cannot resume
                        isFinal = false,
                    )
                }
            }
            "final" -> {
                // A query body is not a documented finalize receipt.
                SessionQueryResult(
                    offset = parsedSize ?: 0L,
                    status = "final",
                    uploadToken = null,
                    isResumable = false,
                    isFinal = true,
                )
            }
            else -> {
                SessionQueryResult(
                    offset = 0L,
                    status = normalizedStatus,
                    uploadToken = null,
                    isResumable = false,
                    isFinal = false,
                )
            }
        }
    }

    /**
     * Query session state from the server.
     * Evaluates X-Goog-Upload-Status, received byte count, and does not trust a query body as a finalize receipt.
     */
    open fun querySession(authToken: String, uploadUrl: String): SessionQueryResult {
        val connection = open(uploadUrl, authToken, uploadSession = true).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Length", "0")
            setRequestProperty("X-Goog-Upload-Command", "query")
            doOutput = true
        }
        try {
            connection.outputStream.use { }
            checkSuccess(connection)
            return parseSessionQuery(
                connection.getHeaderField("X-Goog-Upload-Status"),
                connection.getHeaderField("X-Goog-Upload-Size-Received"),
                null,
            )
        } finally {
            connection.disconnect()
        }
    }

    /** How many bytes the server has already received (resume after death). */
    fun queryOffset(authToken: String, uploadUrl: String): Long =
        querySession(authToken, uploadUrl).offset

    /**
     * Upload one chunk at [offset]. Returns the upload token when [isLast]
     * finalizes the session, null otherwise.
     */
    open fun uploadChunk(
        authToken: String,
        uploadUrl: String,
        chunk: ByteArray,
        length: Int,
        offset: Long,
        isLast: Boolean,
    ): String? {
        val connection = open(uploadUrl, authToken, uploadSession = true).apply {
            requestMethod = "POST"
            setRequestProperty("X-Goog-Upload-Command", if (isLast) "upload, finalize" else "upload")
            setRequestProperty("X-Goog-Upload-Offset", offset.toString())
            setFixedLengthStreamingMode(length)
            doOutput = true
            readTimeout = 120_000
        }
        try {
            connection.outputStream.use { it.write(chunk, 0, length) }
            checkSuccess(connection)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return if (isLast) body.trim().ifEmpty {
                throw HttpStatusException(500, "finalize returned an empty upload token")
            } else null
        } finally {
            connection.disconnect()
        }
    }

    /** Create an item; the returned ID alone is not a verified backup. */
    open fun batchCreate(authToken: String, uploadToken: String, fileName: String): String {
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
        val response = try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            checkSuccess(connection)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
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
    open fun getMediaItem(authToken: String, mediaItemId: String): RemoteItem {
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

    internal var connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }

    private fun open(
        urlString: String,
        authToken: String,
        uploadSession: Boolean = false,
    ): HttpURLConnection {
        // Check every use, including a URL restored from the local upload queue,
        // before opening a socket or attaching credentials or media bytes.
        validateDestination(urlString, uploadSession)
        return connectionFactory(urlString).apply {
            // A session URL comes only from an explicitly validated response header.
            // Never replay bearer credentials or a request body via Location.
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $authToken")
            connectTimeout = 30_000
            readTimeout = 60_000
        }
    }

    private fun validateDestination(urlString: String, uploadSession: Boolean) {
        val uri = runCatching { URI(urlString) }.getOrNull()
        val allowedAuthority = uri?.rawAuthority.equals(PHOTOS_HOST, ignoreCase = true) ||
            uri?.rawAuthority.equals("$PHOTOS_HOST:443", ignoreCase = true)
        val allowedOrigin = uri != null && uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(PHOTOS_HOST, ignoreCase = true) && allowedAuthority &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (uri.port == -1 || uri.port == 443)
        // Preserve the opaque query exactly; do not reconstruct or decode a session.
        // Google's current guide uses this path on the Library service origin, and
        // documents no alternate/regional upload host. See docs/PHOTOS_TRANSPORT.md.
        val allowedSession = !uploadSession ||
            (uri?.rawPath == "/v1/uploads" && !uri.rawQuery.isNullOrBlank())
        if (!allowedOrigin || !allowedSession) {
            // Do not echo session URLs: their query can contain upload capabilities.
            throw UnexpectedDestinationException(
                "Unexpected Google Photos ${if (uploadSession) "upload session" else "API"} destination; " +
                    "expected HTTPS photoslibrary.googleapis.com on port 443" +
                    if (uploadSession) " with /v1/uploads and a session query" else ""
            )
        }
    }

    private fun checkSuccess(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) {
            if (code in 300..399) {
                throw HttpStatusException(code, "Google Photos redirect rejected (HTTP $code)")
            }
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw HttpStatusException(code, error ?: "HTTP $code")
        }
    }

    companion object {
        const val PHOTOS_APPEND_SCOPE = "https://www.googleapis.com/auth/photoslibrary.appendonly"
        const val PHOTOS_READ_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata"
        private const val PHOTOS_HOST = "photoslibrary.googleapis.com"
        private const val UPLOADS_URL = "https://photoslibrary.googleapis.com/v1/uploads"
        private const val BATCH_CREATE_URL = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate"
        private const val MEDIA_ITEMS_URL = "https://photoslibrary.googleapis.com/v1/mediaItems"
    }
}

