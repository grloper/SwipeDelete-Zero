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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.swipedelete.zero.ui.components.SdzIconButton
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.theme.SdzColor

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCloudSetup: () -> Unit = {},
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
            Text(
                "Settings",
                color = SdzColor.Phosphor,
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
                onOpenSetup = onOpenCloudSetup,
            )
            Spacer(Modifier.height(20.dp))
        }

        Text(
            "Exclusion Vault",
            color = SdzColor.Phosphor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Starred items and excluded folders are hidden from every future scan.",
            color = SdzColor.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        if (exclusions.isEmpty()) {
            // A compact card, not a centred label in a weight(1f) box — that
            // stretched to fill the screen and left the text marooned mid-page.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SdzColor.Surface1)
                    .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    "Vault is empty. Swipe up on a card to star & exclude it.",
                    color = SdzColor.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.weight(1f))
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
            color = SdzColor.TextSecondary,
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
    onOpenSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SdzColor.Surface1)
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                Icons.Rounded.CloudUpload,
                contentDescription = null,
                tint = SdzColor.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Google Drive Backup",
                color = SdzColor.Phosphor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            "Kept & starred files are uploaded once each — new keeps are picked up by the next run, nothing is uploaded twice.",
            color = SdzColor.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
        )

        when (state) {
            is BackupState.SignedOut -> {
                // A raw status code helps nobody: when a decoded diagnosis
                // exists, say what broke and send the user to the step that
                // fixes it rather than to a docs file they have to go find.
                val diagnostic = state.diagnostic
                if (diagnostic != null) {
                    Text(
                        diagnostic.headline,
                        color = SdzColor.Amber,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        diagnostic.fix,
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    BackupButton(text = "Fix in setup wizard", onClick = onOpenSetup)
                } else {
                    state.message?.let {
                        Text(it, color = SdzColor.Amber, style = MaterialTheme.typography.labelMedium)
                    }
                    BackupButton(text = "Connect Google account", onClick = onConnect)
                    Text(
                        "First time? The setup wizard walks through it and shows the exact "
                            + "values Google needs.",
                        color = SdzColor.TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    BackupButton(text = "Open setup wizard", onClick = onOpenSetup)
                }
            }

            is BackupState.Ready -> {
                Text(
                    "${state.accountEmail} · $pendingCount pending · $backedUpCount backed up",
                    color = SdzColor.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
                state.message?.let {
                    Text(it, color = SdzColor.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                BackupButton(
                    text = if (pendingCount > 0) "Back up $pendingCount file${if (pendingCount == 1) "" else "s"} now" else "Backed up — nothing pending",
                    onClick = onBackupNow,
                )
                Text(
                    "Disconnect",
                    color = SdzColor.TextSecondary,
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
                    color = SdzColor.Teal,
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SdzColor.Hairline),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (state.total == 0) 0f else state.done.toFloat() / state.total)
                            .fillMaxHeight()
                            .background(SdzColor.Teal),
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
            .background(SdzColor.TextSecondary.copy(alpha = 0.15f))
            .border(1.dp, SdzColor.TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = SdzColor.TextSecondary,
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
            .background(SdzColor.Surface1)
            .border(1.dp, SdzColor.Hairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (isFolder) Icons.Rounded.Folder else Icons.Rounded.Star,
            contentDescription = null,
            tint = if (isFolder) SdzColor.TextSecondary else SdzColor.Amber,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                ex.label,
                color = SdzColor.Phosphor,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (isFolder) "Excluded folder" else "Starred item",
                color = SdzColor.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        SdzIconButton(
            icon = SdzIcons.Reclaim,
            label = "Remove from vault",
            onClick = onRemove,
            tint = SdzColor.TextSecondary,
            glyphSize = 18.dp,
        )
    }
}
