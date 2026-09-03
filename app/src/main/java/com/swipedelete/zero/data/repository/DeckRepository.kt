package com.swipedelete.zero.data.repository

import com.swipedelete.zero.data.local.DeckSessionDao
import com.swipedelete.zero.data.local.DeckSessionEntity
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.scanner.DeckBuilder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the current scan result and persists per-deck progress so a user can
 * resume a half-finished deck ("24/50 swiped in July 2024").
 *
 * The scan is cached in memory for the session; [refresh] rebuilds it (e.g. after
 * the analysis worker adds new hashes, or the user changes the Exclusion Vault).
 */
@Singleton
class DeckRepository @Inject constructor(
    private val deckBuilder: DeckBuilder,
    private val sessionDao: DeckSessionDao,
) {
    private val mutex = Mutex()
    @Volatile private var cache: DeckBuilder.ScanResult? = null

    /** Decks plus the de-duplicated totals the dashboard headline needs. */
    data class LibrarySummary(
        val decks: List<Deck>,
        val candidateBytes: Long,
        val candidateCount: Int,
    )

    suspend fun getDecks(forceRefresh: Boolean = false): List<Deck> =
        getSummary(forceRefresh).decks

    suspend fun getSummary(forceRefresh: Boolean = false): LibrarySummary {
        val result = scan(forceRefresh)
        // Merge persisted progress so the dashboard shows accurate rings. One
        // query for the whole table, not one per deck — a large library builds
        // hundreds of decks, and the per-deck lookup made every dashboard load
        // (and every deck open, which calls through here) pay that many
        // sequential round trips.
        val sessions = sessionDao.getAll().associateBy { it.deckId }
        val decks = result.decks.map { deck ->
            val session = sessions[deck.id]
            val completed = if (session != null) {
                // If items were purged/deleted such that totalCount changed and cursor exceeded,
                // or if session recorded a completion on an old batch, reset to 0 so
                // newly shifted items in this deck can be reviewed seamlessly!
                if (session.cursor >= deck.totalCount && deck.totalCount > 0 && session.totalCount != deck.totalCount) {
                    0
                } else {
                    session.cursor.coerceAtMost(deck.totalCount)
                }
            } else 0
            deck.copy(completedCount = completed)
        }
        return LibrarySummary(
            decks = decks,
            candidateBytes = result.candidateBytes,
            candidateCount = result.candidateCount,
        )
    }

    suspend fun getDeck(deckId: String): Deck? =
        getDecks().firstOrNull { it.id == deckId }

    /** Returns the next unfinished part in the same logical collection/group. */
    suspend fun getNextDeckInGroup(currentDeck: Deck): Deck? {
        val allDecks = getDecks()
        val groupDecks = allDecks.filter { it.groupId == currentDeck.groupId }
        val currentIndex = groupDecks.indexOfFirst { it.id == currentDeck.id }
        if (currentIndex >= 0 && currentIndex + 1 < groupDecks.size) {
            val next = groupDecks[currentIndex + 1]
            if (next.remainingCount > 0) return next
        }
        return groupDecks.firstOrNull { it.id != currentDeck.id && it.remainingCount > 0 }
    }

    suspend fun getComparisonPairs(deckId: String): List<ComparisonPair> =
        scan(false).comparisonDecks[deckId].orEmpty()

    suspend fun refresh(): List<Deck> = getDecks(forceRefresh = true)

    /** Persist how far the user got in a deck. */
    suspend fun saveProgress(deck: Deck, cursor: Int) {
        sessionDao.upsert(
            DeckSessionEntity(
                deckId = deck.id,
                kind = deck.kind.name,
                title = deck.title,
                cursor = cursor,
                totalCount = deck.totalCount,
                updatedAtMillis = 0L,
            )
        )
    }

    suspend fun clearProgress(deckId: String) = sessionDao.delete(deckId)

    private suspend fun scan(forceRefresh: Boolean): DeckBuilder.ScanResult =
        mutex.withLock {
            val existing = cache
            if (existing != null && !forceRefresh) return@withLock existing
            deckBuilder.buildAll().also { cache = it }
        }
}
