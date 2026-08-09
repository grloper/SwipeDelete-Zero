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
import com.swipedelete.zero.ui.components.SwipeableCard
import com.swipedelete.zero.ui.components.rememberDominantColors
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.video.FilmstripScrubber
import com.swipedelete.zero.ui.video.TopCardPlayerState
import com.swipedelete.zero.ui.video.rememberTopCardPlayer
import kotlinx.coroutines.delay

@Composable
fun SwipeEngineScreen(
    onBack: () -> Unit,
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
            .background(SdzColors.PitchBlack),
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
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = SdzColors.PureWhite,
                    modifier = Modifier.size(26.dp).clickable(onClick = onBack),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        state.deck?.title ?: "Deck",
                        color = SdzColors.PureWhite,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val total = state.deck?.totalCount ?: 0
                    Text(
                        "${state.cursor}/$total swiped",
                        color = SdzColors.CrispCyan,
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
                    state.loading -> Text("Loading…", color = SdzColors.MutedGray)
                    state.isComplete -> DeckCompleteView(onBack)
                    else -> CardStack(state, viewModel, topVideoMeta, backedUpUris, playerState)
                }
            }

            // Action buttons — thumb zone (bottom 40%).
            if (!state.isComplete) {
                ActionBar(
                    undoEnabled = state.lastAction != null,
                    cloudArchive = viewModel.cloudArchiveEnabled,
                    onUndo = viewModel::undo,
                    onTrash = { viewModel.onSwipe(SwipeDirection.LEFT) },
                    onUp = { viewModel.onSwipe(SwipeDirection.UP) },
                    onKeep = { viewModel.onSwipe(SwipeDirection.RIGHT) },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(vertical = 20.dp),
                )
            }
        }

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
    val upColor = if (cloudArchive) SdzColors.CrispCyan else SdzColors.StarGold

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        contentAlignment = Alignment.Center,
    ) {
        // The next card peeks behind with its real preview, growing toward
        // full size as the top card is dragged away.
        state.nextItem?.let { next ->
            key(next.id) {
                val peekScale = 0.94f + 0.06f * dragProgress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = peekScale
                            scaleY = peekScale
                            alpha = 0.65f + 0.35f * dragProgress
                        }
                        .clip(RoundedCornerShape(28.dp))
                        .background(SdzColors.Obsidian)
                        .border(1.dp, SdzColors.Hairline, RoundedCornerShape(28.dp)),
                ) {
                    MediaPreview(item = next, modifier = Modifier.fillMaxSize())
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
                        upLabel = if (cloudArchive) "CLOUD" else "STAR",
                        upColor = upColor,
                    )
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

@Composable
private fun ActionBar(
    undoEnabled: Boolean,
    cloudArchive: Boolean,
    onUndo: () -> Unit,
    onTrash: () -> Unit,
    onUp: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(Icons.AutoMirrored.Rounded.Undo, SdzColors.CrispCyan, 52.dp, enabled = undoEnabled, onClick = onUndo)
        CircleAction(Icons.Rounded.Close, SdzColors.HyperCoral, 72.dp, onClick = onTrash)
        CircleAction(
            icon = if (cloudArchive) Icons.Rounded.CloudUpload else Icons.Rounded.Star,
            color = if (cloudArchive) SdzColors.CrispCyan else SdzColors.StarGold,
            diameter = 60.dp,
            onClick = onUp,
        )
        CircleAction(Icons.Rounded.Favorite, SdzColors.ElectricEmerald, 72.dp, onClick = onKeep)
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    color: Color,
    diameter: Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "action-press",
    )
    val accent = color.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier = Modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(SdzColors.Obsidian.copy(alpha = 0.85f))
            .border(2.dp, accent, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(diameter / 2.4f))
    }
}

@Composable
private fun DeckCompleteView(onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("🎉", style = MaterialTheme.typography.displayLarge)
        Text("Deck complete", color = SdzColors.PureWhite, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Head to the Staging Drawer to review & free up space.",
            color = SdzColors.MutedGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SdzColors.ElectricEmerald)
                .clickable(onClick = onBack)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("Done", color = SdzColors.PitchBlack, fontWeight = FontWeight.Black)
        }
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
                .background(SdzColors.Obsidian)
                .border(1.dp, SdzColors.Hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(label, color = SdzColors.PureWhite, style = MaterialTheme.typography.bodyMedium)
            Text(
                "UNDO",
                color = SdzColors.CrispCyan,
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
