package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * The 1px "glass" border gradient: brighter at the top (12% white) fading to
 * nearly nothing at the bottom (2% white). This single hairline is what sells
 * the elevated, back-lit-glass feel across every surface.
 */
val GlassBorderBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x1FFFFFFF), // rgba(255,255,255,0.12)
        Color(0x05FFFFFF), // rgba(255,255,255,0.02)
    )
)

/** The monospaced family used for every numeric/HUD readout in the app. */
val MonoFamily: FontFamily = FontFamily.Monospace

/**
 * Applies the standard SwipeDelete glass treatment: rounded clip, deep-obsidian
 * fill (#0D0F12), and the top-to-bottom hairline border gradient.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = SdzColors.Obsidian,
): Modifier = this
    .clip(shape)
    .background(fill)
    .border(1.dp, GlassBorderBrush, shape)

/**
 * A ready-made obsidian glass card. Content is laid out in a [BoxScope] so
 * callers can freely align children (badges, overlays, etc.).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = SdzColors.Obsidian,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.glassSurface(shape, fill), content = content)
}
