package com.swipedelete.zero

import android.net.Uri
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.DeckGroup
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class DeckGroupTest {

    private fun item(id: Long, size: Long) = MediaItem(
        id = id,
        contentUri = mock(Uri::class.java),
        displayName = "f$id.jpg",
        mimeType = "image/jpeg",
        type = MediaType.IMAGE,
        sizeBytes = size,
        dateAddedMillis = id,
    )

    private fun deck(id: String, groupId: String, items: List<MediaItem>, completed: Int = 0) =
        Deck(
            id = id,
            kind = DeckKind.TIME_MACHINE,
            title = "August 2026",
            subtitle = "",
            items = items,
            completedCount = completed,
            groupId = groupId,
            groupTitle = "August 2026",
        )

    @Test
    fun `parts of one month collapse into a single group`() {
        val decks = listOf(
            deck("time:202607", "time:202607", listOf(item(1, 100), item(2, 200))),
            deck("time:202607:1", "time:202607", listOf(item(3, 300))),
            deck("time:202606", "time:202606", listOf(item(4, 400))),
        )
        val groups = DeckGroup.from(decks)
        assertEquals(2, groups.size)
        assertEquals(3, groups[0].totalCount)
        assertEquals(600L, groups[0].remainingBytes)
        assertEquals("August 2026", groups[0].title)
    }

    @Test
    fun `progress aggregates across parts`() {
        val groups = DeckGroup.from(
            listOf(
                deck("a", "g", List(50) { item(it.toLong(), 10) }, completed = 50),
                deck("a:1", "g", List(50) { item(100L + it, 10) }, completed = 25),
            )
        )
        val group = groups.single()
        assertEquals(100, group.totalCount)
        assertEquals(75, group.completedCount)
        assertEquals(25, group.remainingCount)
        assertEquals(0.75f, group.progress, 0.001f)
    }

    @Test
    fun `nextDeck resumes at the first unfinished part`() {
        val first = deck("a", "g", List(2) { item(it.toLong(), 10) }, completed = 2)
        val second = deck("a:1", "g", List(2) { item(10L + it, 10) }, completed = 1)
        val group = DeckGroup.from(listOf(first, second)).single()
        assertSame(second, group.nextDeck)
    }

    @Test
    fun `fully finished group still offers a deck to reopen`() {
        val only = deck("a", "g", listOf(item(1, 10)), completed = 1)
        assertSame(only, DeckGroup.from(listOf(only)).single().nextDeck)
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<DeckGroup>(), DeckGroup.from(emptyList()))
    }
}
