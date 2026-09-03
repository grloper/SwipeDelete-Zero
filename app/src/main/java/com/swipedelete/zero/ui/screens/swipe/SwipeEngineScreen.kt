package com.swipedelete.zero.ui.screens.swipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.domain.scanner.VideoMeta
import com.swipedelete.zero.ui.components.CloudChip
import com.swipedelete.zero.ui.components.MediaPreview
import com.swipedelete.zero.ui.components.MetadataPill
import com.swipedelete.zero.ui.components.PaletteBackdrop
import com.swipedelete.zero.ui.components.SwipeStamps
import com.swipedelete.zero.ui.components.MediaClassBadge
import com.swipedelete.zero.ui.components.DecisionActionRow
import com.swipedelete.zero.ui.components.DeckCoachmark
import com.swipedelete.zero.ui.components.DeckCompleteCelebration
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.components.SwipeableCard
import com.swipedelete.zero.ui.components.rememberDominantColors
import com.swipedelete.zero.ui.components.SdzIconButton
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.video.FilmstripScrubber
import com.swipedelete.zero.ui.video.TopCardPlayerState
import com.swipedelete.zero.ui.video.rememberTopCardPlayer
import kotlinx.coroutines.delay

@Composable
fun SwipeEngineScreen(
    onBack: () -> Unit,
    onOpenCloudManager: () -> Unit = {},
    viewModel: SwipeEngineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topVideoMeta by viewModel.topVideoMeta.collectAsStateWithLifecycle()
    val backedUpUris by viewModel.backedUpUris.collectAsStateWithLifecycle()

    // Ambient backdrop tracks the top card's dominant palette.
    val palette by rememberDominantColors(state.topItem)

    // One ExoPlayer for the whole screen, re-targeted at each top video card.
    val playerState = rememberTopCardPlayer()
    LaunchedEffect(state.topItem?.id) {
        playerState.showItem(state.topItem)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColor.Surface0),
    ) {
        PaletteBackdrop(palette, Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SdzIconButton(
                    icon = SdzIcons.Back,
                    label = "Back",
                    onClick = onBack,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        state.deck?.title ?: "Deck",
                        color = SdzColor.Phosphor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val total = state.deck?.totalCount ?: 0
                    Text(
                        "${state.cursor}/$total swiped",
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Card area (top 60%).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    state.loading -> Text("Loading…", color = SdzColor.TextSecondary)
                    state.isComplete -> DeckCompleteCelebration(
                        freedBytes = state.sessionReclaimedBytes,
                        fileCount = state.sessionReclaimedCount,
                        onDone = onBack,
                    )
                    else -> CardStack(state, viewModel, topVideoMeta, backedUpUris, playerState)
                }
            }

            // Live cloud archive status — renders nothing when the queue is
            // empty (always the case in the fdroid/play flavors).
            val uploadQueue by viewModel.uploadQueue.collectAsStateWithLifecycle()
            UploadStatusStrip(
                queue = uploadQueue,
                onClick = onOpenCloudManager,
                modifier = Modifier.fillMaxWidth()
            )

            // Action buttons — thumb zone (bottom 40%).
            if (!state.isComplete) {
                DecisionActionRow(
                    onUndo = viewModel::undo,
                    onReclaim = { viewModel.onSwipe(SwipeDirection.LEFT) },
                    onArchive = { viewModel.onSwipe(SwipeDirection.UP) },
                    onKeep = { viewModel.onSwipe(SwipeDirection.RIGHT) },
                    undoEnabled = state.lastAction != null,
                    archiveLabel = if (viewModel.cloudArchiveEnabled) "Archive" else "Star",
                )
            }
        }

        // First-run gesture lesson, shown once ever.
        DeckCoachmark(
            visible = state.showCoachmark,
            onDismiss = viewModel::dismissCoachmark,
            archiveLabel = if (viewModel.cloudArchiveEnabled)
                "Upload to Google Photos, then queue the local copy once verified."
            else
                "Star it and hide it from every future scan.",
        )

        // 5-second floating Undo toast.
        UndoToast(
            visible = state.lastAction != null,
            label = state.lastAction?.let { undoLabel(it.direction, viewModel.cloudArchiveEnabled) } ?: "",
            onUndo = viewModel::undo,
            onTimeout = viewModel::dismissUndo,
            key = state.lastAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
    }
}

@Composable
private fun CardStack(
    state: SwipeUiState,
    viewModel: SwipeEngineViewModel,
    topVideoMeta: VideoMeta?,
    backedUpUris: Set<String>,
    playerState: TopCardPlayerState,
) {
    val topItem = state.topItem ?: return
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val cloudArchive = viewModel.cloudArchiveEnabled
    // Up-swipe is Archive: teal, cloud silhouette, top of the card.
    val upColor = SdzColor.Teal

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        contentAlignment = Alignment.Center,
    ) {
        // Two cards peek behind the active one, each a step smaller and dimmer
        // and nudged down — so the deck reads as a physical stack with depth
        // rather than a single card on a background. Both step forward as the
        // top card is dragged away, which previews the payoff of deciding.
        val deck = state.deck
        if (deck != null) {
            for (depth in 2 downTo 1) {
                val behind = deck.items.getOrNull(state.cursor + depth) ?: continue
                key(behind.id) {
                    // depth 2 sits furthest back; dragProgress advances both.
                    val advance = dragProgress
                    val step = depth - advance
                    val peekScale = 1f - 0.05f * step
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = peekScale
                                scaleY = peekScale
                                translationY = step * 14.dp.toPx()
                                alpha = (1f - 0.28f * step).coerceIn(0f, 1f)
                            }
                            .clip(RoundedCornerShape(28.dp))
                            .background(SdzColor.Surface2),
                    ) {
                        MediaPreview(item = behind, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        // key on the item so a fresh Animatable is created per card.
        key(topItem.id) {
            SwipeableCard(
                item = topItem,
                onSwiped = viewModel::onSwipe,
                modifier = Modifier.fillMaxSize(),
                onDragProgress = { dragProgress = it },
                upAccent = upColor,
            ) { leftGlow, rightGlow, upGlow ->
                Box(Modifier.fillMaxSize()) {
                    MediaPreview(
                        item = topItem,
                        modifier = Modifier.fillMaxSize(),
                        playerState = playerState,
                    )
                    SwipeStamps(
                        leftGlow = leftGlow,
                        rightGlow = rightGlow,
                        upGlow = upGlow,
                        modifier = Modifier.fillMaxSize(),
                        archiveLabel = if (cloudArchive) "ARCHIVE" else "STAR",
                    )
                    topItem.mediaClass.badge?.let { badge ->
                        MediaClassBadge(
                            label = badge,
                            icon = if (topItem.mediaClass ==
                                com.swipedelete.zero.domain.model.MediaClass.DOCUMENT
                            ) SdzIcons.Documents else SdzIcons.Screenshots,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                        )
                    }
                    CloudChip(
                        backedUp = topItem.contentUri.toString() in backedUpUris,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        if (topItem.isVideo) {
                            FilmstripScrubber(
                                item = topItem,
                                playerState = playerState,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        MetadataPill(item = topItem, videoMeta = topVideoMeta)
                    }
                }
            }
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

@Composable
private fun UndoToast(
    visible: Boolean,
    label: String,
    onUndo: () -> Unit,
    onTimeout: () -> Unit,
    key: Any?,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss after 5 seconds; restarts whenever a new swipe arrives.
    LaunchedEffect(key) {
        if (key != null) {
            delay(5_000)
            onTimeout()
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SdzColor.Surface1)
                .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(label, color = SdzColor.Phosphor, style = MaterialTheme.typography.bodyMedium)
            Text(
                "UNDO",
                color = SdzColor.TextSecondary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.clickable(onClick = onUndo),
            )
        }
    }
}

private fun undoLabel(direction: SwipeDirection, cloudArchive: Boolean): String = when (direction) {
    SwipeDirection.LEFT -> "Moved to Staging Drawer"
    SwipeDirection.RIGHT -> "Kept"
    SwipeDirection.UP -> if (cloudArchive) "Uploading to Google Photos" else "Starred & excluded"
    SwipeDirection.NONE -> ""
}
