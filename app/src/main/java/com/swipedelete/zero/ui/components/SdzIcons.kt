package com.swipedelete.zero.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.swipedelete.zero.R

/**
 * The app's icon set: one grid (24dp), one stroke weight (1.8dp, 2.1 for the
 * cross so it holds up at small sizes), round caps and joins throughout.
 *
 * The previous UI mixed Material glyphs at whatever weight each happened to
 * ship with, which is why nothing looked like it came from the same family.
 * Anything decision-carrying is drawn here so its *silhouette* is designed,
 * not inherited: keep is an enclosed shield, reclaim is an open cross, archive
 * is a rounded cloud. Those three read apart in greyscale, at 16px, and with
 * any form of colour-vision deficiency.
 */
object SdzIcons {
    val Keep: Painter @Composable get() = painterResource(R.drawable.ic_action_keep)
    val Reclaim: Painter @Composable get() = painterResource(R.drawable.ic_action_trash)
    val Archive: Painter @Composable get() = painterResource(R.drawable.ic_action_cloud)
    val Undo: Painter @Composable get() = painterResource(R.drawable.ic_action_undo)
    val Back: Painter @Composable get() = painterResource(R.drawable.ic_action_back)

    val Duplicates: Painter @Composable get() = painterResource(R.drawable.ic_bucket_duplicates)
    val Blurry: Painter @Composable get() = painterResource(R.drawable.ic_bucket_blurry)
    val LargeVideo: Painter @Composable get() = painterResource(R.drawable.ic_bucket_video)
    val Screenshots: Painter @Composable get() = painterResource(R.drawable.ic_bucket_screenshot)
    val Documents: Painter @Composable get() = painterResource(R.drawable.ic_bucket_document)

    val LogoMark: Painter @Composable get() = painterResource(R.drawable.ic_logo_mark)
}
