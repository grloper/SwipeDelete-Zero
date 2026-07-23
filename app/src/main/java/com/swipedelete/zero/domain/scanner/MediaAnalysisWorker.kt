package com.swipedelete.zero.domain.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.swipedelete.zero.data.local.MediaAnalysisDao
import com.swipedelete.zero.data.local.MediaAnalysisEntity
import com.swipedelete.zero.data.repository.MediaStoreRepository
import com.swipedelete.zero.domain.algorithm.BlurDetector
import com.swipedelete.zero.domain.algorithm.PerceptualHasher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background analysis pass: computes dHash + pHash + Laplacian-variance blur for
 * every image that hasn't been analysed yet, caching results in Room.
 *
 * Scheduled through WorkManager with `setRequiresCharging(true)` and
 * `setRequiresDeviceIdle(true)` (see [AnalysisScheduler]) so heavy decoding never
 * competes with the user or drains battery. The work is chunked and fully
 * resumable — already-analysed ids are skipped, so an interrupted run just picks
 * up where it left off.
 *
 * ### Memory discipline
 * Bitmaps are decoded with `inSampleSize` down to ~32px BEFORE any pixels are
 * read, so peak heap per item is a few KB — never a full-resolution decode.
 */
@HiltWorker
class MediaAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val mediaStore: MediaStoreRepository,
    private val analysisDao: MediaAnalysisDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            val alreadyDone = analysisDao.analyzedIds().toHashSet()
            val images = mediaStore.queryVisualMedia()
                .filter { it.type == com.swipedelete.zero.domain.model.MediaType.IMAGE }
                .filter { it.id !in alreadyDone }

            val batch = ArrayList<MediaAnalysisEntity>(BATCH_SIZE)
            for (item in images) {
                if (isStopped) break
                val matrix = decodeGrayscale(item.contentUri) ?: continue

                val dHash = PerceptualHasher.dHash(matrix, MATRIX, MATRIX)
                val pHash = PerceptualHasher.pHash(matrix, MATRIX, MATRIX)
                val blur = BlurDetector.analyze(matrix, MATRIX, MATRIX)

                batch += MediaAnalysisEntity(
                    mediaId = item.id,
                    contentUri = item.contentUri.toString(),
                    dHash = dHash,
                    pHash = pHash,
                    sharpnessVariance = blur.variance,
                    meanLuma = blur.meanLuma,
                    isBlurry = blur.isBlurry,
                    sizeBytes = item.sizeBytes,
                    analyzedAtMillis = 0L,
                )
                if (batch.size >= BATCH_SIZE) {
                    analysisDao.upsertAll(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) analysisDao.upsertAll(batch)
            Result.success()
        } catch (_: Exception) {
            // Transient decode/IO failures — let WorkManager retry with backoff.
            Result.retry()
        }
    }

    /**
     * Decode [uri] straight to a 32×32 grayscale luma matrix. Two-pass decode:
     * bounds-only first to compute a power-of-two `inSampleSize`, so the full
     * bitmap is never materialised in memory.
     */
    private fun decodeGrayscale(uri: Uri): IntArray? {
        val resolver = applicationContext.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val target = MATRIX
            var sample = 1
            var half = minOf(bounds.outWidth, bounds.outHeight) / 2
            while (half >= target) { sample *= 2; half /= 2 }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val matrix = PerceptualHasher.toGrayscaleMatrix(bmp, target)
            bmp.recycle()
            matrix
        } catch (_: Exception) {
            null // cloud-only / corrupt / revoked — skip, never crash the batch.
        }
    }

    companion object {
        const val WORK_NAME = "media-analysis"
        private const val MATRIX = 32
        private const val BATCH_SIZE = 64
    }
}
