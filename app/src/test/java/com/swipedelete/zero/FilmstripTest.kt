package com.swipedelete.zero

import com.swipedelete.zero.domain.model.Filmstrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmstripTest {

    @Test
    fun `ten midpoint timestamps span the timeline`() {
        val stamps = Filmstrip.timestampsMillis(10_000)
        assertEquals(10, stamps.size)
        assertEquals(500L, stamps.first())   // midpoint of segment 1
        assertEquals(9_500L, stamps.last())  // midpoint of segment 10
        assertTrue(stamps.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `zero duration yields no slots`() {
        assertTrue(Filmstrip.timestampsMillis(0).isEmpty())
    }

    @Test
    fun `fraction maps and clamps to the timeline`() {
        assertEquals(0L, Filmstrip.fractionToPositionMillis(-0.5f, 8_000))
        assertEquals(4_000L, Filmstrip.fractionToPositionMillis(0.5f, 8_000))
        assertEquals(8_000L, Filmstrip.fractionToPositionMillis(1.5f, 8_000))
    }

    @Test
    fun `slot index clamps at both edges`() {
        assertEquals(0, Filmstrip.slotIndexFor(0f))
        assertEquals(4, Filmstrip.slotIndexFor(0.45f))
        assertEquals(9, Filmstrip.slotIndexFor(1f))
        assertEquals(9, Filmstrip.slotIndexFor(2f))
    }
}
