package com.swipedelete.zero.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.swipedelete.zero.domain.model.SwipeDirection

/**
 * Progressive haptic vocabulary for the swipe deck, built on
 * [View.performHapticFeedback] because Compose's `HapticFeedbackType` only
 * exposes LongPress/TextHandleMove. Every call degrades gracefully down to the
 * API 29 minSdk; OEMs that mute a constant simply stay silent.
 */
class SdzHaptics(private val view: View) {

    /** Soft tick when the drag passes ~50% of a commit threshold. */
    fun progressTick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Firmer pulse the moment a commit threshold arms (glow saturates). */
    fun thresholdArmed() = perform(
        when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
    )

    /** Distinct commit signature per direction: reject / confirm / double-tick. */
    fun commit(direction: SwipeDirection) {
        when (direction) {
            SwipeDirection.LEFT -> perform(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            )
            SwipeDirection.RIGHT -> perform(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.KEYBOARD_TAP
            )
            SwipeDirection.UP -> {
                perform(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.KEYBOARD_TAP
                )
                view.postDelayed({ perform(HapticFeedbackConstants.CLOCK_TICK) }, 40)
            }
            SwipeDirection.NONE -> Unit
        }
    }

    /** Gentle end-of-gesture cue on an uncommitted release (API 34+ only). */
    fun releaseSpringBack() {
        if (Build.VERSION.SDK_INT >= 34) perform(HapticFeedbackConstants.GESTURE_END)
    }

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberSdzHaptics(): SdzHaptics {
    val view = LocalView.current
    return remember(view) { SdzHaptics(view) }
}
