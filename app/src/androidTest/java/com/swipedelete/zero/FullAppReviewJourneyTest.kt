package com.swipedelete.zero

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Real MainActivity, MediaStore and repositories; only synthetic originals on a fresh CI AVD.
 * No account connection, upload, purge, or deletion is invoked. Revocation is performed by
 * the external runner between instrumentation processes because Android kills the UID on revoke.
 */
class FullAppReviewJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val originals = linkedMapOf<Uri, String>()
    private val progress = Regex("\\d+/\\d+ reviewed")

    @Before fun requireSyntheticEmulator() {
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) {
            "Full-library permission journeys are restricted to a fresh synthetic Android emulator"
        }
    }

    @Test fun syntheticThirtySessionsPreserveOriginalsAndPersistUndo() {
        assertTrue("This journey must run in the cleanup-locked Photos test flavor", BuildConfig.SUPPORTS_PHOTOS_ARCHIVE)
        seedOriginals()
        grantPhotos()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            awaitText("Start review")
            val stagedBefore = rowCount("staged_files")
            val keptBefore = rowCount("kept_files")
            repeat(30) { cycle ->
                compose.onNodeWithText("Start review").performClick()
                compose.waitUntil(30_000) { runCatching { progressText().substringAfter('/').substringBefore(' ').toInt() > 0 && compose.onAllNodesWithText("Loading…").fetchSemanticsNodes().isEmpty() }.getOrDefault(false) }
                if (compose.onAllNodesWithText("Got it").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Got it").performClick()
                val initial = cursor()
                compose.onNodeWithContentDescription("Stage").performClick()
                awaitCursor(initial + 1)
                compose.onAllNodesWithContentDescription("Undo").onFirst().performClick()
                awaitCursor(initial)
                compose.onNodeWithContentDescription(if (cycle % 2 == 0) "Stage" else "Keep").performClick()
                awaitCursor(initial + 1)
                assertTrue("No deletion claim during staging", compose.onAllNodes(hasText("files removed", substring = true)).fetchSemanticsNodes().isEmpty())
                evidence("cycle-$cycle")
                compose.onNodeWithContentDescription("Back").performClick()
                awaitText("Start review")
                if (cycle == 14) {
                    scenario.recreate()
                    awaitText("Start review")
                }
                assertOriginalHashes()
            }
            // A persisted review action must survive recreation; the staging entry stays reachable.
            scenario.recreate()
            awaitText("Start review")
            val staged = hasContentDescription("Review", substring = true) and hasContentDescription("staged files", substring = true)
            compose.waitUntil(15_000) { compose.onAllNodes(staged).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(staged).onFirst().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("queue-unstage-all").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("queue-list").performScrollToKey("cleanup")
            compose.onNodeWithTag("queue-cleanup").performScrollTo()
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("Cleanup is unavailable", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodes(hasText("Cleanup is unavailable", substring = true)).onFirst().assertIsDisplayed()
            assertEquals(stagedBefore + 15, rowCount("staged_files"))
            assertEquals(keptBefore + 15, rowCount("kept_files"))
            saveCheckpoint(stagedBefore + 15, keptBefore + 15)
            evidence("cleanup-locked")
            assertOriginalHashes()
        }
    }

    /** Runner starts this method with all photo grants revoked, then grants and reruns
     * afterGrantShowsLibrary. Separate instrumentation processes make OS revocation genuine. */
    @Test fun deniedPermissionShowsPhotoOnboarding() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("permissionJourney") == "denied")
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            awaitText("Your library, your call")
            compose.onNodeWithText("Choose photos").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText("Start review").fetchSemanticsNodes().isEmpty())
            evidence("permission-denied")
        }
    }

    @Test fun afterGrantShowsLibrary() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("permissionJourney") == "granted")
        grantPhotos()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            awaitText("Start review")
            restoreAndAssertCheckpoint()
            compose.onNodeWithText("Start review").assertIsEnabled().performClick()
            compose.waitUntil(30_000) {
                runCatching {
                    val total = progressText().substringAfter('/').substringBefore(' ').toInt()
                    total > cursor() && compose.onAllNodesWithText("Loading…").fetchSemanticsNodes().isEmpty()
                }.getOrDefault(false)
            }
            if (compose.onAllNodesWithText("Got it").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Got it").performClick()
            compose.onNodeWithContentDescription("Stage").assertIsEnabled()
            compose.onNodeWithContentDescription("Keep").assertIsEnabled()
            evidence("permission-regranted")
            compose.onNodeWithContentDescription("Back").performClick()
            awaitText("Start review")
            restoreAndAssertCheckpoint()
        }
    }

    private fun awaitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun progressText(): String {
        val text = compose.onAllNodes(hasText("reviewed", substring = true)).fetchSemanticsNodes()
            .flatMap { if (it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) it.config[androidx.compose.ui.semantics.SemanticsProperties.Text] else emptyList() }
            .map { it.text }.single { progress.matches(it) }
        return text
    }
    private fun cursor(): Int = progressText().substringBefore('/').toInt()
    private fun awaitCursor(expected: Int) = compose.waitUntil(15_000) { runCatching { cursor() == expected }.getOrDefault(false) }
    private fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } } }
    private fun grantPhotos() {
        shell("pm grant ${context.packageName} ${if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE}")
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE))
    }
    private fun seedOriginals() {
        val count = InstrumentationRegistry.getArguments().getString("fixtureCount")?.toIntOrNull()?.coerceIn(100, 1000) ?: 120
        repeat(count) { index ->
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Screenshot_endurance_${System.nanoTime()}_$index.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Screenshots/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values))
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(index % 255, (index * 7) % 255, (index * 13) % 255))
            try { resolver.openOutputStream(uri, "w")!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
            assertEquals(1, resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null))
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)!!.use { assertTrue(it.moveToFirst()); assertTrue(it.getLong(0) > 0) }
            originals[uri] = hash(uri)
        }
    }
    private fun hash(uri: Uri): String = context.contentResolver.openInputStream(uri)!!.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun saveCheckpoint(staged: Int, kept: Int) {
        File(context.filesDir, "synthetic-journey-counts.txt").writeText("$staged\n$kept")
        File(context.filesDir, "synthetic-journey-originals.txt").writeText(originals.entries.joinToString("\n") { "${it.value} ${it.key}" })
    }
    private fun restoreAndAssertCheckpoint() {
        val expected = File(context.filesDir, "synthetic-journey-counts.txt").readLines().map { it.toInt() }
        assertEquals(expected[0], rowCount("staged_files"))
        assertEquals(expected[1], rowCount("kept_files"))
        originals.clear()
        val lines = File(context.filesDir, "synthetic-journey-originals.txt").readLines()
        assertTrue("The full journey's original manifest must survive UID restart", lines.size >= 100)
        for (line in lines) {
            val uri = Uri.parse(line.substringAfter(' '))
            require(uri.scheme == "content" && uri.authority == "media")
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)!!.use { row ->
                assertTrue(row.moveToFirst())
                assertTrue("Only our synthetic originals may be inspected", row.getString(0).startsWith("Screenshot_endurance_"))
            }
            originals[uri] = line.substringBefore(' ')
        }
        assertOriginalHashes()
    }
    private fun rowCount(table: String): Int {
        require(table in setOf("staged_files", "kept_files"))
        return android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("swipedelete-zero.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { rows -> assertTrue(rows.moveToFirst()); rows.getInt(0) }
        }
    }
    private fun assertOriginalHashes() { originals.forEach { (uri, before) -> assertEquals("Original changed/disappeared: $uri", before, hash(uri)) } }
    private fun evidence(name: String) {
        val directory = File(context.getExternalFilesDir(null), "journey-evidence").apply { mkdirs() }
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
        File(directory, "$name.txt").writeText((0 until roots).joinToString("\n") { compose.onAllNodes(isRoot())[it].printToString() })
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Runtime screenshot unavailable" }
        try { File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
        if (originals.isNotEmpty()) File(directory, "original-sha256.txt").writeText(originals.entries.joinToString("\n") { "${it.value} ${it.key}" })
    }
}
