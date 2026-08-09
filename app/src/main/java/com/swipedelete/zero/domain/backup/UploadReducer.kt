package com.swipedelete.zero.domain.backup

import com.swipedelete.zero.data.local.CloudUploadEntity

/** Everything that can happen to one resumable Photos upload. */
sealed interface UploadEvent {
    /** The `start` handshake returned a resumable session URL. */
    data class SessionStarted(val uploadUrl: String) : UploadEvent

    /** A chunk was accepted; [newOffset] is the server-acked byte count. */
    data class ChunkAcked(val newOffset: Long) : UploadEvent

    /** The finalize chunk returned the upload token. */
    data class Finalized(val uploadToken: String) : UploadEvent

    /** mediaItems:batchCreate answered — the verification handshake. */
    data class Created(val mediaItemId: String) : UploadEvent

    /** Any transport/HTTP failure; null [httpCode] = network-level error. */
    data class Failed(val httpCode: Int?, val message: String) : UploadEvent
}

/**
 * Pure state transitions for the Photos upload queue. The worker applies the
 * result to Room after every event, which is exactly what makes a 20 GB
 * upload resumable across process death.
 *
 * Invariant enforced here: a row can only reach VERIFIED through a [Created]
 * event carrying a non-blank mediaItemId — a blank id is a terminal failure,
 * never a verified upload.
 */
object UploadReducer {

    const val MAX_ATTEMPTS = 5

    /** 408/429/5xx and transport errors retry with backoff; 4xx are terminal. */
    fun isRetryable(httpCode: Int?): Boolean =
        httpCode == null || httpCode == 408 || httpCode == 429 || httpCode >= 500

    /** 8 MiB rounded to the session's chunk granularity (headers require it). */
    fun chunkSizeFor(granularityBytes: Long): Int {
        val base = 8L * 1024 * 1024
        if (granularityBytes <= 0) return base.toInt()
        val rounded = (base / granularityBytes) * granularityBytes
        return maxOf(rounded, granularityBytes).toInt()
    }

    fun reduce(entity: CloudUploadEntity, event: UploadEvent, nowMillis: Long): CloudUploadEntity =
        when (event) {
            is UploadEvent.SessionStarted -> entity.copy(
                state = CloudUploadEntity.STATE_UPLOADING,
                uploadUrl = event.uploadUrl,
                bytesUploaded = 0,
                updatedAtMillis = nowMillis,
            )
            is UploadEvent.ChunkAcked -> entity.copy(
                state = CloudUploadEntity.STATE_UPLOADING,
                bytesUploaded = event.newOffset,
                updatedAtMillis = nowMillis,
            )
            is UploadEvent.Finalized -> entity.copy(
                state = CloudUploadEntity.STATE_VERIFYING,
                uploadToken = event.uploadToken,
                bytesUploaded = entity.sizeBytes,
                updatedAtMillis = nowMillis,
            )
            is UploadEvent.Created ->
                if (event.mediaItemId.isBlank()) {
                    entity.copy(
                        state = CloudUploadEntity.STATE_FAILED,
                        attempts = entity.attempts + 1,
                        lastError = "batchCreate returned no mediaItemId",
                        updatedAtMillis = nowMillis,
                    )
                } else {
                    entity.copy(
                        state = CloudUploadEntity.STATE_VERIFIED,
                        mediaItemId = event.mediaItemId,
                        lastError = null,
                        updatedAtMillis = nowMillis,
                    )
                }
            is UploadEvent.Failed -> {
                val attempts = entity.attempts + 1
                if (isRetryable(event.httpCode) && attempts < MAX_ATTEMPTS) {
                    // Session URL + acked offset survive, so the retry resumes
                    // instead of restarting from byte zero.
                    entity.copy(
                        state = CloudUploadEntity.STATE_QUEUED,
                        attempts = attempts,
                        lastError = event.message,
                        updatedAtMillis = nowMillis,
                    )
                } else {
                    entity.copy(
                        state = CloudUploadEntity.STATE_FAILED,
                        attempts = attempts,
                        lastError = event.message,
                        updatedAtMillis = nowMillis,
                    )
                }
            }
        }
}
