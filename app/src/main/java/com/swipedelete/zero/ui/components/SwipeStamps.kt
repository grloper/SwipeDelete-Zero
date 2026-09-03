package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType

/**
 * The in-drag decision preview.
 *
 * The dominant gesture's stamp is rendered **large and centered** over the
 * card — the word and icon silhouette carry the meaning (never colour alone),
 * and the stamp grows as the drag nears its commit threshold, so the imminent
 * outcome is impossible to miss without the user having to glance to a corner.
 *
 * Each stamp only shows when its direction is the one being dragged:
 * - dragging left  → big red "DELETE" stamp
 * - dragging right → big blue "KEEP" stamp
 * - dragging up    → big teal "ARCHIVE" stamp
 */
@Composable
fun SwipeStamps(
    leftGlow: Float,
    rightGlow: Float,
    upGlow: Float,
    modifier: Modifier = Modifier,
    archiveLabel: String = "ARCHIVE",
) {
    Box(
        modifier = modifier.padding(SdzSpace.md),
        contentAlignment = Alignment.Center,
    ) {
        CenteredStamp(
            text = "DELETE",
            icon = SdzIcons.Delete,
            color = SdzColor.Red,
            glow = leftGlow,
        )
        CenteredStamp(
            text = "KEEP",
            icon = SdzIcons.Keep,
            color = SdzColor.Azure,
            glow = rightGlow,
        )
        CenteredStamp(
            text = archiveLabel,
            icon = SdzIcons.Archive,
            color = SdzColor.Teal,
            glow = upGlow,
        )
    }
}

/**
 * One centered stamp. [glow] is the drag progress toward that direction's
 * commit threshold (0..1); the stamp fades in past a small floor and scales
 * up as the drag strengthens.
 */
@Composable
private fun CenteredStamp(
    text: String,
    icon: Painter,
    color: Color,
    glow: Float,
) {
    // Keep a small floor invisible: the stamp should only appear once the
    // user has meaningfully committed to that direction.
    val visible by animateFloatAsState(
        targetValue = if (glow > 0.08f) ((glow - 0.08f) / 0.92f).coerceIn(0f, 1f) else 0f,
        label = "stamp-alpha",
    )
    if (visible <= 0.01f) return

    val scale by animateFloatAsState(
        targetValue = 0.82f + 0.28f * visible,
        label = "stamp-scale",
    )

    Row(
        modifier = Modifier
            .alpha(visible)
            .scale(scale)
            .clip(RoundedCornerShape(SdzRadius.lg))
            .background(SdzColor.Surface0.copy(alpha = 0.62f))
            .border(3.dp, color, RoundedCornerShape(SdzRadius.lg))
            .padding(horizontal = SdzSpace.xl, vertical = SdzSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
    ) {
        Icon(painter = icon, contentDescription = null, tint = color, modifier = Modifier.size(44.dp))
        Text(
            text = text,
            style = SdzType.Title.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            color = color,
        )
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
