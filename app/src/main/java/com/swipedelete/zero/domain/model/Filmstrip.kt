package com.swipedelete.zero.domain.model

/**
 * Pure math for the 10-slot video filmstrip scrubber: slot timestamps are the
 * midpoints of ten equal segments, so the strip samples the whole timeline
 * instead of clustering at the start.
 */
object Filmstrip {
    const val SLOT_COUNT = 10

    /** Thumbnail decode bounds — tiny on purpose (~100 KB per strip in memory). */
    const val THUMB_WIDTH = 96
    const val THUMB_HEIGHT = 54

    /** Midpoint timestamp of each of [slots] equal segments across [durationMillis]. */
    fun timestampsMillis(durationMillis: Long, slots: Int = SLOT_COUNT): List<Long> {
        if (durationMillis <= 0 || slots <= 0) return emptyList()
        return (0 until slots).map { i -> durationMillis * (2L * i + 1) / (2L * slots) }
    }

    /** Horizontal fraction on the strip -> playback position, clamped to the timeline. */
    fun fractionToPositionMillis(fraction: Float, durationMillis: Long): Long =
        (fraction.coerceIn(0f, 1f) * durationMillis).toLong()

    /** Which slot the finger is over, clamped to valid indices. */
    fun slotIndexFor(fraction: Float, slots: Int = SLOT_COUNT): Int =
        (fraction * slots).toInt().coerceIn(0, slots - 1)
}
