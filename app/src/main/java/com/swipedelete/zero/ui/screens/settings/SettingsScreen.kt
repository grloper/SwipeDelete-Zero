package com.swipedelete.zero.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import com.swipedelete.zero.ui.theme.SdzColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val exclusions by viewModel.exclusions.collectAsStateWithLifecycle()

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
            "SwipeDelete Zero · GPL v3 · 100% Offline · Zero Net-Permissions",
            color = SdzColors.MutedGray,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 20.dp),
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
