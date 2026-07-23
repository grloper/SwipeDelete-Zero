package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toDurationLabel
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Glassmorphic bottom metadata pill: size · resolution · date · duration.
 * Sits over the media preview with a translucent gradient scrim so text stays
 * legible against any photo.
 */
@Composable
fun MetadataPill(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val parts = buildList {
        add(item.sizeBytes.toReadableSize())
        if (item.width > 0) add(item.resolutionLabel)
        if (item.durationMillis > 0) add(item.durationMillis.toDurationLabel())
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        SdzColors.PitchBlack.copy(alpha = 0.72f),
                        SdzColors.Obsidian.copy(alpha = 0.62f),
                    )
                )
            )
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        parts.forEachIndexed { index, text ->
            Text(
                text = text,
                color = if (index == 0) SdzColors.CrispCyan else SdzColors.PureWhite,
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
    }
}
