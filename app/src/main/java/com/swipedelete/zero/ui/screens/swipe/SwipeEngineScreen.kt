package com.swipedelete.zero.ui.screens.swipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.ui.components.SwipeableCard
import com.swipedelete.zero.ui.theme.SdzColors
import kotlinx.coroutines.delay

@Composable
fun SwipeEngineScreen(
    onBack: () -> Unit,
    viewModel: SwipeEngineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack)
            .padding(horizontal = 20.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.ArrowBack,
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
                    else -> CardStack(state, viewModel)
                }
            }

            // Action buttons — thumb zone (bottom 40%).
            if (!state.isComplete) {
                ActionBar(
                    onTrash = { viewModel.onSwipe(SwipeDirection.LEFT) },
                    onStar = { viewModel.onSwipe(SwipeDirection.UP) },
                    onKeep = { viewModel.onSwipe(SwipeDirection.RIGHT) },
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        // 5-second floating Undo toast.
        UndoToast(
            visible = state.lastAction != null,
            label = state.lastAction?.let { undoLabel(it.direction) } ?: "",
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
private fun CardStack(state: SwipeUiState, viewModel: SwipeEngineViewModel) {
    val deck = state.deck ?: return
    val topIndex = state.cursor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        contentAlignment = Alignment.Center,
    ) {
        // Peek of the next card behind (scaled down) for depth.
        if (topIndex + 1 < deck.totalCount) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(SdzColors.Obsidian)
                    .border(1.dp, SdzColors.Hairline, RoundedCornerShape(28.dp)),
            )
        }
        if (topIndex < deck.totalCount) {
            // key on the item so a fresh Animatable is created per card.
            key(deck.items[topIndex].id) {
                SwipeableCard(
                    item = deck.items[topIndex],
                    onSwiped = viewModel::onSwipe,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ActionBar(
    onTrash: () -> Unit,
    onStar: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(Icons.Rounded.Close, SdzColors.HyperCoral, 72.dp, onTrash)
        CircleAction(Icons.Rounded.Star, SdzColors.StarGold, 60.dp, onStar)
        CircleAction(Icons.Rounded.Favorite, SdzColors.ElectricEmerald, 72.dp, onKeep)
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    color: Color,
    diameter: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(SdzColors.Obsidian)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(diameter / 2.4f))
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

private fun undoLabel(direction: SwipeDirection): String = when (direction) {
    SwipeDirection.LEFT -> "Moved to Staging Drawer"
    SwipeDirection.RIGHT -> "Kept"
    SwipeDirection.UP -> "Starred & excluded"
    SwipeDirection.NONE -> ""
}
