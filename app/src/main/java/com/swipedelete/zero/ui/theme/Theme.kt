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
 * Dark-only by design: a darkroom does not have a light mode.
 *
 * The scheme maps Material's slots onto [SdzColor] so any stray M3 component
 * inherits the system instead of falling back to Material purple. Screens
 * should still reference [SdzColor] directly — the semantic role names carry
 * meaning that `primary`/`secondary` do not.
 */
private val SdzDarkScheme = darkColorScheme(
    primary = SdzColor.Azure,
    onPrimary = SdzColor.OnAccent,
    primaryContainer = SdzColor.AzureDim,
    onPrimaryContainer = SdzColor.Azure,

    secondary = SdzColor.Amber,
    onSecondary = SdzColor.OnAccent,
    secondaryContainer = SdzColor.AmberDim,
    onSecondaryContainer = SdzColor.Amber,

    tertiary = SdzColor.Teal,
    onTertiary = SdzColor.OnAccent,

    error = SdzColor.Safelight,
    onError = SdzColor.OnAccent,
    errorContainer = SdzColor.SafelightDim,
    onErrorContainer = SdzColor.Safelight,

    background = SdzColor.Surface0,
    onBackground = SdzColor.Phosphor,
    surface = SdzColor.Surface1,
    onSurface = SdzColor.Phosphor,
    surfaceVariant = SdzColor.Surface2,
    onSurfaceVariant = SdzColor.TextSecondary,
    surfaceContainerLowest = SdzColor.Surface0,
    surfaceContainerLow = SdzColor.Surface1,
    surfaceContainer = SdzColor.Surface2,
    surfaceContainerHigh = SdzColor.Surface3,
    surfaceContainerHighest = SdzColor.Surface4,
    outline = SdzColor.TextTertiary,
    outlineVariant = SdzColor.Hairline,
    scrim = SdzColor.Scrim,
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
            // Transparent bars: content draws edge-to-edge beneath them and each
            // screen insets its own chrome.
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
