package com.swipedelete.zero.ui.screens.staging

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.swipedelete.zero.ui.components.PurgeConfirmSheet
import com.swipedelete.zero.ui.components.FreedCelebration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.components.SortChip
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Safety Staging as a modal bottom sheet over the dashboard (UX_BLUEPRINT
 * §3.5): the queue, execution-mode toggle and purge CTA in a thumb-reach
 * surface, headed by the lifetime "X GB Reclaimed" counter.
 *
 * The ActivityResult launcher for the OS delete/trash dialog deliberately does
 * NOT live here — it is registered by the stable dashboard composition and
 * results flow into the shared [StagingViewModel], so dismissing the sheet
 * while the system dialog is up can never drop the confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingSheet(
    viewModel: StagingViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // The custom explainer runs before the OS dialog, never instead of it.
    var confirming by remember { mutableStateOf(false) }
    var celebrating by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SdzColor.Surface1,
        contentColor = SdzColor.Phosphor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(state, onClear = viewModel::clearQueue)

            if (state.count == 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nothing staged. Swipe left on cards to queue files.",
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Sort",
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    SortChip("Newest", state.sort == StagingSort.NEWEST) {
                        viewModel.setSort(StagingSort.NEWEST)
                    }
                    SortChip("Largest", state.sort == StagingSort.LARGEST) {
                        viewModel.setSort(StagingSort.LARGEST)
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.contentUri }) { item ->
                        StagedRow(
                            item = item,
                            onPreview = {},
                            onRestore = { viewModel.restore(item.contentUri) },
                        )
                    }
                }

                ExecutionModeToggle(
                    mode = state.mode,
                    onSelect = viewModel::setMode,
                )

                PurgeCta(
                    bytes = state.totalBytes,
                    enabled = !state.purging,
                    onClick = { confirming = true },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            celebrating?.let { (bytes, count) ->
                FreedCelebration(
                    freedBytes = bytes,
                    fileCount = count,
                    onFinished = { celebrating = null },
                )
            }
        }
    }

    if (confirming) {
        PurgeConfirmSheet(
            fileCount = state.count,
            totalBytes = state.totalBytes,
            mode = state.mode,
            onConfirm = {
                confirming = false
                celebrating = state.totalBytes to state.count
                viewModel.purge()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun SheetHeader(state: StagingUiState, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Safety Staging",
                color = SdzColor.Phosphor,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${state.count} files • ${state.totalBytes.toReadableSize()} ready to purge",
                color = SdzColor.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            ReclaimedCounter(state.lifetimeReclaimedBytes)
        }
        if (state.count > 0) {
            Text(
                "Clear",
                color = SdzColor.TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(4.dp),
            )
        }
    }
}

/** Rolling "14.2 GB Reclaimed" lifetime tally, animated as purges land. */
@Composable
private fun ReclaimedCounter(lifetimeBytes: Long) {
    val animated by animateFloatAsState(
        targetValue = lifetimeBytes.toFloat(),
        animationSpec = tween(700),
        label = "reclaimed-counter",
    )
    Text(
        "${animated.toLong().toReadableSize()} Reclaimed all-time",
        color = SdzColor.Azure,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelMedium,
    )
}
