package com.swipedelete.zero.ui.navigation

/** Type-safe-ish route table for the single-activity Compose nav graph. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val STAGING = "staging"
    const val SETTINGS = "settings"

    private const val DECK_ARG = "deckId"
    const val SWIPE_ENGINE = "deck/{$DECK_ARG}"
    const val DUAL_CARD = "compare/{$DECK_ARG}"

    fun swipeEngine(deckId: String) = "deck/$deckId"
    fun dualCard(deckId: String) = "compare/$deckId"

    const val ARG_DECK_ID = DECK_ARG
}
