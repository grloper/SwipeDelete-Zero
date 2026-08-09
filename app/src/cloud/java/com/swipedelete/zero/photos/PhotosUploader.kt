package com.swipedelete.zero.photos

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw Google Photos Library API client (cloud flavor only), speaking the
 * X-Goog-Upload resumable protocol over HttpURLConnection — same zero-SDK
 * style as the Drive backup.
 *
 * Flow: `start` mints a resumable session URL → chunks POST with an offset
 * header (last one adds `finalize` and returns the upload token) → `query`
 * recovers the server-acked offset after a crash → `mediaItems:batchCreate`
 * exchanges the token for a mediaItemId, which is the verification handshake
 * the purge path requires.
 *
 * Scope note (post-March-2025 API): `photoslibrary.appendonly` is upload-only
 * and still fully supports this flow; the app can never read the library.
 */
@Singleton
class PhotosUploader @Inject constructor() {

    class HttpStatusException(val code: Int, message: String) : Exception(message)

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

    /**
     * The verification handshake: batchCreate must answer 200 with a
     * non-empty mediaItem id, or the upload is NOT verified.
     */
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
        private const val UPLOADS_URL = "https://photoslibrary.googleapis.com/v1/uploads"
        private const val BATCH_CREATE_URL = "https://photoslibrary.googleapis.com/v1/mediaItems:batchCreate"
    }
}
