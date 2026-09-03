package com.swipedelete.zero.domain.model

/** The category of a deck; also selects the UI (single-card vs. dual-card split). */
enum class DeckKind {
    TIME_MACHINE,
    HEAVY_HITTERS,
    CAMERA_VIDEOS,
    CLUTTER_HOTSPOT,
    DUPLICATES,
    BLURRY,
    SCREENSHOTS,
    /** Text screenshots, receipts, scans — words, not pictures. */
    DOCUMENTS;

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
    /**
     * Identity of the logical collection this deck belongs to. A month with 240
     * photos becomes five ≤50-card decks that all share one groupId, so the
     * dashboard can present "August 2026" once instead of five near-identical
     * "· Part N" cards while sessions stay snackable.
     */
    val groupId: String = id,
    /** Human title of the group, without any "· Part N" suffix. */
    val groupTitle: String = title,
) {
    val totalCount: Int get() = items.size
    val remainingCount: Int get() = (totalCount - completedCount).coerceAtLeast(0)
    val progress: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount

    /** Items not yet swiped. The only set a "still to review" total may use. */
    val remainingItems: List<MediaItem>
        get() = items.drop(completedCount.coerceIn(0, items.size))

    /**
     * Bytes still up for review.
     *
     * Counts and totals must be derived from the *same* set or they drift
     * apart: a bucket previously showed "0 items" beside a size left over from
     * when it held nine, because the count used the remaining items while the
     * size summed all of them forever.
     */
    val remainingBytes: Long get() = remainingItems.sumOf { it.sizeBytes }

    /** Bytes across every item in the deck, reviewed or not. */
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

/**
 * Several decks of the same logical collection, presented as one dashboard
 * entry. Totals are summed across parts; [nextDeck] is where "continue" goes.
 */
data class DeckGroup(
    val id: String,
    val kind: DeckKind,
    val title: String,
    val parts: List<Deck>,
) {
    val totalCount: Int get() = parts.sumOf { it.totalCount }
    val completedCount: Int get() = parts.sumOf { it.completedCount }
    val remainingCount: Int get() = parts.sumOf { it.remainingCount }

    /** Derived from the same items as [remainingCount] — see [Deck.remainingBytes]. */
    val remainingBytes: Long get() = parts.sumOf { it.remainingBytes }
    val totalBytes: Long get() = parts.sumOf { it.totalBytes }
    val progress: Float
        get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount

    /** First part with cards left, so the user always resumes where they stopped. */
    val nextDeck: Deck? get() = parts.firstOrNull { it.remainingCount > 0 } ?: parts.firstOrNull()

    val coverItem get() = parts.firstOrNull()?.items?.firstOrNull()

    companion object {
        /** Collapse a flat deck list into groups, preserving order. */
        fun from(decks: List<Deck>): List<DeckGroup> =
            decks.groupBy { it.groupId }.map { (groupId, parts) ->
                DeckGroup(
                    id = groupId,
                    kind = parts.first().kind,
                    title = parts.first().groupTitle,
                    parts = parts,
                )
            }
    }
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
    /**
     * Every accessor returns null unless one file is *genuinely* better on that
     * dimension.
     *
     * These previously used `>=` / `<=`, so a tie silently resolved to
     * [primary] — which meant two byte-identical duplicates were labelled
     * "Higher res" AND "Smaller" at the same time, on the same file. A badge
     * that contradicts itself is worse than no badge: it teaches the user the
     * comparison cannot be trusted, and this screen exists purely to be
     * trusted. Ties now produce no badge at all.
     */
    val sharperItem: MediaItem?
        get() {
            val a = primary.sharpnessScore ?: return null
            val b = secondary.sharpnessScore ?: return null
            // Laplacian variance is noisy; only call it if the gap is meaningful.
            if (kotlin.math.abs(a - b) < SHARPNESS_EPSILON) return null
            return if (a > b) primary else secondary
        }

    val higherResItem: MediaItem?
        get() {
            val a = primary.width.toLong() * primary.height
            val b = secondary.width.toLong() * secondary.height
            if (a <= 0L || b <= 0L) return null
            // Under ~2% apart is the same picture for the user's purposes.
            if (kotlin.math.abs(a - b).toDouble() / maxOf(a, b) < RESOLUTION_EPSILON) return null
            return if (a > b) primary else secondary
        }

    val smallerItem: MediaItem?
        get() {
            val a = primary.sizeBytes
            val b = secondary.sizeBytes
            if (a <= 0L || b <= 0L) return null
            if (kotlin.math.abs(a - b).toDouble() / maxOf(a, b) < SIZE_EPSILON) return null
            return if (a < b) primary else secondary
        }

    /** True when the two files are, as far as we can tell, interchangeable. */
    val isIndistinguishable: Boolean
        get() = sharperItem == null && higherResItem == null && smallerItem == null

    private companion object {
        const val SHARPNESS_EPSILON = 5.0
        const val RESOLUTION_EPSILON = 0.02
        const val SIZE_EPSILON = 0.02
    }
}
