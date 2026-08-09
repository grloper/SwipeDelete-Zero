package com.swipedelete.zero.domain.model

/**
 * How worried the user should be about free space.
 *
 * Kept in the domain layer and free of Android types so the thresholds are
 * unit-testable — this is the single most motivating fact in the app, and it
 * previously rendered as an unlabelled grey sliver with no urgency at all.
 */
enum class StorageUrgency {
    /** Plenty of room. Nothing to shout about. */
    COMFORTABLE,

    /** Getting tight — worth a session. */
    FILLING,

    /** Effectively out of space; things are already failing for the user. */
    CRITICAL;

    /** A short status word shown next to the meter, so urgency is readable as text too. */
    val label: String
        get() = when (this) {
            COMFORTABLE -> "Comfortable"
            FILLING -> "Filling up"
            CRITICAL -> "Critically full"
        }

    companion object {
        /**
         * Thresholds by *fraction used*. 85% is where photo capture and OTA
         * updates start failing on most devices, so that is where the tone
         * changes; 93% is where the OS itself begins warning.
         */
        fun of(usedFraction: Float): StorageUrgency = when {
            usedFraction >= 0.93f -> CRITICAL
            usedFraction >= 0.85f -> FILLING
            else -> COMFORTABLE
        }

        fun of(usedBytes: Long, totalBytes: Long): StorageUrgency =
            if (totalBytes <= 0L) COMFORTABLE else of(usedBytes.toFloat() / totalBytes)
    }
}
