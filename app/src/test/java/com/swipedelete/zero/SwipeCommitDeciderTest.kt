package com.swipedelete.zero

import com.swipedelete.zero.domain.model.SwipeCommitDecider
import com.swipedelete.zero.domain.model.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeCommitDeciderTest {

    // 1080-wide card: commit at 345.6px, fling floor at 86.4px, velocity gate 2000px/s.
    private val t = SwipeCommitDecider.Thresholds(
        horizontalCommitPx = 345.6f,
        verticalCommitPx = 537.6f,
        velocityCommitPxPerSec = 2000f,
        minFlingDistancePx = 86.4f,
    )

    @Test
    fun `positional commit right`() {
        assertEquals(
            SwipeDirection.RIGHT,
            SwipeCommitDecider.decide(400f, 0f, 0f, 0f, t),
        )
    }

    @Test
    fun `positional commit left`() {
        assertEquals(
            SwipeDirection.LEFT,
            SwipeCommitDecider.decide(-400f, -10f, 0f, 0f, t),
        )
    }

    @Test
    fun `positional commit up requires dominant vertical offset`() {
        assertEquals(
            SwipeDirection.UP,
            SwipeCommitDecider.decide(-100f, -600f, 0f, 0f, t),
        )
        // Horizontal offset dominates -> not an up commit, and X is short of
        // its own line -> spring back.
        assertNull(SwipeCommitDecider.decide(-300f, -290f, 0f, 0f, t))
    }

    @Test
    fun `fast fling commits early`() {
        assertEquals(
            SwipeDirection.RIGHT,
            SwipeCommitDecider.decide(120f, 0f, 3000f, 0f, t),
        )
        assertEquals(
            SwipeDirection.LEFT,
            SwipeCommitDecider.decide(-120f, 0f, -3000f, 0f, t),
        )
        assertEquals(
            SwipeDirection.UP,
            SwipeCommitDecider.decide(0f, -120f, 0f, -3000f, t),
        )
    }

    @Test
    fun `fling below minimum travel is rejected`() {
        assertNull(SwipeCommitDecider.decide(40f, 0f, 3000f, 0f, t))
    }

    @Test
    fun `slow release short of the line springs back`() {
        assertNull(SwipeCommitDecider.decide(200f, 0f, 500f, 0f, t))
    }

    @Test
    fun `velocity sign must match offset sign`() {
        // Fast rightward velocity while the card sits left of centre: no commit.
        assertNull(SwipeCommitDecider.decide(-120f, 0f, 3000f, 0f, t))
    }

    @Test
    fun `dominant axis of velocity picks the gesture`() {
        // Mostly-vertical fling with some horizontal drift commits UP.
        assertEquals(
            SwipeDirection.UP,
            SwipeCommitDecider.decide(-100f, -150f, -1000f, -2500f, t),
        )
    }

    @Test
    fun `downward fling never commits`() {
        assertNull(SwipeCommitDecider.decide(0f, 0f, 0f, 3000f, t))
    }
}
