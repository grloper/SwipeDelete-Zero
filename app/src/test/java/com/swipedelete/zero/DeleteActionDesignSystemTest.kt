package com.swipedelete.zero

import androidx.compose.ui.graphics.Color
import com.swipedelete.zero.ui.theme.SdzColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteActionDesignSystemTest {

    @Test
    fun `DeleteRed is distinct from Amber and Azure`() {
        assertNotEquals(SdzColor.Red, SdzColor.Amber)
        assertNotEquals(SdzColor.Red, SdzColor.Azure)
        assertNotEquals(SdzColor.Red, SdzColor.Teal)
        assertEquals(Color(0xFFEF8B82), SdzColor.Red)
        assertEquals(Color(0x33EF8B82), SdzColor.RedDim)
    }

    @Test
    fun `Error remains visibly red and contrasts with studio surfaces`() {
        assertTrue(SdzColor.Red.red > 0.9f)
        fun luminance(color: Color): Double {
            fun channel(value: Float): Double = if (value <= 0.04045f) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
            return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        }
        assertTrue((luminance(SdzColor.Red) + 0.05) / (luminance(SdzColor.Surface1) + 0.05) >= 4.5)
        assertTrue(SdzColor.Red.red - SdzColor.Red.green > 0.35f)
        assertTrue(SdzColor.Red.red - SdzColor.Red.blue > 0.35f)
    }
}
