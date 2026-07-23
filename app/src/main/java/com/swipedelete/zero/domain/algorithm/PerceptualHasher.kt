package com.swipedelete.zero.domain.algorithm

import android.graphics.Bitmap

/**
 * Perceptual image hashing for near-duplicate detection — fully local, no ML,
 * no network.
 *
 * Two complementary 64-bit hashes are produced from a tiny grayscale thumbnail:
 *  - **dHash** (difference hash): encodes horizontal gradient direction. Robust
 *    to brightness/scale changes; excellent for "same photo, re-encoded".
 *  - **pHash** (perceptual hash via 2-D DCT): captures low-frequency structure;
 *    catches crops/minor edits dHash can miss.
 *
 * Callers downsample bitmaps to a small grayscale square first (see
 * [toGrayscaleMatrix]) so the whole operation is a few thousand int ops.
 */
object PerceptualHasher {

    /** dHash works on a 9×8 grid (8×8 comparisons → 64 bits). */
    private const val DHASH_W = 9
    private const val DHASH_H = 8

    /** pHash uses a 32×32 DCT, keeping the top-left 8×8 low-frequency block. */
    private const val PHASH_SIZE = 32
    private const val PHASH_LOW = 8

    /**
     * Compute a dHash from an already-small grayscale [matrix] of size [w]×[h].
     * The matrix should be at least [DHASH_W]×[DHASH_H]; it is resampled with a
     * nearest-neighbour read to the required grid.
     */
    fun dHash(matrix: IntArray, w: Int, h: Int): Long {
        var hash = 0L
        var bit = 0
        for (row in 0 until DHASH_H) {
            val sy = row * h / DHASH_H
            for (col in 0 until DHASH_W - 1) {
                val sxL = col * w / DHASH_W
                val sxR = (col + 1) * w / DHASH_W
                val left = matrix[sy * w + sxL]
                val right = matrix[sy * w + sxR]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Compute a DCT-based pHash from a grayscale [matrix] of size [w]×[h]. */
    fun pHash(matrix: IntArray, w: Int, h: Int): Long {
        // Resample to PHASH_SIZE×PHASH_SIZE.
        val g = DoubleArray(PHASH_SIZE * PHASH_SIZE)
        for (y in 0 until PHASH_SIZE) {
            val sy = y * h / PHASH_SIZE
            for (x in 0 until PHASH_SIZE) {
                val sx = x * w / PHASH_SIZE
                g[y * PHASH_SIZE + x] = matrix[sy * w + sx].toDouble()
            }
        }
        val dct = dct2d(g, PHASH_SIZE)

        // Average of the top-left 8×8 low-frequency block, excluding the DC term.
        var sum = 0.0
        var count = 0
        for (y in 0 until PHASH_LOW) {
            for (x in 0 until PHASH_LOW) {
                if (x == 0 && y == 0) continue
                sum += dct[y * PHASH_SIZE + x]
                count++
            }
        }
        val avg = sum / count

        var hash = 0L
        var bit = 0
        for (y in 0 until PHASH_LOW) {
            for (x in 0 until PHASH_LOW) {
                if (x == 0 && y == 0) continue
                if (dct[y * PHASH_SIZE + x] > avg) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Hamming distance between two 64-bit hashes (0 == identical structure). */
    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Downsample [bitmap] to a [size]×[size] grayscale luminance matrix. Uses a
     * cheap box read of the already-decoded (and already-small) bitmap. Callers
     * are expected to have decoded at ~32px via inSampleSize, so this stays O(N).
     */
    fun toGrayscaleMatrix(bitmap: Bitmap, size: Int = PHASH_SIZE): IntArray {
        val scaled = if (bitmap.width == size && bitmap.height == size) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val out = IntArray(size * size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val gc = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma.
            out[i] = ((r * 299 + gc * 587 + b * 114) / 1000)
        }
        if (scaled !== bitmap) scaled.recycle()
        return out
    }

    // --- Separable 2-D DCT-II -------------------------------------------------

    private fun dct2d(input: DoubleArray, n: Int): DoubleArray {
        val temp = DoubleArray(n * n)
        val out = DoubleArray(n * n)
        val cos = dctCosTable(n)
        // Rows
        for (y in 0 until n) {
            for (u in 0 until n) {
                var s = 0.0
                for (x in 0 until n) s += input[y * n + x] * cos[u * n + x]
                temp[y * n + u] = s
            }
        }
        // Columns
        for (u in 0 until n) {
            for (v in 0 until n) {
                var s = 0.0
                for (y in 0 until n) s += temp[y * n + u] * cos[v * n + y]
                out[v * n + u] = s
            }
        }
        return out
    }

    private var cachedN = -1
    private var cachedTable: DoubleArray = DoubleArray(0)

    private fun dctCosTable(n: Int): DoubleArray {
        if (cachedN == n) return cachedTable
        val table = DoubleArray(n * n)
        for (u in 0 until n) {
            for (x in 0 until n) {
                table[u * n + x] = Math.cos((2 * x + 1) * u * Math.PI / (2.0 * n))
            }
        }
        cachedN = n
        cachedTable = table
        return table
    }
}
