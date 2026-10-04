package com.swipedelete.zero.ui.screens.staging

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.data.local.StagedFileEntity
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.components.FreedCelebration
import com.swipedelete.zero.ui.components.PurgeConfirmSheet
import com.swipedelete.zero.ui.components.SdzButton
import com.swipedelete.zero.ui.components.SdzButtonStyle
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize

/** Queue actions and the locked cleanup explanation share one bounded scroll surface.
 * The OS confirmation launcher stays in the stable dashboard composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingSheet(
    viewModel: StagingViewModel,
    onDismiss: () -> Unit,
    onOpenBackupSetup: () -> Unit = {},
    completedPurge: Pair<Long, Int>? = null,
    onCelebrationFinished: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirming by remember { mutableStateOf(false) }
    var previewItem by remember { mutableStateOf<StagedFileEntity?>(null) }
    val gutter = if (LocalConfiguration.current.screenWidthDp < 400) 16.dp else 20.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SdzColor.Surface1,
        contentColor = SdzColor.Phosphor,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        // Material 1.3 follows the OS theme for this separate window. The app is
        // always dark, including when Android itself uses a light theme.
        val view = LocalView.current
        SideEffect {
            generateSequence(view.parent) { it.parent }
                .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = false
                        isAppearanceLightNavigationBars = false
                    }
                }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp)
                .navigationBarsPadding().testTag("queue-list"),
            contentPadding = PaddingValues(start = gutter, end = gutter, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Review queue", color = SdzColor.Phosphor, style = SdzType.Title,
                        modifier = Modifier.fillMaxWidth().testTag("queue-sheet-title"))
                    Text("${state.count} ${if (state.count == 1) "file" else "files"} · ${state.totalBytes.toReadableSize()}",
                        color = SdzColor.TextSecondary, style = SdzType.Numeric, modifier = Modifier.testTag("queue-count"))
                    if (state.count > 0) SdzButton("Unstage all", onClick = viewModel::clearQueue,
                        style = SdzButtonStyle.Secondary, modifier = Modifier.fillMaxWidth().testTag("queue-unstage-all"))
                }
            }
            if (state.count == 0) {
                item("empty") {
                    Text("Nothing staged. Swipe left on cards to queue files.",
                        color = SdzColor.TextSecondary, style = SdzType.Body)
                }
            } else {
                item("sort") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sort", color = SdzColor.TextSecondary, style = SdzType.BodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            QueueChoice("Newest", state.sort == StagingSort.NEWEST, Modifier.weight(1f)) { viewModel.setSort(StagingSort.NEWEST) }
                            QueueChoice("Largest", state.sort == StagingSort.LARGEST, Modifier.weight(1f)) { viewModel.setSort(StagingSort.LARGEST) }
                        }
                    }
                }
                items(state.items, key = { it.contentUri }) { item ->
                    StagedRow(item = item, onPreview = { previewItem = item },
                        onRestore = { viewModel.restore(item.contentUri) }, stackActions = true)
                }
                if (state.backupRequired) {
                    item("backup") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Google Photos items · ${state.verifiedCount}/${state.count} found",
                                color = SdzColor.Phosphor, style = SdzType.Row)
                            if (state.backupConnected) Text(
                                when {
                                    state.failedBackupCount > 0 -> "${state.failedBackupCount} upload(s) failed. Retry them in Backups."
                                    state.pendingBackupCount > 0 -> "Local files stay untouched while uploads finish."
                                    else -> "Google Photos item found. Original-byte restore still requires Drive."
                                }, color = SdzColor.TextSecondary, style = SdzType.BodySmall)
                            SdzButton(
                                label = if (!state.backupConnected) "Backup options" else if (state.pendingBackupCount > 0) "Back up staged files" else "Recheck backups",
                                onClick = { if (!state.backupConnected) onOpenBackupSetup() else viewModel.backUpStaged() },
                                style = SdzButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item("mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cleanup mode", color = SdzColor.TextSecondary, style = SdzType.BodySmall)
                        // Full-width options keep real text insets at every font scale.
                        QueueChoice("30-Day OS Trash", state.mode == ExecutionMode.OS_TRASH_30_DAY,
                            Modifier.fillMaxWidth().testTag("queue-mode-trash")) { viewModel.setMode(ExecutionMode.OS_TRASH_30_DAY) }
                        QueueChoice("Permanent Delete", state.mode == ExecutionMode.PERMANENT_PURGE,
                            Modifier.fillMaxWidth().testTag("queue-mode-permanent")) { viewModel.setMode(ExecutionMode.PERMANENT_PURGE) }
                    }
                }
                item("cleanup") {
                    Column(Modifier.fillMaxWidth().testTag("queue-cleanup"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.cleanupAvailable) {
                            Text("Cleanup is unavailable in this build. Originals stay on this device.",
                                color = SdzColor.Phosphor, style = SdzType.BodySmall,
                                modifier = Modifier.fillMaxWidth().testTag("queue-lock"))
                        } else state.cleanupLockExplanation?.let { explanation ->
                            Text(explanation, color = SdzColor.Phosphor, style = SdzType.BodySmall,
                                modifier = Modifier.fillMaxWidth().testTag("queue-lock"))
                        }
                        PurgeCta(bytes = state.totalBytes, mode = state.mode,
                            enabled = !state.purging && state.canDelete,
                            onClick = { confirming = true }, modifier = Modifier.fillMaxWidth().testTag("queue-cleanup-action"))
                    }
                }
            }
            completedPurge?.let { (bytes, count) ->
                item("celebration") {
                    FreedCelebration(freedBytes = bytes, fileCount = count, onFinished = onCelebrationFinished)
                }
            }
        }
    }
    if (confirming) {
        PurgeConfirmSheet(fileCount = state.count, totalBytes = state.totalBytes, mode = state.mode,
            onConfirm = { confirming = false; viewModel.purge() }, onDismiss = { confirming = false })
    }
    previewItem?.let { item ->
        Dialog(onDismissRequest = { previewItem = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StagedPreviewOverlay(item = item,
                onRestore = { viewModel.restore(item.contentUri); previewItem = null },
                onDismiss = { previewItem = null })
        }
    }
}

@Composable
private fun QueueChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp))
        .background(if (selected) SdzColor.Surface2 else SdzColor.Surface1)
        .border(1.dp, if (selected) SdzColor.Sage else SdzColor.Boundary, RoundedCornerShape(12.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = SdzColor.Phosphor, fontWeight = FontWeight.Medium,
            style = SdzType.Label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
