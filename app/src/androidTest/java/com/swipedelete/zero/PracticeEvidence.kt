package com.swipedelete.zero

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import kotlin.math.pow

internal data class PixelContrastRegion(
    val label: String,
    val measure: () -> PixelBounds,
    val foreground: Int = Color.rgb(244,245,242),
    val background: Int = Color.rgb(16,18,17),
    val minimumRatio: Double = 7.0
)

internal data class PixelBounds(val root: Rect, val window: Rect, val screen: Rect)

internal fun contrastRegion(label: String, node: SemanticsNodeInteraction,
    background: Int = Color.rgb(16,18,17), foreground: Int = Color.rgb(244,245,242)) = PixelContrastRegion(label, {
    val semantics = node.assertIsDisplayed().fetchSemanticsNode()
    val root = semantics.boundsInRoot
    // UiAutomation returns the entire display, while boundsInRoot is local to the Compose host.
    // Recreation/insets can move that host. Derive the actual screen origin; never hard-code an inset.
    val offset = semantics.positionOnScreen - semantics.positionInRoot
    check(offset.x.isFinite() && offset.y.isFinite()) { "No attached screen position for $label" }
    PixelBounds(root, semantics.boundsInWindow, root.translate(offset))
}, background = background, foreground = foreground)

private fun committedFrame() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    if(Build.VERSION.SDK_INT < 29) return
    val committed = java.util.concurrent.CountDownLatch(1)
    instrumentation.runOnMainSync {
        val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
        val view = checkNotNull(activity).window.decorView
        check(view.isHardwareAccelerated) { "Runtime evidence needs a hardware-rendered activity" }
        view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
        view.postInvalidateOnAnimation()
    }
    check(committed.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "No committed evidence frame" }
}

private val linear = DoubleArray(256) {
    val value = it / 255.0
    if(value <= .04045) value / 12.92 else ((value + .055) / 1.055).pow(2.4)
}
private fun luminance(color: Int) = .2126 * linear[Color.red(color)] + .7152 * linear[Color.green(color)] + .0722 * linear[Color.blue(color)]
private fun matches(color: Int, expected: Int, tolerance: Int) =
    abs(Color.red(color)-Color.red(expected)) <= tolerance && abs(Color.green(color)-Color.green(expected)) <= tolerance && abs(Color.blue(color)-Color.blue(expected)) <= tolerance

private data class PixelResult(val label: String, val foregroundPixels: Int, val backgroundPixels: Int,
    val foregroundFraction: Double, val observedContrast: Double, val passed: Boolean, val signature: Long)

private fun inspectRegion(bitmap: Bitmap, region: PixelContrastRegion, bounds: Rect): PixelResult {
    val left = bounds.left.toInt().coerceIn(0,bitmap.width)
    val top = bounds.top.toInt().coerceIn(0,bitmap.height)
    val right = bounds.right.toInt().coerceIn(left,bitmap.width)
    val bottom = bounds.bottom.toInt().coerceIn(top,bitmap.height)
    var foreground = 0; var background = 0
    var dimmestForeground = 1.0; var brightestBackground = 0.0
    var signature = 1L
    for(y in top until bottom) for(x in left until right) {
        val pixel = bitmap.getPixel(x,y)
        signature = signature * 31 + pixel
        if(matches(pixel,region.foreground,16)) { foreground++; dimmestForeground = minOf(dimmestForeground,luminance(pixel)) }
        if(matches(pixel,region.background,4)) { background++; brightestBackground = maxOf(brightestBackground,luminance(pixel)) }
    }
    val fraction = foreground.toDouble() / ((right-left)*(bottom-top)).coerceAtLeast(1)
    val ratio = if(foreground == 0 || background == 0) 0.0 else (dimmestForeground+.05)/(brightestBackground+.05)
    return PixelResult(region.label,foreground,background,fraction,ratio,
        foreground >= 12 && background >= 12 && fraction >= .008 && ratio >= region.minimumRatio,signature)
}

/** Original compositor pixels only. Color checks reject dark titles/icons and stale destinations. */
internal fun capturePracticeEvidence(name: String, regions: List<PixelContrastRegion> = emptyList()) {
    if (!Build.FINGERPRINT.contains("generic") && !Build.FINGERPRINT.contains("emulator") &&
        !Build.MODEL.contains("sdk", ignoreCase = true) && Build.HARDWARE !in setOf("ranchu", "goldfish")) return
    require(name.matches(Regex("[a-z0-9-]+")))
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "practice-evidence").apply { mkdirs() }
    var previousSignatures: List<Long>? = null
    var lastResults = emptyList<PixelResult>()
    repeat(8) {
        val bounds = regions.map { it.measure() }
        committedFrame()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            lastResults = regions.mapIndexed { index, region -> inspectRegion(bitmap,region,bounds[index].screen) }
            val rootResults = regions.mapIndexed { index, region -> inspectRegion(bitmap,region,bounds[index].root) }
            // Preserve the actual failed frame too, so failures are visually reviewable.
            File(directory,"$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) { "PNG encoding failed: $name" }
            }
            if(regions.isNotEmpty()) File(directory,"$name-contrast.json").writeText(org.json.JSONObject().apply {
                put("screenshot",name); put("width_px",bitmap.width); put("height_px",bitmap.height)
                put("coordinate_space","display pixels; semantic root translated by positionOnScreen - positionInRoot")
                put("regions",org.json.JSONArray(lastResults.mapIndexed { index, result -> org.json.JSONObject().apply {
                    put("label",result.label); put("foreground_pixels",result.foregroundPixels)
                    put("background_pixels",result.backgroundPixels); put("foreground_fraction",result.foregroundFraction)
                    put("observed_contrast",result.observedContrast); put("passed",result.passed)
                    fun rect(value: Rect) = org.json.JSONArray(listOf(value.left,value.top,value.right,value.bottom))
                    put("root_bounds_px",rect(bounds[index].root)); put("window_bounds_px",rect(bounds[index].window))
                    put("screen_bounds_px",rect(bounds[index].screen))
                    put("legacy_root_sample_foreground_pixels",rootResults[index].foregroundPixels)
                    put("legacy_root_sample_contrast",rootResults[index].observedContrast)
                } }))
            }.toString(2))
            val signatures = lastResults.map { it.signature }
            if(regions.isEmpty() || (lastResults.all { it.passed } && signatures == previousSignatures)) {
                println("Committed screenshot: $name; pixel contrast=$lastResults")
                return
            }
            previousSignatures = if(lastResults.all { it.passed }) signatures else null
        } finally { bitmap.recycle() }
    }
    error("Screenshot did not settle with readable expected foreground for $name: $lastResults")
}
