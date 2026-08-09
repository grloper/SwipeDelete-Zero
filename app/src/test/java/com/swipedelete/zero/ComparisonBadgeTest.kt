package com.swipedelete.zero

import android.net.Uri
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.MediaType
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * The comparison screen exists to be trusted. These lock in that a badge only
 * appears when the underlying data genuinely supports it.
 */
class ComparisonBadgeTest {

    private fun item(
        id: Long,
        size: Long = 1_000_000,
        w: Int = 4000,
        h: Int = 3000,
        sharpness: Double? = null,
    ) = MediaItem(
        id = id,
        contentUri = mock(Uri::class.java),
        displayName = "f$id.jpg",
        mimeType = "image/jpeg",
        type = MediaType.IMAGE,
        sizeBytes = size,
        dateAddedMillis = id,
        width = w,
        height = h,
        sharpnessScore = sharpness,
    )

    @Test
    fun `byte-identical files claim nothing`() {
        // The exact reported defect: two identical files were tagged both
        // "Higher Res" and "Smaller" because ties resolved to primary.
        val pair = ComparisonPair(item(1), item(2))
        assertNull(pair.higherResItem)
        assertNull(pair.smallerItem)
        assertNull(pair.sharperItem)
        assertTrue(pair.isIndistinguishable)
    }

    @Test
    fun `a genuinely larger resolution wins`() {
        val pair = ComparisonPair(item(1, w = 4000, h = 3000), item(2, w = 2000, h = 1500))
        assertSame(pair.primary, pair.higherResItem)
    }

    @Test
    fun `trivial resolution differences are ignored`() {
        // 4000x3000 vs 3990x3000 — under the 2% epsilon.
        val pair = ComparisonPair(item(1, w = 4000, h = 3000), item(2, w = 3990, h = 3000))
        assertNull(pair.higherResItem)
    }

    @Test
    fun `a genuinely smaller file wins`() {
        val pair = ComparisonPair(item(1, size = 500_000), item(2, size = 2_000_000))
        assertSame(pair.primary, pair.smallerItem)
    }

    @Test
    fun `trivial size differences are ignored`() {
        val pair = ComparisonPair(item(1, size = 1_000_000), item(2, size = 1_005_000))
        assertNull(pair.smallerItem)
    }

    @Test
    fun `sharpness needs a meaningful gap`() {
        // Within the noise floor of Laplacian variance: no claim.
        assertNull(ComparisonPair(item(1, sharpness = 100.0), item(2, sharpness = 102.0)).sharperItem)
        // Clearly sharper: the claim is made, against the same pair instance.
        val decisive = ComparisonPair(item(1, sharpness = 100.0), item(2, sharpness = 40.0))
        assertSame(decisive.primary, decisive.sharperItem)
    }

    @Test
    fun `unmeasured sharpness produces no claim`() {
        assertNull(ComparisonPair(item(1), item(2, sharpness = 90.0)).sharperItem)
    }

    @Test
    fun `badges never contradict each other`() {
        // Whatever the inputs, one file may not be both higher-res and
        // simultaneously the loser on resolution.
        val samples = listOf(
            ComparisonPair(item(1, size = 900_000, w = 4000, h = 3000), item(2, size = 2_000_000, w = 2000, h = 1500)),
            ComparisonPair(item(1), item(2)),
            ComparisonPair(item(1, w = 100, h = 100), item(2, w = 4000, h = 3000)),
        )
        samples.forEach { pair ->
            val hi = pair.higherResItem
            val small = pair.smallerItem
            if (hi != null) assertTrue(hi === pair.primary || hi === pair.secondary)
            if (small != null) assertTrue(small === pair.primary || small === pair.secondary)
        }
    }
}

/** Counts and totals must come from the same set — see Deck.remainingBytes. */
class DeckAggregateTest {

    private fun item(id: Long, size: Long) = MediaItem(
        id = id,
        contentUri = mock(Uri::class.java),
        displayName = "f$id",
        mimeType = "image/jpeg",
        type = MediaType.IMAGE,
        sizeBytes = size,
        dateAddedMillis = id,
    )

    @Test
    fun `remaining bytes shrink alongside remaining count`() {
        val items = (1..4).map { item(it.toLong(), 100) }
        val fresh = com.swipedelete.zero.domain.model.Deck(
            id = "d", kind = com.swipedelete.zero.domain.model.DeckKind.BLURRY,
            title = "t", subtitle = "s", items = items,
        )
        assertTrue(fresh.remainingCount == 4 && fresh.remainingBytes == 400L)

        val partly = fresh.copy(completedCount = 3)
        assertTrue(partly.remainingCount == 1 && partly.remainingBytes == 100L)

        // The defect: 0 items shown beside a stale non-zero size.
        val done = fresh.copy(completedCount = 4)
        assertTrue(done.remainingCount == 0)
        assertTrue("0 items must mean 0 bytes", done.remainingBytes == 0L)
        // The all-time figure is still available where it is genuinely wanted.
        assertTrue(done.totalBytes == 400L)
    }
}
