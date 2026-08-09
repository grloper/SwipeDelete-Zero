package com.swipedelete.zero.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipedelete.zero.ui.theme.DisplayFamily
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * The wordmark lockup.
 *
 * "SWIPEDELETE" is set in the display face at a wide tracking that echoes the
 * spacing of edge markings printed along a film strip; "ZERO" is the same size
 * but rendered in the phosphor tone at full weight so the eye lands on it —
 * the mark itself is a zero, so the word and the glyph reinforce each other.
 *
 * Mark and type are locked at a fixed ratio: the mark's height equals the
 * cap-height of the wordmark, and the gap between them is exactly half the
 * mark's width.
 */
@Composable
fun SdzWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 28.dp,
    tint: Color = SdzColor.Phosphor,
    showTagline: Boolean = false,
) {
    Row(
        modifier = modifier.semantics { contentDescription = "SwipeDelete Zero" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(markSize / 2),
    ) {
        Icon(
            painter = SdzIcons.LogoMark,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(markSize),
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "SWIPEDELETE",
                    style = TextStyle(
                        fontFamily = DisplayFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = (markSize.value * 0.5f).sp,
                        letterSpacing = (markSize.value * 0.055f).sp,
                    ),
                    color = tint.copy(alpha = 0.72f),
                )
                Text(
                    text = "ZERO",
                    style = TextStyle(
                        fontFamily = DisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (markSize.value * 0.5f).sp,
                        letterSpacing = (markSize.value * 0.055f).sp,
                    ),
                    color = tint,
                )
            }
            if (showTagline) {
                Text(
                    text = "Reclaim the negative space",
                    style = SdzType.LabelSmall,
                    color = SdzColor.TextTertiary,
                )
            }
        }
    }
}

/** The mark alone, for compact placements. */
@Composable
fun SdzLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = SdzColor.Phosphor,
) {
    Icon(
        painter = SdzIcons.LogoMark,
        contentDescription = "SwipeDelete Zero",
        tint = tint,
        modifier = modifier.size(size),
    )
}
