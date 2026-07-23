package com.swipedelete.zero.ui.screens.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.Deck
import com.swipedelete.zero.ui.components.StorageRing
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize

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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "SwipeDelete Zero",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SdzColors.PureWhite,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "100% Offline · Zero Net-Permissions",
                        style = MaterialTheme.typography.labelMedium,
                        color = SdzColors.ElectricEmerald,
                    )
                }
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = SdzColors.MutedGray,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onOpenSettings() },
                )
            }

            StorageHeader(state)

            Text(
                "Recommended Decks",
                style = MaterialTheme.typography.titleLarge,
                color = SdzColors.PureWhite,
            )

            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(220.dp), Alignment.Center) {
                    Text("Scanning your library…", color = SdzColors.MutedGray)
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(state.decks, key = { it.id }) { deck ->
                        DeckCard(deck = deck, onClick = { onOpenDeck(deck) })
                    }
                }
            }
        }

        // Thumb-zone bottom drawer launcher.
        ReviewDrawerButton(
            stagedCount = state.stagedCount,
            stagedBytes = state.stagedBytes,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenStaging()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
        )
    }
}

@Composable
private fun StorageHeader(state: DashboardUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StorageRing(progress = state.usedFraction, diameter = 96.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(state.usedFraction * 100).toInt()}%",
                    color = SdzColors.CrispCyan,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("used", color = SdzColors.MutedGray, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Staged Space Ready to Free",
                color = SdzColors.MutedGray,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                state.stagedBytes.toReadableSize(),
                color = SdzColors.ElectricEmerald,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                "${state.freeStorageBytes.toReadableSize()} free of ${state.totalStorageBytes.toReadableSize()}",
                color = SdzColors.MutedGray,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun DeckCard(deck: Deck, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StorageRing(
            progress = deck.progress,
            diameter = 72.dp,
            strokeWidth = 8.dp,
            color = deck.accentColor(),
        ) {
            Text(
                "${deck.remainingCount}",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column {
            Text(
                deck.title,
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                deck.subtitle,
                color = SdzColors.MutedGray,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Text(
            deck.reclaimableBytes.toReadableSize(),
            color = SdzColors.CrispCyan,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

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
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.HyperCoral)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = SdzColors.PureWhite)
            Text(
                "Review Staging Drawer",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            if (stagedCount == 0) "Empty" else "$stagedCount · ${stagedBytes.toReadableSize()}",
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun Deck.accentColor() = when (kind) {
    com.swipedelete.zero.domain.model.DeckKind.HEAVY_HITTERS -> SdzColors.HyperCoral
    com.swipedelete.zero.domain.model.DeckKind.DUPLICATES -> SdzColors.StarGold
    com.swipedelete.zero.domain.model.DeckKind.BLURRY -> SdzColors.CrispCyan
    else -> SdzColors.ElectricEmerald
}
