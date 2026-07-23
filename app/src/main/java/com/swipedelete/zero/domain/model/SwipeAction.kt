package com.swipedelete.zero.domain.model

/** The four gesture outcomes on the Active Deck engine. */
enum class SwipeDirection {
    /** Left → move to the Staging Drawer (trash queue). */
    LEFT,

    /** Right → keep, advance without staging. */
    RIGHT,

    /** Up → star / add to the Exclusion Vault. */
    UP,

    /** No committed swipe (spring-back). */
    NONE,
}

/** A committed swipe, retained briefly so the 5-second Undo can reverse it. */
data class SwipeAction(
    val item: MediaItem,
    val direction: SwipeDirection,
    val deckId: String,
    /** Index within the deck, so Undo restores position exactly. */
    val deckIndex: Int,
)

/** How the Disk Execution Engine physically removes staged files. */
enum class ExecutionMode {
    /** MediaStore.createTrashRequest — IS_TRASHED=1, recoverable 30 days. */
    OS_TRASH_30_DAY,

    /** createDeleteRequest / SAF delete — immediate, unrecoverable. */
    PERMANENT_PURGE,
}
