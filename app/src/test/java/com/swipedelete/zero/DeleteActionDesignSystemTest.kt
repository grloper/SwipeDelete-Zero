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
        assertEquals(Color(0xFFFF453A), SdzColor.Red)
        assertEquals(Color(0x33FF453A), SdzColor.RedDim)
    }

    @Test
    fun `DeleteRed has strong red component`() {
        assertTrue(SdzColor.Red.red > 0.9f)
        assertTrue(SdzColor.Red.green < 0.4f)
        assertTrue(SdzColor.Red.blue < 0.4f)
    }
}
