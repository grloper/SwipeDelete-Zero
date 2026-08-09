package com.swipedelete.zero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.swipedelete.zero.domain.backup.CloudDestination
import com.swipedelete.zero.domain.backup.RemoteState
import com.swipedelete.zero.ui.theme.SdzColor
import com.swipedelete.zero.ui.theme.SdzRadius
import com.swipedelete.zero.ui.theme.SdzSpace
import com.swipedelete.zero.ui.theme.SdzType

/**
 * What this app knows about one card's cloud copy — and nothing more.
 *
 * The old chip said "Cloud Backed Up" or "Local Only". Both were overclaims.
 * The first hid *where* the copy went, so a file safe in Drive looked identical
 * to one in the user's photo library. The second asserted a negative this app
 * cannot establish: the `photoslibrary.appendonly` scope is upload-only, so
 * Google Photos' own auto-backup is invisible here. A photo the Photos app
 * saved months ago would have been labelled "Local Only" — which is exactly the
 * mislabelling the user hit.
 *
 * So the absent case now describes *this app's* record ("Not sent by this app"),
 * which is a claim we can actually support, and the present case names the
 * destination and distinguishes an acknowledged write from a verified one.
 */
@Composable
fun CloudChip(
    destination: CloudDestination?,
    remoteState: RemoteState?,
    modifier: Modifier = Modifier,
) {
    val label: String
    val tint: Color
    val icon = when {
        destination == null -> SdzIcons.Archive
        remoteState == RemoteState.MISSING -> SdzIcons.Reclaim
        else -> SdzIcons.Archive
    }

    when {
        destination == null -> {
            label = "Not sent by this app"
            tint = SdzColor.TextSecondary
        }
        remoteState == RemoteState.MISSING -> {
            label = "Gone from ${destination.shortLabel}"
            tint = SdzColor.Safelight
        }
        remoteState == RemoteState.UNKNOWN -> {
            label = "${destination.shortLabel} · unverified"
            tint = SdzColor.Amber
        }
        remoteState == RemoteState.CONFIRMED -> {
            label = "Verified in ${destination.shortLabel}"
            tint = SdzColor.Teal
        }
        else -> {
            label = "Sent to ${destination.shortLabel}"
            tint = SdzColor.TextSecondary
        }
    }

    val shape = RoundedCornerShape(SdzRadius.pill)
    Row(
        modifier = modifier
            .clip(shape)
            .background(SdzColor.Surface0.copy(alpha = 0.55f))
            .border(1.dp, tint.copy(alpha = 0.45f), shape)
            .padding(horizontal = SdzSpace.sm, vertical = SdzSpace.xs)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SdzSpace.xs),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(label, color = tint, style = SdzType.LabelSmall)
    }
}

/** "Photos" / "Drive" — the full name does not fit a card corner. */
private val CloudDestination.shortLabel: String
    get() = when (this) {
        CloudDestination.DRIVE -> "Drive"
        CloudDestination.PHOTOS -> "Photos"
    }
