package com.swipedelete.zero.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize

private val MediaPermissions = arrayOf(
    android.Manifest.permission.READ_MEDIA_IMAGES,
    android.Manifest.permission.READ_MEDIA_VIDEO,
    android.Manifest.permission.READ_EXTERNAL_STORAGE,
)

/** Fill for the "used" segment of the storage bar and its legend dot. */
private val UsedFill = Color(0xFF454C58)

/** Fill for the "free" segment (the bar track) and its legend dot. */
private val FreeFill = Color(0x26FFFFFF)

@Composable
fun DashboardScreen(
    onOpenDeck: (Deck) -> Unit,
    onOpenStaging: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionResult(result.values.any { it })
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(MediaPermissions)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { BrandHeader(onOpenSettings) }
            item(key = "hero") {
                Spacer(Modifier.height(6.dp))
                HeroCard(state)
            }
            item(key = "decks-title") {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Decks",
                        style = MaterialTheme.typography.titleLarge,
                        color = SdzColors.PureWhite,
                    )
                    if (!state.loading && state.decks.isNotEmpty()) {
                        Text(
                            "${state.decks.size}",
                            style = MaterialTheme.typography.titleLarge,
                            color = SdzColors.MutedGray,
                        )
                    }
                }
            }
            when {
                state.loading -> items(3, key = { "skeleton-$it" }) { SkeletonDeckRow() }
                state.decks.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        hasMediaAccess = state.hasMediaAccess,
                        onRequestAccess = { permissionLauncher.launch(MediaPermissions) },
                    )
                }
                else -> items(state.decks, key = { it.id }) { deck ->
                    DeckRow(deck = deck, onClick = { onOpenDeck(deck) })
                }
            }
        }

        // Thumb-zone launcher for the staging drawer — only exists once there is
        // something to free, so an empty state never shows a red "alert" bar.
        AnimatedVisibility(
            visible = state.stagedCount > 0,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            StagingPill(
                stagedCount = state.stagedCount,
                stagedBytes = state.stagedBytes,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenStaging()
                },
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun BrandHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "SwipeDelete Zero",
                style = MaterialTheme.typography.headlineMedium,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SdzColors.ElectricEmerald.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(SdzColors.ElectricEmerald, CircleShape)
                )
                Text(
                    "100% offline",
                    style = MaterialTheme.typography.labelMedium,
                    color = SdzColors.ElectricEmerald,
                )
            }
        }
        // 44dp touch target around the 24dp glyph.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = SdzColors.MutedGray,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun HeroCard(state: DashboardUiState) {
    val reclaimable = state.decks.sumOf { it.reclaimableBytes }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "READY TO REVIEW",
            style = MaterialTheme.typography.labelMedium,
            color = SdzColors.MutedGray,
        )
        val pulse = rememberInfiniteTransition(label = "hero-pulse")
        val scanAlpha by pulse.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "scan-alpha",
        )
        Text(
            when {
                state.loading -> "Scanning…"
                reclaimable == 0L -> "All clean"
                else -> "up to " + reclaimable.toReadableSize()
            },
            style = MaterialTheme.typography.displayLarge,
            color = SdzColors.ElectricEmerald,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = if (state.loading) scanAlpha else 1f },
        )
        Text(
            when {
                state.loading -> "building decks from your library"
                reclaimable == 0L -> "your library is already lean"
                else -> "across ${state.decks.size} decks · nothing leaves this device"
            },
            style = MaterialTheme.typography.labelMedium,
            color = SdzColors.MutedGray,
        )
        Spacer(Modifier.height(12.dp))
        StorageBar(state)
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendItem(UsedFill, "Used", state.usedStorageBytes)
            if (state.stagedBytes > 0) {
                LegendItem(SdzColors.ElectricEmerald, "Staged", state.stagedBytes)
            }
            LegendItem(FreeFill, "Free", state.freeStorageBytes)
        }
    }
}

/**
 * One linear bar for the whole disk: [used][free], with the staged bytes drawn
 * as an emerald sliver carved out of the *end* of the used segment — the space
 * that is about to cross over into free.
 */
@Composable
private fun StorageBar(state: DashboardUiState) {
    val usedFrac = state.usedFraction.coerceIn(0f, 1f)
    val total = state.totalStorageBytes
    val stagedFrac = if (total > 0) (state.stagedBytes.toFloat() / total).coerceIn(0f, usedFrac) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(FreeFill),
    ) {
        if (usedFrac > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(usedFrac)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.dp))
                    .background(UsedFill),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (state.stagedBytes > 0) {
                    // Keep the sliver ≥2% so even a few staged KB stay visible.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((stagedFrac / usedFrac).coerceAtLeast(0.02f))
                            .fillMaxHeight()
                            .background(SdzColors.ElectricEmerald),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            "$label ${bytes.toReadableSize()}",
            style = MaterialTheme.typography.labelMedium,
            color = SdzColors.MutedGray,
        )
    }
}

@Composable
private fun DeckRow(deck: Deck, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DeckThumbFan(deck)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                deck.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (deck.completedCount > 0) {
                    "${deck.remainingCount} of ${deck.totalCount} left · ${deck.reclaimableBytes.toReadableSize()}"
                } else {
                    "${deck.totalCount} cards · ${deck.reclaimableBytes.toReadableSize()}"
                },
                color = SdzColors.MutedGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            if (deck.completedCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FreeFill),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(deck.progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(deck.kind.accent()),
                    )
                }
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SdzColors.MutedGray,
        )
    }
}

/**
 * A fanned stack of up to three real thumbnails from the deck — the dashboard
 * shows the actual photos at stake, not an abstract stat.
 */
@Composable
private fun DeckThumbFan(deck: Deck) {
    val thumbs = deck.items.take(3)
    Box(Modifier.size(76.dp)) {
        thumbs.asReversed().forEachIndexed { revIndex, item ->
            val depth = thumbs.lastIndex - revIndex // 2 back … 0 front (drawn last)
            val rotation = when (depth) {
                2 -> 9f
                1 -> -9f
                else -> 0f
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(62.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .clip(RoundedCornerShape(12.dp))
                    .background(SdzColors.PitchBlack)
                    .border(1.dp, SdzColors.Hairline, RoundedCornerShape(12.dp)),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.contentUri)
                        .size(160)
                        .crossfade(true)
                        .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        KindBadge(deck.kind, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun KindBadge(kind: DeckKind, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(kind.accent())
            .border(2.dp, SdzColors.PitchBlack, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            kind.glyph(),
            contentDescription = null,
            tint = SdzColors.PitchBlack,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SkeletonDeckRow() {
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(20.dp))
            .padding(14.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FreeFill)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(FreeFill)
            )
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(FreeFill)
            )
        }
    }
}

@Composable
private fun EmptyState(hasMediaAccess: Boolean, onRequestAccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.TaskAlt,
            contentDescription = null,
            tint = SdzColors.ElectricEmerald,
            modifier = Modifier.size(48.dp),
        )
        Text(
            if (hasMediaAccess) "Your library is clean" else "Photo access needed",
            style = MaterialTheme.typography.titleLarge,
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (hasMediaAccess) {
                "New decks appear here as photos, screenshots and downloads pile up."
            } else {
                "SwipeDelete scans entirely on-device — it can't even reach the internet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SdzColors.MutedGray,
            textAlign = TextAlign.Center,
        )
        if (!hasMediaAccess) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant access",
                style = MaterialTheme.typography.labelLarge,
                color = SdzColors.PitchBlack,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SdzColors.ElectricEmerald)
                    .clickable(onClick = onRequestAccess)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun StagingPill(
    stagedCount: Int,
    stagedBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SdzColors.HyperCoral)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Rounded.DeleteSweep,
            contentDescription = null,
            tint = SdzColors.PureWhite,
            modifier = Modifier.size(26.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Free up ${stagedBytes.toReadableSize()}",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (stagedCount == 1) "1 item staged · tap to review" else "$stagedCount items staged · tap to review",
                color = SdzColors.PureWhite.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = SdzColors.PureWhite,
        )
    }
}

private fun DeckKind.accent() = when (this) {
    DeckKind.HEAVY_HITTERS -> SdzColors.HyperCoral
    DeckKind.DUPLICATES -> SdzColors.StarGold
    DeckKind.BLURRY -> SdzColors.CrispCyan
    else -> SdzColors.ElectricEmerald
}

private fun DeckKind.glyph(): ImageVector = when (this) {
    DeckKind.TIME_MACHINE -> Icons.Rounded.History
    DeckKind.HEAVY_HITTERS -> Icons.Rounded.Storage
    DeckKind.CLUTTER_HOTSPOT -> Icons.Rounded.Whatshot
    DeckKind.DUPLICATES -> Icons.Rounded.ContentCopy
    DeckKind.BLURRY -> Icons.Rounded.BlurOn
}
