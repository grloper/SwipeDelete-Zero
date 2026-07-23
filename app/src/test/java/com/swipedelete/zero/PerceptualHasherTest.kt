package com.swipedelete.zero

import com.swipedelete.zero.domain.algorithm.PerceptualHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHasherTest {

    private fun gradient(size: Int, shift: Int = 0): IntArray =
        IntArray(size * size) { i ->
            val x = i % size
            ((x + shift) * 255 / size).coerceIn(0, 255)
        }

    @Test
    fun `identical matrices hash identically`() {
        val a = gradient(32)
        val b = gradient(32)
        assertEquals(PerceptualHasher.dHash(a, 32, 32), PerceptualHasher.dHash(b, 32, 32))
        assertEquals(PerceptualHasher.pHash(a, 32, 32), PerceptualHasher.pHash(b, 32, 32))
    }

    @Test
    fun `hamming distance of identical hashes is zero`() {
        val a = gradient(32)
        val h = PerceptualHasher.dHash(a, 32, 32)
        assertEquals(0, PerceptualHasher.hammingDistance(h, h))
    }

    @Test
    fun `near-duplicate shift stays within threshold`() {
        val original = gradient(32, shift = 0)
        val shifted = gradient(32, shift = 1)
        val d = PerceptualHasher.hammingDistance(
            PerceptualHasher.dHash(original, 32, 32),
            PerceptualHasher.dHash(shifted, 32, 32),
        )
        assertTrue("near-dup distance should be small, was $d", d <= 8)
    }

    @Test
    fun `structurally opposite images differ substantially`() {
        val ascending = IntArray(32 * 32) { (it % 32) * 8 }
        val descending = IntArray(32 * 32) { (31 - (it % 32)) * 8 }
        val d = PerceptualHasher.hammingDistance(
            PerceptualHasher.dHash(ascending, 32, 32),
            PerceptualHasher.dHash(descending, 32, 32),
        )
        assertTrue("opposite gradients should differ, was $d", d > 20)
    }
}
