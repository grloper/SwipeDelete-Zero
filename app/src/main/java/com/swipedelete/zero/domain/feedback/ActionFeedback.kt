package com.swipedelete.zero.domain.feedback

/** Local review confirmations, never remote-backup or deletion confirmations. */
enum class ReviewFeedback(val frequencyHz: Double) {
    KEPT(660.0), STAGED(440.0), QUEUED(550.0), STARRED(740.0), UNDONE(330.0)
}

/** Audio failure must never turn a committed operation into a reported failure. */
class ActionFeedback(private val enabled: () -> Boolean, private val play: (ReviewFeedback) -> Unit) {
    suspend fun afterCommit(event: ReviewFeedback, operation: suspend () -> Unit) {
        operation()
        committed(event)
    }
    fun committed(event: ReviewFeedback) {
        if (enabled()) runCatching { play(event) }
    }
}
