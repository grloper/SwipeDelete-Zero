package com.swipedelete.zero.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Neutral studio surfaces. Text and icons carry action meaning; sage is the only accent. */
object SdzColor {
    val Surface0 = Color(0xFF101211)
    val Surface1 = Color(0xFF1A1D1B)
    val Surface2 = Color(0xFF252925)
    val Surface3 = Surface2
    val Surface4 = Surface2
    val Hairline = Color(0xFF353B35)
    val Boundary = Color(0xFF69736A)
    val Scrim = Color(0xCC101211)
    val Phosphor = Color(0xFFF4F5F2)
    val TextSecondary = Color(0xFFB5BAB4)
    val TextTertiary = Color(0xFF929991)
    val OnAccent = Surface0
    val Sage = Color(0xFFA8C9B5)
    // Existing semantic call sites share one quiet accent.
    val Azure = Sage
    val AzureDim = Surface2
    val Amber = Sage
    val AmberDim = Surface2
    val Teal = Sage
    val TealDim = Surface2
    val Red = Color(0xFFEF8B82)
    val RedDim = Color(0x33EF8B82)
    val Safelight = Red
    val SafelightDim = RedDim
    val UrgencyCalm = TextTertiary
    val UrgencyFilling = Sage
    val UrgencyCritical = Red
    val Track = Hairline
}
object SdzSpace {
    val xxs = 4.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val h1 = 32.dp
    val h2 = 40.dp
    val h3 = 48.dp
    val h4 = 64.dp
}
object SdzRadius {
    val xs = 4.dp
    val sm = 12.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}
object SdzElevation {
    val flat = 0.dp
    val raised = 0.dp
    val floating = 0.dp
    val dialog = 0.dp
    val dragging = 0.dp
}
object SdzMotion {
    const val Instant = 100
    const val Quick = 150
    const val Standard = 240
    const val Expressive = 280
    const val Celebration = 180
    val Emphasised: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
    fun <T> fling(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
}
object SdzTouch {
    val minTarget = 48.dp
    val primaryAction = 52.dp
    val secondaryAction = 52.dp
}
