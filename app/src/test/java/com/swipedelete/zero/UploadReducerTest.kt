package com.swipedelete.zero

import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.UploadEvent
import com.swipedelete.zero.domain.backup.UploadReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadReducerTest {

    private val row = CloudUploadEntity(
        contentUri = "content://media/external/video/1",
        displayName = "clip.mp4",
        mimeType = "video/mp4",
        sizeBytes = 100L shl 20,
        state = CloudUploadEntity.STATE_QUEUED,
        enqueuedAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    @Test
    fun `happy path reaches verified`() {
        var r = UploadReducer.reduce(row, UploadEvent.SessionStarted("https://u/1"), 2)
        assertEquals(CloudUploadEntity.STATE_UPLOADING, r.state)
        assertEquals("https://u/1", r.uploadUrl)

        r = UploadReducer.reduce(r, UploadEvent.ChunkAcked(8L shl 20), 3)
        assertEquals(8L shl 20, r.bytesUploaded)

        r = UploadReducer.reduce(r, UploadEvent.Finalized("tok"), 4)
        assertEquals(CloudUploadEntity.STATE_VERIFYING, r.state)
        assertEquals("tok", r.uploadToken)
        assertEquals(r.sizeBytes, r.bytesUploaded)

        r = UploadReducer.reduce(r, UploadEvent.Created("media-item-9"), 5)
        assertEquals(CloudUploadEntity.STATE_VERIFIED, r.state)
        assertEquals("media-item-9", r.mediaItemId)
    }

    @Test
    fun `blank mediaItemId can never verify`() {
        val verifying = row.copy(state = CloudUploadEntity.STATE_VERIFYING, uploadToken = "tok")
        val r = UploadReducer.reduce(verifying, UploadEvent.Created(""), 9)
        assertEquals(CloudUploadEntity.STATE_FAILED, r.state)
        assertNull(r.mediaItemId)
    }

    @Test
    fun `retryable failure re-queues and keeps resume state`() {
        val uploading = row.copy(
            state = CloudUploadEntity.STATE_UPLOADING,
            uploadUrl = "https://u/1",
            bytesUploaded = 24L shl 20,
        )
        val r = UploadReducer.reduce(uploading, UploadEvent.Failed(503, "server"), 9)
        assertEquals(CloudUploadEntity.STATE_QUEUED, r.state)
        assertEquals(1, r.attempts)
        // The session survives so the retry resumes at the acked offset.
        assertEquals("https://u/1", r.uploadUrl)
        assertEquals(24L shl 20, r.bytesUploaded)
    }

    @Test
    fun `terminal http error fails immediately`() {
        val r = UploadReducer.reduce(row, UploadEvent.Failed(403, "forbidden"), 9)
        assertEquals(CloudUploadEntity.STATE_FAILED, r.state)
    }

    @Test
    fun `attempt cap turns retryable into terminal`() {
        var r = row
        repeat(UploadReducer.MAX_ATTEMPTS) {
            r = UploadReducer.reduce(r, UploadEvent.Failed(null, "offline"), 9)
        }
        assertEquals(CloudUploadEntity.STATE_FAILED, r.state)
        assertEquals(UploadReducer.MAX_ATTEMPTS, r.attempts)
    }

    @Test
    fun `retryable classification`() {
        assertTrue(UploadReducer.isRetryable(null))
        assertTrue(UploadReducer.isRetryable(408))
        assertTrue(UploadReducer.isRetryable(429))
        assertTrue(UploadReducer.isRetryable(500))
        assertTrue(UploadReducer.isRetryable(503))
        assertFalse(UploadReducer.isRetryable(400))
        assertFalse(UploadReducer.isRetryable(403))
        assertFalse(UploadReducer.isRetryable(413))
    }

    @Test
    fun `chunk size rounds to granularity`() {
        // Default Photos granularity is 256 KiB — 8 MiB is already a multiple.
        assertEquals(8 shl 20, UploadReducer.chunkSizeFor(256L shl 10))
        // Odd granularity: rounded down to a multiple, never below one unit.
        val g = 3_000_000L
        val size = UploadReducer.chunkSizeFor(g)
        assertEquals(0, size % g)
        assertTrue(size >= g)
        // No granularity reported -> plain 8 MiB.
        assertEquals(8 shl 20, UploadReducer.chunkSizeFor(0))
    }
}
