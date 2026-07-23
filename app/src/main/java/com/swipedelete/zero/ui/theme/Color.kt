package com.swipedelete.zero.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OLED "Pitch-Black" design tokens for SwipeDelete Zero.
 *
 * These are the single source of truth for the Material 3 Expressive palette.
 * Backgrounds are true #000000 so that on OLED panels the pixels are physically
 * off — maximising contrast for the neon accent system and saving battery.
 */
object SdzColors {
    /** True black canvas — pixels off on OLED. */
    val PitchBlack = Color(0xFF000000)

    /** Card & elevated surface colour. */
    val Obsidian = Color(0xFF0D0F12)

    /** 1px hairline border on surfaces (≈10% white). */
    val Hairline = Color(0x1AFFFFFF)

    /** Primary accent — "Keep" / right-swipe. */
    val ElectricEmerald = Color(0xFF00E676)

    /** Danger accent — "Trash" / left-swipe. */
    val HyperCoral = Color(0xFFFF3B30)

    /** Info / data readouts. */
    val CrispCyan = Color(0xFF00F0FF)

    /** Star / favourite / up-swipe. */
    val StarGold = Color(0xFFFFD700)

    /** Secondary / muted text. */
    val MutedGray = Color(0xFF8E95A2)

    /** Primary text on dark surfaces. */
    val PureWhite = Color(0xFFF4F6FA)
}
