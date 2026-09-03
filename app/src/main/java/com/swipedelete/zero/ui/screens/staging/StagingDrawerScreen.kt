package com.swipedelete.zero.ui.screens.staging

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.backup.PhotosArchive
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.domain.model.MediaType
import com.swipedelete.zero.ui.components.SortChip
import com.swipedelete.zero.ui.components.SdzIconButton
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.util.toReadableSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StagingDrawerScreen(
    onBack: () -> Unit,
    viewModel: StagingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The staged file currently opened in the full-screen preview, if any.
    var previewItem by remember { mutableStateOf<StagedFileEntity?>(null) }

    // Keep the preview in sync with the queue: if the shown file is restored or
    // purged elsewhere, close the overlay instead of previewing a ghost.
    LaunchedEffect(state.items, previewItem) {
        val shown = previewItem ?: return@LaunchedEffect
        if (state.items.none { it.contentUri == shown.contentUri }) previewItem = null
    }

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

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SdzColor.Surface0)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SdzIconButton(
                    icon = SdzIcons.Back,
                    label = "Back",
                    onClick = onBack,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        "Staging Drawer",
                        color = SdzColor.Phosphor,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${state.count} files • ${state.totalBytes.toReadableSize()} ready to purge",
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (state.count > 0) {
                    Text(
                        "Clear",
                        color = SdzColor.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.clearQueue() },
                    )
                }
            }

            if (state.count == 0) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    Text("Nothing staged yet. Swipe left to queue files.", color = SdzColor.TextSecondary)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
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
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.contentUri }) { item ->
                        StagedRow(
                            item = item,
                            onPreview = { previewItem = item },
                            onRestore = { viewModel.restore(item.contentUri) },
                        )
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

        AnimatedVisibility(
            visible = previewItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            previewItem?.let { item ->
                StagedPreviewOverlay(
                    item = item,
                    onRestore = {
                        viewModel.restore(item.contentUri)
                        previewItem = null
                    },
                    onDismiss = { previewItem = null },
                )
            }
        }
    }
}

@Composable
internal fun StagedRow(
    item: StagedFileEntity,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColor.Surface1)
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onPreview)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StagedThumbnail(item, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.displayName,
                color = SdzColor.Phosphor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${item.mediaType} · ${item.sizeBytes.toReadableSize()}",
                color = SdzColor.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
            if (item.sourceDeckId == PhotosArchive.VERIFIED_SOURCE_DECK) {
                Text(
                    "☁ Verified in Google Photos — safe to delete",
                    color = SdzColor.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onRestore)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Rounded.Restore, contentDescription = "Restore", tint = SdzColor.Azure, modifier = Modifier.size(20.dp))
            Text("Restore", color = SdzColor.Azure, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StagedThumbnail(item: StagedFileEntity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SdzColor.Surface0)
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (item.hasVisualPreview) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.contentUri.toUri())
                    .size(128)
                    .crossfade(true)
                    .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isVideo) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = SdzColor.Phosphor,
                    modifier = Modifier
                        .size(20.dp)
                        .background(SdzColor.Surface0.copy(alpha = 0.5f), CircleShape),
                )
            }
        } else {
            Icon(
                item.fallbackGlyph,
                contentDescription = null,
                tint = SdzColor.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Full-screen look at a staged file before it is purged — the last chance to
 * recognise a keeper. Images render full-bleed; videos show their first frame.
 */
@Composable
private fun StagedPreviewOverlay(
    item: StagedFileEntity,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColor.Surface0)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    color = SdzColor.Phosphor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${item.sizeBytes.toReadableSize()} · staged ${item.stagedAtLabel()}",
                    color = SdzColor.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SdzColor.Surface1)
                    .border(1.dp, SdzColor.Hairline, CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close preview",
                    tint = SdzColor.Phosphor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SdzColor.Surface1)
                .border(1.dp, SdzColor.Hairline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.hasVisualPreview) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.contentUri.toUri())
                        .crossfade(true)
                        .apply { if (item.isVideo) decoderFactory(VideoFrameDecoder.Factory()) }
                        .build(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.isVideo) {
                    Text(
                        "First frame · video won't play here",
                        color = SdzColor.Phosphor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(SdzColor.Surface0.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        item.fallbackGlyph,
                        contentDescription = null,
                        tint = SdzColor.TextSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(
                        "No visual preview for this file type",
                        color = SdzColor.TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SdzColor.Azure.copy(alpha = 0.15f))
                    .border(1.dp, SdzColor.Azure.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onRestore)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {}
                Icon(Icons.Rounded.Restore, contentDescription = null, tint = SdzColor.Azure, modifier = Modifier.size(20.dp))
                Text(
                    "Restore",
                    color = SdzColor.Azure,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
                Box(Modifier.weight(1f)) {}
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SdzColor.Surface1)
                    .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Keep in queue",
                    color = SdzColor.Phosphor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private val StagedFileEntity.isVideo: Boolean
    get() = mediaType == MediaType.VIDEO.name

private val StagedFileEntity.hasVisualPreview: Boolean
    get() = mediaType == MediaType.IMAGE.name || mediaType == MediaType.VIDEO.name

private val StagedFileEntity.fallbackGlyph: ImageVector
    get() = if (mediaType == MediaType.AUDIO.name) Icons.Rounded.MusicNote else Icons.Rounded.Description

private fun StagedFileEntity.stagedAtLabel(): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(stagedAtMillis))

@Composable
internal fun ExecutionModeToggle(
    mode: ExecutionMode,
    onSelect: (ExecutionMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SdzColor.Surface1)
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SegmentButton(
            text = "30-Day OS Trash",
            selected = mode == ExecutionMode.OS_TRASH_30_DAY,
            accent = SdzColor.Azure,
            modifier = Modifier.weight(1f),
        ) { onSelect(ExecutionMode.OS_TRASH_30_DAY) }
        SegmentButton(
            text = "Permanent Delete",
            selected = mode == ExecutionMode.PERMANENT_PURGE,
            accent = SdzColor.Red,
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
            color = if (selected) accent else SdzColor.TextSecondary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun PurgeCta(
    bytes: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) SdzColor.Red else SdzColor.TextSecondary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = SdzIcons.Delete,
                contentDescription = null,
                tint = if (enabled) SdzColor.OnAccent else SdzColor.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Delete and Free Up " + bytes.toReadableSize(),
                color = if (enabled) SdzColor.OnAccent else SdzColor.TextSecondary,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
