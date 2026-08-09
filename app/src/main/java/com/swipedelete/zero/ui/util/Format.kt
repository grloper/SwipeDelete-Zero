package com.swipedelete.zero.ui.util

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Human-readable byte size, e.g. 2_576_980_378 -> "2.4 GB". */
fun Long.toReadableSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(this.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = this / 1024.0.pow(digitGroups.toDouble())
    return String.format(Locale.US, if (digitGroups == 0) "%.0f %s" else "%.1f %s", value, units[digitGroups])
}

/** Video/audio duration millis -> "m:ss" or "h:mm:ss". */
fun Long.toDurationLabel(): String {
    if (this <= 0) return ""
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Frame rate -> "60fps" (rounded to the nearest integer). */
fun Float.toFpsLabel(): String = "${Math.round(this)}fps"

/**
 * Resolution class from pixel dimensions, orientation-agnostic:
 * "4K" ≥ 2160 on the short side, then "1080p" / "720p" / "SD".
 */
fun resolutionClass(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "—"
    val short = minOf(width, height)
    return when {
        short >= 2160 -> "4K"
        short >= 1440 -> "1440p"
        short >= 1080 -> "1080p"
        short >= 720 -> "720p"
        else -> "SD"
    }
}

/** Bits/second -> "48 Mbps" (or "820 Kbps" below 1 Mbps). */
fun Long.toBitrateLabel(): String {
    if (this <= 0) return ""
    return if (this >= 1_000_000) String.format(Locale.US, "%.0f Mbps", this / 1_000_000.0)
    else String.format(Locale.US, "%.0f Kbps", this / 1_000.0)
}
