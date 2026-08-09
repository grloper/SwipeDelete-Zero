package com.swipedelete.zero

import com.swipedelete.zero.domain.scanner.DeckBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotHeuristicTest {

    @Test
    fun `screenshot folders match`() {
        assertTrue(DeckBuilder.isScreenshotOrReceipt("Pictures/Screenshots/", "IMG_0001.png"))
        assertTrue(DeckBuilder.isScreenshotOrReceipt("DCIM/Screenshots/", "shot.jpg"))
    }

    @Test
    fun `screenshot filenames match anywhere`() {
        assertTrue(DeckBuilder.isScreenshotOrReceipt("Download/", "Screenshot_20240501-101112.png"))
    }

    @Test
    fun `receipts and scans match by name`() {
        assertTrue(DeckBuilder.isScreenshotOrReceipt("Download/", "hotel-receipt.pdf.jpg"))
        assertTrue(DeckBuilder.isScreenshotOrReceipt(null, "Invoice-2024-004.png"))
        assertTrue(DeckBuilder.isScreenshotOrReceipt("Documents/", "scan_0093.jpg"))
    }

    @Test
    fun `camera photos do not match`() {
        assertFalse(DeckBuilder.isScreenshotOrReceipt("DCIM/Camera/", "IMG_20240501_101112.jpg"))
        assertFalse(DeckBuilder.isScreenshotOrReceipt(null, "PXL_20240501_101112.mp4"))
    }
}
