package com.swipedelete.zero.domain.model

/** The category of a deck; also selects the UI (single-card vs. dual-card split). */
enum class DeckKind {
    TIME_MACHINE,
    HEAVY_HITTERS,
    CLUTTER_HOTSPOT,
    DUPLICATES,
    BLURRY,
    SCREENSHOTS;

    /**
     * Only DUPLICATES use the top/bottom A-vs-B comparison screen — a duplicate
     * has a natural partner to weigh against. BLURRY shots have no comparison
     * partner, so they flow through the standard single-card swipe engine
     * (swipe left to trash the soft frame).
     */
    val isComparison: Boolean
        get() = this == DUPLICATES
}

/** Max items per deck — enforced everywhere to bound cognitive load & memory. */
const val MAX_DECK_SIZE = 50

/**
 * A bite-sized, ≤50-item queue of media presented to the user.
 *
 * [id] is a stable, deterministic string (e.g. "time:2024-07", "heavy:video",
 * "hotspot:screenshots") so a resumed session can be matched back to its deck.
 */
data class Deck(
    val id: String,
    val kind: DeckKind,
    val title: String,
    val subtitle: String,
    val items: List<MediaItem>,
    /** How many of [items] have already been swiped in a resumed session. */
    val completedCount: Int = 0,
) {
    val totalCount: Int get() = items.size
    val remainingCount: Int get() = (totalCount - completedCount).coerceAtLeast(0)
    val progress: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
    val reclaimableBytes: Long get() = items.sumOf { it.sizeBytes }
}

/**
 * A single A-vs-B comparison used by the duplicate/blurry split screen. The
 * [primary] is the algorithm's suggested "keeper"; the UI still lets the user
 * override with any of the four actions.
 */
data class ComparisonPair(
    val primary: MediaItem,
    val secondary: MediaItem,
) {
    val sharperItem: MediaItem?
        get() {
            val a = primary.sharpnessScore ?: return null
            val b = secondary.sharpnessScore ?: return null
            return if (a >= b) primary else secondary
        }

    val higherResItem: MediaItem
        get() = if (primary.megapixels >= secondary.megapixels) primary else secondary

    val smallerItem: MediaItem
        get() = if (primary.sizeBytes <= secondary.sizeBytes) primary else secondary
}
