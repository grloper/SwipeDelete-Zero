package com.swipedelete.zero.domain.algorithm

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Blur detection via **variance of the Laplacian** — the classic, fully-local
 * sharpness estimator. A sharp image has strong high-frequency edges, so the
 * Laplacian (a 2nd-derivative edge operator) has high variance. A blurry image
 * has weak edges → low variance.
 *
 * ### Guarding against false positives
 * Macro shots, bokeh portraits and night photos legitimately contain large soft
 * regions and can score low. To avoid nuking a deliberately-shallow-DOF portrait
 * we:
 *  1. use a **conservative** default threshold (only very low variance flags),
 *  2. compute variance over the **luma** channel of a downscaled grayscale image
 *     so noise is averaged out, and
 *  3. skip images whose mean luma is very low (dark/night shots) unless variance
 *     is extremely low, since dark frames naturally suppress edge energy.
 */
object BlurDetector {

    /**
     * Below this Laplacian variance an image is considered blurry. Deliberately
     * conservative — tuned so only genuinely soft, mis-focused frames trip it.
     */
    const val DEFAULT_BLUR_THRESHOLD = 60.0

    /** Mean luma (0..255) under which we treat a frame as a "dark/night" shot. */
    private const val DARK_LUMA = 40.0

    /** For dark frames we require an even lower variance before flagging. */
    private const val DARK_BLUR_THRESHOLD = 18.0

    data class Result(
        val variance: Double,
        val meanLuma: Double,
        val isBlurry: Boolean,
    )

    /**
     * Analyse a grayscale [matrix] of dimensions [w]×[h] (as produced by
     * [PerceptualHasher.toGrayscaleMatrix]). Returns the variance, mean luma and
     * a conservative blur verdict.
     */
    fun analyze(
        matrix: IntArray,
        w: Int,
        h: Int,
        threshold: Double = DEFAULT_BLUR_THRESHOLD,
    ): Result {
        val variance = laplacianVariance(matrix, w, h)
        var lumaSum = 0.0
        for (v in matrix) lumaSum += v
        val meanLuma = lumaSum / max(1, matrix.size)

        val effectiveThreshold =
            if (meanLuma < DARK_LUMA) DARK_BLUR_THRESHOLD else threshold
        return Result(
            variance = variance,
            meanLuma = meanLuma,
            isBlurry = variance < effectiveThreshold,
        )
    }

    /** Convenience overload that downsamples a bitmap first. */
    fun analyze(bitmap: Bitmap, threshold: Double = DEFAULT_BLUR_THRESHOLD): Result {
        val size = 32
        val matrix = PerceptualHasher.toGrayscaleMatrix(bitmap, size)
        return analyze(matrix, size, size, threshold)
    }

    /**
     * Convolve the grayscale matrix with the 3×3 Laplacian kernel
     * `[0 1 0; 1 -4 1; 0 1 0]` and return the variance of the response. Border
     * pixels are skipped to avoid edge artifacts.
     */
    private fun laplacianVariance(m: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return Double.MAX_VALUE
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = m[y * w + x]
                val lap = (m[(y - 1) * w + x] +
                    m[(y + 1) * w + x] +
                    m[y * w + (x - 1)] +
                    m[y * w + (x + 1)] -
                    4 * c).toDouble()
                sum += lap
                sumSq += lap * lap
                n++
            }
        }
        if (n == 0) return Double.MAX_VALUE
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}
