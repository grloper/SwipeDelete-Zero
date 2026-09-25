package com.swipedelete.zero.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0-R3 / M0-R4: Production HTTP session query parser regression tests.
 *
 * Exercises [PhotosUploader.parseSessionQuery] against real Google Photos resumable upload
 * HTTP header and body fixtures according to the specification:
 * https://developers.google.com/photos/library/guides/resumable-uploads
 */
class PhotosUploaderQueryParserTest {

    private val uploader = PhotosUploader()

    @Test
    fun `parseSessionQuery - active status with valid positive offset is resumable`() {
        val result = uploader.parseSessionQuery(
            statusHeader = "active",
            sizeReceivedHeader = "10485760",
            responseBody = "",
        )
        assertTrue("Active session with valid offset must be resumable", result.isResumable)
        assertFalse("Active session must not be marked final", result.isFinal)
        assertEquals(10485760L, result.offset)
        assertEquals("active", result.status)
        assertNull(result.uploadToken)
    }

    @Test
    fun `parseSessionQuery - active status with zero offset is resumable from byte zero`() {
        val result = uploader.parseSessionQuery(
            statusHeader = "active",
            sizeReceivedHeader = "0",
            responseBody = null,
        )
        assertTrue("Active session with zero offset is valid and resumable", result.isResumable)
        assertFalse(result.isFinal)
        assertEquals(0L, result.offset)
    }

    @Test
    fun `parseSessionQuery - active status with negative offset fails closed as not resumable`() {
        val result = uploader.parseSessionQuery(
            statusHeader = "active",
            sizeReceivedHeader = "-1024",
            responseBody = null,
        )
        assertFalse("Active session with negative offset must NOT be resumable", result.isResumable)
        assertFalse(result.isFinal)
        assertEquals(0L, result.offset)
    }

    @Test
    fun `parseSessionQuery - active status with missing or malformed size fails closed as not resumable`() {
        val nullSizeResult = uploader.parseSessionQuery(
            statusHeader = "active",
            sizeReceivedHeader = null,
            responseBody = null,
        )
        assertFalse("Missing size header must fail closed", nullSizeResult.isResumable)

        val malformedSizeResult = uploader.parseSessionQuery(
            statusHeader = "active",
            sizeReceivedHeader = "not_a_number",
            responseBody = null,
        )
        assertFalse("Non-numeric size header must fail closed", malformedSizeResult.isResumable)
    }

    @Test
    fun `parseSessionQuery - final status with response body recovers valid upload token`() {
        val token = "CAIShQEKZXlKaGJHY2lPaUpTVXpVeE1q..."
        val result = uploader.parseSessionQuery(
            statusHeader = "final",
            sizeReceivedHeader = "52428800",
            responseBody = token,
        )
        assertTrue("Final status must be marked final", result.isFinal)
        assertFalse("Final status is not resumable for more chunks", result.isResumable)
        assertEquals(token, result.uploadToken)
        assertEquals(52428800L, result.offset)
        assertEquals("final", result.status)
    }

    @Test
    fun `parseSessionQuery - final status with empty or missing body indicates lost receipt`() {
        val emptyResult = uploader.parseSessionQuery(
            statusHeader = "final",
            sizeReceivedHeader = "52428800",
            responseBody = "  ",
        )
        assertTrue("Status is final", emptyResult.isFinal)
        assertFalse("Not resumable", emptyResult.isResumable)
        assertNull("Upload token is lost and must remain null", emptyResult.uploadToken)

        val nullResult = uploader.parseSessionQuery(
            statusHeader = "final",
            sizeReceivedHeader = "52428800",
            responseBody = null,
        )
        assertTrue(nullResult.isFinal)
        assertNull(nullResult.uploadToken)
    }

    @Test
    fun `parseSessionQuery - terminated status fails closed as not resumable and not final`() {
        val result = uploader.parseSessionQuery(
            statusHeader = "terminated",
            sizeReceivedHeader = "1048576",
            responseBody = null,
        )
        assertFalse("Terminated session must NEVER be resumed", result.isResumable)
        assertFalse(result.isFinal)
        assertEquals("terminated", result.status)
        assertNull(result.uploadToken)
    }

    @Test
    fun `parseSessionQuery - cancelled or unknown status fails closed as not resumable`() {
        val cancelled = uploader.parseSessionQuery(
            statusHeader = "cancelled",
            sizeReceivedHeader = "4096",
            responseBody = null,
        )
        assertFalse(cancelled.isResumable)
        assertFalse(cancelled.isFinal)

        val unknown = uploader.parseSessionQuery(
            statusHeader = "unexpected_future_status",
            sizeReceivedHeader = "4096",
            responseBody = null,
        )
        assertFalse(unknown.isResumable)
        assertFalse(unknown.isFinal)

        val nullHeader = uploader.parseSessionQuery(
            statusHeader = null,
            sizeReceivedHeader = "4096",
            responseBody = null,
        )
        assertFalse(nullHeader.isResumable)
        assertFalse(nullHeader.isFinal)
    }
}
