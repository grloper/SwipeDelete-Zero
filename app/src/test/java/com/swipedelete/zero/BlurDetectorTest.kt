package com.swipedelete.zero

import com.swipedelete.zero.domain.algorithm.BlurDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurDetectorTest {

    /** A flat gray field has zero edge energy → maximally "blurry". */
    @Test
    fun `flat field is flagged blurry`() {
        val flat = IntArray(32 * 32) { 128 }
        val r = BlurDetector.analyze(flat, 32, 32)
        assertTrue(r.isBlurry)
    }

    /** A high-contrast checkerboard has strong edges → sharp. */
    @Test
    fun `checkerboard is sharp`() {
        val checker = IntArray(32 * 32) { i ->
            val x = i % 32
            val y = i / 32
            if ((x + y) % 2 == 0) 0 else 255
        }
        val r = BlurDetector.analyze(checker, 32, 32)
        assertFalse(r.isBlurry)
        assertTrue(r.variance > BlurDetector.DEFAULT_BLUR_THRESHOLD)
    }

    /** Dark night-shot guard: a dark frame with mild texture is NOT over-flagged. */
    @Test
    fun `dark textured frame uses conservative threshold`() {
        val dark = IntArray(32 * 32) { i ->
            val x = i % 32
            // low overall luma, small local variation
            (10 + (x % 3) * 6)
        }
        val r = BlurDetector.analyze(dark, 32, 32)
        assertTrue("dark frame mean luma should be low", r.meanLuma < 40.0)
    }
}
