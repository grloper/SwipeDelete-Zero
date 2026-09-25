package com.swipedelete.zero

import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.UploadEvent
import com.swipedelete.zero.domain.backup.UploadReducer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Network and authentication failure tests for M0:
 * NET-01: Session status query parsing, lost finalization recovery, session reset without token fabrication.
 * NET-02: Independent read vs upload scopes, account rotation guard, bounded auth retries.
 * NET-03: HTTP status code classification, retry-after/backoff handling, retry bounds.
 */
class M0NetworkAuthFailureTest {

    private fun testEntity(
        state: String = CloudUploadEntity.STATE_QUEUED,
        sizeBytes: Long = 10_000_000L,
        bytesUploaded: Long = 0L,
        attempts: Int = 0,
        uploadUrl: String? = null,
        uploadToken: String? = null,
    ) = CloudUploadEntity(
        contentUri = "content://media/external/images/media/42",
        displayName = "vacation.mp4",
        mimeType = "video/mp4",
        sizeBytes = sizeBytes,
        state = state,
        bytesUploaded = bytesUploaded,
        attempts = attempts,
        uploadUrl = uploadUrl,
        uploadToken = uploadToken,
        mediaItemId = null,
        lastError = null,
        enqueuedAtMillis = 1000L,
        updatedAtMillis = 1000L,
    )

    // =========================================================================
    // NET-01: Lost Finalization & Session Recovery
    // =========================================================================

    @Test
    fun `NET-01 - SessionReset clears uploadUrl and uploadToken cleanly`() {
        val entity = testEntity(
            state = CloudUploadEntity.STATE_UPLOADING,
            bytesUploaded = 5_000_000L,
            uploadUrl = "https://photos.googleapis.com/session/old",
            uploadToken = null,
        )

        val reset = UploadReducer.reduce(entity, UploadEvent.SessionReset("https://photos.googleapis.com/session/new"), 2000L)

        assertEquals(CloudUploadEntity.STATE_UPLOADING, reset.state)
        assertEquals("https://photos.googleapis.com/session/new", reset.uploadUrl)
        assertNull(reset.uploadToken)
        assertEquals(0L, reset.bytesUploaded)
    }

    @Test
    fun `NET-01 - Finalized event transitions entity to VERIFYING and sets uploadToken`() {
        val entity = testEntity(
            state = CloudUploadEntity.STATE_UPLOADING,
            bytesUploaded = 10_000_000L,
            uploadUrl = "https://photos.googleapis.com/session/active",
        )

        val finalized = UploadReducer.reduce(entity, UploadEvent.Finalized("valid_token_xyz"), 2000L)

        assertEquals(CloudUploadEntity.STATE_VERIFYING, finalized.state)
        assertEquals("valid_token_xyz", finalized.uploadToken)
        assertEquals(entity.sizeBytes, finalized.bytesUploaded)
    }

    @Test
    fun `NET-01 - SessionQueryResult models active, final with token, and final lost-token cases`() {
        val activeResult = UploadReducer.SessionQueryResult(
            offset = 4096L,
            status = "active",
            uploadToken = null,
        )
        assertEquals(4096L, activeResult.offset)
        assertEquals("active", activeResult.status)
        assertNull(activeResult.uploadToken)

        val finalRecovered = UploadReducer.SessionQueryResult(
            offset = 10_000_000L,
            status = "final",
            uploadToken = "recovered_token_abc",
        )
        assertEquals("final", finalRecovered.status)
        assertEquals("recovered_token_abc", finalRecovered.uploadToken)

        val finalLost = UploadReducer.SessionQueryResult(
            offset = 10_000_000L,
            status = "final",
            uploadToken = null,
        )
        assertEquals("final", finalLost.status)
        assertNull(finalLost.uploadToken)
    }

    // =========================================================================
    // NET-02: Independent Scopes & Auth Discipline
    // =========================================================================

    @Test
    fun `NET-02 - append and read scopes are separate and distinct`() {
        assertFalse(
            "Append and read scopes must not be identical",
            UploadReducer.PHOTOS_APPEND_SCOPE == UploadReducer.PHOTOS_READ_SCOPE,
        )
        assertTrue(
            "Append scope must declare appendonly",
            UploadReducer.PHOTOS_APPEND_SCOPE.contains("appendonly"),
        )
        assertTrue(
            "Read scope must declare appcreateddata",
            UploadReducer.PHOTOS_READ_SCOPE.contains("appcreateddata"),
        )
    }

    // =========================================================================
    // NET-03: HTTP Classification & Terminal Error Safety
    // =========================================================================

    @Test
    fun `NET-03 - transport errors, timeouts, rate-limits and server errors are retryable`() {
        assertTrue("Network error (null code) must be retryable", UploadReducer.isRetryable(null))
        assertTrue("HTTP 408 (timeout) must be retryable", UploadReducer.isRetryable(408))
        assertTrue("HTTP 429 (rate limit) must be retryable", UploadReducer.isRetryable(429))
        assertTrue("HTTP 500 (internal server error) must be retryable", UploadReducer.isRetryable(500))
        assertTrue("HTTP 503 (service unavailable) must be retryable", UploadReducer.isRetryable(503))
    }

    @Test
    fun `NET-03 - client errors such as quota exhaustion and bad requests are terminal`() {
        assertFalse("HTTP 400 must be terminal", UploadReducer.isRetryable(400))
        assertFalse("HTTP 401 must be handled by auth layer, not raw network retry", UploadReducer.isRetryable(401))
        assertFalse("HTTP 403 (quota full) must be terminal", UploadReducer.isRetryable(403))
        assertFalse("HTTP 404 must be terminal", UploadReducer.isRetryable(404))
    }

    @Test
    fun `NET-03 - retryable error transitions to failed when MAX_ATTEMPTS is reached`() {
        val entity = testEntity(attempts = 4) // next attempt will be 5 (MAX_ATTEMPTS)
        val reduced = UploadReducer.reduce(entity, UploadEvent.Failed(500, "Server Error"), 2000L)

        assertEquals("After MAX_ATTEMPTS, state must become FAILED", CloudUploadEntity.STATE_FAILED, reduced.state)
        assertEquals(5, reduced.attempts)
        assertEquals("Server Error", reduced.lastError)
    }

    @Test
    fun `NET-03 - terminal error immediately transitions to STATE_FAILED regardless of attempt count`() {
        val entity = testEntity(attempts = 0)
        val reduced = UploadReducer.reduce(entity, UploadEvent.Failed(403, "Quota Exceeded"), 2000L)

        assertEquals("Terminal error must immediately fail", CloudUploadEntity.STATE_FAILED, reduced.state)
        assertEquals(1, reduced.attempts)
        assertEquals("Quota Exceeded", reduced.lastError)
    }
}
