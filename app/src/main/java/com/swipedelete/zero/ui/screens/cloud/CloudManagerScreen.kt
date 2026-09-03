package com.swipedelete.zero.ui.screens.cloud

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.swipedelete.zero.data.local.BackedUpFileEntity
import com.swipedelete.zero.data.local.CloudUploadEntity
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudUploadStats
import com.swipedelete.zero.ui.components.CloudConflictDialog
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.util.toReadableSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudManagerScreen(
    onBack: () -> Unit,
    viewModel: CloudManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedConflictFile by remember { mutableStateOf<BackedUpFileEntity?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    selectedConflictFile?.let { file ->
        val uri = file.contentUri
        val displayName = uri.substringAfterLast("/").ifBlank { "Media file" }
        CloudConflictDialog(
            fileName = displayName,
            sizeBytes = file.sizeBytes,
            isAlreadyBackedUp = true,
            onRebackup = {
                viewModel.rebackupFile(
                    uri = uri,
                    displayName = displayName,
                    mimeType = "image/jpeg",
                    sizeBytes = file.sizeBytes,
                )
            },
            onForgetLedger = {
                viewModel.forgetBackedUp(uri)
            },
            onDismiss = { selectedConflictFile = null },
        )
    }

    Scaffold(
        containerColor = SdzColor.Surface0,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cloud Control Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SdzColor.Phosphor,
                        )
                        val subtitle = when (val s = uiState.backupState) {
                            is BackupState.Ready -> s.accountEmail
                            is BackupState.Running -> "Uploading ${s.done}/${s.total} to Drive…"
                            is BackupState.SignedOut -> "Google Account disconnected"
                            BackupState.Unsupported -> "Cloud features not enabled"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = SdzColor.TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = SdzColor.Phosphor,
                        )
                    }
                },
                actions = {
                    if (uiState.isCheckingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            color = SdzColor.Teal,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::verifyConnection) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = "Verify Connection",
                                tint = SdzColor.Teal,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SdzColor.Surface0,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Real-Time Speed & Performance Meter
            CloudPerformanceCard(
                stats = uiState.uploadStats,
                onRetryAll = viewModel::retryAllFailed,
                onClearCompleted = viewModel::clearCompleted,
                onOpenPhotos = { viewModel.openInGooglePhotos(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SdzSpace.lg, vertical = SdzSpace.sm),
            )

            // Tabs: Active Queue vs Cloud Ledger
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = SdzColor.Surface1,
                contentColor = SdzColor.Phosphor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                        color = SdzColor.Teal,
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue (${uiState.uploads.size})")
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cloud Ledger (${uiState.backedUpFiles.size})")
                        }
                    }
                )
            }

            // Tab Content
            if (uiState.selectedTab == 0) {
                ActiveQueueTabContent(
                    uploads = uiState.uploads,
                    onRetry = viewModel::retryUpload,
                    onCancel = viewModel::cancelUpload,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CloudLedgerTabContent(
                    files = uiState.filteredBackedUpFiles,
                    searchQuery = uiState.searchQuery,
                    onSearchChange = viewModel::updateSearchQuery,
                    onSelectFile = { selectedConflictFile = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CloudPerformanceCard(
    stats: CloudUploadStats,
    onRetryAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onOpenPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(SdzRadius.md),
        colors = CardDefaults.cardColors(containerColor = SdzColor.Surface1),
    ) {
        Column(modifier = Modifier.padding(SdzSpace.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = SdzColor.Teal,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(SdzSpace.xs))
                    Text(
                        text = if (stats.isIdle) "Queue Idle" else "Uploading · ${stats.uploadSpeedBytesPerSec.toReadableSize()}/s",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SdzColor.Phosphor,
                    )
                }

                if (stats.etaSeconds != null) {
                    Text(
                        text = "ETA: ${stats.etaSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = SdzColor.Amber,
                    )
                }
            }

            Spacer(modifier = Modifier.height(SdzSpace.sm))

            // Progress Bar
            val animatedProgress by animateFloatAsState(targetValue = stats.overallProgress, label = "progress")
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(SdzRadius.pill)),
                color = SdzColor.Teal,
                trackColor = SdzColor.Surface3,
            )

            Spacer(modifier = Modifier.height(SdzSpace.sm))

            // Bytes & Count Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stats.uploadedBytes.toReadableSize()} / ${stats.totalBytes.toReadableSize()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SdzColor.TextSecondary,
                )
                Text(
                    text = "Queued: ${stats.queuedCount} · In Flight: ${stats.uploadingCount} · Done: ${stats.verifiedCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SdzColor.TextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(SdzSpace.sm))

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm),
            ) {
                if (stats.failedCount > 0) {
                    Button(
                        onClick = onRetryAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SdzColor.Amber),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry (${stats.failedCount})", color = SdzColor.OnAccent, style = MaterialTheme.typography.labelSmall)
                    }
                }

                if (stats.verifiedCount > 0) {
                    OutlinedButton(
                        onClick = onClearCompleted,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Finished", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onOpenPhotos,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photos App", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ActiveQueueTabContent(
    uploads: List<CloudUploadEntity>,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uploads.isEmpty()) {
        Box(
            modifier = modifier.padding(SdzSpace.h1),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = SdzColor.TextTertiary, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(SdzSpace.sm))
                Text("No items in upload queue", style = MaterialTheme.typography.titleSmall, color = SdzColor.TextSecondary)
                Text("Swipe Up on any media card to archive to Google Photos", style = MaterialTheme.typography.bodySmall, color = SdzColor.TextTertiary)
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(SdzSpace.md),
            verticalArrangement = Arrangement.spacedBy(SdzSpace.sm),
        ) {
            items(uploads, key = { it.contentUri }) { upload ->
                UploadItemRow(
                    upload = upload,
                    onRetry = { onRetry(upload.contentUri) },
                    onCancel = { onCancel(upload.contentUri) },
                )
            }
        }
    }
}

@Composable
private fun UploadItemRow(
    upload: CloudUploadEntity,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(SdzRadius.sm),
        colors = CardDefaults.cardColors(containerColor = SdzColor.Surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(SdzSpace.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(upload.contentUri)
                        .crossfade(true)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(SdzRadius.xs))
                        .background(SdzColor.Surface3),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = upload.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = SdzColor.Phosphor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${upload.bytesUploaded.toReadableSize()} / ${upload.sizeBytes.toReadableSize()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SdzColor.TextSecondary,
                    )
                }

                StateBadge(state = upload.state)
            }

            if (upload.state == CloudUploadEntity.STATE_UPLOADING) {
                Spacer(modifier = Modifier.height(SdzSpace.xs))
                val progress = if (upload.sizeBytes > 0) upload.bytesUploaded.toFloat() / upload.sizeBytes else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SdzColor.Teal,
                    trackColor = SdzColor.Surface3,
                )
            }

            if (upload.lastError != null) {
                Spacer(modifier = Modifier.height(SdzSpace.xs))
                Text(
                    text = upload.lastError,
                    style = MaterialTheme.typography.labelSmall,
                    color = SdzColor.Safelight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(SdzSpace.xs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (upload.state == CloudUploadEntity.STATE_FAILED) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: String) {
    val (label, bg, fg, icon) = when (state) {
        CloudUploadEntity.STATE_QUEUED -> Quadruple("Queued", SdzColor.Surface3, SdzColor.TextSecondary, Icons.Rounded.HourglassEmpty)
        CloudUploadEntity.STATE_UPLOADING -> Quadruple("Uploading", SdzColor.TealDim, SdzColor.Teal, Icons.Rounded.Sync)
        CloudUploadEntity.STATE_VERIFYING -> Quadruple("Verifying", SdzColor.AmberDim, SdzColor.Amber, Icons.Rounded.CloudSync)
        CloudUploadEntity.STATE_VERIFIED -> Quadruple("In Photos", SdzColor.AzureDim, SdzColor.Azure, Icons.Rounded.CheckCircle)
        CloudUploadEntity.STATE_FAILED -> Quadruple("Failed", SdzColor.SafelightDim, SdzColor.Safelight, Icons.Rounded.ErrorOutline)
        else -> Quadruple(state, SdzColor.Surface3, SdzColor.TextSecondary, Icons.Rounded.CloudOff)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(SdzRadius.pill))
            .background(bg)
            .padding(horizontal = SdzSpace.sm, vertical = SdzSpace.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CloudLedgerTabContent(
    files: List<BackedUpFileEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectFile: (BackedUpFileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by filename or ID…", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(SdzSpace.md),
            singleLine = true,
            shape = RoundedCornerShape(SdzRadius.md),
        )

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(SdzSpace.h1),
                contentAlignment = Alignment.Center,
            ) {
                Text("No matching backed up files found", color = SdzColor.TextSecondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(SdzSpace.md),
                verticalArrangement = Arrangement.spacedBy(SdzSpace.sm),
            ) {
                items(files, key = { it.contentUri }) { file ->
                    BackedUpFileRow(file = file, onClick = { onSelectFile(file) })
                }
            }
        }
    }
}

@Composable
private fun BackedUpFileRow(
    file: BackedUpFileEntity,
    onClick: () -> Unit,
) {
    val displayName = file.contentUri.substringAfterLast("/").ifBlank { "Remote file" }
    Card(
        shape = RoundedCornerShape(SdzRadius.sm),
        colors = CardDefaults.cardColors(containerColor = SdzColor.Surface2),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(SdzSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file.contentUri)
                    .crossfade(true)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(SdzRadius.xs))
                    .background(SdzColor.Surface3),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = SdzColor.Phosphor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Size: ${file.sizeBytes.toReadableSize()} · ID: ${file.remoteId.take(18)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = SdzColor.TextSecondary,
                )
            }

            Icon(
                imageVector = Icons.Rounded.CloudDone,
                contentDescription = "Tap to manage",
                tint = SdzColor.Teal,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
