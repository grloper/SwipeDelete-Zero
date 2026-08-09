package com.swipedelete.zero.ui.screens.dual

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.domain.model.ComparisonPair
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.ui.components.SdzButton
import com.swipedelete.zero.ui.components.SdzButtonStyle
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.components.SdzTopBar
import com.swipedelete.zero.ui.haptics.rememberSdzHaptics
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Duplicate comparison.
 *
 * Interaction: **tap the one you want to keep.** The previous screen stacked
 * four equal-weight text buttons ("Keep A · Trash B", "Keep B · Trash A",
 * "Keep Both", "Trash Both") which forced the user to read four sentences and
 * mentally map letters onto pictures. Here the photograph itself is the
 * target — the primary decision costs one tap on the thing you are already
 * looking at — and the two-sided outcomes are demoted to a small secondary
 * row where they belong.
 *
 * Badges appear only where [ComparisonPair] can prove a real difference, so
 * two identical files show none at all rather than contradicting themselves.
 */
@Composable
fun DualCardSplitScreen(
    onBack: () -> Unit,
    viewModel: DualCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberSdzHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColor.Surface0)
            .statusBarsPadding()
            .padding(horizontal = SdzSpace.lg),
    ) {
        SdzTopBar(
            title = "Compare",
            onBack = onBack,
            subtitle = if (state.total > 0) "${state.index + 1} of ${state.total}" else null,
        )

        val pair = state.current
        if (pair == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (state.loading) "Loading…" else "No duplicates left to compare",
                    style = SdzType.Body,
                    color = SdzColor.TextSecondary,
                )
            }
            return@Column
        }

        Text(
            text = if (pair.isIndistinguishable) {
                "These look identical. Keeping either is fine."
            } else {
                "Tap the one to keep — the other moves to Staging."
            },
            style = SdzType.BodySmall,
            color = SdzColor.TextSecondary,
            modifier = Modifier.padding(vertical = SdzSpace.sm),
        )

        ComparePane(
            item = pair.primary,
            label = "A",
            isSharper = pair.sharperItem === pair.primary,
            isHigherRes = pair.higherResItem === pair.primary,
            isSmaller = pair.smallerItem === pair.primary,
            onKeep = {
                haptics.commit(com.swipedelete.zero.domain.model.SwipeDirection.RIGHT)
                viewModel.act(CompareAction.KEEP_A_TRASH_B)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Spacer()

        ComparePane(
            item = pair.secondary,
            label = "B",
            isSharper = pair.sharperItem === pair.secondary,
            isHigherRes = pair.higherResItem === pair.secondary,
            isSmaller = pair.smallerItem === pair.secondary,
            onKeep = {
                haptics.commit(com.swipedelete.zero.domain.model.SwipeDirection.RIGHT)
                viewModel.act(CompareAction.KEEP_B_TRASH_A)
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        // Demoted: these are the uncommon outcomes, so they read as links, not
        // as peers of the primary decision.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = SdzSpace.md),
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md, Alignment.CenterHorizontally),
        ) {
            SdzButton(
                label = "Keep both",
                onClick = { viewModel.act(CompareAction.KEEP_BOTH) },
                style = SdzButtonStyle.Tertiary,
            )
            SdzButton(
                label = "Reclaim both",
                onClick = { viewModel.act(CompareAction.TRASH_BOTH) },
                style = SdzButtonStyle.Tertiary,
            )
        }
    }
}

@Composable
private fun Spacer() {
    Box(Modifier.size(SdzSpace.md))
}

/**
 * One candidate. The whole pane is the tap target for "keep this one", which
 * makes the primary action as large as the content.
 */
@Composable
private fun ComparePane(
    item: MediaItem,
    label: String,
    isSharper: Boolean,
    isHigherRes: Boolean,
    isSmaller: Boolean,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(1f, label = "pane-scale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(SdzRadius.lg))
            .background(SdzColor.Surface1)
            .clickable(onClick = onKeep)
            .semantics {
                contentDescription = "Keep photo $label, ${item.sizeBytes.toReadableSize()}"
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.contentUri)
                .crossfade(true)
                .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Identity chip
        Box(
            modifier = Modifier
                .padding(SdzSpace.md)
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(SdzColor.Surface0.copy(alpha = 0.72f))
                .padding(horizontal = SdzSpace.md, vertical = SdzSpace.xs),
        ) {
            Text(label, style = SdzType.Label, color = SdzColor.Phosphor)
        }

        // Facts, bottom-left. Only badges the data supports.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(SdzSpace.md),
            verticalArrangement = Arrangement.spacedBy(SdzSpace.xs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SdzSpace.xs)) {
                if (isSharper) Badge("Sharper")
                if (isHigherRes) Badge("Higher res")
                if (isSmaller) Badge("Smaller file")
            }
            Text(
                "${item.sizeBytes.toReadableSize()} · ${item.resolutionLabel}",
                style = SdzType.Numeric,
                color = SdzColor.Phosphor,
            )
        }

        // "Keep" affordance, bottom-right, using the same shield + azure as
        // everywhere else in the app.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(SdzSpace.md)
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(SdzColor.Azure)
                .padding(horizontal = SdzSpace.lg, vertical = SdzSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.xs),
        ) {
            Icon(
                painter = SdzIcons.Keep,
                contentDescription = null,
                tint = SdzColor.OnAccent,
                modifier = Modifier.size(16.dp),
            )
            Text("Keep", style = SdzType.Label, color = SdzColor.OnAccent)
        }
    }
}

/**
 * A comparison fact. Neutral phosphor on a dark plate, deliberately *not*
 * colour-coded: these are attributes of a file, not decisions, and colouring
 * them would dilute the four meanings the palette already carries.
 */
@Composable
private fun Badge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SdzRadius.xs))
            .background(SdzColor.Surface0.copy(alpha = 0.78f))
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(SdzRadius.xs))
            .padding(horizontal = SdzSpace.sm, vertical = 3.dp),
    ) {
        Text(
            label,
            style = SdzType.LabelSmall,
            color = SdzColor.Phosphor,
            textAlign = TextAlign.Center,
        )
    }
}
