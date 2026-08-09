package com.swipedelete.zero

import com.swipedelete.zero.domain.algorithm.TextDetector
import com.swipedelete.zero.domain.model.MediaClass
import com.swipedelete.zero.domain.model.MediaType
import com.swipedelete.zero.domain.model.StorageUrgency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Storage urgency thresholds — the hero stat's whole meaning. */
class StorageUrgencyTest {

    @Test
    fun `bands map to the right tone`() {
        assertEquals(StorageUrgency.COMFORTABLE, StorageUrgency.of(0.10f))
        assertEquals(StorageUrgency.COMFORTABLE, StorageUrgency.of(0.84f))
        assertEquals(StorageUrgency.FILLING, StorageUrgency.of(0.85f))
        assertEquals(StorageUrgency.FILLING, StorageUrgency.of(0.92f))
        assertEquals(StorageUrgency.CRITICAL, StorageUrgency.of(0.93f))
        assertEquals(StorageUrgency.CRITICAL, StorageUrgency.of(1.0f))
    }

    @Test
    fun `the reported device reads as critical`() {
        // 432.5 GB used of 460.1 GB — the real screenshot, 94% full.
        val used = 432_500L * 1024 * 1024
        val total = used + 27_600L * 1024 * 1024
        assertEquals(StorageUrgency.CRITICAL, StorageUrgency.of(used, total))
    }

    @Test
    fun `unknown capacity never cries wolf`() {
        assertEquals(StorageUrgency.COMFORTABLE, StorageUrgency.of(100L, 0L))
    }

    @Test
    fun `every level has a human label`() {
        StorageUrgency.entries.forEach { assertTrue(it.label.isNotBlank()) }
    }
}

/** Text-vs-photo separation, which drives the document bucket and card badge. */
class TextDetectorTest {

    private fun matrix(vararg v: Int) = v

    @Test
    fun `a page of text is strongly bimodal`() {
        // Mostly white background with black glyphs, nothing in the midtones.
        val page = IntArray(1024) { if (it % 7 == 0) 12 else 243 }
        assertTrue(TextDetector.bimodality(page) >= TextDetector.TEXT_BIMODALITY_THRESHOLD)
        assertTrue(TextDetector.isLikelyText(page))
    }

    @Test
    fun `a photograph fills the midtones`() {
        // A smooth gradient — the opposite of bimodal.
        val photo = IntArray(1024) { (it % 256) }
        assertFalse(TextDetector.isLikelyText(photo))
    }

    @Test
    fun `a flat frame is not called text`() {
        assertEquals(0.0, TextDetector.bimodality(IntArray(64) { 128 }), 0.0001)
    }

    @Test
    fun `empty input is safe`() {
        assertEquals(0.0, TextDetector.bimodality(intArrayOf()), 0.0001)
    }

    @Test
    fun `scoring is invariant to overall brightness`() {
        val bright = IntArray(512) { if (it % 5 == 0) 200 else 255 }
        val dim = IntArray(512) { if (it % 5 == 0) 40 else 95 }
        assertEquals(TextDetector.bimodality(bright), TextDetector.bimodality(dim), 0.0001)
    }
}

/** Classification: a chat screenshot must not be presented as a photograph. */
class MediaClassTest {

    @Test
    fun `video always wins`() {
        assertEquals(
            MediaClass.VIDEO,
            MediaClass.of(MediaType.VIDEO, "DCIM/Camera/", "PXL_1.mp4", isLikelyText = true),
        )
    }

    @Test
    fun `a text-heavy screenshot becomes a document`() {
        assertEquals(
            MediaClass.DOCUMENT,
            MediaClass.of(MediaType.IMAGE, "Pictures/Screenshots/", "Screenshot_1.png", true),
        )
    }

    @Test
    fun `a pictorial screenshot stays a screenshot`() {
        assertEquals(
            MediaClass.SCREENSHOT,
            MediaClass.of(MediaType.IMAGE, "Pictures/Screenshots/", "Screenshot_2.png", false),
        )
    }

    @Test
    fun `an unanalysed screenshot is not guessed at`() {
        assertEquals(
            MediaClass.SCREENSHOT,
            MediaClass.of(MediaType.IMAGE, "Pictures/Screenshots/", "Screenshot_3.png", null),
        )
    }

    @Test
    fun `receipts are documents by name alone`() {
        assertEquals(
            MediaClass.DOCUMENT,
            MediaClass.of(MediaType.IMAGE, "Download/", "hotel-receipt.jpg", null),
        )
    }

    @Test
    fun `ordinary photos stay photos`() {
        assertEquals(
            MediaClass.PHOTO,
            MediaClass.of(MediaType.IMAGE, "DCIM/Camera/", "IMG_20240501.jpg", false),
        )
    }

    @Test
    fun `only non-photos carry a badge`() {
        assertEquals(null, MediaClass.PHOTO.badge)
        assertEquals(null, MediaClass.VIDEO.badge)
        assertTrue(MediaClass.SCREENSHOT.badge!!.isNotBlank())
        assertTrue(MediaClass.DOCUMENT.badge!!.isNotBlank())
    }
}
