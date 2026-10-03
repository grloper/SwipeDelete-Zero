package com.swipedelete.zero.domain.feedback

/** Shared admission gate for swipes, undo and deck changes; reject overlapping gestures. */
class ReviewActionGate {
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    fun enter(): Boolean = busy.compareAndSet(false, true)
    fun leave() { busy.set(false) }
    fun isBusy(): Boolean = busy.get()
}
