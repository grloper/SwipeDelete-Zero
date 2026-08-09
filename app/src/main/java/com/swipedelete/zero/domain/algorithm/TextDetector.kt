package com.swipedelete.zero.domain.algorithm

/**
 * Tells a screenshot of *words* apart from a screenshot of a *picture*.
 *
 * A photo's luminance histogram is broadly continuous — skin, sky and foliage
 * fill the midtones. A page of text is strongly bimodal: nearly every pixel is
 * either background or glyph, with little in between. Measuring how much of
 * the image sits in the extremes separates the two without OCR, without
 * decoding anything at full resolution, and without a model.
 *
 * It reuses the same 32x32 grayscale matrix the hashing and blur passes
 * already produce, so classification costs no extra decode.
 */
object TextDetector {

    /** Above this, an image is treated as predominantly text. */
    const val TEXT_BIMODALITY_THRESHOLD = 0.72

    /**
     * Fraction of pixels lying in the darkest or lightest fifth of the range.
     *
     * Returns 0..1. Text pages land high (0.8+); photographs land low (0.3-0.5).
     * Computed against the image's own min/max rather than absolute 0..255, so
     * a dim screenshot and a bright one score the same.
     */
    fun bimodality(matrix: IntArray): Double {
        if (matrix.isEmpty()) return 0.0
        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        for (v in matrix) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = max - min
        // A flat image has no modes to speak of.
        if (range < 24) return 0.0

        val lowCut = min + range / 5.0
        val highCut = max - range / 5.0
        var extremes = 0
        for (v in matrix) {
            if (v <= lowCut || v >= highCut) extremes++
        }
        return extremes.toDouble() / matrix.size
    }

    fun isLikelyText(matrix: IntArray): Boolean =
        bimodality(matrix) >= TEXT_BIMODALITY_THRESHOLD
}
