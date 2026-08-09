package com.swipedelete.zero.domain.model

import kotlin.math.abs

/**
 * Pure decision table for when a released drag commits a swipe.
 *
 * Two independent ways to commit:
 *  1. **Positional** — the card was dragged past the commit line
 *     (identical to the classic behaviour).
 *  2. **Velocity fling** — the finger was moving fast enough on release, even
 *     well short of the commit line, as long as the card already travelled a
 *     minimum distance in that direction (rejects twitch taps) and the
 *     velocity points the same way as the offset.
 *
 * Kept free of Android/Compose types so the whole matrix is unit-testable.
 */
object SwipeCommitDecider {

    data class Thresholds(
        /** Positional commit line on the X axis, px. */
        val horizontalCommitPx: Float,
        /** Positional commit line for the up-swipe, px (positive magnitude). */
        val verticalCommitPx: Float,
        /** Speed at which a fling commits regardless of position, px/s. */
        val velocityCommitPxPerSec: Float,
        /** Minimum travel on the fling axis before velocity may commit, px. */
        val minFlingDistancePx: Float,
    )

    /** Returns the committed direction, or null when the card should spring back. */
    fun decide(
        offsetX: Float,
        offsetY: Float,
        velocityX: Float,
        velocityY: Float,
        t: Thresholds,
    ): SwipeDirection? {
        // 1. Positional commits — up wins only when clearly the dominant axis.
        if (offsetY < -t.verticalCommitPx && abs(offsetY) > abs(offsetX)) return SwipeDirection.UP
        if (offsetX > t.horizontalCommitPx) return SwipeDirection.RIGHT
        if (offsetX < -t.horizontalCommitPx) return SwipeDirection.LEFT

        // 2. Velocity commits — dominant axis of the *velocity* decides which
        //    gesture the user meant; sign must agree with the travelled offset.
        if (abs(velocityY) > abs(velocityX)) {
            if (velocityY < -t.velocityCommitPxPerSec && offsetY < -t.minFlingDistancePx) {
                return SwipeDirection.UP
            }
        } else {
            if (velocityX > t.velocityCommitPxPerSec && offsetX > t.minFlingDistancePx) {
                return SwipeDirection.RIGHT
            }
            if (velocityX < -t.velocityCommitPxPerSec && offsetX < -t.minFlingDistancePx) {
                return SwipeDirection.LEFT
            }
        }
        return null
    }
}
