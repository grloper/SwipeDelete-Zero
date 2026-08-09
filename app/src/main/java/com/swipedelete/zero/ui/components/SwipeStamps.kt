package com.swipedelete.zero.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * The "TRASH / KEEP / STAR" rubber-stamp overlays that fade in as the card is
 * dragged, reinforcing the pending action. Alpha is driven by the same glow
 * ratios used for the edge backlights.
 */
@Composable
fun SwipeStamps(
    leftGlow: Float,
    rightGlow: Float,
    upGlow: Float,
    modifier: Modifier = Modifier,
    /** Up-swipe stamp text/color: STAR by default, CLOUD in the cloud flavor. */
    upLabel: String = "STAR",
    upColor: Color = SdzColors.StarGold,
) {
    Box(modifier = modifier.padding(24.dp)) {
        Stamp(
            text = "TRASH",
            color = SdzColors.HyperCoral,
            angle = 14f,
            alpha = leftGlow,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Stamp(
            text = "KEEP",
            color = SdzColors.ElectricEmerald,
            angle = -14f,
            alpha = rightGlow,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Stamp(
            text = upLabel,
            color = upColor,
            angle = -4f,
            alpha = upGlow,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Stamp(
    text: String,
    color: Color,
    angle: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .rotate(angle)
            .border(4.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
        )
    }
}
