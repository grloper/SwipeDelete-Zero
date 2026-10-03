package com.swipedelete.zero

import com.swipedelete.zero.domain.backup.NoOpPhotosArchive
import com.swipedelete.zero.domain.backup.PhotosMediaReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Security, privacy and flavor boundary tests for M0:
 * SEC-01: F-Droid air-gap boundary, HTTPS-only requirements, no embedded secrets.
 */
class M0PrivacyFlavorSecurityTest {

    @Test
    fun `SEC-01 - F-Droid flavor binds NoOpPhotosArchive with isAvailable false`() {
        val noOp = NoOpPhotosArchive()
        assertFalse("Offline F-Droid edition must have isAvailable = false", noOp.isAvailable)
    }

    @Test
    fun `SEC-01 - non-HTTPS or plaintext URLs are strictly rejected by media readiness policy`() {
        val httpUrl = "http://photoslibrary.googleapis.com/v1/mediaItems/123"
        val ftpUrl = "ftp://photoslibrary.googleapis.com/v1/mediaItems/123"
        val credentialUrl = "https://user:password@photoslibrary.googleapis.com/v1/mediaItems/123"

        assertEquals(
            "HTTP URL must be rejected",
            PhotosMediaReadiness.Result.REJECTED,
            PhotosMediaReadiness.evaluate("image/jpeg", httpUrl, null),
        )
        assertEquals(
            "FTP URL must be rejected",
            PhotosMediaReadiness.Result.REJECTED,
            PhotosMediaReadiness.evaluate("image/jpeg", ftpUrl, null),
        )
        assertEquals(
            "URL containing embedded user credentials must be rejected",
            PhotosMediaReadiness.Result.REJECTED,
            PhotosMediaReadiness.evaluate("image/jpeg", credentialUrl, null),
        )
    }

    @Test
    fun `SEC-01 - valid secure HTTPS URL with valid host passes readiness check`() {
        val secureUrl = "https://lh3.googleusercontent.com/lr/ANi1v8..."
        assertEquals(
            "Secure HTTPS URL must be available",
            PhotosMediaReadiness.Result.AVAILABLE,
            PhotosMediaReadiness.evaluate("image/jpeg", secureUrl, null),
        )
    }
}
