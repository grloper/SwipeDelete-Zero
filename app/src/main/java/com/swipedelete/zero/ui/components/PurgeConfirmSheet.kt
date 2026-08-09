package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.swipedelete.zero.domain.model.ExecutionMode
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Shown *before* the system's "move to trash" dialog.
 *
 * Android's dialog appears abruptly and says almost nothing about consequence
 * or reversibility, so meeting it cold is the most anxious moment in the app.
 * This screen sets the expectation first: what is going, how much comes back,
 * whether it can be undone, and that a system prompt is the next thing they
 * will see.
 *
 * The two execution modes are given genuinely different treatments, because
 * they carry genuinely different risk. 30-day trash is reversible and gets the
 * ordinary primary button. Permanent purge is the only irreversible action in
 * the app, and it is the only place the safelight red appears as a button.
 */
@Composable
fun PurgeConfirmSheet(
    fileCount: Int,
    totalBytes: Long,
    mode: ExecutionMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val permanent = mode == ExecutionMode.PERMANENT_PURGE

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SdzRadius.xl))
                .background(SdzColor.Surface4)
                .padding(SdzSpace.xl),
            verticalArrangement = Arrangement.spacedBy(SdzSpace.lg),
        ) {
            Text(
                text = if (permanent) "Delete permanently?" else "Move to trash?",
                style = SdzType.Title,
                color = SdzColor.Phosphor,
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(SdzSpace.sm),
            ) {
                Text(
                    totalBytes.toReadableSize(),
                    style = SdzType.StatNumber,
                    color = SdzColor.Amber,
                )
                Text(
                    "comes back",
                    style = SdzType.Body,
                    color = SdzColor.TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(SdzSpace.sm)) {
                Fact(
                    icon = SdzIcons.Reclaim,
                    tint = SdzColor.Amber,
                    text = if (fileCount == 1) "1 file will be removed"
                    else "$fileCount files will be removed",
                )
                Fact(
                    icon = if (permanent) SdzIcons.Reclaim else SdzIcons.Undo,
                    tint = if (permanent) SdzColor.Safelight else SdzColor.Azure,
                    text = if (permanent) {
                        "This cannot be undone. The files are gone immediately."
                    } else {
                        "Recoverable for 30 days from your gallery's Trash."
                    },
                )
                Fact(
                    icon = SdzIcons.Keep,
                    tint = SdzColor.TextSecondary,
                    text = "Android will ask you to confirm next — that prompt is the real one.",
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
            ) {
                SdzButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    style = SdzButtonStyle.Tertiary,
                    modifier = Modifier.weight(1f),
                )
                SdzButton(
                    label = if (permanent) "Delete forever" else "Move to trash",
                    onClick = onConfirm,
                    style = if (permanent) SdzButtonStyle.Destructive else SdzButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Fact(icon: Painter, tint: Color, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(SdzRadius.pill))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Text(text, style = SdzType.BodySmall, color = SdzColor.TextSecondary)
    }
}
