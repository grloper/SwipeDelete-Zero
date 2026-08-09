package com.swipedelete.zero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.MediaItem
import com.swipedelete.zero.domain.model.SwipeCommitDecider
import com.swipedelete.zero.domain.model.SwipeDirection
import com.swipedelete.zero.ui.haptics.rememberSdzHaptics
import com.swipedelete.zero.ui.theme.SdzColors
import kotlinx.coroutines.launch

/**
 * The Active Deck's top card — a physics-driven, flingable surface.
 *
 * Gesture model (`Modifier.pointerInput` + `detectDragGestures` + a manual
 * [VelocityTracker], since foundation's onDragEnd carries no velocity):
 *  - Positional commit past 32% width (LEFT/RIGHT) or 28% height (UP), OR a
 *    velocity fling — a fast flick commits early without a full-screen drag
 *    (see [SwipeCommitDecider] for the exact matrix).
 *  - The exit animation inherits the finger's release velocity, so a hard
 *    fling leaves faster than a lazy drag-release.
 *  - Rotation is width-relative (±16°) around a bottom-weighted pivot for the
 *    classic card-arc feel; the card also lifts (scale + shadow) while held.
 *  - Uncommitted releases spring back with `StiffnessLow` /
 *    `DampingRatioMediumBouncy` — the lazier, premium settle.
 *  - Progressive haptics: a soft tick at 50% of a threshold, a firm pulse when
 *    the threshold arms, and a distinct per-direction signature on commit.
 */
@Composable
fun SwipeableCard(
    item: MediaItem,
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Live 0..1 drag progress toward any commit threshold (drives peek-card scale). */
    onDragProgress: (Float) -> Unit = {},
    content: @Composable (leftGlow: Float, rightGlow: Float, upGlow: Float) -> Unit = { l, r, u ->
        DefaultCardContent(item, l, r, u)
    },
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val haptics = rememberSdzHaptics()

        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val offsetX = remember { Animatable(0f) }
        val offsetY = remember { Animatable(0f) }
        var committed by remember { mutableFloatStateOf(0f) } // guards double-fire

        val horizontalCommit = widthPx * 0.32f
        val verticalCommit = heightPx * 0.28f
        val thresholds = remember(widthPx, heightPx) {
            SwipeCommitDecider.Thresholds(
                horizontalCommitPx = horizontalCommit,
                verticalCommitPx = verticalCommit,
                velocityCommitPxPerSec = with(density) { 800.dp.toPx() },
                minFlingDistancePx = widthPx * 0.08f,
            )
        }

        // Live drag ratios drive the edge glow intensity.
        val dragX = offsetX.value
        val dragY = offsetY.value
        val rightGlow = (dragX / horizontalCommit).coerceIn(0f, 1f)
        val leftGlow = (-dragX / horizontalCommit).coerceIn(0f, 1f)
        val upGlow = (-dragY / verticalCommit).coerceIn(0f, 1f)
        val dragProgress = maxOf(leftGlow, rightGlow, upGlow)

        LaunchedEffect(Unit) {
            snapshotFlow {
                maxOf(
                    (offsetX.value / horizontalCommit).coerceIn(0f, 1f),
                    (-offsetX.value / horizontalCommit).coerceIn(0f, 1f),
                    (-offsetY.value / verticalCommit).coerceIn(0f, 1f),
                )
            }.collect(onDragProgress)
        }

        fun flingOut(direction: SwipeDirection, velocityX: Float, velocityY: Float) {
            if (committed != 0f) return
            committed = 1f
            haptics.commit(direction)
            scope.launch {
                val exitSpec = spring<Float>(
                    dampingRatio = 1f,
                    stiffness = 180f,
                    visibilityThreshold = 1f,
                )
                val targetX = when (direction) {
                    SwipeDirection.LEFT -> -widthPx * 1.5f
                    SwipeDirection.RIGHT -> widthPx * 1.5f
                    else -> offsetX.value
                }
                val targetY = if (direction == SwipeDirection.UP) -heightPx * 1.5f else offsetY.value
                // Secondary axis drifts with its release velocity for a natural arc.
                launch { offsetX.animateTo(targetX, exitSpec, initialVelocity = velocityX) }
                offsetY.animateTo(targetY, exitSpec, initialVelocity = velocityY)
                onSwiped(direction)
            }
        }

        fun springBack() {
            haptics.releaseSpringBack()
            scope.launch {
                launch { offsetX.animateTo(0f, springBackSpec()) }
                offsetY.animateTo(0f, springBackSpec())
            }
        }

        // Width-relative rotation so tablets don't over-rotate.
        val rotation = ((dragX / widthPx) * 16f).coerceIn(-16f, 16f)

        // Progressive-haptic stage: 0 idle, 1 half-way tick fired, 2 threshold armed.
        var hapticStage by remember { mutableIntStateOf(0) }
        var lastArmedAt by remember { mutableLongStateOf(0L) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = rotation
                    transformOrigin = TransformOrigin(0.5f, 0.85f)
                    val lift = 1f + 0.03f * dragProgress
                    scaleX = lift
                    scaleY = lift
                    shadowElevation = (8 + 16 * dragProgress).dp.toPx()
                }
                .edgeGlow(leftGlow, rightGlow, upGlow)
                .clip(RoundedCornerShape(28.dp))
                .background(SdzColors.Obsidian)
                .border(1.dp, SdzColors.Hairline, RoundedCornerShape(28.dp))
                .pointerInput(item.id, enabled) {
                    if (!enabled) return@pointerInput
                    val velocityTracker = VelocityTracker()
                    val onEnd = {
                        val velocity = velocityTracker.calculateVelocity()
                        val direction = SwipeCommitDecider.decide(
                            offsetX = offsetX.value,
                            offsetY = offsetY.value,
                            velocityX = velocity.x,
                            velocityY = velocity.y,
                            t = thresholds,
                        )
                        if (direction != null) {
                            flingOut(direction, velocity.x, velocity.y)
                        } else {
                            springBack()
                        }
                    }
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            hapticStage = 0
                        },
                        onDragEnd = onEnd,
                        onDragCancel = onEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newX = offsetX.value + dragAmount.x
                            val newY = (offsetY.value + dragAmount.y).coerceAtMost(0f)
                            scope.launch {
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }

                            val progress = maxOf(
                                (newX / horizontalCommit).coerceIn(0f, 1f),
                                (-newX / horizontalCommit).coerceIn(0f, 1f),
                                (-newY / verticalCommit).coerceIn(0f, 1f),
                            )
                            val now = change.uptimeMillis
                            when {
                                progress >= 1f -> if (hapticStage < 2 && now - lastArmedAt >= 150) {
                                    haptics.thresholdArmed()
                                    hapticStage = 2
                                    lastArmedAt = now
                                }
                                progress >= 0.5f -> when {
                                    hapticStage == 0 -> {
                                        haptics.progressTick()
                                        hapticStage = 1
                                    }
                                    hapticStage == 2 -> hapticStage = 1 // re-arm the threshold pulse
                                }
                                else -> hapticStage = 0
                            }
                        },
                    )
                },
        ) {
            content(leftGlow, rightGlow, upGlow)
        }
    }
}

/** Standard single-card content: media, stamps, metadata pill. */
@Composable
private fun DefaultCardContent(item: MediaItem, leftGlow: Float, rightGlow: Float, upGlow: Float) {
    Box(Modifier.fillMaxSize()) {
        MediaPreview(item = item, modifier = Modifier.fillMaxSize())
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

private fun springBackSpec() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow,
)

/** Draws the proportional coral/emerald/gold backlight around the card. */
internal fun Modifier.edgeGlow(left: Float, right: Float, up: Float): Modifier =
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
