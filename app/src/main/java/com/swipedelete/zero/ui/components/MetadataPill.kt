package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.scanner.VideoMeta
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.util.resolutionClass
import com.swipedelete.zero.ui.util.toDurationLabel
import com.swipedelete.zero.ui.util.toFpsLabel
import com.swipedelete.zero.ui.util.toReadableSize
import java.util.Locale

/** ≥1 GB or ≥25 Mbps or 4K marks a video as a major storage-reclaim target. */
private const val HIGH_IMPACT_BYTES = 1L shl 30
private const val HIGH_IMPACT_BITRATE = 25_000_000L

/**
 * Glassmorphic bottom metadata pill. Videos read like a spec sheet
 * ("2.4 GB • 4K 60fps • HEVC • 2:41"); images show size, dimensions and
 * megapixels. High-bitrate / 4K / >1 GB videos get a coral flame accent to
 * spotlight the storage-reclaim win of trashing them.
 */
@Composable
fun MetadataPill(
    item: MediaItem,
    modifier: Modifier = Modifier,
    videoMeta: VideoMeta? = null,
) {
    val parts = buildList {
        add(item.sizeBytes.toReadableSize())
        if (item.isVideo) {
            val res = resolutionClass(item.width, item.height).takeIf { it != "—" }
            val fps = videoMeta?.frameRate?.toFpsLabel()
            listOfNotNull(res, fps).joinToString(" ").takeIf { it.isNotBlank() }?.let(::add)
            videoMeta?.codec?.let(::add)
            if (item.durationMillis > 0) add(item.durationMillis.toDurationLabel())
        } else {
            if (item.width > 0) add(item.resolutionLabel)
            if (item.megapixels >= 0.1) add(String.format(Locale.US, "%.1f MP", item.megapixels))
        }
    }

    val highImpact = item.isVideo && (
        item.sizeBytes >= HIGH_IMPACT_BYTES ||
            (videoMeta?.bitrateBps ?: 0) >= HIGH_IMPACT_BITRATE ||
            resolutionClass(item.width, item.height) == "4K"
        )
    val borderColor = if (highImpact) SdzColor.Amber.copy(alpha = 0.75f) else SdzColor.Hairline

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        SdzColor.Surface0.copy(alpha = 0.72f),
                        SdzColor.Surface1.copy(alpha = 0.62f),
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (highImpact) {
            Icon(
                Icons.Rounded.Whatshot,
                contentDescription = "Large storage impact",
                tint = SdzColor.Amber,
                modifier = Modifier.size(14.dp),
            )
        }
        parts.forEachIndexed { index, text ->
            Text(
                text = text,
                color = when {
                    index == 0 && highImpact -> SdzColor.Amber
                    index == 0 -> SdzColor.TextSecondary
                    else -> SdzColor.Phosphor
                },
                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
