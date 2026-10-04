package com.swipedelete.zero

import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.json.JSONObject

/** Real activity + repositories + MediaStore. Each display profile runs in its own process. */
class StudioVisualEvidenceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun await(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun cursor(): Int = compose.onNodeWithTag("review-progress", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text.substringBefore('/').toInt()
    private fun awaitCursor(value: Int) = compose.waitUntil(15_000) { runCatching { cursor() == value }.getOrDefault(false) }
    private fun capture(label: String, screen: String, vararg tags: String) {
        compose.waitForIdle()
        capturePracticeEvidence("swipe-$label-$screen", tags.map { tag ->
            contrastRegion(tag, compose.onNodeWithTag(tag, useUnmergedTree = true), foreground = if (tag == "review-progress" || tag == "access-label") Color.rgb(181,186,180) else Color.rgb(244,245,242))
        })
    }
    @Test fun dashboardReviewQueueAndRecreation() {
        val label = InstrumentationRegistry.getArguments().getString("visualLabel")
        assumeTrue("Dedicated independent visual matrix", label != null)
        seedStudioPhotographs()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            await("start-review")
            compose.waitUntil(30_000) { compose.onNodeWithTag("start-review").isEnabled() }
            val access = compose.onNodeWithTag("manage-access").assertIsDisplayed().getUnclippedBoundsInRoot()
            val accessLabel = compose.onNodeWithTag("access-label", useUnmergedTree = true).assertIsDisplayed()
            val labelBounds = accessLabel.getUnclippedBoundsInRoot()
            assertTrue("Wrapped access text clears the rounded button edges", labelBounds.left.value >= access.left.value + 8f && labelBounds.right.value <= access.right.value - 8f)
            val accessLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            accessLabel.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(accessLayouts) }
            assertFalse("Access label remains readable at the configured font scale", accessLayouts.single().hasVisualOverflow)
            capture(label!!, "dashboard", "dashboard-title", "access-label")
            compose.onNodeWithTag("start-review").performClick()
            await("review-progress")
            compose.waitUntil(30_000) { compose.onNodeWithTag("stage-action").isEnabled() }
            if (compose.onAllNodesWithText("Got it").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Got it").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Preview ready")).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            compose.onNodeWithTag("dashboard-screen").assertDoesNotExist()
            val initial = cursor()
            val stage = compose.onNodeWithTag("stage-action").assertIsDisplayed().getUnclippedBoundsInRoot()
            val keep = compose.onNodeWithTag("keep-action").assertIsDisplayed().getUnclippedBoundsInRoot()
            val undo = compose.onNodeWithTag("undo-action").assertIsDisplayed().getUnclippedBoundsInRoot()
            val photo = compose.onNodeWithTag("review-photo").assertIsDisplayed().getUnclippedBoundsInRoot()
            val metadata = compose.onNodeWithTag("review-metadata").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Stage target >=52dp", stage.bottom - stage.top >= androidx.compose.ui.unit.Dp(52f))
            assertTrue("Undo target >=48dp", undo.bottom - undo.top >= androidx.compose.ui.unit.Dp(48f))
            assertEquals("Equal decision heights", (stage.bottom - stage.top).value, (keep.bottom - keep.top).value, 0.5f)
            assertEquals("Equal decision widths", (stage.right - stage.left).value, (keep.right - keep.left).value, 0.5f)
            assertTrue("12dp decision gap", keep.left.value - stage.right.value >= 11.5f)
            assertTrue("Metadata does not cover the photograph", metadata.top >= photo.bottom)
            assertTrue("Decision dock follows metadata", stage.top >= metadata.bottom)
            assertTrue("Photograph retains usable height", photo.bottom.value - photo.top.value >= 100f)
            capture(label, "review", "review-progress")
            // Repeated callbacks in the same frame must only admit one repository decision.
            val action = compose.onNodeWithTag("stage-action").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            compose.runOnUiThread { repeat(10) { action.invoke() } }
            awaitCursor(initial + 1)
            compose.onNodeWithTag("undo-action").assertIsEnabled()
            capture(label, "staged", "review-progress")
            scenario.recreate()
            await("review-progress")
            awaitCursor(initial + 1)
            compose.onNodeWithTag("undo-action").performClick()
            awaitCursor(initial)
            compose.onNodeWithTag("keep-action").performClick()
            awaitCursor(initial + 1)
            compose.onNodeWithTag("undo-action").performClick()
            awaitCursor(initial)
            compose.onNodeWithTag("more-action").performClick()
            compose.onNodeWithText("More actions").assertIsDisplayed()
            compose.onNodeWithText("Archive", useUnmergedTree = true).assertExists()
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.pressBack())
            compose.onNodeWithText("More actions").assertDoesNotExist()
            compose.onNodeWithTag("stage-action").performClick()
            awaitCursor(initial + 1)
            compose.onNodeWithContentDescription("Back").performClick()
            await("dashboard-title")
            compose.onNodeWithTag("review-screen").assertDoesNotExist()
            await("queue-bar")
            val list = compose.onNodeWithTag("dashboard-list").getUnclippedBoundsInRoot()
            val queue = compose.onNodeWithTag("queue-bar").getUnclippedBoundsInRoot()
            assertTrue("Queue reserves space below the scroll region", list.bottom <= queue.top)
            capture(label, "dashboard-queue", "dashboard-title")
            compose.onNodeWithTag("queue-bar").performClick()
            compose.onNodeWithText("Review queue").assertIsDisplayed()
            compose.onAllNodes(hasText("Cleanup is unavailable", substring = true)).onLast().assertIsDisplayed()
            compose.onNodeWithText("Unstage all").performClick()
            val output = File(context.getExternalFilesDir(null), "practice-evidence").apply { mkdirs() }
            File(output, "swipe-$label-geometry.json").writeText(JSONObject().apply {
                put("density", context.resources.displayMetrics.density)
                put("font_scale", context.resources.configuration.fontScale)
                put("screen_width_dp", context.resources.configuration.screenWidthDp)
                put("stage_height_dp", stage.bottom.value - stage.top.value)
                put("stage_width_dp", stage.right.value - stage.left.value)
                put("keep_width_dp", keep.right.value - keep.left.value)
                put("decision_gap_dp", keep.left.value - stage.right.value)
                put("photo_height_dp", photo.bottom.value - photo.top.value)
                put("queue_reserved", true); put("rapid_callbacks", 10); put("admitted", 1)
                put("recreation_persisted", true); put("stage_keep_undo", true)
            }.toString(2))
        }
    }
    private fun SemanticsNodeInteraction.isEnabled() = runCatching { assertIsEnabled(); true }.getOrDefault(false)
}
