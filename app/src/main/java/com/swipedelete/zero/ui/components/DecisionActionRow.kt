package com.swipedelete.zero.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzTouch
import com.swipedelete.zero.ui.theme.SdzType

/**
 * The deck's decision row.
 *
 * Every control carries a **visible** text label, not just an accessible name.
 * The previous row was four unlabelled circles, and the cloud one in particular
 * was genuinely unguessable — "archive to the cloud, then remove the local
 * copy" is not something an icon can say on its own.
 *
 * Left-to-right order mirrors the gestures exactly: Reclaim sits left because
 * you swipe left, Keep sits right because you swipe right, Archive sits between
 * them because you swipe up. That positional mapping is a second, non-colour
 * channel for the decision, and the icon silhouettes are a third.
 *
 * The row applies [navigationBarsPadding] so it always clears the gesture
 * inset, and every target is at least [SdzTouch.minTarget].
 */
@Composable
fun DecisionActionRow(
    onUndo: () -> Unit,
    onReclaim: () -> Unit,
    onArchive: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
    undoEnabled: Boolean = false,
    archiveEnabled: Boolean = true,
    archiveLabel: String = "Archive",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = SdzSpace.lg, vertical = SdzSpace.lg),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        SdzCircleAction(
            icon = SdzIcons.Undo,
            label = "Undo",
            accent = SdzColor.TextSecondary,
            onClick = onUndo,
            diameter = SdzTouch.minTarget,
            enabled = undoEnabled,
        )
        SdzCircleAction(
            icon = SdzIcons.Delete,
            label = "Delete",
            accent = SdzColor.Red,
            onClick = onReclaim,
            diameter = SdzTouch.primaryAction,
        )
        SdzCircleAction(
            icon = SdzIcons.Archive,
            label = archiveLabel,
            accent = SdzColor.Teal,
            onClick = onArchive,
            diameter = SdzTouch.secondaryAction,
            enabled = archiveEnabled,
        )
        SdzCircleAction(
            icon = SdzIcons.Keep,
            label = "Keep",
            accent = SdzColor.Azure,
            onClick = onKeep,
            diameter = SdzTouch.primaryAction,
            filled = true,
        )
    }
}

/**
 * First-run coachmark. Shown once, dismissible, and it teaches the gesture
 * mapping in the same words and colours the row uses — so the lesson and the
 * interface agree.
 */
@Composable
fun DeckCoachmark(
    visible: Boolean,
    onDismiss: () -> Unit,
    archiveLabel: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SdzColor.Scrim)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(SdzSpace.xxl)
                    .clip(RoundedCornerShape(SdzRadius.xl))
                    .background(SdzColor.Surface3)
                    .padding(SdzSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SdzSpace.lg),
            ) {
                Text("Three ways to decide", style = SdzType.Title, color = SdzColor.Phosphor)
                CoachLine(
                    icon = SdzIcons.Delete,
                    accent = SdzColor.Red,
                    gesture = "Swipe left",
                    meaning = "Delete it. Reversible — it waits safely in Staging before permanent removal.",
                )
                CoachLine(
                    icon = SdzIcons.Keep,
                    accent = SdzColor.Azure,
                    gesture = "Swipe right",
                    meaning = "Keep it. Nothing happens to the file.",
                )
                CoachLine(
                    icon = SdzIcons.Archive,
                    accent = SdzColor.Teal,
                    gesture = "Swipe up",
                    meaning = archiveLabel,
                )
                SdzButton(label = "Got it", onClick = onDismiss, style = SdzButtonStyle.Primary)
            }
        }
    }
}

@Composable
private fun CoachLine(
    icon: Painter,
    accent: Color,
    gesture: String,
    meaning: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.fillMaxWidth()) {
            Text(gesture, style = SdzType.Label, color = accent)
            Text(
                meaning,
                style = SdzType.BodySmall,
                color = SdzColor.TextSecondary,
                textAlign = TextAlign.Start,
            )
        }
    }
}
