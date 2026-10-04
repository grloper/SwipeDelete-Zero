package com.swipedelete.zero

import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.swipedelete.zero.domain.model.*
import com.swipedelete.zero.ui.components.SwipeableCard
import com.swipedelete.zero.ui.theme.SwipeDeleteZeroTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic UI fixture: no media permissions, account access or personal files. */
class SwipeableCardRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val item = MediaItem(1, Uri.parse("content://fixture/images/1"), "fixture", "image/jpeg",
        MediaType.IMAGE, 100, 0)

    @Test fun acceptedGestureIsAdmittedBeforeExitFramesAndSurvivesRemoval() {
        var calls = 0
        var visible by mutableStateOf(true)
        compose.setContent {
            SwipeDeleteZeroTheme {
                if (visible) SwipeableCard(item, onSwiped = { calls++; true },
                    modifier = Modifier.size(240.dp, 360.dp).testTag("surface")) { _, _, _ -> Text("fixture") }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            val surface = compose.onNodeWithTag("surface")
            surface.performTouchInput { down(center) }
            repeat(6) { step ->
                surface.performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(width * (0.5f - (step + 1) * 0.065f), height * 0.5f)) }
                compose.mainClock.advanceTimeByFrame()
            }
            surface.performTouchInput { up() }
            // No exit frames are advanced. Removing the composition must not discard the decision.
            compose.runOnIdle { assertEquals(1, calls); visible = false }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("surface").assertDoesNotExist()
            assertEquals(1, calls)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun undoBeforeOutgoingExitRecreatesGestureStateAndAllowsAnotherSwipe() {
        var current by mutableStateOf(1L to 0)
        var calls = 0
        compose.setContent {
            SwipeDeleteZeroTheme {
                AnimatedContent(current, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) }, label = "test-photo") { value ->
                    SwipeableCard(item.copy(id = value.first), onSwiped = {
                        if (value != current) false else { calls++; current = 2L to value.second; true }
                    }, enabled = value == current,
                        modifier = Modifier.size(240.dp, 360.dp).testTag(if (value == current) "active" else "outgoing-${value.first}-${value.second}")) { _, _, _ -> Text("fixture") }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun gesture() {
            val surface = compose.onNodeWithTag("active")
            surface.performTouchInput { down(center) }
            repeat(6) { step ->
                surface.performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(width * (0.5f - (step + 1) * 0.065f), height * 0.5f)) }
                compose.mainClock.advanceTimeByFrame()
            }
            surface.performTouchInput { up() }
        }
        try {
            gesture()
            compose.runOnIdle { assertEquals(1, calls) }
            compose.mainClock.advanceTimeByFrame()
            // Mirrors the ViewModel's successful Undo generation, while A is still retained.
            compose.runOnIdle { current = 1L to 1 }
            compose.mainClock.advanceTimeByFrame()
            gesture()
            compose.runOnIdle { assertEquals(2, calls) }
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun rejectedAdmissionReturnsCardAndAllowsAnotherGesture() {
        var calls = 0
        compose.setContent {
            SwipeDeleteZeroTheme {
                SwipeableCard(item, onSwiped = { calls++; false },
                    modifier = Modifier.size(240.dp, 360.dp).testTag("surface")) { _, _, _ -> Text("fixture") }
            }
        }
        val initialX = compose.onNodeWithText("fixture").fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithTag("surface").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitForIdle()
        compose.onNodeWithText("fixture").assertIsDisplayed()
        assertEquals(initialX, compose.onNodeWithText("fixture").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithTag("surface").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(2, calls)
    }

    @Test fun asynchronousFailureResetReconstructsSameCardForRetry() {
        var resetToken by mutableStateOf(0)
        var calls = 0
        compose.setContent {
            SwipeDeleteZeroTheme {
                key(item.id, resetToken) {
                    SwipeableCard(item, onSwiped = { calls++; true },
                        modifier = Modifier.size(240.dp, 360.dp).testTag("surface")) { _, _, _ -> Text("fixture") }
                }
            }
        }
        val initialX = compose.onNodeWithText("fixture").fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithTag("surface").performTouchInput { swipeLeft(durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(1, calls)
        // A failed ViewModel action keeps the same item/cursor and increments cardResetToken.
        compose.runOnIdle { resetToken++ }
        compose.waitForIdle()
        compose.onNodeWithText("fixture").assertIsDisplayed()
        assertEquals(initialX, compose.onNodeWithText("fixture").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithTag("surface").performTouchInput { swipeRight(durationMillis = 400) }
        compose.waitForIdle()
        assertEquals(2, calls)
    }
}
