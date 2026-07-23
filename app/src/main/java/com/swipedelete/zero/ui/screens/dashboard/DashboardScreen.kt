package com.swipedelete.zero.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.domain.model.DeckKind
import com.swipedelete.zero.ui.components.GlassCard
import com.swipedelete.zero.ui.components.GradientArcGauge
import com.swipedelete.zero.ui.components.MonoFamily
import com.swipedelete.zero.ui.components.glassSurface
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize

private val CardShape = RoundedCornerShape(24.dp)

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
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Header(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            StorageHeroCard(
                state = state,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Text(
                "Recommended Decks",
                style = MaterialTheme.typography.titleLarge,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03f).em,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            if (state.loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(236.dp)
                        .padding(horizontal = 20.dp),
                    Alignment.Center,
                ) {
                    Text("Scanning your library…", color = SdzColors.MutedGray, fontFamily = MonoFamily)
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.decks, key = { it.id }) { deck ->
                        DeckCard(deck = deck, onClick = { onOpenDeck(deck) })
                    }
                }
            }

            // Breathing room so the floating drawer button never hides content.
            Spacer(Modifier.height(120.dp))
        }

        // Thumb-zone bottom launcher — lifted clear of the system nav bar.
        ReviewDrawerButton(
            stagedCount = state.stagedCount,
            stagedBytes = state.stagedBytes,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenStaging()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun Header(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "SwipeDelete Zero",
                style = MaterialTheme.typography.headlineMedium,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03f).em,
            )
            Text(
                "100% OFFLINE · ZERO NET-PERMISSIONS",
                style = MaterialTheme.typography.labelMedium,
                color = SdzColors.ElectricEmerald,
                fontFamily = MonoFamily,
                letterSpacing = 0.5.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .glassSurface(CircleShape)
                .clickable { onOpenSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = SdzColors.MutedGray,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Storage hero card
// ---------------------------------------------------------------------------

@Composable
private fun StorageHeroCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxWidth(), shape = CardShape) {
        // Subtle cyan radial glow behind the gauge.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(SdzColors.CrispCyan.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.26f, size.height * 0.5f),
                            radius = size.height * 1.1f,
                        )
                    )
                }
        )

        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientArcGauge(progress = state.usedFraction, diameter = 104.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(state.usedFraction * 100).toInt()}%",
                        color = SdzColors.CrispCyan,
                        fontWeight = FontWeight.Black,
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "USED",
                        color = SdzColors.MutedGray,
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "STORAGE",
                    color = SdzColors.MutedGray,
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.freeStorageBytes.toReadableSize(),
                        color = SdzColors.PureWhite,
                        fontWeight = FontWeight.Black,
                        fontFamily = MonoFamily,
                        letterSpacing = (-0.03f).em,
                        fontSize = 30.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "FREE",
                        color = SdzColors.ElectricEmerald,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                StagedStatPill(stagedBytes = state.stagedBytes)
                Text(
                    "${state.usedStorageBytes.toReadableSize()} used of ${state.totalStorageBytes.toReadableSize()}",
                    color = SdzColors.MutedGray,
                    fontFamily = MonoFamily,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Small glowing emerald pill: "0 B staged". */
@Composable
private fun StagedStatPill(stagedBytes: Long) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(SdzColors.ElectricEmerald.copy(alpha = 0.12f))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(SdzColors.ElectricEmerald.copy(alpha = 0.10f), Color.Transparent),
                        radius = size.width * 0.7f,
                    )
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(SdzColors.ElectricEmerald)
        )
        Text(
            "${stagedBytes.toReadableSize()} staged",
            color = SdzColors.ElectricEmerald,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFamily,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ---------------------------------------------------------------------------
// Deck card
// ---------------------------------------------------------------------------

@Composable
private fun DeckCard(deck: Deck, onClick: () -> Unit) {
    val accent = deck.accentColor()
    Box(
        modifier = Modifier
            .width(190.dp)
            .height(236.dp)
            .glassSurface(CardShape)
            .clickable(onClick = onClick),
    ) {
        // Blurred, darkened media backdrop pulled from the deck's own files.
        DeckThumbnail(deck = deck, accent = accent, modifier = Modifier.matchParentSize())

        // Legibility scrim.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SdzColors.PitchBlack.copy(alpha = 0.30f),
                            SdzColors.PitchBlack.copy(alpha = 0.88f),
                        )
                    )
                )
        )

        // "N CARDS" glass pill, top-right.
        CardCountPill(
            count = deck.totalCount,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )

        // Bottom content block.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                deck.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03f).em,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                deck.subtitle,
                color = SdzColors.MutedGray,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                deck.reclaimableBytes.toReadableSize(),
                color = SdzColors.CrispCyan,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily,
                style = MaterialTheme.typography.labelLarge,
            )
            // Slim progress bar.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(SdzColors.Hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(deck.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}

@Composable
private fun DeckThumbnail(deck: Deck, accent: Color, modifier: Modifier = Modifier) {
    val first = deck.items.firstOrNull()
    if (first == null) {
        Box(
            modifier.background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), SdzColors.Obsidian))
            )
        )
        return
    }
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(first.contentUri)
        .crossfade(true)
        .apply { if (first.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
        .build()
    AsyncImage(
        model = request,
        contentDescription = deck.title,
        contentScale = ContentScale.Crop,
        modifier = modifier.blur(16.dp),
    )
}

@Composable
private fun CardCountPill(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .glassSurface(CircleShape, fill = SdzColors.PitchBlack.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            "$count CARDS",
            color = SdzColors.PureWhite,
            fontFamily = MonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom launcher
// ---------------------------------------------------------------------------

@Composable
private fun ReviewDrawerButton(
    stagedCount: Int,
    stagedBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.horizontalGradient(
                    listOf(SdzColors.HyperCoral, Color(0xFFFF5B52))
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = SdzColors.PureWhite)
            Text(
                "Review Staging Drawer",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.03f).em,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            if (stagedCount == 0) "EMPTY" else "$stagedCount · ${stagedBytes.toReadableSize()}",
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Bold,
            fontFamily = MonoFamily,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun Deck.accentColor(): Color = when (kind) {
    DeckKind.HEAVY_HITTERS -> SdzColors.HyperCoral
    DeckKind.DUPLICATES -> SdzColors.StarGold
    DeckKind.BLURRY -> SdzColors.CrispCyan
    else -> SdzColors.ElectricEmerald
}
