package com.swipedelete.zero.ui.screens.cloud

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipedelete.zero.domain.backup.BackupState
import com.swipedelete.zero.domain.backup.CloudActivity
import com.swipedelete.zero.domain.backup.CloudActivityStatus
import com.swipedelete.zero.domain.backup.CloudActivitySummary
import com.swipedelete.zero.ui.components.SdzButton
import com.swipedelete.zero.ui.components.SdzButtonStyle
import com.swipedelete.zero.ui.components.SdzIcons
import com.swipedelete.zero.ui.components.SdzLevel
import com.swipedelete.zero.ui.components.SdzSectionHeader
import com.swipedelete.zero.ui.components.SdzSurface
import com.swipedelete.zero.ui.components.SdzTopBar
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzMotion
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * The receipt book. Every file this app has sent to the cloud, where it went,
 * what the provider last said about it, and what is moving right now.
 *
 * Two honesty rules govern the copy on this screen:
 *
 *  1. "Uploaded" and "Verified" are never used interchangeably. The first means
 *     a write was acknowledged; the second means the item was read back.
 *  2. Nothing here claims to know about Google Photos' own auto-backup. The
 *     upload scope cannot read the user's library, so a photo the Photos app
 *     backed up is invisible to this app — and the screen says so rather than
 *     letting an empty list imply "nothing is backed up".
 */
@Composable
fun CloudMonitorScreen(
    onBack: () -> Unit,
    viewModel: CloudMonitorViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val verifyState by viewModel.verifyState.collectAsStateWithLifecycle()

    val connected = backupState is BackupState.Ready || backupState is BackupState.Running

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SdzColor.Surface0)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = SdzSpace.xl),
    ) {
        SdzTopBar(
            title = "Cloud backups",
            subtitle = if (summary.total == 0) null else "${summary.total} files tracked",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SdzSpace.md),
            contentPadding = PaddingValues(bottom = SdzSpace.h2),
        ) {
            item("summary") {
                SummaryCard(
                    summary = summary,
                    verifyState = verifyState,
                    canVerify = connected && summary.total > summary.inFlight,
                    onVerify = viewModel::verifyAll,
                    onDismiss = viewModel::dismissVerifyResult,
                )
            }

            item("scope") { ScopeNote() }

            if (rows.isEmpty()) {
                item("empty") { EmptyState(connected = connected) }
            } else {
                item("header") {
                    SdzSectionHeader(
                        title = "Activity",
                        modifier = Modifier.padding(top = SdzSpace.sm),
                    )
                }
                items(rows, key = { it.contentUri }) { row ->
                    ActivityRow(
                        row = row,
                        onRetry = { viewModel.retry(row.contentUri) },
                        onForget = { viewModel.forget(row.contentUri) },
                    )
                }
            }
        }
    }
}

/** Accent for a status. Teal is the app's one "goes to the cloud" colour. */
private fun accentFor(status: CloudActivityStatus): Color = when (status) {
    CloudActivityStatus.Queued,
    CloudActivityStatus.Uploading,
    CloudActivityStatus.Verifying,
    CloudActivityStatus.Verified -> SdzColor.Teal
    CloudActivityStatus.Uploaded -> SdzColor.TextSecondary
    CloudActivityStatus.Unverified -> SdzColor.Amber
    CloudActivityStatus.Missing, CloudActivityStatus.Failed -> SdzColor.Safelight
}

@Composable
private fun SummaryCard(
    summary: CloudActivitySummary,
    verifyState: VerifyState,
    canVerify: Boolean,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    SdzSurface(modifier = Modifier.fillMaxWidth(), level = SdzLevel.Raised) {
        Text("Sent by this app", style = SdzType.Overline, color = SdzColor.TextTertiary)
        Text(
            summary.uploadedBytes.toReadableSize(),
            style = SdzType.HeroNumber,
            color = SdzColor.Phosphor,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = SdzSpace.sm),
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.xl),
        ) {
            Stat("Verified", summary.verified, SdzColor.Teal)
            Stat("Not re-checked", summary.uploaded, SdzColor.TextSecondary)
            if (summary.inFlight > 0) Stat("In flight", summary.inFlight, SdzColor.Teal)
            if (summary.problems > 0) Stat("Needs attention", summary.problems, SdzColor.Safelight)
        }

        if (summary.lastVerifiedAtMillis > 0) {
            Text(
                "Last checked ${relativeTime(summary.lastVerifiedAtMillis)}",
                style = SdzType.BodySmall,
                color = SdzColor.TextTertiary,
            )
        }

        when (verifyState) {
            VerifyState.Idle -> SdzButton(
                label = "Check my backups",
                onClick = onVerify,
                style = SdzButtonStyle.Secondary,
                enabled = canVerify,
                modifier = Modifier.fillMaxWidth().padding(top = SdzSpace.sm),
            )
            VerifyState.Running -> Text(
                "Reading every file back from the server…",
                style = SdzType.BodySmall,
                color = SdzColor.Teal,
                modifier = Modifier.padding(top = SdzSpace.md),
            )
            is VerifyState.Done -> {
                val result = verifyState.result
                Text(
                    result.message,
                    style = SdzType.Body,
                    color = if (result.missing > 0) SdzColor.Safelight else SdzColor.Phosphor,
                    modifier = Modifier.padding(top = SdzSpace.md),
                )
                SdzButton(
                    label = "Done",
                    onClick = onDismiss,
                    style = SdzButtonStyle.Tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(SdzSpace.xxs)) {
        Text("$value", style = SdzType.StatNumber, color = accent)
        Text(label, style = SdzType.LabelSmall, color = SdzColor.TextTertiary)
    }
}

/**
 * The limitation, stated in the app rather than buried in a changelog. Without
 * it an empty or short list reads as "your photos are not backed up", which is
 * a claim this app has no way to make.
 */
@Composable
private fun ScopeNote() {
    SdzSurface(modifier = Modifier.fillMaxWidth(), level = SdzLevel.Card) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = SdzIcons.Archive,
                contentDescription = null,
                tint = SdzColor.TextTertiary,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(SdzSpace.xs)) {
                Text("This list is only what this app sent", style = SdzType.Label, color = SdzColor.Phosphor)
                Text(
                    "Google only grants this app permission to add photos, never to read " +
                        "your library. If the Google Photos app backed something up on its " +
                        "own, it will not appear here — that does not mean it is unsaved. " +
                        "Check the Photos app itself to see everything in your library.",
                    style = SdzType.BodySmall,
                    color = SdzColor.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(connected: Boolean) {
    SdzSurface(modifier = Modifier.fillMaxWidth(), level = SdzLevel.Card) {
        Text(
            if (connected) "Nothing sent yet" else "Not connected",
            style = SdzType.Subtitle,
            color = SdzColor.Phosphor,
        )
        Text(
            if (connected) {
                "Swipe up on a card to archive it to Google Photos, or run a backup " +
                    "from Settings. Every upload will be listed here with its receipt."
            } else {
                "Connect a Google account in Settings to archive files to the cloud."
            },
            style = SdzType.BodySmall,
            color = SdzColor.TextSecondary,
        )
    }
}

@Composable
private fun ActivityRow(
    row: CloudActivity,
    onRetry: () -> Unit,
    onForget: () -> Unit,
) {
    val accent = accentFor(row.status)
    SdzSurface(
        modifier = Modifier.fillMaxWidth(),
        level = SdzLevel.Card,
        radius = SdzRadius.md,
        accent = if (row.status.isProblem) accent else null,
        contentPadding = SdzSpace.lg,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.displayName,
                    style = SdzType.Label,
                    color = SdzColor.Phosphor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${row.sizeBytes.toReadableSize()} · ${row.destination.label}",
                    style = SdzType.Numeric,
                    color = SdzColor.TextSecondary,
                )
            }
            StatusPill(status = row.status, accent = accent)
        }

        if (row.progress != null) {
            ProgressTrack(progress = row.progress, accent = accent)
        }

        // Status line: the receipt, or the reason there isn't one.
        val detail = when {
            row.error != null -> row.error
            row.remoteId != null -> "id ${row.remoteId.take(18)}…"
            else -> null
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail != null) {
                Text(
                    detail,
                    style = SdzType.BodySmall,
                    color = if (row.error != null) accent else SdzColor.TextTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (row.atMillis > 0) {
                Text(
                    relativeTime(row.atMillis),
                    style = SdzType.BodySmall,
                    color = SdzColor.TextTertiary,
                )
            }
        }

        if (row.retryable) {
            SdzButton(
                label = if (row.status == CloudActivityStatus.Missing) {
                    "Forget & back up again"
                } else {
                    "Retry upload"
                },
                onClick = if (row.status == CloudActivityStatus.Missing) onForget else onRetry,
                style = SdzButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusPill(status: CloudActivityStatus, accent: Color) {
    val shape = RoundedCornerShape(SdzRadius.pill)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = SdzSpace.md, vertical = SdzSpace.xs),
    ) {
        Text(status.label, style = SdzType.LabelSmall, color = accent)
    }
}

@Composable
private fun ProgressTrack(progress: Float, accent: Color) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(SdzMotion.Standard),
        label = "upload-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(SdzRadius.pill))
            .background(SdzColor.Track)
            .semantics { contentDescription = "${(progress * 100).toInt()} percent uploaded" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxSize()
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(accent)
                .clearAndSetSemantics { },
        )
    }
}

private fun relativeTime(millis: Long): String = DateUtils.getRelativeTimeSpanString(
    millis,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
).toString()
