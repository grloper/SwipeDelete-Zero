package com.swipedelete.zero.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.*

/** A reserved dock: equal decisions, then Undo and secondary actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionActionRow(
    onUndo: () -> Unit,
    onReclaim: () -> Unit,
    onArchive: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    undoEnabled: Boolean = false,
    archiveEnabled: Boolean = true,
    archiveLabel: String = "Archive",
    status: String = "Originals stay on this device",
    statusOptions: List<String> = listOf(status),
    onSort: (() -> Unit)? = null,
    sortLabel: String = "Newest first",
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().testTag("review-dock").navigationBarsPadding().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SdzButton("Stage", onReclaim, Modifier.weight(1f).fillMaxHeight().testTag("stage-action").semantics { contentDescription = "Stage" }, style = SdzButtonStyle.Secondary, enabled = enabled)
            SdzButton("Keep", onKeep, Modifier.weight(1f).fillMaxHeight().testTag("keep-action").semantics { contentDescription = "Keep" }, enabled = enabled)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("undo-action").semantics { contentDescription = "Undo" }) {
                Text("Undo", style = SdzType.Label)
            }
            TextButton(onClick = { showMore = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("more-action")) {
                Text("More", style = SdzType.Label)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            // Reserve every possible message at this width/font scale before the first decision.
            val reservedPixels = (statusOptions + status).maxOf { message ->
                measurer.measure(message, style = SdzType.Numeric, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height
            }
            val reservedHeight = with(density) { reservedPixels.toDp() }
            Text(status, style = SdzType.Numeric, color = SdzColor.TextSecondary,
                modifier = Modifier.fillMaxWidth().heightIn(min = reservedHeight).testTag("decision-status"), textAlign = TextAlign.Center)
        }
    }
    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }, containerColor = SdzColor.Surface1, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("More actions", style = SdzType.Subtitle)
                Text(if (archiveLabel == "Archive") "Archive queues an upload to Google Photos. Local cleanup stays locked until backup and restore are verified." else "Star excludes this item from future scans.", style = SdzType.BodySmall, color = SdzColor.TextSecondary)
                SdzButton(archiveLabel, { showMore = false; onArchive() }, Modifier.fillMaxWidth(), style = SdzButtonStyle.Secondary, enabled = enabled && archiveEnabled)
                if (onSort != null) SdzButton("Sort: $sortLabel", { showMore = false; onSort() }, Modifier.fillMaxWidth(), style = SdzButtonStyle.Secondary, enabled = enabled)
            }
        }
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
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(SdzRadius.xl))
                    .background(SdzColor.Surface3)
                    .padding(SdzSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SdzSpace.lg),
            ) {
                Text("Review at your pace", style = SdzType.Title, color = SdzColor.Phosphor)
                CoachLine(
                    icon = SdzIcons.Delete,
                    accent = SdzColor.Sage,
                    gesture = "Swipe left",
                    meaning = "Stage for review. Your original stays on this device.",
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
