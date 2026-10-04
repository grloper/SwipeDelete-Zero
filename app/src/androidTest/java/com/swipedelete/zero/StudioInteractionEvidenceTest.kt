package com.swipedelete.zero

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs after all still-screen captures; a recorder failure cannot suppress those artifacts. */
class StudioInteractionEvidenceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun advanceFrames(milliseconds: Int) {
        repeat((milliseconds + 15) / 16) {
            compose.mainClock.advanceTimeBy(16)
            SystemClock.sleep(16)
        }
    }

    private fun shell(command: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // UiAutomation tokenizes command strings rather than interpreting quotes/operators.
        val script = File(instrumentation.targetContext.getExternalFilesDir(null), "studio-recorder-command.sh")
        check(script.absolutePath.matches(Regex("[A-Za-z0-9_./-]+")))
        script.writeText("#!/system/bin/sh\n$command\n")
        val descriptor = instrumentation.uiAutomation.executeShellCommand("sh ${script.absolutePath}")
        val reader = Executors.newSingleThreadExecutor { task -> Thread(task,"studio-recorder-output").apply { isDaemon = true } }
        return try {
            reader.submit<String> {
                ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText().trim() }
            }.get(5,TimeUnit.SECONDS)
        } finally {
            descriptor.close()
            reader.shutdownNow()
        }
    }

    private fun diagnostic() = shell("cat /sdcard/studio-record.log 2>/dev/null")
    private fun running(pid: Int) = shell("if kill -0 $pid 2>/dev/null; then echo running; else echo stopped; fi") == "running"
    private fun startRecording(): Int {
        val output = shell("screenrecord --verbose --time-limit 60 --bit-rate 1600000 /sdcard/studio-interaction.mp4 </dev/null >/sdcard/studio-record.log 2>&1 &\necho PID:\$!")
        val pid = output.removePrefix("PID:").toIntOrNull()?.takeIf { it > 1 }
        checkNotNull(pid) { "Recorder returned no owned PID: '$output'; ${diagnostic()}" }
        val deadline = SystemClock.elapsedRealtime() + 5000
        while(SystemClock.elapsedRealtime() < deadline) {
            check(running(pid)) { "Recorder exited during startup; ${diagnostic()}" }
            if(shell("test -s /sdcard/studio-interaction.mp4 && echo ready") == "ready") {
                println("Recorder started with owned PID $pid after committed library frame")
                return pid
            }
            SystemClock.sleep(100)
        }
        shell("kill -2 $pid")
        error("Recorder did not create video within 5 seconds; ${diagnostic()}")
    }

    private fun stopRecording(pid: Int) {
        shell("kill -2 $pid")
        val deadline = SystemClock.elapsedRealtime() + 5000
        while(SystemClock.elapsedRealtime() < deadline) {
            // A terminated child may remain a zombie until the shell/Android init reaps it.
            val status = shell("if [ ! -d /proc/$pid ]; then echo stopped; else cut -d ' ' -f 3 /proc/$pid/stat; fi")
            if(status == "stopped" || status == "Z") {
                check(shell("test -s /sdcard/studio-interaction.mp4 && echo present") == "present") { "Recorder produced no video; ${diagnostic()}" }
                return
            }
            SystemClock.sleep(100)
        }
        shell("kill -KILL $pid")
        error("Owned recorder did not finalize within 5 seconds; ${diagnostic()}")
    }

    @Test fun recordDashboardGestureDecisionsUndoAndQueue() {
        org.junit.Assume.assumeTrue("Independent recording run", InstrumentationRegistry.getArguments().getString("studioRecording") == "true")
        seedStudioPhotographs()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        androidx.test.core.app.ActivityScenario.launch<MainActivity>(android.content.Intent(context, MainActivity::class.java)).use {
            compose.waitUntil(30_000) { runCatching { compose.onNodeWithTag("start-review").assertIsEnabled(); true }.getOrDefault(false) }
            capturePracticeEvidence("swipe-recording-ready", listOf(contrastRegion("Dashboard title", compose.onNodeWithTag("dashboard-title", useUnmergedTree = true))))
            val pid = startRecording()
            compose.mainClock.autoAdvance = false
            try {
                SystemClock.sleep(900)
                compose.onNodeWithTag("start-review").performClick()
                advanceFrames(400)
                // Repository loading is wall-clock work, independent of animation frames.
                repeat(20) {
                    if (runCatching { compose.onNodeWithTag("stage-action").assertIsEnabled(); true }.getOrDefault(false)) return@repeat
                    advanceFrames(100)
                }
                if (compose.onAllNodesWithText("Got it").fetchSemanticsNodes().isNotEmpty()) { compose.onNodeWithText("Got it").performScrollTo().performClick(); advanceFrames(300) }
                compose.onNodeWithTag("review-photo").assertIsDisplayed()
                SystemClock.sleep(1000)
                val photo = compose.onNodeWithTag("review-photo")
                photo.performTouchInput { down(center) }
                repeat(12) { step ->
                    photo.performTouchInput { moveTo(androidx.compose.ui.geometry.Offset(width * (0.5f - (step + 1) * 0.04f), height * 0.5f)) }
                    advanceFrames(32)
                }
                photo.performTouchInput { up() }
                advanceFrames(500)
                compose.onNodeWithTag("undo-action").assertIsEnabled().performClick()
                advanceFrames(500)
                SystemClock.sleep(600)
                compose.onNodeWithTag("keep-action").performClick()
                advanceFrames(500)
                compose.onNodeWithTag("undo-action").assertIsEnabled().performClick()
                advanceFrames(500)
                compose.onNodeWithTag("stage-action").performClick()
                advanceFrames(500)
                SystemClock.sleep(700)
                compose.onNodeWithContentDescription("Back").performClick()
                advanceFrames(400)
                compose.onNodeWithTag("queue-bar").assertIsDisplayed()
                SystemClock.sleep(900)
            } finally {
                compose.mainClock.autoAdvance = true
                stopRecording(pid)
            }
        }
    }
}
