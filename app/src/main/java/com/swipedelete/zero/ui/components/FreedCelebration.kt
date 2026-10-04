package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    stagedOnly: Boolean = false,
    modifier: Modifier = Modifier,
    fileCount: Int = 0,
    onFinished: () -> Unit = {},
) {
    val haptics = rememberSdzHaptics()
    val progress = remember(freedBytes) { Animatable(0f) }
    val markScale = remember(freedBytes) { Animatable(0.7f) }

    LaunchedEffect(freedBytes, stagedOnly) {
        if (stagedOnly) {
            // Review completion is a calm summary, without fake space-reclaim reward ticks.
            progress.snapTo(1f)
            markScale.snapTo(1f)
            return@LaunchedEffect
        }
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
                contentDescription = if (stagedOnly) "Review complete. $fileCount files staged, ${freedBytes.toReadableSize()}. Nothing was deleted." else "Freed ${freedBytes.toReadableSize()} from $fileCount files"
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
            text = if (stagedOnly) "Staged for review" else "You freed",
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
                text = if (stagedOnly) "$fileCount files staged. Nothing was deleted." else if (fileCount == 1) "1 file removed" else "$fileCount files removed",
                style = SdzType.Body,
                color = SdzColor.TextSecondary,
            )
        }

        if (stagedOnly && fileCount == 0) {
            Text("Review complete. Nothing was deleted.", color = SdzColor.TextSecondary)
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


/** Summary of decisions only. Staging has not freed storage or deleted originals. */
@Composable
fun DeckCompleteCelebration(
    freedBytes: Long,
    fileCount: Int,
    onDone: () -> Unit,
    nextPartLabel: String? = null,
    onContinueNextPart: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Review complete", style = SdzType.Title, color = SdzColor.Phosphor)
        Text("$fileCount files staged. Originals stay on this device.", style = SdzType.Body, color = SdzColor.TextSecondary)
        if (onContinueNextPart != null) SdzButton(nextPartLabel ?: "Continue reviewing", onContinueNextPart, Modifier.fillMaxWidth())
        SdzButton("Back to library", onDone, Modifier.fillMaxWidth(), style = if (onContinueNextPart == null) SdzButtonStyle.Primary else SdzButtonStyle.Secondary)
    }
}
