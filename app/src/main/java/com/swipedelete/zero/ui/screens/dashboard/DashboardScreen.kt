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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Screenshot
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
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
import com.swipedelete.zero.domain.scanner.DeckBuilder
import com.swipedelete.zero.ui.components.SortChip
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize
import kotlin.math.roundToInt

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

    // Feed ordering — survives rotation via the enum's name.
    var deckSortName by rememberSaveable { mutableStateOf(DeckSort.FOR_YOU.name) }
    val deckSort = DeckSort.valueOf(deckSortName)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionResult(result.values.any { it })
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(MediaPermissions)
    }

    // Section split: month sprints ride the horizontal rail, the four AI
    // buckets aggregate their decks into the grid, everything else feeds the
    // classic sorted list.
    val sections = remember(state.decks) { DashboardSections.from(state.decks) }
    val sortedOthers = remember(sections.others, deckSort) { deckSort.apply(sections.others) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") { BrandHeader(onOpenSettings) }
            item(key = "hero") {
                Spacer(Modifier.height(6.dp))
                HeroCard(state)
            }

            if (sections.sprints.isNotEmpty()) {
                item(key = "sprints-title") {
                    Spacer(Modifier.height(6.dp))
                    SectionTitle("Cleanup Sprints", sections.sprints.size)
                }
                item(key = "sprints-row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(sections.sprints, key = { it.id }) { deck ->
                            SprintCard(deck = deck, onClick = { onOpenDeck(deck) })
                        }
                    }
                }
            }

            if (sections.hasBuckets) {
                item(key = "buckets-title") {
                    Spacer(Modifier.height(6.dp))
                    SectionTitle("AI Smart Buckets", null)
                }
                item(key = "buckets-grid") {
                    BucketGrid(sections, onOpenDeck)
                }
            }

            item(key = "decks-title") {
                Spacer(Modifier.height(6.dp))
                SectionTitle("More Decks", sortedOthers.size.takeIf { it > 0 })
                if (!state.loading && sortedOthers.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Sort",
                            color = SdzColors.MutedGray,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        SortChip("For you", deckSort == DeckSort.FOR_YOU) {
                            deckSortName = DeckSort.FOR_YOU.name
                        }
                        SortChip("Date", deckSort == DeckSort.NEWEST) {
                            deckSortName = DeckSort.NEWEST.name
                        }
                        SortChip("Size", deckSort == DeckSort.LARGEST) {
                            deckSortName = DeckSort.LARGEST.name
                        }
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
                else -> items(sortedOthers, key = { it.id }) { deck ->
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
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(20.dp),
            )
        }
    }
}

/** Deck grouping for the three dashboard sections. */
private data class DashboardSections(
    val sprints: List<Deck>,
    val duplicates: List<Deck>,
    val blurry: List<Deck>,
    val largeVideos: List<Deck>,
    val screenshots: List<Deck>,
    val others: List<Deck>,
) {
    val hasBuckets: Boolean
        get() = duplicates.isNotEmpty() || blurry.isNotEmpty() ||
            largeVideos.isNotEmpty() || screenshots.isNotEmpty()

    companion object {
        fun from(decks: List<Deck>): DashboardSections {
            val largeVideos = decks.filter {
                it.id == DeckBuilder.LARGE_VIDEO_DECK_ID ||
                    it.id.startsWith("${DeckBuilder.LARGE_VIDEO_DECK_ID}:")
            }
            return DashboardSections(
                sprints = decks.filter { it.kind == DeckKind.TIME_MACHINE },
                duplicates = decks.filter { it.kind == DeckKind.DUPLICATES },
                blurry = decks.filter { it.kind == DeckKind.BLURRY },
                largeVideos = largeVideos,
                screenshots = decks.filter { it.kind == DeckKind.SCREENSHOTS },
                others = decks.filter { deck ->
                    deck.kind == DeckKind.CLUTTER_HOTSPOT ||
                        (deck.kind == DeckKind.HEAVY_HITTERS && deck !in largeVideos)
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = SdzColors.PureWhite,
        )
        if (count != null) {
            Text(
                "$count",
                style = MaterialTheme.typography.titleLarge,
                color = SdzColors.MutedGray,
            )
        }
    }
}

/**
 * One month-sprint card on the horizontal rail: real cover photo, progress
 * ring, and "% complete" so a half-finished month invites you back.
 */
@Composable
private fun SprintCard(deck: Deck, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        deck.items.firstOrNull()?.let { cover ->
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover.contentUri)
                    .size(300)
                    .crossfade(true)
                    .apply { if (cover.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to SdzColors.PitchBlack.copy(alpha = 0.15f),
                        1f to SdzColors.PitchBlack.copy(alpha = 0.88f),
                    )
                )
        )
        ProgressRing(
            progress = deck.progress,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(44.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                deck.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (deck.completedCount > 0) {
                    "${(deck.progress * 100).roundToInt()}% complete"
                } else {
                    "${deck.totalCount} cards"
                },
                color = if (deck.completedCount > 0) SdzColors.ElectricEmerald else SdzColors.MutedGray,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                deck.reclaimableBytes.toReadableSize(),
                color = SdzColors.CrispCyan,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(
                color = FreeFill,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (progress > 0f) {
                drawArc(
                    color = SdzColors.ElectricEmerald,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        Text(
            "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 2×2 grid of the four AI buckets, each aggregating its decks. */
@Composable
private fun BucketGrid(sections: DashboardSections, onOpenDeck: (Deck) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BucketCard(
                title = "Duplicates & Near-Shots",
                icon = Icons.Rounded.ContentCopy,
                accent = SdzColors.StarGold,
                decks = sections.duplicates,
                onOpenDeck = onOpenDeck,
                modifier = Modifier.weight(1f),
            )
            BucketCard(
                title = "Blurry Media",
                icon = Icons.Rounded.BlurOn,
                accent = SdzColors.CrispCyan,
                decks = sections.blurry,
                onOpenDeck = onOpenDeck,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BucketCard(
                title = "Large Videos",
                icon = Icons.Rounded.Movie,
                accent = SdzColors.HyperCoral,
                decks = sections.largeVideos,
                onOpenDeck = onOpenDeck,
                modifier = Modifier.weight(1f),
            )
            BucketCard(
                title = "Screenshots & Receipts",
                icon = Icons.Rounded.Screenshot,
                accent = SdzColors.ElectricEmerald,
                decks = sections.screenshots,
                onOpenDeck = onOpenDeck,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BucketCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    decks: List<Deck>,
    onOpenDeck: (Deck) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemCount = decks.sumOf { it.remainingCount }
    val bytes = decks.sumOf { it.reclaimableBytes }
    val target = decks.firstOrNull { it.remainingCount > 0 } ?: decks.firstOrNull()
    val enabled = target != null

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, if (enabled) accent.copy(alpha = 0.35f) else SdzColors.Hairline, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled) { target?.let(onOpenDeck) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (enabled) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) accent else SdzColors.MutedGray,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            color = if (enabled) SdzColors.PureWhite else SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (enabled) "$itemCount items · ${bytes.toReadableSize()}" else "All clean",
            color = if (enabled) accent else SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
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

/** Ordering options for the deck feed. */
private enum class DeckSort {
    /** The curated order the deck builder produced. */
    FOR_YOU,

    /** Decks whose newest item is most recent first. */
    NEWEST,

    /** Most reclaimable bytes first. */
    LARGEST;

    fun apply(decks: List<Deck>): List<Deck> = when (this) {
        FOR_YOU -> decks
        NEWEST -> decks.sortedByDescending { deck -> deck.items.maxOfOrNull { it.dateAddedMillis } ?: 0L }
        LARGEST -> decks.sortedByDescending { it.reclaimableBytes }
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
    DeckKind.SCREENSHOTS -> Icons.Rounded.Screenshot
}
