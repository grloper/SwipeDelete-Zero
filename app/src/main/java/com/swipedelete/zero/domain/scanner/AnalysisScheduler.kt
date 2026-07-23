package com.swipedelete.zero.domain.scanner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the [MediaAnalysisWorker] under battery-friendly constraints.
 *
 * Per the PRD, perceptual hashing and blur detection are CPU-heavy and must only
 * run when the device is **charging and idle**. WorkManager guarantees the job is
 * deferred until those constraints hold and re-run after reboots.
 */
@Singleton
class AnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureScheduled() {
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<MediaAnalysisWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MediaAnalysisWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
