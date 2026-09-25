package com.swipedelete.zero

import com.swipedelete.zero.domain.backup.PhotosMediaReadiness
import com.swipedelete.zero.domain.backup.PhotosMediaReadiness.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotosMediaReadinessTest {
    private val downloadUrl = "https://lh3.googleusercontent.com/example"

    @Test
    fun `ready video with download URL is available`() {
        assertEquals(Result.AVAILABLE, PhotosMediaReadiness.evaluate("video/mp4", downloadUrl, "READY"))
    }

    @Test
    fun `processing video is blocked even when it has a URL`() {
        assertEquals(Result.WAITING, PhotosMediaReadiness.evaluate("video/mp4", downloadUrl, "PROCESSING"))
    }

    @Test
    fun `failed video is rejected even when it has a URL`() {
        assertEquals(Result.REJECTED, PhotosMediaReadiness.evaluate("video/mp4", downloadUrl, "FAILED"))
    }

    @Test
    fun `missing or unspecified video state never qualifies`() {
        for (status in listOf(null, "", "UNSPECIFIED")) {
            assertEquals(Result.WAITING, PhotosMediaReadiness.evaluate("video/mp4", downloadUrl, status))
        }
    }

    @Test
    fun `unknown video state fails closed`() {
        assertEquals(Result.REJECTED, PhotosMediaReadiness.evaluate("video/mp4", downloadUrl, "FUTURE_STATE"))
    }

    @Test
    fun `ready video without downloadable content is blocked`() {
        for (url in listOf(null, "", " ")) {
            assertEquals(Result.WAITING, PhotosMediaReadiness.evaluate("video/mp4", url, "READY"))
        }
    }

    @Test
    fun `image requires a download URL but no video state`() {
        assertEquals(Result.AVAILABLE, PhotosMediaReadiness.evaluate("image/jpeg", downloadUrl, null))
        assertEquals(Result.WAITING, PhotosMediaReadiness.evaluate("image/jpeg", null, null))
    }

    @Test
    fun `unsupported media does not qualify`() {
        assertEquals(Result.REJECTED, PhotosMediaReadiness.evaluate("application/pdf", downloadUrl, null))
        assertEquals(Result.REJECTED, PhotosMediaReadiness.evaluate("", downloadUrl, "READY"))
    }

    @Test
    fun `malformed insecure or credential-bearing URL fails closed`() {
        for (url in listOf("http://example.com/media", "not a URL", "https:///media", "https://user@example.com/media")) {
            assertEquals(Result.REJECTED, PhotosMediaReadiness.evaluate("image/jpeg", url, null))
        }
    }
}
