package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * The auto-computed comparison badges for the dual-card duplicate/blurry screen:
 * `[SHARPER ⚡]`, `[HIGHER RES 📐]`, `[SMALLER SIZE 💾]`. Each badge is shown on
 * whichever of the two photos wins that dimension.
 */
@Composable
fun DiffBadgeRow(
    isSharper: Boolean,
    isHigherRes: Boolean,
    isSmaller: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isSharper) DiffBadge("SHARPER ⚡", SdzColors.ElectricEmerald)
        if (isHigherRes) DiffBadge("HIGHER RES 📐", SdzColors.CrispCyan)
        if (isSmaller) DiffBadge("SMALLER 💾", SdzColors.StarGold)
    }
}

@Composable
private fun DiffBadge(label: String, accent: Color) {
    Text(
        text = label,
        color = SdzColors.PitchBlack,
        fontWeight = FontWeight.Black,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
