package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.ui.theme.SdzColors
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Active Deck's top card — a physics-driven, flingable surface.
 *
 * Gesture model (`Modifier.pointerInput` + `detectDragGestures`):
 *  - Horizontal drag past [commitFraction] of the width commits LEFT (trash) or
 *    RIGHT (keep); a strong upward drag commits UP (star).
 *  - Rotation tracks horizontal offset at `offsetX * 0.04f` for a natural flick.
 *  - Uncommitted releases spring back with `StiffnessMediumLow` /
 *    `DampingRatioMediumBouncy`.
 *  - Edge-glow backlights (coral/emerald/gold) grow proportionally to drag,
 *    telegraphing the pending action before the user lets go.
 */
@Composable
fun SwipeableCard(
    item: MediaItem,
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onTapVideo: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current

        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val offsetX = remember { Animatable(0f) }
        val offsetY = remember { Animatable(0f) }
        var committed by remember { mutableFloatStateOf(0f) } // guards double-fire

        val commitFraction = 0.32f
        val horizontalCommit = widthPx * commitFraction
        val verticalCommit = heightPx * 0.28f

        // Live drag ratios drive the edge glow intensity.
        val dragX = offsetX.value
        val dragY = offsetY.value
        val rightGlow = (dragX / horizontalCommit).coerceIn(0f, 1f)
        val leftGlow = (-dragX / horizontalCommit).coerceIn(0f, 1f)
        val upGlow = (-dragY / verticalCommit).coerceIn(0f, 1f)

        fun flingOut(direction: SwipeDirection) {
            if (committed != 0f) return
            committed = 1f
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch {
                val targetX = when (direction) {
                    SwipeDirection.LEFT -> -widthPx * 1.5f
                    SwipeDirection.RIGHT -> widthPx * 1.5f
                    else -> offsetX.value
                }
                val targetY = if (direction == SwipeDirection.UP) -heightPx * 1.5f else offsetY.value
                launch { offsetX.animateTo(targetX, tween(220)) }
                offsetY.animateTo(targetY, tween(220))
                onSwiped(direction)
            }
        }

        fun springBack() {
            scope.launch { launch { offsetX.animateTo(0f, springSpec()) }; offsetY.animateTo(0f, springSpec()) }
        }

        val rotation = (dragX * 0.04f).coerceIn(-18f, 18f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = rotation
                }
                .edgeGlow(leftGlow, rightGlow, upGlow)
                .clip(RoundedCornerShape(28.dp))
                .background(SdzColors.Obsidian)
                .border(1.dp, SdzColors.Hairline, RoundedCornerShape(28.dp))
                .pointerInput(item.id, enabled) {
                    if (!enabled) return@pointerInput
                    detectDrag(
                        onDrag = { dx, dy ->
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dx)
                                offsetY.snapTo((offsetY.value + dy).coerceAtMost(0f))
                            }
                        },
                        onEnd = {
                            when {
                                offsetY.value < -verticalCommit &&
                                    abs(offsetY.value) > abs(offsetX.value) -> flingOut(SwipeDirection.UP)
                                offsetX.value > horizontalCommit -> flingOut(SwipeDirection.RIGHT)
                                offsetX.value < -horizontalCommit -> flingOut(SwipeDirection.LEFT)
                                else -> springBack()
                            }
                        },
                    )
                },
        ) {
            MediaPreview(item = item, modifier = Modifier.fillMaxSize())

            // Directional stamp overlays (TRASH / KEEP / STAR).
            SwipeStamps(
                leftGlow = leftGlow,
                rightGlow = rightGlow,
                upGlow = upGlow,
                modifier = Modifier.fillMaxSize(),
            )

            MetadataPill(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}

private fun springSpec() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** Draws the proportional coral/emerald/gold backlight around the card. */
private fun Modifier.edgeGlow(left: Float, right: Float, up: Float): Modifier =
    drawBehind {
        val stroke = 6.dp.toPx()
        fun glow(color: Color, alpha: Float) {
            if (alpha <= 0.01f) return
            drawRoundRect(
                color = color.copy(alpha = alpha * 0.9f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke + alpha * 18.dp.toPx()),
            )
        }
        glow(SdzColors.HyperCoral, left)
        glow(SdzColors.ElectricEmerald, right)
        glow(SdzColors.StarGold, up)
    }

/**
 * Small local re-implementation of drag detection so we can coerce the vertical
 * axis (up-only) and split X/Y deltas cleanly. Wraps [detectDragGestures].
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectDrag(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onEnd: () -> Unit,
) {
    androidx.compose.foundation.gestures.detectDragGestures(
        onDragEnd = onEnd,
        onDragCancel = onEnd,
        onDrag = { change, dragAmount: Offset ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        },
    )
}
