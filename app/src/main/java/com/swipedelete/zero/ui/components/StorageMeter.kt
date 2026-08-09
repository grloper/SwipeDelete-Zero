package com.swipedelete.zero.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.StorageUrgency
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzMotion
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize
import kotlin.math.roundToInt

/**
 * The storage stat, given the weight it deserves.
 *
 * This is the most motivating number in the app and it used to be a flat grey
 * sliver with no label while a less actionable figure took the hero slot. Here
 * it is the hero, and its urgency is carried by four independent channels so
 * it is legible at a glance without reading a single digit:
 *
 *  1. **Colour** — calm neutral, amber, or safelight, from [StorageUrgency].
 *  2. **Proportion** — how much of the strip is filled.
 *  3. **A status word** — "Filling up" / "Critically full".
 *  4. **Motion** — the leading cell breathes, but only when critical, so the
 *     animation means something rather than decorating everything.
 *
 * The strip is drawn as discrete cells rather than a continuous bar: it echoes
 * the film-negative frames in the brand mark, and countable cells communicate
 * "how much is left" faster than a smooth gradient.
 */
@Composable
fun StorageMeter(
    usedBytes: Long,
    freeBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    /** Space already queued for reclaim, drawn as the segment about to be won back. */
    reclaimableBytes: Long = 0L,
) {
    val usedFraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val urgency = StorageUrgency.of(usedFraction)

    val urgencyColor = when (urgency) {
        StorageUrgency.COMFORTABLE -> SdzColor.UrgencyCalm
        StorageUrgency.FILLING -> SdzColor.UrgencyFilling
        StorageUrgency.CRITICAL -> SdzColor.UrgencyCritical
    }
    val animatedColor by animateColorAsState(
        urgencyColor,
        tween(SdzMotion.Expressive),
        label = "urgency-colour",
    )
    val animatedFraction by animateFloatAsState(
        usedFraction,
        tween(SdzMotion.Expressive, easing = SdzMotion.Emphasised),
        label = "urgency-fill",
    )

    // Only critical breathes. Ambient animation everywhere would make none of it
    // mean anything.
    val pulse = rememberInfiniteTransition(label = "critical-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "critical-pulse-alpha",
    )

    val percent = (usedFraction * 100).roundToInt()
    val reclaimFraction =
        if (totalBytes > 0) (reclaimableBytes.toFloat() / totalBytes).coerceIn(0f, usedFraction) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Storage $percent percent full, ${urgency.label}. " +
                        "${freeBytes.toReadableSize()} free of ${totalBytes.toReadableSize()}."
            },
        verticalArrangement = Arrangement.spacedBy(SdzSpace.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "$percent%",
                    style = SdzType.HeroNumber,
                    color = animatedColor,
                )
                Text(
                    text = urgency.label,
                    style = SdzType.Label,
                    color = animatedColor,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = freeBytes.toReadableSize(),
                    style = SdzType.StatNumber,
                    color = SdzColor.Phosphor,
                )
                Text(
                    text = "free of ${totalBytes.toReadableSize()}",
                    style = SdzType.BodySmall,
                    color = SdzColor.TextSecondary,
                )
            }
        }

        CellStrip(
            fraction = animatedFraction,
            reclaimFraction = reclaimFraction,
            fill = animatedColor,
            leadingAlpha = if (urgency == StorageUrgency.CRITICAL) pulseAlpha else 1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp),
        )
    }
}

/**
 * The film-cell strip. Cells already reclaimable are drawn in [SdzColor.Amber]
 * at the leading edge of the used run — the space that is about to cross back
 * over into free.
 */
@Composable
private fun CellStrip(
    fraction: Float,
    reclaimFraction: Float,
    fill: Color,
    leadingAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cells = 32
        val gap = 3f
        val cellWidth = (size.width - gap * (cells - 1)) / cells
        val radius = CornerRadius(2f, 2f)

        val usedCells = (fraction * cells)
        val reclaimCells = (reclaimFraction * cells)
        // Reclaimable sits at the *end* of the used run: the next thing to free.
        val reclaimStart = usedCells - reclaimCells

        for (i in 0 until cells) {
            val x = i * (cellWidth + gap)
            val isUsed = i < usedCells
            val isReclaim = reclaimCells > 0f && i >= reclaimStart && i < usedCells
            val isLeading = i.toFloat() >= usedCells - 1f && isUsed

            val color = when {
                isReclaim -> SdzColor.Amber
                isUsed -> fill
                else -> SdzColor.Track
            }
            drawRoundRect(
                color = if (isLeading && !isReclaim) color.copy(alpha = leadingAlpha) else color,
                topLeft = Offset(x, 0f),
                size = Size(cellWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}
