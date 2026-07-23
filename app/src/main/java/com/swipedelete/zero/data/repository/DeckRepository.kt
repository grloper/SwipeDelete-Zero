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

    suspend fun getDecks(forceRefresh: Boolean = false): List<Deck> {
        val result = scan(forceRefresh)
        // Merge persisted progress so the dashboard shows accurate rings.
        return result.decks.map { deck ->
            val session = sessionDao.get(deck.id)
            if (session != null) deck.copy(completedCount = session.cursor) else deck
        }
    }

    suspend fun getDeck(deckId: String): Deck? =
        getDecks().firstOrNull { it.id == deckId }

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
