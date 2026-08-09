package com.swipedelete.zero.ui.screens.dashboard

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.text.font.FontFamily
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
import com.swipedelete.zero.domain.model.DeckGroup
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.domain.scanner.DeckBuilder
import com.swipedelete.zero.ui.components.SortChip
import com.swipedelete.zero.ui.screens.staging.PurgeEffect
import com.swipedelete.zero.ui.screens.staging.StagingSheet
import com.swipedelete.zero.ui.screens.staging.StagingViewModel
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
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    stagingViewModel: StagingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // Feed ordering — survives rotation via the enum's name.
    var deckSortName by rememberSaveable { mutableStateOf(DeckSort.FOR_YOU.name) }
    val deckSort = DeckSort.valueOf(deckSortName)

    var showStaging by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        viewModel.onPermissionResult(result.values.any { it })
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(MediaPermissions)
    }

    // The OS delete/trash dialog launcher lives HERE, in the stable dashboard
    // composition — never inside the sheet, whose dismissal mid-dialog would
    // otherwise drop the result on the floor.
    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        stagingViewModel.onConfirmationResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        stagingViewModel.effect.collect { effect ->
            when (effect) {
                is PurgeEffect.LaunchConfirmation ->
                    confirmLauncher.launch(IntentSenderRequest.Builder(effect.sender).build())
                is PurgeEffect.Completed ->
                    Toast.makeText(
                        context,
                        "Freed ${effect.freedBytes.toReadableSize()} · ${effect.purgedCount} files",
                        Toast.LENGTH_LONG,
                    ).show()
                is PurgeEffect.NeedsSafAccess ->
                    Toast.makeText(
                        context,
                        "${effect.uriCount} non-media files need folder access (grant via SAF).",
                        Toast.LENGTH_LONG,
                    ).show()
                is PurgeEffect.Message ->
                    Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
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
            // The gesture bar's height varies by device, so add the real inset
            // on top of the floating pill's clearance instead of guessing with
            // a fixed dp — otherwise the last bucket row hides behind it.
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 120.dp +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
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
                        items(sections.sprints, key = { it.id }) { group ->
                            SprintCard(
                                group = group,
                                onClick = { group.nextDeck?.let(onOpenDeck) },
                            )
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
                    BucketGrid(
                        sections = sections,
                        needsScan = !state.hasAnalysis,
                        scanning = state.isScanning,
                        onOpenDeck = onOpenDeck,
                        onScanNow = viewModel::scanNow,
                    )
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
                else -> items(sortedOthers, key = { it.id }) { group ->
                    DeckRow(group = group, onClick = { group.nextDeck?.let(onOpenDeck) })
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
                    showStaging = true
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(20.dp),
            )
        }

        if (showStaging) {
            StagingSheet(
                viewModel = stagingViewModel,
                onDismiss = { showStaging = false },
            )
        }
    }
}

/** Deck grouping for the three dashboard sections. */
private data class DashboardSections(
    /** One entry per month, not per 50-card part. */
    val sprints: List<DeckGroup>,
    val duplicates: List<Deck>,
    val blurry: List<Deck>,
    val largeVideos: List<Deck>,
    val screenshots: List<Deck>,
    /** Clutter hotspots and remaining heavy hitters, also collapsed by group. */
    val others: List<DeckGroup>,
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
                sprints = DeckGroup.from(decks.filter { it.kind == DeckKind.TIME_MACHINE }),
                duplicates = decks.filter { it.kind == DeckKind.DUPLICATES },
                blurry = decks.filter { it.kind == DeckKind.BLURRY },
                largeVideos = largeVideos,
                screenshots = decks.filter { it.kind == DeckKind.SCREENSHOTS },
                others = DeckGroup.from(
                    decks.filter { deck ->
                        deck.kind == DeckKind.CLUTTER_HOTSPOT ||
                            (deck.kind == DeckKind.HEAVY_HITTERS && deck !in largeVideos)
                    }
                ),
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
private fun SprintCard(group: DeckGroup, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        group.coverItem?.let { cover ->
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
            progress = group.progress,
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
                group.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (group.completedCount > 0) {
                    "${(group.progress * 100).roundToInt()}% complete"
                } else {
                    "${group.totalCount} cards"
                },
                color = if (group.completedCount > 0) SdzColors.ElectricEmerald else SdzColors.MutedGray,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                group.reclaimableBytes.toReadableSize(),
                color = SdzColors.CrispCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
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
private fun BucketGrid(
    sections: DashboardSections,
    needsScan: Boolean,
    scanning: Boolean,
    onOpenDeck: (Deck) -> Unit,
    onScanNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BucketCard(
                title = "Duplicates & Near-Shots",
                icon = Icons.Rounded.ContentCopy,
                accent = SdzColors.StarGold,
                decks = sections.duplicates,
                // These two are the only buckets built from on-device analysis,
                // so they are the only ones that can be "not scanned yet".
                needsScan = needsScan,
                scanning = scanning,
                onOpenDeck = onOpenDeck,
                onScanNow = onScanNow,
                modifier = Modifier.weight(1f),
            )
            BucketCard(
                title = "Blurry Media",
                icon = Icons.Rounded.BlurOn,
                accent = SdzColors.CrispCyan,
                decks = sections.blurry,
                needsScan = needsScan,
                scanning = scanning,
                onOpenDeck = onOpenDeck,
                onScanNow = onScanNow,
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
    needsScan: Boolean = false,
    scanning: Boolean = false,
    onScanNow: () -> Unit = {},
) {
    val itemCount = decks.sumOf { it.remainingCount }
    val bytes = decks.sumOf { it.reclaimableBytes }
    val target = decks.firstOrNull { it.remainingCount > 0 } ?: decks.firstOrNull()
    val hasResults = target != null
    // Empty because we never looked is a completely different state from empty
    // because there is nothing to find — never label the first one "All clean".
    val unscanned = !hasResults && needsScan

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(
                1.dp,
                if (hasResults || unscanned) accent.copy(alpha = 0.35f) else SdzColors.Hairline,
                RoundedCornerShape(20.dp),
            )
            .clickable(enabled = hasResults) { target?.let(onOpenDeck) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (hasResults || unscanned) 0.18f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (hasResults || unscanned) accent else SdzColors.MutedGray,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            title,
            color = if (hasResults || unscanned) SdzColors.PureWhite else SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        when {
            hasResults -> Text(
                "$itemCount items · ${bytes.toReadableSize()}",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
            )
            scanning && needsScan -> Text(
                "Scanning…",
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
            unscanned -> ScanNowChip(accent = accent, onClick = onScanNow)
            else -> Text(
                "All clean",
                color = SdzColors.MutedGray,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ScanNowChip(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            "Scan now",
            color = accent,
            fontWeight = FontWeight.Black,
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
    // Each candidate file counted once, capped at what the device actually
    // holds — the previous total summed overlapping decks and could exceed the
    // disk's used space, which reads as a made-up number.
    val reclaimable = state.headlineReclaimableBytes
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
            fontFamily = FontFamily.Monospace,
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
                else -> reclaimable.toReadableSize()
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
                else -> "flagged across ${state.candidateCount} files · " +
                    "de-duplicated, no file counted twice"
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
            fontFamily = FontFamily.Monospace,
            color = SdzColors.MutedGray,
        )
    }
}

@Composable
private fun DeckRow(group: DeckGroup, onClick: () -> Unit) {
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
        DeckThumbFan(group)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                group.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildString {
                    if (group.completedCount > 0) {
                        append("${group.remainingCount} of ${group.totalCount} left")
                    } else {
                        append("${group.totalCount} cards")
                    }
                    append(" · ${group.reclaimableBytes.toReadableSize()}")
                    // Sessions stay ≤50 cards; say so instead of splitting the
                    // dashboard into near-identical "Part N" rows.
                    if (group.parts.size > 1) append(" · ${group.parts.size} sessions")
                },
                color = SdzColors.MutedGray,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
            if (group.completedCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(FreeFill),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(group.progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(group.kind.accent()),
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
private fun DeckThumbFan(group: DeckGroup) {
    val thumbs = group.parts.firstOrNull()?.items.orEmpty().take(3)
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
        KindBadge(group.kind, Modifier.align(Alignment.BottomStart))
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

    /** Groups whose newest item is most recent first. */
    NEWEST,

    /** Most reclaimable bytes first. */
    LARGEST;

    fun apply(groups: List<DeckGroup>): List<DeckGroup> = when (this) {
        FOR_YOU -> groups
        NEWEST -> groups.sortedByDescending { group ->
            group.parts.maxOfOrNull { deck ->
                deck.items.maxOfOrNull { it.dateAddedMillis } ?: 0L
            } ?: 0L
        }
        LARGEST -> groups.sortedByDescending { it.reclaimableBytes }
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
