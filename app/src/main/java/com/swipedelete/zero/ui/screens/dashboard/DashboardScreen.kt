package com.swipedelete.zero.ui.screens.dashboard

import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.domain.scanner.AnalysisRunState
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.domain.model.DeckGroup
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.ui.components.SdzButton
import com.swipedelete.zero.ui.components.SdzButtonStyle
import com.swipedelete.zero.ui.components.SdzChip
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.components.SdzLevel
import com.swipedelete.zero.ui.components.SdzSectionHeader
import com.swipedelete.zero.ui.components.SdzSurface
import com.swipedelete.zero.ui.components.SdzWordmark
import com.swipedelete.zero.ui.components.StorageMeter
import com.swipedelete.zero.ui.screens.staging.PurgeEffect
import com.swipedelete.zero.ui.screens.staging.StagingSheet
import com.swipedelete.zero.ui.screens.staging.StagingViewModel
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzTouch
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize
import kotlin.math.roundToInt

internal fun photoReviewPermissions(sdk: Int): Array<String> = when {
    sdk >= 34 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    sdk >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * Home.
 *
 * Structured around one question — *should I act right now?* — so the storage
 * state is the hero and there is exactly one primary action. The previous home
 * gave six cards equal weight and put a large, less actionable "flagged" figure
 * above a grey, unlabelled storage sliver, which inverted the hierarchy.
 *
 * Buckets and sprints are no longer two unrelated stacked sections: they are
 * two *lenses* on the same library, stated as such and switched with a toggle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onOpenDeck: (Deck) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    stagingViewModel: StagingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadError by viewModel.loadError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onPermissionResult(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var lensName by rememberSaveable { mutableStateOf(Lens.CONTENT.name) }
    val lens = Lens.valueOf(lensName)
    var showStaging by rememberSaveable { mutableStateOf(false) }
    val currentShowStaging by rememberUpdatedState(showStaging)
    var completedPurge by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> viewModel.onPermissionResult(result.values.any { it }) }

    // The OS delete dialog launcher lives in this stable composition, never in
    // the sheet — dismissing the sheet mid-dialog would drop the result.
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
                is PurgeEffect.Completed -> {
                    if (effect.purgedCount > 0) {
                        if (effect.mode == ExecutionMode.PERMANENT_PURGE) {
                            completedPurge = effect.freedBytes to effect.purgedCount
                        }
                        if (!currentShowStaging) Toast.makeText(
                            context,
                            if (effect.mode == ExecutionMode.PERMANENT_PURGE)
                                "${effect.purgedCount} files deleted · ${effect.freedBytes.toReadableSize()} reclaimed"
                            else "${effect.purgedCount} files moved to Android Trash",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(context, "No files were deleted. Your queue is still here.", Toast.LENGTH_LONG).show()
                    }
                }
                is PurgeEffect.NeedsSafAccess -> Toast.makeText(
                    context,
                    "${effect.uriCount} non-media files need folder access.",
                    Toast.LENGTH_LONG,
                ).show()
                is PurgeEffect.Message ->
                    Toast.makeText(context, effect.text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val sections = remember(state.decks) { LibrarySections.from(state.decks) }

    Box(
        Modifier
            .fillMaxSize()
            .background(SdzColor.Surface0),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = SdzSpace.xl,
                end = SdzSpace.xl,
                top = SdzSpace.md,
                bottom = SdzSpace.h4 * 2 +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(SdzSpace.lg),
        ) {
            item("brand") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SdzWordmark(markSize = 26.dp)
                    Box(
                        modifier = Modifier
                            .size(SdzTouch.minTarget)
                            .clip(RoundedCornerShape(SdzRadius.pill))
                            .clickable(onClick = onOpenSettings)
                            .semantics { contentDescription = "Settings" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = SdzIcons.Duplicates,
                            contentDescription = null,
                            tint = SdzColor.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (!state.hasMediaAccess) {
                item("permission") {
                    SdzSurface(level = SdzLevel.Raised, contentPadding = SdzSpace.xl) {
                        Text("Your library, your call", style = SdzType.Subtitle, color = SdzColor.Phosphor)
                        Text(
                            "Choose photos to review. Videos and audio are optional and are not requested here. " +
                                "Review happens on this device. " +
                                (if (com.swipedelete.zero.BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)
                                    "Google Photos backup is optional. Cleanup is unavailable in this test build. "
                                else "Local cleanup requires your confirmation. ") +
                                "You can choose selected photos on supported Android versions.",
                            style = SdzType.BodySmall,
                            color = SdzColor.TextSecondary,
                        )
                        SdzButton(
                            label = "Choose photos",
                            onClick = { permissionLauncher.launch(photoReviewPermissions(Build.VERSION.SDK_INT)) },
                            style = SdzButtonStyle.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
            // THE ONE ACTION.
            item("primary-action") {
                PrimaryCallToAction(
                    loading = state.loading,
                    candidateCount = state.candidateCount,
                    candidateBytes = state.headlineReclaimableBytes,
                    onStart = { sections.suggestedDeck()?.let(onOpenDeck) },
                    enabled = sections.suggestedDeck() != null,
                )
            }

            item("access-scope") {
                if (loadError != null) {
                    Text(loadError ?: "", color = SdzColor.TextSecondary)
                    SdzButton(label = "Retry", onClick = { viewModel.loadDecks(forceRefresh = true) }, style = SdzButtonStyle.Secondary)
                } else if (!state.loading && state.decks.isEmpty()) {
                    Text("No review cards in the current selection. Choose other photos or scan again.", style = SdzType.BodySmall, color = SdzColor.TextSecondary)
                }
                Text("${state.accessDescription}. Changing selection refreshes this review list.",
                    style = SdzType.BodySmall, color = SdzColor.TextSecondary)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(SdzSpace.sm),
                ) {
                    androidx.compose.material3.TextButton(onClick = { permissionLauncher.launch(photoReviewPermissions(Build.VERSION.SDK_INT)) }) { Text("Choose photos", maxLines = 1) }
                    androidx.compose.material3.TextButton(onClick = { permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 33)
                        arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO) else photoReviewPermissions(Build.VERSION.SDK_INT)) }) { Text("Videos", maxLines = 1) }
                    androidx.compose.material3.TextButton(onClick = { permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 33)
                        arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO) else photoReviewPermissions(Build.VERSION.SDK_INT)) }) { Text("Audio", maxLines = 1) }
                }
            }
            if (lens == Lens.CONTENT) {
                item("content-scan") {
                    ContentScanPanel(state = state, onScan = viewModel::scanNow)
                }
            }

            // Storage overview.
            item("storage") {
                SdzSurface(level = SdzLevel.Raised, contentPadding = SdzSpace.xl) {
                    StorageMeter(
                        usedBytes = state.usedStorageBytes,
                        freeBytes = state.freeStorageBytes,
                        totalBytes = state.totalStorageBytes,
                        reclaimableBytes = state.stagedBytes,
                    )
                }
            }

            item("lens") {
                Column(verticalArrangement = Arrangement.spacedBy(SdzSpace.sm)) {
                    SdzSectionHeader("Your library")
                    Text(
                        "The same photos, seen two ways.",
                        style = SdzType.BodySmall,
                        color = SdzColor.TextSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm)) {
                        SdzChip("By content", lens == Lens.CONTENT) { lensName = Lens.CONTENT.name }
                        SdzChip("By date", lens == Lens.DATE) { lensName = Lens.DATE.name }
                    }
                }
            }

            when {
                state.loading -> items(3) { SkeletonRow() }

                lens == Lens.CONTENT -> items(
                    sections.contentLenses(state.hasAnalysis || state.analysisState == AnalysisRunState.DONE),
                    key = { it.id },
                ) { entry ->
                    LibraryRow(
                        entry = entry,
                        scanning = state.isScanning,
                        onOpen = { entry.deck?.let(onOpenDeck) },
                    )
                }

                else -> items(sections.dateLenses(), key = { it.id }) { entry ->
                    LibraryRow(
                        entry = entry,
                        scanning = false,
                        onOpen = { entry.deck?.let(onOpenDeck) },
                    )
                }
            }
            }
        }

        AnimatedVisibility(
            visible = state.stagedCount > 0,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            StagingBar(
                stagedCount = state.stagedCount,
                stagedBytes = state.stagedBytes,
                onClick = { showStaging = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(SdzSpace.xl),
            )
        }

        if (showStaging) {
            StagingSheet(
                viewModel = stagingViewModel,
                onDismiss = { showStaging = false },
                onOpenBackupSetup = {
                    showStaging = false
                    onOpenSettings()
                },
                completedPurge = completedPurge,
                onCelebrationFinished = { completedPurge = null },
            )
        }
    }
}

private enum class Lens { CONTENT, DATE }

/**
 * A single row model.
 *
 * Both lenses render through this one shape, so every sibling row in every list
 * exposes the same fields in the same order — icon, title, item count, size,
 * progress. Previously one sprint card showed a percentage where its neighbour
 * showed a card count, which makes a list impossible to scan.
 */
private data class LibraryEntry(
    val id: String,
    val title: String,
    val icon: @Composable () -> Painter,
    val remainingCount: Int,
    val remainingBytes: Long,
    val completedCount: Int,
    val totalCount: Int,
    val deck: Deck?,
    val coverUri: android.net.Uri?,
    /** True when this lane depends on analysis that has not run yet. */
    val needsScan: Boolean = false,
) {
    val progress: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

private data class LibrarySections(
    val duplicates: List<Deck>,
    val blurry: List<Deck>,
    val cameraVideos: List<Deck> = emptyList(),
    val largeVideos: List<Deck>,
    val screenshots: List<Deck>,
    val documents: List<Deck>,
    val hotspots: List<DeckGroup>,
    val months: List<DeckGroup>,
) {
    /** What the single primary button should open. */
    fun suggestedDeck(): Deck? =
        (largeVideos + cameraVideos + duplicates + blurry + screenshots + documents)
            .firstOrNull { it.remainingCount > 0 }
            ?: months.firstOrNull { it.remainingCount > 0 }?.nextDeck
            ?: hotspots.firstOrNull { it.remainingCount > 0 }?.nextDeck

    fun contentLenses(hasAnalysis: Boolean): List<LibraryEntry> = listOfNotNull(
        entry("duplicates", "Duplicates & near-shots", { SdzIcons.Duplicates }, duplicates, !hasAnalysis),
        entry("blurry", "Blurry media", { SdzIcons.Blurry }, blurry, !hasAnalysis),
        if (cameraVideos.isNotEmpty()) entry("camera_videos", "Camera videos (Largest first)", { SdzIcons.LargeVideo }, cameraVideos, false) else null,
        entry("large", "Large videos (>1 GB)", { SdzIcons.LargeVideo }, largeVideos, false),
        entry("screenshots", "Screenshots", { SdzIcons.Screenshots }, screenshots, false),
        entry("documents", "Text & documents", { SdzIcons.Documents }, documents, !hasAnalysis),
    ) + hotspots.map { group ->
        LibraryEntry(
            id = group.id,
            title = group.title,
            icon = { SdzIcons.Duplicates },
            remainingCount = group.remainingCount,
            remainingBytes = group.remainingBytes,
            completedCount = group.completedCount,
            totalCount = group.totalCount,
            deck = group.nextDeck,
            coverUri = group.coverItem?.contentUri,
        )
    }

    fun dateLenses(): List<LibraryEntry> = months.map { group ->
        LibraryEntry(
            id = group.id,
            title = group.title,
            icon = { SdzIcons.LargeVideo },
            remainingCount = group.remainingCount,
            remainingBytes = group.remainingBytes,
            completedCount = group.completedCount,
            totalCount = group.totalCount,
            deck = group.nextDeck,
            coverUri = group.coverItem?.contentUri,
        )
    }

    private fun entry(
        id: String,
        title: String,
        icon: @Composable () -> Painter,
        decks: List<Deck>,
        needsScan: Boolean,
    ) = LibraryEntry(
        id = id,
        title = title,
        icon = icon,
        // Count and size both derive from the remaining items, so they can
        // never disagree the way "0 items · 1.2 GB" used to.
        remainingCount = decks.sumOf { it.remainingCount },
        remainingBytes = decks.sumOf { it.remainingBytes },
        completedCount = decks.sumOf { it.completedCount },
        totalCount = decks.sumOf { it.totalCount },
        deck = decks.firstOrNull { it.remainingCount > 0 } ?: decks.firstOrNull(),
        coverUri = decks.firstOrNull()?.items?.firstOrNull()?.contentUri,
        needsScan = needsScan && decks.isEmpty(),
    )

    companion object {
        fun from(decks: List<Deck>): LibrarySections {
            val large = decks.filter { it.id.startsWith("heavy:xl") }
            val cameraVids = decks.filter { it.id.startsWith("camera:videos") }
            return LibrarySections(
                duplicates = decks.filter { it.kind == DeckKind.DUPLICATES },
                blurry = decks.filter { it.kind == DeckKind.BLURRY },
                cameraVideos = cameraVids,
                largeVideos = large,
                screenshots = decks.filter { it.kind == DeckKind.SCREENSHOTS },
                documents = decks.filter { it.kind == DeckKind.DOCUMENTS },
                hotspots = DeckGroup.from(
                    decks.filter {
                        (it.kind == DeckKind.CLUTTER_HOTSPOT || (it.kind == DeckKind.HEAVY_HITTERS && it !in large)) &&
                            it !in cameraVids
                    }
                ),
                months = DeckGroup.from(decks.filter { it.kind == DeckKind.TIME_MACHINE }),
            )
        }
    }
}

/** The single dominant action. Exactly one Primary button exists on this screen. */
/** One shared entry point: the worker analyzes every supported content category. */
@Composable
private fun ContentScanPanel(state: DashboardUiState, onScan: () -> Unit) {
    SdzSurface(level = SdzLevel.Raised, contentPadding = SdzSpace.xl) {
        Text("CONTENT SCAN", style = SdzType.Overline, color = SdzColor.TextTertiary)
        Text(
            text = when {
                state.isScanning -> "Scanning your library…"
                state.analysisState == AnalysisRunState.FAILED -> "Scan interrupted"
                state.analysisState == AnalysisRunState.DONE && state.loading -> "Updating categories…"
                state.analysisState == AnalysisRunState.DONE -> "Scan finished"
                else -> "One scan. All categories."
            },
            style = SdzType.Subtitle,
            color = SdzColor.Phosphor,
        )
        Text(
            text = when {
                state.isScanning -> "Checking accessible photos and videos for duplicates, blur and text. You can keep browsing."
                state.analysisState == AnalysisRunState.FAILED -> "The scan did not finish. Tap below to try again."
                state.analysisState == AnalysisRunState.DONE -> "Review the results in By content below. Scan again after adding photos or changing media access."
                else -> "Find duplicates, blurry photos and text across the media you allow. One tap checks all content categories on this device."
            },
            style = SdzType.BodySmall,
            color = SdzColor.TextSecondary,
        )
        if (state.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Content scan in progress" },
                color = SdzColor.Phosphor,
                trackColor = SdzColor.Surface3,
            )
        }
        SdzButton(
            label = when {
                state.isScanning -> "Scanning all content…"
                state.analysisState == AnalysisRunState.FAILED -> "Retry scan"
                state.analysisState == AnalysisRunState.DONE || state.hasAnalysis -> "Scan all content again"
                else -> "Scan all content"
            },
            onClick = onScan,
            style = SdzButtonStyle.Primary,
            enabled = !state.isScanning && !state.loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Scanning does not delete files.", style = SdzType.LabelSmall, color = SdzColor.TextTertiary)
    }
}

@Composable
private fun PrimaryCallToAction(
    loading: Boolean,
    candidateCount: Int,
    candidateBytes: Long,
    onStart: () -> Unit,
    enabled: Boolean,
) {
    SdzSurface(level = SdzLevel.Card, contentPadding = SdzSpace.xl) {
        Text("START HERE", style = SdzType.Overline, color = SdzColor.TextTertiary)
        AnimatedVisibility(visible = !loading && candidateCount == 0, enter = fadeIn(), exit = fadeOut()) {
            Image(
                painter = painterResource(com.swipedelete.zero.R.drawable.ic_empty_review),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
        }
        Text(
            text = when {
                loading -> "Sorting your library…"
                candidateCount == 0 -> "Ready when you are"
                else -> "Review $candidateCount flagged files"
            },
            style = SdzType.Subtitle,
            color = SdzColor.Phosphor,
        )
        Text(
            text = when {
                loading -> "This takes a moment on a large library."
                candidateCount == 0 -> "Scan all content above to look for matches, or add photos to review."
                else -> "${candidateBytes.toReadableSize()} could come back, de-duplicated."
            },
            style = SdzType.BodySmall,
            color = SdzColor.TextSecondary,
        )
        Spacer(Modifier.height(SdzSpace.xs))
        if (enabled) {
            SdzButton(
                label = "Start reviewing",
                onClick = onStart,
                style = SdzButtonStyle.Primary,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The one list row. Identical field order in both lenses:
 * cover, title, "N left · SIZE", progress.
 */
@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    scanning: Boolean,
    onOpen: () -> Unit,
) {
    val interactive = entry.remainingCount > 0 && entry.deck != null
    SdzSurface(
        level = SdzLevel.Card,
        onClick = if (interactive) onOpen else null,
        contentPadding = SdzSpace.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
        ) {
            RowCover(entry)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SdzSpace.xxs)) {
                Text(
                    entry.title,
                    style = SdzType.Subtitle,
                    color = if (interactive) SdzColor.Phosphor else SdzColor.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Same two facts, same order, every row, always.
                    text = if (entry.needsScan) {
                        if (scanning) "Waiting for scan results…" else "Use Scan all content above"
                    } else "${entry.remainingCount} left · ${entry.remainingBytes.toReadableSize()}",
                    style = SdzType.Numeric,
                    color = SdzColor.TextSecondary,
                )
                if (entry.completedCount > 0 && entry.totalCount > 0) {
                    ProgressTrack(entry.progress)
                }
            }
            when {
                entry.completedCount > 0 -> Text(
                    "${(entry.progress * 100).roundToInt()}%",
                    style = SdzType.Numeric,
                    color = SdzColor.TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun RowCover(entry: LibraryEntry) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(SdzRadius.sm))
            .background(SdzColor.Surface3),
        contentAlignment = Alignment.Center,
    ) {
        if (entry.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(entry.coverUri)
                    .size(160)
                    .crossfade(true)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = entry.icon(),
                contentDescription = null,
                tint = SdzColor.TextTertiary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(SdzRadius.xs))
            .background(SdzColor.Track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(SdzColor.Azure),
        )
    }
}

@Composable
private fun SkeletonRow() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    SdzSurface(level = SdzLevel.Card, contentPadding = SdzSpace.md) {
        Row(
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(SdzRadius.sm))
                    .background(SdzColor.Track)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SdzSpace.sm)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(SdzRadius.xs))
                        .background(SdzColor.Track)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.3f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(SdzRadius.xs))
                        .background(SdzColor.Track)
                )
            }
        }
    }
}

/**
 * The staging bar. Amber, because it is reclaimable space — not red. It is a
 * safe, reversible review queue, and dressing it as danger was one of the
 * clearest colour-meaning collisions in the old build.
 */
@Composable
private fun StagingBar(
    stagedCount: Int,
    stagedBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SdzRadius.lg))
            .background(SdzColor.Surface4)
            .clickable(onClick = onClick)
            .padding(horizontal = SdzSpace.xl, vertical = SdzSpace.lg)
            .semantics {
                contentDescription =
                    "Review $stagedCount staged files, ${stagedBytes.toReadableSize()} kept locally"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
    ) {
        Icon(
            painter = SdzIcons.Reclaim,
            contentDescription = null,
            tint = SdzColor.Amber,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Review ${stagedBytes.toReadableSize()} staged",
                style = SdzType.Label,
                color = SdzColor.Phosphor,
            )
            Text(
                if (stagedCount == 1) "1 file staged" else "$stagedCount files staged",
                style = SdzType.BodySmall,
                color = SdzColor.TextSecondary,
            )
        }
        Text("Review", style = SdzType.Label, color = SdzColor.Amber, textAlign = TextAlign.End)
    }
}
