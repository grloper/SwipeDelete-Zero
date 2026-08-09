package com.swipedelete.zero.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The app is intentionally dark-only: an OLED pitch-black aesthetic is core to
 * the brand and to the battery/contrast benefits. We therefore ignore the system
 * light/dark setting and always apply the obsidian scheme.
 */
private val SdzDarkScheme = darkColorScheme(
    primary = SdzColors.ElectricEmerald,
    onPrimary = SdzColors.PitchBlack,
    secondary = SdzColors.CrispCyan,
    onSecondary = SdzColors.PitchBlack,
    tertiary = SdzColors.StarGold,
    onTertiary = SdzColors.PitchBlack,
    error = SdzColors.HyperCoral,
    onError = SdzColors.PitchBlack,
    background = SdzColors.PitchBlack,
    onBackground = SdzColors.PureWhite,
    surface = SdzColors.Obsidian,
    onSurface = SdzColors.PureWhite,
    surfaceVariant = SdzColors.Obsidian,
    onSurfaceVariant = SdzColors.MutedGray,
    outline = SdzColors.MutedGray,
    outlineVariant = Color(0x1AFFFFFF),
)

@Composable
fun SwipeDeleteZeroTheme(
    // Ignored on purpose — retained only so previews can flip it if ever needed.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars: the palette backdrop bleeds edge-to-edge behind
            // them; screens inset their own headers with statusBarsPadding().
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = SdzDarkScheme,
        typography = SdzTypography,
        content = content,
    )
}
