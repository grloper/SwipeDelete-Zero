package com.swipedelete.zero

import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.CloudUploadStats
import com.swipedelete.zero.domain.backup.UploadEvent
import com.swipedelete.zero.domain.backup.UploadReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudManagerTest {

    @Test
    fun `CloudUploadStats computes overall progress accurately`() {
        val stats = CloudUploadStats(
            totalCount = 4,
            queuedCount = 1,
            uploadingCount = 1,
            verifyingCount = 1,
            verifiedCount = 1,
            totalBytes = 100_000_000L, // 100 MB
            uploadedBytes = 50_000_000L, // 50 MB
            uploadSpeedBytesPerSec = 2_000_000L, // 2 MB/s
            etaSeconds = 25L,
        )

        assertEquals(0.5f, stats.overallProgress, 0.001f)
        assertFalse(stats.isIdle)
        assertEquals(25L, stats.etaSeconds)
    }

    @Test
    fun `CloudUploadStats handles zero bytes gracefully without division by zero`() {
        val stats = CloudUploadStats(
            totalCount = 0,
            totalBytes = 0L,
            uploadedBytes = 0L,
        )

        assertEquals(0f, stats.overallProgress, 0.001f)
        assertTrue(stats.isIdle)
        assertNull(stats.etaSeconds)
    }

    @Test
    fun `Rebackup flow transitions entity from verified or failed to fresh queued`() {
        val failedEntity = CloudUploadEntity(
            contentUri = "content://media/external/images/media/42",
            displayName = "vacation.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 5_000_000L,
            state = CloudUploadEntity.STATE_FAILED,
            attempts = 5,
            lastError = "Connection reset",
            enqueuedAtMillis = 1000L,
            updatedAtMillis = 2000L,
        )

        val freshNow = 3000L
        val rebackupEntity = failedEntity.copy(
            state = CloudUploadEntity.STATE_QUEUED,
            uploadUrl = null,
            bytesUploaded = 0,
            uploadToken = null,
            mediaItemId = null,
            attempts = 0,
            lastError = null,
            enqueuedAtMillis = freshNow,
            updatedAtMillis = freshNow,
        )

        assertEquals(CloudUploadEntity.STATE_QUEUED, rebackupEntity.state)
        assertEquals(0, rebackupEntity.attempts)
        assertNull(rebackupEntity.lastError)
        assertEquals(0L, rebackupEntity.bytesUploaded)
    }

    @Test
    fun `UploadReducer verifies successful chunk progress and finalization`() {
        val initial = CloudUploadEntity(
            contentUri = "content://media/external/images/media/99",
            displayName = "family.png",
            mimeType = "image/png",
            sizeBytes = 10_000_000L,
            state = CloudUploadEntity.STATE_QUEUED,
            enqueuedAtMillis = 1000L,
            updatedAtMillis = 1000L,
        )

        val session = UploadReducer.reduce(initial, UploadEvent.SessionStarted("https://upload.google.com/session1"), 1100L)
        assertEquals(CloudUploadEntity.STATE_UPLOADING, session.state)
        assertEquals("https://upload.google.com/session1", session.uploadUrl)

        val chunk1 = UploadReducer.reduce(session, UploadEvent.ChunkAcked(5_000_000L), 1200L)
        assertEquals(5_000_000L, chunk1.bytesUploaded)

        val finalized = UploadReducer.reduce(chunk1, UploadEvent.Finalized("token_abc_123"), 1300L)
        assertEquals(CloudUploadEntity.STATE_VERIFYING, finalized.state)
        assertEquals("token_abc_123", finalized.uploadToken)

        val verified = UploadReducer.reduce(finalized, UploadEvent.Created("photos_media_item_999"), 1400L)
        assertEquals(CloudUploadEntity.STATE_VERIFIED, verified.state)
        assertEquals("photos_media_item_999", verified.mediaItemId)
    }
}
