package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType

/**
 * The in-drag decision preview.
 *
 * Each stamp pairs its **icon silhouette** with its word, so the pending
 * outcome is legible without relying on colour at all — and the stamps appear
 * on the side of the card matching the gesture, giving position as a third
 * channel. The old version rotated bare words in a red/green pair, which is the
 * single worst colour combination for red-green colour vision deficiency.
 */
@Composable
fun SwipeStamps(
    leftGlow: Float,
    rightGlow: Float,
    upGlow: Float,
    modifier: Modifier = Modifier,
    archiveLabel: String = "ARCHIVE",
) {
    Box(modifier = modifier.padding(SdzSpace.xxl)) {
        Stamp(
            text = "DELETE",
            icon = SdzIcons.Delete,
            color = SdzColor.Red,
            alpha = leftGlow,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Stamp(
            text = "KEEP",
            icon = SdzIcons.Keep,
            color = SdzColor.Azure,
            alpha = rightGlow,
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Stamp(
            text = archiveLabel,
            icon = SdzIcons.Archive,
            color = SdzColor.Teal,
            alpha = upGlow,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Stamp(
    text: String,
    icon: Painter,
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(SdzRadius.md))
            .background(SdzColor.Surface0.copy(alpha = 0.55f))
            .border(2.dp, color, RoundedCornerShape(SdzRadius.md))
            .padding(horizontal = SdzSpace.md, vertical = SdzSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm),
    ) {
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = SdzType.Label, color = color)
    }
}

/**
 * Says what a card *is* when it is not an ordinary photograph — a screenshot,
 * or a page of text. Without this, a screenshot of a chat sits in a date deck
 * looking exactly like a holiday photo and the user is asked to judge it as
 * one.
 */
@Composable
fun MediaClassBadge(
    label: String,
    icon: Painter,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(SdzRadius.pill))
            .background(SdzColor.Surface0.copy(alpha = 0.72f))
            .padding(horizontal = SdzSpace.md, vertical = SdzSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.xs),
    ) {
        Icon(painter = icon, contentDescription = null, tint = SdzColor.Phosphor, modifier = Modifier.size(14.dp))
        Text(label, style = SdzType.LabelSmall, color = SdzColor.Phosphor)
    }
}
