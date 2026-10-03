package com.swipedelete.zero

import android.net.Uri
import androidx.compose.foundation.layout.size
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
