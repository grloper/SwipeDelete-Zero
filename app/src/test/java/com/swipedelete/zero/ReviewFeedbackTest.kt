package com.swipedelete.zero

import com.swipedelete.zero.domain.feedback.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReviewFeedbackTest {
    @Test fun `failed and cancelled writes remain silent and retry confirms once`() = runTest {
        val played = mutableListOf<ReviewFeedback>()
        val feedback = ActionFeedback({ true }, played::add)
        try { feedback.afterCommit(ReviewFeedback.STAGED) { error("database unavailable") } } catch (_: IllegalStateException) {}
        try { feedback.afterCommit(ReviewFeedback.UNDONE) { throw CancellationException("screen left") } } catch (_: CancellationException) {}
        assertTrue(played.isEmpty())
        feedback.afterCommit(ReviewFeedback.STAGED) {}
        assertEquals(listOf(ReviewFeedback.STAGED), played)
    }
    @Test fun `mute is read per action and playback failure never reverses a commit`() = runTest {
        var enabled = false
        var commits = 0
        val played = mutableListOf<ReviewFeedback>()
        val feedback = ActionFeedback({ enabled }, played::add)
        feedback.afterCommit(ReviewFeedback.KEPT) { commits++ }
        enabled = true
        feedback.afterCommit(ReviewFeedback.UNDONE) { commits++ }
        assertEquals(2, commits)
        assertEquals(listOf(ReviewFeedback.UNDONE), played)
        ActionFeedback({ true }, { error("audio unavailable") }).afterCommit(ReviewFeedback.KEPT) { commits++ }
        assertEquals(3, commits)
    }
    @Test fun `delayed write blocks mixed gestures and deck change until committed`() = runTest {
        val gate = ReviewActionGate()
        val pending = CompletableDeferred<Unit>()
        var confirmed = 0
        assertTrue(gate.enter())
        val writer = launch {
            try { ActionFeedback({ true }, { confirmed++ }).afterCommit(ReviewFeedback.STAGED) { pending.await() } }
            finally { gate.leave() }
        }
        assertFalse(gate.enter()) // second swipe, comparison tap, undo or deck change
        assertEquals(0, confirmed)
        pending.complete(Unit)
        writer.join()
        assertEquals(1, confirmed)
        assertTrue(gate.enter())
        gate.leave()
    }
}
