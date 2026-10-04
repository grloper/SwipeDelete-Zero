package com.swipedelete.zero.ui.screens.swipe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.domain.scanner.VideoMeta
import com.swipedelete.zero.ui.components.*
import com.swipedelete.zero.ui.theme.*
import com.swipedelete.zero.ui.video.FilmstripScrubber
import com.swipedelete.zero.ui.video.TopCardPlayerState
import com.swipedelete.zero.ui.video.rememberTopCardPlayer

@Composable
fun SwipeEngineScreen(
    onBack: () -> Unit,
    onOpenCloudManager: () -> Unit = {},
    viewModel: SwipeEngineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedbackLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var active by remember { mutableStateOf(feedbackLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) }
    fun canInteract() = feedbackLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    fun decide(direction: SwipeDirection) { if (canInteract()) viewModel.onSwipe(direction) }
    DisposableEffect(feedbackLifecycleOwner, viewModel) {
        viewModel.setFeedbackActive(feedbackLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            active = canInteract()
            viewModel.setFeedbackActive(feedbackLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
        feedbackLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { feedbackLifecycleOwner.lifecycle.removeObserver(observer); viewModel.setFeedbackActive(false) }
    }
    val topVideoMeta by viewModel.topVideoMeta.collectAsStateWithLifecycle()
    val backedUpUris by viewModel.backedUpUris.collectAsStateWithLifecycle()
    val playerState = rememberTopCardPlayer()
    val statusOptions = remember(viewModel.cloudArchiveEnabled) {
        listOf("Originals stay on this device") + SwipeDirection.entries.map { undoLabel(it, viewModel.cloudArchiveEnabled) }
    }
    LaunchedEffect(state.topItem?.id) { playerState.showItem(state.topItem) }

    BoxWithConstraints(Modifier.fillMaxSize().background(SdzColor.Surface0).testTag("review-screen")) {
        val gutter = if (maxWidth >= 400.dp) 20.dp else 16.dp
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = gutter)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SdzIconButton(SdzIcons.Back, "Back", onBack)
                Column(Modifier.weight(1f)) {
                    Text(state.deck?.title ?: "Review", style = SdzType.Row, color = SdzColor.Phosphor, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("${state.cursor}/${state.deck?.totalCount ?: 0} reviewed", style = SdzType.Numeric, color = SdzColor.TextSecondary, modifier = Modifier.testTag("review-progress"))
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).testTag("review-photo"), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> Text("Loading…", color = SdzColor.TextSecondary)
                    state.isComplete -> DeckCompleteCelebration(
                        freedBytes = state.sessionStagedBytes, fileCount = state.sessionStagedCount, onDone = onBack,
                        nextPartLabel = state.nextDeckTitle?.let { "Continue with $it" },
                        onContinueNextPart = state.nextDeckId?.let { nextId -> { viewModel.loadDeck(nextId) } },
                    )
                    state.deck == null -> Text("This review is unavailable. Return to the library and scan again.", color = SdzColor.TextSecondary)
                    else -> CardStack(state, viewModel, playerState, active, ::canInteract)
                }
            }
            state.topItem?.let { item ->
                Column(Modifier.fillMaxWidth().testTag("review-metadata")) {
                    if (item.isVideo) FilmstripScrubber(item, playerState, Modifier.fillMaxWidth())
                    MetadataPill(item, Modifier.fillMaxWidth(), topVideoMeta)
                    if (item.contentUri.toString() in backedUpUris) CloudChip(backedUp = true)
                }
            }
            state.actionError?.let { Text(it, style = SdzType.BodySmall, color = SdzColor.TextSecondary) }
            val uploadQueue by viewModel.uploadQueue.collectAsStateWithLifecycle()
            UploadStatusStrip(uploadQueue, onOpenCloudManager, Modifier.fillMaxWidth())
            DecisionActionRow(
                onUndo = { if (canInteract()) viewModel.undo() },
                onReclaim = { decide(SwipeDirection.LEFT) },
                onArchive = { decide(SwipeDirection.UP) },
                onKeep = { decide(SwipeDirection.RIGHT) },
                enabled = active && !state.loading && !state.isComplete && state.topItem != null && !state.actionInProgress,
                undoEnabled = active && state.lastAction != null && !state.actionInProgress,
                archiveEnabled = !state.actionInProgress,
                archiveLabel = if (viewModel.cloudArchiveEnabled) "Archive" else "Star",
                status = state.lastAction?.let { undoLabel(it.direction, viewModel.cloudArchiveEnabled) } ?: "Originals stay on this device",
                statusOptions = statusOptions,
                sortLabel = if (state.sortOrder == DeckSortOrder.LARGEST_FIRST) "Largest first" else "Newest first",
                onSort = { if (canInteract()) viewModel.setSortOrder(if (state.sortOrder == DeckSortOrder.LARGEST_FIRST) DeckSortOrder.NEWEST_FIRST else DeckSortOrder.LARGEST_FIRST) },
            )
        }
        DeckCoachmark(
            visible = state.showCoachmark, onDismiss = viewModel::dismissCoachmark,
            archiveLabel = if (viewModel.cloudArchiveEnabled) "Queue an upload to Google Photos. Cleanup remains locked until backup and restore are verified." else "Star it and hide it from future scans.",
        )
    }
}

@Composable
private fun CardStack(state: SwipeUiState, viewModel: SwipeEngineViewModel, playerState: TopCardPlayerState, active: Boolean, canInteract: () -> Boolean) {
    val topItem = state.topItem ?: return
    AnimatedContent(
        targetState = topItem to state.cardResetToken,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        label = "review-photo-change",
    ) { (item, generation) ->
        SwipeableCard(
            item = item,
            enabled = active && !state.actionInProgress && item.id == state.topItem?.id && generation == state.cardResetToken,
            onSwiped = { direction ->
                // Outgoing content remains for its transition, but cannot decide for the next item.
                if (canInteract() && viewModel.state.value.topItem?.id == item.id && viewModel.state.value.cardResetToken == generation) viewModel.onSwipe(direction) else false
            },
            modifier = Modifier.fillMaxSize(),
        ) { left, right, up ->
            MediaPreview(item, Modifier.fillMaxSize(), playerState, ContentScale.Fit)
            SwipeStamps(left, right, up, Modifier.fillMaxSize(), archiveLabel = if (viewModel.cloudArchiveEnabled) "Archive" else "Star")
        }
    }
}

/** One-line summary of the Photos upload queue: uploading % · queued · verified. */
@Composable
private fun UploadStatusStrip(
    queue: Map<String, com.swipedelete.zero.domain.backup.ArchiveItemState>,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return
    val uploading = queue.values.filterIsInstance<com.swipedelete.zero.domain.backup.ArchiveItemState.Uploading>()
    val queued = queue.values.count { it is com.swipedelete.zero.domain.backup.ArchiveItemState.Queued }
    val verified = queue.values.count { it is com.swipedelete.zero.domain.backup.ArchiveItemState.Verified }
    val failed = queue.values.count { it is com.swipedelete.zero.domain.backup.ArchiveItemState.Failed }

    val parts = buildList {
        if (uploading.isNotEmpty()) {
            val pct = (uploading.map { it.progress }.average() * 100).toInt()
            add("${uploading.size} uploading $pct%")
        }
        if (queued > 0) add("$queued queued")
        if (verified > 0) add("$verified in Google Photos")
        if (failed > 0) add("$failed failed")
    }
    if (parts.isEmpty()) return

    Row(
        modifier = modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(SdzColor.Surface1.copy(alpha = 0.85f))
            .border(1.dp, SdzColor.TextSecondary.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.CloudUpload,
            contentDescription = null,
            tint = SdzColor.Teal,
            modifier = Modifier.size(16.dp),
        )
        Text(
            parts.joinToString(" · "),
            color = SdzColor.Phosphor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            "Manage",
            color = SdzColor.Teal,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun undoLabel(direction: SwipeDirection, cloudArchive: Boolean): String = when (direction) {
    SwipeDirection.LEFT -> "Staged for review"
    SwipeDirection.RIGHT -> "Kept"
    SwipeDirection.UP -> if (cloudArchive) "Queued for Google Photos; not backed up yet" else "Starred & excluded"
    SwipeDirection.NONE -> ""
}
