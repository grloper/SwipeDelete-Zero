package com.swipedelete.zero.ui.screens.dual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.components.DiffBadgeRow
import com.swipedelete.zero.ui.components.MediaPreview
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Dual-card split view for the Duplicates / Blurry decks. Renders Photo A on top
 * and Photo B on the bottom, auto-annotates each with which dimension it wins,
 * and offers four single-tap resolutions.
 */
@Composable
fun DualCardSplitScreen(
    onBack: () -> Unit,
    viewModel: DualCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack)
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = SdzColors.PureWhite,
                modifier = Modifier.size(26.dp).clickable(onClick = onBack),
            )
            Text(
                "Compare · ${state.index.coerceAtMost(state.total)}/${state.total}",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        val pair = state.current
        when {
            state.loading -> Center("Loading comparisons…")
            state.isComplete || pair == null -> Center("✅ All duplicates resolved")
            else -> {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ComparePane(
                        label = "A",
                        item = pair.primary,
                        pair = pair,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    ComparePane(
                        label = "B",
                        item = pair.secondary,
                        pair = pair,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                CompareActionBar(onAct = viewModel::act, modifier = Modifier.padding(vertical = 20.dp))
            }
        }
    }
}

@Composable
private fun ComparePane(
    label: String,
    item: MediaItem,
    pair: ComparisonPair,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(22.dp)),
    ) {
        MediaPreview(item = item, modifier = Modifier.fillMaxSize())

        Text(
            label,
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(SdzColors.PitchBlack.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        DiffBadgeRow(
            isSharper = pair.sharperItem?.id == item.id,
            isHigherRes = pair.higherResItem.id == item.id,
            isSmaller = pair.smallerItem.id == item.id,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )

        Text(
            "${item.sizeBytes.toReadableSize()} · ${item.resolutionLabel}",
            color = SdzColors.CrispCyan,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(SdzColors.PitchBlack.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CompareActionBar(
    onAct: (CompareAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Keep A · Trash B", SdzColors.ElectricEmerald, Modifier.weight(1f)) {
                onAct(CompareAction.KEEP_A_TRASH_B)
            }
            PillButton("Keep B · Trash A", SdzColors.ElectricEmerald, Modifier.weight(1f)) {
                onAct(CompareAction.KEEP_B_TRASH_A)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Keep Both", SdzColors.CrispCyan, Modifier.weight(1f)) {
                onAct(CompareAction.KEEP_BOTH)
            }
            PillButton("Trash Both", SdzColors.HyperCoral, Modifier.weight(1f)) {
                onAct(CompareAction.TRASH_BOTH)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColors.Obsidian)
            .border(2.dp, accent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Center(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = SdzColors.MutedGray, style = MaterialTheme.typography.titleMedium)
    }
}
