package com.swipedelete.zero.domain.model

import java.util.Locale

/**
 * What a file *is*, for presentation purposes.
 *
 * A screenshot of a text conversation used to be sorted silently into a
 * date-based photo deck and shown exactly like a holiday snapshot, which makes
 * the deck feel broken — the user is asked to make a photographic judgement
 * about something that is not a photograph. Classifying it lets the card say
 * what it is, and lets it have a bucket of its own.
 */
enum class MediaClass {
    PHOTO,
    VIDEO,
    SCREENSHOT,
    /** A screenshot that is predominantly text: a chat, a receipt, a page. */
    DOCUMENT;

    /** Shown on the card when the item is not an ordinary photograph. */
    val badge: String?
        get() = when (this) {
            PHOTO, VIDEO -> null
            SCREENSHOT -> "Screenshot"
            DOCUMENT -> "Text / document"
        }

    companion object {
        /**
         * Pure classification so it can be unit-tested.
         *
         * [isLikelyText] comes from [com.swipedelete.zero.domain.algorithm.TextDetector]
         * against the cached analysis; it is null when the file has not been
         * analysed yet, in which case a screenshot stays a plain SCREENSHOT
         * rather than being guessed at.
         */
        fun of(
            type: MediaType,
            relativePath: String?,
            displayName: String,
            isLikelyText: Boolean? = null,
        ): MediaClass {
            if (type == MediaType.VIDEO) return VIDEO
            val looksLikeScreenshot = isScreenshotPath(relativePath, displayName)
            val looksLikeDoc = isDocumentName(displayName)
            return when {
                looksLikeDoc -> DOCUMENT
                looksLikeScreenshot && isLikelyText == true -> DOCUMENT
                looksLikeScreenshot -> SCREENSHOT
                else -> PHOTO
            }
        }

        fun isScreenshotPath(relativePath: String?, displayName: String): Boolean {
            val path = (relativePath ?: "").lowercase(Locale.US)
            val name = displayName.lowercase(Locale.US)
            return path.contains("screenshot") || name.startsWith("screenshot")
        }

        fun isDocumentName(displayName: String): Boolean {
            val name = displayName.lowercase(Locale.US)
            return name.contains("receipt") || name.contains("invoice") ||
                name.contains("scan_") || name.contains("statement")
        }
    }
}
