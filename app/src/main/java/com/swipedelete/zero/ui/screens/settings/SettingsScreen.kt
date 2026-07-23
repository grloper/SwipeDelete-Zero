package com.swipedelete.zero.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.data.local.ExclusionEntity
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.ui.theme.SdzColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val pendingBackupCount by viewModel.pendingBackupCount.collectAsStateWithLifecycle()
    val backedUpCount by viewModel.backedUpCount.collectAsStateWithLifecycle()

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onSignInResult(result.data)
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
            Text(
                "Settings",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        if (backupState !is BackupState.Unsupported) {
            DriveBackupSection(
                state = backupState,
                pendingCount = pendingBackupCount,
                backedUpCount = backedUpCount,
                onConnect = { viewModel.signInIntent()?.let { signInLauncher.launch(it) } },
                onBackupNow = viewModel::backupNow,
                onDisconnect = viewModel::disconnectBackup,
            )
            Spacer(Modifier.height(20.dp))
        }

        Text(
            "Exclusion Vault",
            color = SdzColors.PureWhite,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Starred items and excluded folders are hidden from every future scan.",
            color = SdzColors.MutedGray,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        if (exclusions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Text("Vault is empty. Swipe up on a card to star & exclude it.", color = SdzColors.MutedGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(exclusions, key = { it.id }) { ex ->
                    ExclusionRow(ex = ex, onRemove = { viewModel.remove(ex.id) })
                }
            }
        }

        Text(
            if (backupState is BackupState.Unsupported) {
                "SwipeDelete Zero · GPL v3 · 100% Offline · Zero Net-Permissions"
            } else {
                "SwipeDelete Zero · GPL v3 · Cloud build — network used only for opt-in Drive backup"
            },
            color = SdzColors.MutedGray,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 20.dp),
        )
    }
}

@Composable
private fun DriveBackupSection(
    state: BackupState,
    pendingCount: Int,
    backedUpCount: Int,
    onConnect: () -> Unit,
    onBackupNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColors.Obsidian)
            .border(1.dp, SdzColors.Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = SdzColors.CrispCyan,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Google Drive Backup",
                color = SdzColors.PureWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Kept & starred files are uploaded once each — new keeps are picked up by the next run, nothing is uploaded twice.",
            color = SdzColors.MutedGray,
            style = MaterialTheme.typography.labelMedium,
        )

        when (state) {
            is BackupState.SignedOut -> {
                state.message?.let {
                    Text(it, color = SdzColors.HyperCoral, style = MaterialTheme.typography.labelMedium)
                }
                BackupButton(text = "Connect Google Drive", onClick = onConnect)
            }

            is BackupState.Ready -> {
                Text(
                    "${state.accountEmail} · $pendingCount pending · $backedUpCount backed up",
                    color = SdzColors.CrispCyan,
                    style = MaterialTheme.typography.labelMedium,
                )
                state.message?.let {
                    Text(it, color = SdzColors.MutedGray, style = MaterialTheme.typography.labelMedium)
                }
                BackupButton(
                    text = if (pendingCount > 0) "Back up $pendingCount file${if (pendingCount == 1) "" else "s"} now" else "Backed up — nothing pending",
                    onClick = onBackupNow,
                )
                Text(
                    "Disconnect",
                    color = SdzColors.MutedGray,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDisconnect)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            is BackupState.Running -> {
                Text(
                    "Uploading ${state.done} of ${state.total}…",
                    color = SdzColors.ElectricEmerald,
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SdzColors.Hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (state.total == 0) 0f else state.done.toFloat() / state.total)
                            .fillMaxHeight()
                            .background(SdzColors.ElectricEmerald),
                    )
                }
            }

            BackupState.Unsupported -> Unit
        }
    }
}

@Composable
private fun BackupButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SdzColors.CrispCyan.copy(alpha = 0.15f))
            .border(1.dp, SdzColors.CrispCyan.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = SdzColors.CrispCyan,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExclusionRow(ex: ExclusionEntity, onRemove: () -> Unit) {
    val isFolder = ex.type == ExclusionEntity.TYPE_EXCLUDED_FOLDER
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
        Icon(
            if (isFolder) Icons.Rounded.Folder else Icons.Rounded.Star,
            contentDescription = null,
            tint = if (isFolder) SdzColors.CrispCyan else SdzColors.StarGold,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                ex.label,
                color = SdzColors.PureWhite,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (isFolder) "Excluded folder" else "Starred item",
                color = SdzColors.MutedGray,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Remove",
            tint = SdzColors.HyperCoral,
            modifier = Modifier.size(22.dp).clickable(onClick = onRemove),
        )
    }
}
