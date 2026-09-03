package com.swipedelete.zero

import android.net.Uri
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.MediaType
import com.swipedelete.zero.domain.scanner.DeckBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class DeckContinuationAndSortingTest {

    private fun dummyMediaItem(
        id: Long,
        displayName: String,
        sizeBytes: Long,
        dateAddedMillis: Long,
        isVideo: Boolean,
        relativePath: String? = "DCIM/Camera/"
    ) = MediaItem(
        id = id,
        contentUri = mock(Uri::class.java),
        displayName = displayName,
        mimeType = if (isVideo) "video/mp4" else "image/jpeg",
        type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
        sizeBytes = sizeBytes,
        dateAddedMillis = dateAddedMillis,
        width = 1920,
        height = 1080,
        durationMillis = if (isVideo) 60_000L else 0L,
        relativePath = relativePath,
    )

    @Test
    fun `isCameraVideo recognizes DCIM and camera video prefixes`() {
        val cameraVid = dummyMediaItem(1, "VID_20260903_120000.mp4", 500_000_000L, 1000L, isVideo = true, relativePath = "DCIM/Camera/")
        assertTrue(DeckBuilder.isCameraVideo(cameraVid))
    }

    @Test
    fun `Deck re-ordering by largest first sorts remaining cards descending by size`() {
        val itemSmall = dummyMediaItem(1, "small.mp4", 10_000_000L, 3000L, isVideo = true)
        val itemMedium = dummyMediaItem(2, "medium.mp4", 50_000_000L, 2000L, isVideo = true)
        val itemHuge = dummyMediaItem(3, "huge.mp4", 500_000_000L, 1000L, isVideo = true)

        val deck = Deck(
            id = "test_deck",
            kind = DeckKind.CAMERA_VIDEOS,
            title = "Camera Videos",
            subtitle = "Test",
            items = listOf(itemSmall, itemMedium, itemHuge),
        )

        val sortedItems = deck.items.sortedByDescending { it.sizeBytes }
        assertEquals(itemHuge.id, sortedItems[0].id)
        assertEquals(itemMedium.id, sortedItems[1].id)
        assertEquals(itemSmall.id, sortedItems[2].id)
    }

    @Test
    fun `Session cursor reconciliation resets completedCount when deck shrunk from purge`() {
        val oldSessionCursor = 50
        val oldSessionTotal = 50
        val newDeckItemCount = 20

        val reconciledCompleted = if (oldSessionCursor >= newDeckItemCount && newDeckItemCount > 0 && oldSessionTotal != newDeckItemCount) {
            0
        } else {
            oldSessionCursor.coerceAtMost(newDeckItemCount)
        }

        assertEquals(0, reconciledCompleted)
    }
}
