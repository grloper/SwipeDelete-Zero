package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor

/**
 * The legacy ledger combines Drive byte-checked receipts and Photos item
 * confirmations. This chip must not imply that either remains restorable now.
 */
@Composable
fun CloudChip(backedUp: Boolean, modifier: Modifier = Modifier) {
    val tint = if (backedUp) SdzColor.TextSecondary else SdzColor.TextSecondary
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(SdzColor.Surface0.copy(alpha = 0.55f))
            .border(1.dp, tint.copy(alpha = 0.45f), shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (backedUp) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = if (backedUp) "Uploaded before" else "Local only",
            color = tint,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
