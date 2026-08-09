package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColor

/** Small pill toggle used by the sort rows on the dashboard and staging drawer. */
@Composable
fun SortChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = if (selected) SdzColor.TextSecondary else SdzColor.TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) SdzColor.TextSecondary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) SdzColor.TextSecondary.copy(alpha = 0.5f) else SdzColor.Hairline,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
