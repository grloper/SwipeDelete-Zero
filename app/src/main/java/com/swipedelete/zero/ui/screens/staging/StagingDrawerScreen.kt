package com.swipedelete.zero.ui.screens.staging

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import android.widget.Toast
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.theme.SdzColors
import com.swipedelete.zero.ui.util.toReadableSize

@Composable
fun StagingDrawerScreen(
    onBack: () -> Unit,
    viewModel: StagingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val confirmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConfirmationResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColors.PitchBlack)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
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
                    "Staging Drawer",
                    color = SdzColors.PureWhite,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${state.count} files • ${state.totalBytes.toReadableSize()} ready to purge",
                    color = SdzColors.CrispCyan,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (state.count > 0) {
                Text(
                    "Clear",
                    color = SdzColors.MutedGray,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { viewModel.clearQueue() },
                )
            }
        }

        if (state.count == 0) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("Nothing staged yet. Swipe left to queue files.", color = SdzColors.MutedGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.contentUri }) { item ->
                    StagedRow(item = item, onRestore = { viewModel.restore(item.contentUri) })
                }
            }

            ExecutionModeToggle(
                mode = state.mode,
                onSelect = viewModel::setMode,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            PurgeCta(
                bytes = state.totalBytes,
                enabled = !state.purging,
                onClick = viewModel::purge,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun StagedRow(item: StagedFileEntity, onRestore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                color = SdzColors.PureWhite,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${item.mediaType} · ${item.sizeBytes.toReadableSize()}",
                color = SdzColors.MutedGray,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onRestore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Restore, contentDescription = "Restore", tint = SdzColors.ElectricEmerald, modifier = Modifier.size(20.dp))
            Text("Restore", color = SdzColors.ElectricEmerald, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExecutionModeToggle(
    mode: ExecutionMode,
    onSelect: (ExecutionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentButton(
            text = "30-Day OS Trash",
            selected = mode == ExecutionMode.OS_TRASH_30_DAY,
            accent = SdzColors.ElectricEmerald,
            modifier = Modifier.weight(1f),
        ) { onSelect(ExecutionMode.OS_TRASH_30_DAY) }
        SegmentButton(
            text = "Permanent Purge",
            selected = mode == ExecutionMode.PERMANENT_PURGE,
            accent = SdzColors.HyperCoral,
            modifier = Modifier.weight(1f),
        ) { onSelect(ExecutionMode.PERMANENT_PURGE) }
    }
}

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) accent else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) accent else SdzColors.MutedGray,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun PurgeCta(
    bytes: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) SdzColors.HyperCoral else SdzColors.MutedGray)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "🔥 Free Up ${bytes.toReadableSize()} Now",
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
