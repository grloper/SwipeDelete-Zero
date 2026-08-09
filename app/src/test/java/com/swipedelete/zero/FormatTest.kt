package com.swipedelete.zero

import com.swipedelete.zero.domain.scanner.VideoMetadataExtractor
import com.swipedelete.zero.ui.util.resolutionClass
import com.swipedelete.zero.ui.util.toBitrateLabel
import com.swipedelete.zero.ui.util.toFpsLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `resolution class is orientation agnostic`() {
        assertEquals("4K", resolutionClass(3840, 2160))
        assertEquals("4K", resolutionClass(2160, 3840))
        assertEquals("1440p", resolutionClass(2560, 1440))
        assertEquals("1080p", resolutionClass(1920, 1080))
        assertEquals("720p", resolutionClass(720, 1280))
        assertEquals("SD", resolutionClass(640, 480))
        assertEquals("—", resolutionClass(0, 0))
    }

    @Test
    fun `fps label rounds`() {
        assertEquals("60fps", 59.94f.toFpsLabel())
        assertEquals("30fps", 30f.toFpsLabel())
        assertEquals("24fps", 23.976f.toFpsLabel())
    }

    @Test
    fun `bitrate label picks sensible units`() {
        assertEquals("48 Mbps", 48_000_000L.toBitrateLabel())
        assertEquals("820 Kbps", 820_000L.toBitrateLabel())
        assertEquals("", 0L.toBitrateLabel())
    }

    @Test
    fun `codec mime mapping`() {
        assertEquals("HEVC", VideoMetadataExtractor.codecDisplayName("video/hevc"))
        assertEquals("H.264", VideoMetadataExtractor.codecDisplayName("video/avc"))
        assertEquals("AV1", VideoMetadataExtractor.codecDisplayName("video/av01"))
        assertEquals("VP9", VideoMetadataExtractor.codecDisplayName("video/x-vnd.on2.vp9"))
        assertEquals("WEIRD", VideoMetadataExtractor.codecDisplayName("video/weird"))
    }
}
