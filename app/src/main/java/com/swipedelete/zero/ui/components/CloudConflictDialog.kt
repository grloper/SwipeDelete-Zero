package com.swipedelete.zero.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.util.toReadableSize

/**
 * Dialog giving users transparent, simple control over Cloud state:
 * - If a file was deleted in Google Photos or shows missing remote status,
 *   the user can choose to Re-backup to Google Photos, Forget from Cloud Ledger,
 *   or Keep Local.
 */
@Composable
fun CloudConflictDialog(
    fileName: String,
    sizeBytes: Long,
    isAlreadyBackedUp: Boolean,
    onRebackup: () -> Unit,
    onForgetLedger: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = SdzColor.Teal,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = "Cloud Backup Control",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = "Size: ${sizeBytes.toReadableSize()} · Status: ${if (isAlreadyBackedUp) "Recorded in Cloud Ledger" else "Local / Unsynced"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "What would you like to do?",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Re-Backup: Clears old cloud tokens and uploads fresh to Google Photos.\n" +
                                   "• Forget Cloud: Removes from ledger without re-uploading (keeps local file safe).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRebackup()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SdzColor.Teal,
                    contentColor = Color.Black,
                ),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-Backup")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onForgetLedger()
                    onDismiss()
                }
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Forget Cloud Record")
            }
        }
    )
}
