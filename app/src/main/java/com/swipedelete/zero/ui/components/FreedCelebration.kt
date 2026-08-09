package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.swipedelete.zero.ui.haptics.rememberSdzHaptics
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzMotion
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize
import kotlinx.coroutines.delay

/**
 * The payoff.
 *
 * A swipe-to-clean app's entire appeal is watching space come back, and the
 * previous build had no moment for it anywhere — not at deck complete, not
 * after a purge. This is that moment: the freed figure counts *up* from zero
 * in the display face, a strip of film cells fills left-to-right beneath it,
 * and the haptics tick along with the count before landing on a confirm.
 *
 * The number uses tabular figures so it does not reflow while it counts, and
 * the whole block is an assertive live region so a screen reader announces the
 * result rather than silently skipping the celebration.
 */
@Composable
fun FreedCelebration(
    freedBytes: Long,
    modifier: Modifier = Modifier,
    fileCount: Int = 0,
    onFinished: () -> Unit = {},
) {
    val haptics = rememberSdzHaptics()
    val progress = remember(freedBytes) { Animatable(0f) }
    val markScale = remember(freedBytes) { Animatable(0.7f) }

    LaunchedEffect(freedBytes) {
        markScale.animateTo(1f, SdzMotion.settle())
        // Tick while the counter climbs — the sound of a number going up.
        val ticks = 6
        repeat(ticks) { i ->
            delay((SdzMotion.Celebration / ticks).toLong())
            haptics.progressTick()
            progress.snapTo((i + 1f) / ticks)
        }
        progress.animateTo(1f, tween(SdzMotion.Quick))
        haptics.celebrate()
        delay(400)
        onFinished()
    }

    val shown = (freedBytes * progress.value).toLong()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SdzSpace.xl)
            .semantics {
                liveRegion = LiveRegionMode.Assertive
                contentDescription = "Freed ${freedBytes.toReadableSize()} from $fileCount files"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SdzSpace.md),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = markScale.value
                    scaleY = markScale.value
                },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = SdzIcons.LogoMark,
                contentDescription = null,
                tint = SdzColor.Amber,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "You freed",
            style = SdzType.Overline,
            color = SdzColor.TextSecondary,
        )
        Text(
            text = shown.toReadableSize(),
            style = SdzType.HeroNumber,
            color = SdzColor.Amber,
            textAlign = TextAlign.Center,
        )
        if (fileCount > 0) {
            Text(
                text = if (fileCount == 1) "1 file removed" else "$fileCount files removed",
                style = SdzType.Body,
                color = SdzColor.TextSecondary,
            )
        }

        // The strip fills as the number climbs — the same film-cell language as
        // the storage meter, so "space coming back" looks like the inverse of
        // "space filling up".
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val cells = 24
            val gap = 3f
            val cellWidth = (size.width - gap * (cells - 1)) / cells
            val filled = progress.value * cells
            for (i in 0 until cells) {
                drawRoundRect(
                    color = if (i < filled) SdzColor.Amber else SdzColor.Track,
                    topLeft = Offset(i * (cellWidth + gap), 0f),
                    size = Size(cellWidth, size.height),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
}

/** Full-screen variant shown when a deck is finished. */
@Composable
fun DeckCompleteCelebration(
    freedBytes: Long,
    fileCount: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SdzColor.Surface0),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SdzSpace.xl),
        ) {
            FreedCelebration(freedBytes = freedBytes, fileCount = fileCount)
            Text(
                text = "Deck complete",
                style = SdzType.Subtitle,
                color = SdzColor.Phosphor,
            )
            SdzButton(
                label = "Back to library",
                onClick = onDone,
                style = SdzButtonStyle.Primary,
            )
        }
    }
}
