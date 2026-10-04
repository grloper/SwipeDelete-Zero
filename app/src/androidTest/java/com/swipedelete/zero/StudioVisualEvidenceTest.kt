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
    private fun decisionPositions(): Map<String, androidx.compose.ui.geometry.Rect> = listOf("stage-action", "keep-action", "undo-action").associateWith { tag ->
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        node.boundsInRoot.translate(node.positionOnScreen - node.positionInRoot)
    }
    private fun assertStableDecisions(reference: Map<String, androidx.compose.ui.geometry.Rect>, state: String): Map<String, androidx.compose.ui.geometry.Rect> {
        compose.waitForIdle()
        return decisionPositions().also { positions ->
            reference.forEach { (tag, before) ->
                val after = positions.getValue(tag)
                assertEquals("$tag left stable after $state", before.left, after.left, 1f)
                assertEquals("$tag top stable after $state", before.top, after.top, 1f)
                assertEquals("$tag right stable after $state", before.right, after.right, 1f)
                assertEquals("$tag bottom stable after $state", before.bottom, after.bottom, 1f)
            }
        }
    }
    private fun capture(label: String, screen: String, vararg tags: String) {
        compose.waitForIdle()
        capturePracticeEvidence("swipe-$label-$screen", tags.map { tag ->
            contrastRegion(tag, compose.onNodeWithTag(tag, useUnmergedTree = true),
                background = when (tag) { "queue-label" -> Color.rgb(37,41,37); "queue-sheet-title", "queue-lock" -> Color.rgb(26,29,27); else -> Color.rgb(16,18,17) },
                foreground = if (tag in setOf("review-progress", "access-label")) Color.rgb(181,186,180) else Color.rgb(244,245,242))
        })
    }
    @Test fun dashboardReviewQueueAndRecreation() {
        val label = InstrumentationRegistry.getArguments().getString("visualLabel")
        assumeTrue("Dedicated independent visual matrix", label != null)
        seedStudioPhotographs()
        val output = File(context.getExternalFilesDir(null), "practice-evidence").apply { mkdirs() }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            await("start-review")
            compose.waitUntil(30_000) { compose.onNodeWithTag("start-review").isEnabled() }
            compose.onNodeWithTag("all-groups-tab").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("date-groups-tab").assertIsNotSelected().performClick().assertIsSelected()
            compose.onNodeWithTag("all-groups-tab").assertIsNotSelected()
            compose.onAllNodesWithText("Duplicates & near-shots").assertCountEquals(0)
            compose.onNodeWithTag("all-groups-tab").performClick().assertIsSelected()
            compose.onNodeWithTag("date-groups-tab").assertIsNotSelected()
            compose.onNodeWithTag("dashboard-list").performScrollToIndex(0)
            val access = compose.onNodeWithTag("manage-access").assertIsDisplayed().getUnclippedBoundsInRoot()
            val accessLabel = compose.onNodeWithTag("access-label", useUnmergedTree = true).assertIsDisplayed()
            val labelBounds = accessLabel.getUnclippedBoundsInRoot()
            val accessLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            accessLabel.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(accessLayouts) }
            val accessLayout = accessLayouts.single()
            val accessDiagnostic = JSONObject().apply {
                put("width_px", accessLayout.size.width); put("height_px", accessLayout.size.height)
                put("paragraph_width_px", accessLayout.multiParagraph.width); put("paragraph_height_px", accessLayout.multiParagraph.height)
                put("constraints", accessLayout.layoutInput.constraints.toString())
                put("button_bounds_dp", org.json.JSONArray(listOf(access.left.value, access.top.value, access.right.value, access.bottom.value)))
                put("text_bounds_dp", org.json.JSONArray(listOf(labelBounds.left.value, labelBounds.top.value, labelBounds.right.value, labelBounds.bottom.value)))
                put("overflow_width", accessLayout.didOverflowWidth); put("overflow_height", accessLayout.didOverflowHeight)
                put("line_count", accessLayout.lineCount); put("font_scale", context.resources.configuration.fontScale)
            }
            File(output, "swipe-$label-access-layout.json").writeText(accessDiagnostic.toString(2))
            capture(label!!, "dashboard", "dashboard-title", "access-label")
            assertTrue("Wrapped access text clears the rounded button edges", labelBounds.left.value >= access.left.value + 8f && labelBounds.right.value <= access.right.value - 8f)
            assertFalse("Access label remains readable at the configured font scale: $accessDiagnostic", accessLayout.hasVisualOverflow)
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
            val positions = linkedMapOf("default" to decisionPositions())
            // Repeated callbacks in the same frame must only admit one repository decision.
            val action = compose.onNodeWithTag("stage-action").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            compose.runOnUiThread { repeat(10) { action.invoke() } }
            awaitCursor(initial + 1)
            compose.onNodeWithTag("undo-action").assertIsEnabled()
            positions["staged"] = assertStableDecisions(positions.getValue("default"), "Stage")
            capture(label, "staged", "review-progress")
            scenario.recreate()
            await("review-progress")
            awaitCursor(initial + 1)
            compose.onNodeWithTag("undo-action").performClick()
            awaitCursor(initial)
            positions["undo-stage"] = assertStableDecisions(positions.getValue("default"), "Undo Stage")
            compose.onNodeWithTag("keep-action").performClick()
            awaitCursor(initial + 1)
            positions["kept"] = assertStableDecisions(positions.getValue("default"), "Keep")
            capture(label, "kept", "review-progress")
            compose.onNodeWithTag("undo-action").performClick()
            awaitCursor(initial)
            positions["undo-keep"] = assertStableDecisions(positions.getValue("default"), "Undo Keep")
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
            capture(label, "dashboard-queue", "dashboard-title", "queue-label")
            compose.onNodeWithTag("dashboard-list").performScrollToKey("content-scan")
            compose.onNodeWithTag("dashboard-end").performScrollTo().assertIsDisplayed()
            val finalRow = compose.onNodeWithTag("dashboard-end-action").assertIsDisplayed().getUnclippedBoundsInRoot()
            val finalNote = compose.onNodeWithTag("dashboard-end").getUnclippedBoundsInRoot()
            assertTrue("Final dashboard action is fully inside the reserved scroll viewport", finalRow.top >= list.top && finalRow.bottom <= list.bottom)
            assertTrue("Last dashboard note clears the queue bar", finalNote.bottom <= queue.top)
            capture(label, "dashboard-end", "queue-label")
            File(output, "swipe-$label-geometry.json").writeText(JSONObject().apply {
                put("density", context.resources.displayMetrics.density)
                put("font_scale", context.resources.configuration.fontScale)
                put("screen_width_dp", context.resources.configuration.screenWidthDp)
                put("stage_height_dp", stage.bottom.value - stage.top.value)
                put("stage_width_dp", stage.right.value - stage.left.value)
                put("keep_width_dp", keep.right.value - keep.left.value)
                put("decision_gap_dp", keep.left.value - stage.right.value)
                put("photo_height_dp", photo.bottom.value - photo.top.value)
                put("queue_height_dp", queue.bottom.value - queue.top.value)
                put("group_selection_semantics", true); put("final_dashboard_row_clear", true)
                put("decision_positions_px", JSONObject().apply {
                    positions.forEach { (state, rects) -> put(state, JSONObject().apply {
                        rects.forEach { (tag, rect) -> put(tag, org.json.JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))) }
                    }) }
                })
                put("queue_reserved", true); put("rapid_callbacks", 10); put("admitted", 1)
                put("recreation_persisted", true); put("stage_keep_undo", true)
            }.toString(2))
            val originalsBeforeQueue = studioOriginalHashes()
            compose.onNodeWithTag("queue-bar").performClick()
            await("queue-unstage-all")
            compose.onNodeWithText("Review queue").assertIsDisplayed()
            capture(label, "queue-sheet-top", "queue-sheet-title")
            val queueCount = compose.onNodeWithTag("queue-count", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            val queueViewport = compose.onNodeWithTag("queue-list").getUnclippedBoundsInRoot()
            val title = compose.onNodeWithTag("queue-sheet-title", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val unstageAll = compose.onNodeWithTag("queue-unstage-all").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Queue title fully visible", title.top >= queueViewport.top && title.bottom <= queueViewport.bottom)
            assertTrue("Unstage all target >=52dp", unstageAll.bottom.value - unstageAll.top.value >= 52f)
            val dialogAppearance = JSONObject()
            instrumentation.runOnMainSync {
                fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
                    if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val provider = android.view.inspector.WindowInspector.getGlobalWindowViews()
                    .flatMap { descendants(it) }.filterIsInstance<androidx.compose.ui.window.DialogWindowProvider>().single()
                val controller = androidx.core.view.WindowCompat.getInsetsController(provider.window, provider.window.decorView)
                dialogAppearance.put("light_status_bar_appearance", controller.isAppearanceLightStatusBars)
                dialogAppearance.put("light_navigation_bar_appearance", controller.isAppearanceLightNavigationBars)
                assertFalse("Dark sheet needs light status icons", controller.isAppearanceLightStatusBars)
                assertFalse("Dark sheet needs light navigation icons", controller.isAppearanceLightNavigationBars)
            }
            val cleanupPositions = JSONObject()
            for (mode in listOf("queue-mode-permanent", "queue-mode-trash")) {
                compose.onNodeWithTag("queue-list").performScrollToKey("mode")
                compose.onNodeWithTag(mode).performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
                val modeBounds = compose.onNodeWithTag(mode).getUnclippedBoundsInRoot()
                assertTrue("Queue mode target >=52dp", modeBounds.bottom.value - modeBounds.top.value >= 52f)
                compose.onNodeWithTag("queue-list").performScrollToKey("cleanup")
                compose.onNodeWithTag("queue-cleanup").performScrollTo().assertIsDisplayed()
                val lock = compose.onNodeWithTag("queue-lock", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
                val cleanup = compose.onNodeWithTag("queue-cleanup-action").assertIsDisplayed().assertIsNotEnabled().getUnclippedBoundsInRoot()
                assertTrue("Whole warning clears the scroll boundary", lock.top >= queueViewport.top && lock.bottom <= queueViewport.bottom)
                assertTrue("Locked cleanup action fully reachable", cleanup.top >= lock.bottom && cleanup.bottom <= queueViewport.bottom)
                assertTrue("Cleanup target >=52dp", cleanup.bottom.value - cleanup.top.value >= 52f)
                compose.onAllNodes(hasText("Cleanup is unavailable", substring = true), useUnmergedTree = true).assertCountEquals(1)
                cleanupPositions.put(mode, JSONObject().apply {
                    put("lock_bounds_dp", org.json.JSONArray(listOf(lock.left.value, lock.top.value, lock.right.value, lock.bottom.value)))
                    put("action_bounds_dp", org.json.JSONArray(listOf(cleanup.left.value, cleanup.top.value, cleanup.right.value, cleanup.bottom.value)))
                    put("disabled", true)
                })
            }
            capture(label, "queue-sheet-bottom", "queue-lock")
            assertEquals("Queue controls never change originals", originalsBeforeQueue, studioOriginalHashes())
            compose.onNodeWithTag("queue-list").performScrollToIndex(0)
            assertEquals("Mode selection preserves queue", queueCount, compose.onNodeWithTag("queue-count", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
            File(output, "swipe-$label-queue-layout.json").writeText(JSONObject().apply {
                put("viewport_dp", org.json.JSONArray(listOf(queueViewport.left.value, queueViewport.top.value, queueViewport.right.value, queueViewport.bottom.value)))
                put("dialog", dialogAppearance); put("modes", cleanupPositions)
                put("original_hashes_unchanged", originalsBeforeQueue.size); put("queue_unchanged", true)
            }.toString(2))
            compose.onNodeWithText("Unstage all").performClick()
        }
    }
    private fun SemanticsNodeInteraction.isEnabled() = runCatching { assertIsEnabled(); true }.getOrDefault(false)
    private fun studioOriginalHashes(): Map<String, String> {
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return buildMap {
            resolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("Studio_fixture_%"), null)!!.use { rows ->
                while (rows.moveToNext()) {
                    val uri = android.content.ContentUris.withAppendedId(collection, rows.getLong(0))
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(uri)!!.use { input ->
                        val buffer = ByteArray(65536)
                        while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
                    }
                    put(uri.toString(), digest.digest().joinToString("") { "%02x".format(it) })
                }
            }
            assertEquals("All synthetic originals remain", 12, size)
        }
    }
}
