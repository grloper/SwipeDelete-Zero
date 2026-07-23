package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.ui.theme.SdzColors

/**
 * A high-tech, open-bottom gauge arc (240° sweep) with a multi-colour progress
 * stroke that runs Electric Emerald → Crisp Cyan. Replaces the flat single-tone
 * ring for a dashboard-widget feel. [progress] is 0f..1f.
 */
@Composable
fun GradientArcGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 128.dp,
    strokeWidth: Dp = 12.dp,
    startAngle: Float = 150f,
    sweepAngle: Float = 240f,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "gauge",
    )
    val gradient = Brush.linearGradient(
        colors = listOf(SdzColors.ElectricEmerald, SdzColors.CrispCyan),
    )
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Track (unfilled remainder).
            drawArc(
                color = SdzColors.Hairline,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress with emerald→cyan gradient.
            drawArc(
                brush = gradient,
                startAngle = startAngle,
                sweepAngle = sweepAngle * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}
