package com.swipedelete.zero.domain.scanner

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI needs to know about the on-device analysis pass. */
enum class AnalysisRunState { IDLE, RUNNING, DONE, FAILED }

/**
 * Schedules the [MediaAnalysisWorker].
 *
 * Two entry points, deliberately:
 *  - [ensureScheduled] keeps the battery-friendly daily pass (charging + idle),
 *    which is right for keeping an already-analysed library current.
 *  - [runNow] is the user-initiated path. The background pass alone means a
 *    fresh install can go days without ever satisfying charging-AND-idle, so the
 *    duplicate and blurry decks stay empty and the app looks like it found
 *    nothing rather than like it has not looked yet. When the user explicitly
 *    asks to scan, the constraints are dropped and the work is expedited.
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

    /** User tapped "Scan now" — run immediately, no charging/idle requirement. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<MediaAnalysisWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /** Live state of the manual pass, for the dashboard's scan affordance. */
    fun observeManualRun(): Flow<AnalysisRunState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME)
            .map { infos ->
                when (infos.firstOrNull()?.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> AnalysisRunState.RUNNING
                    WorkInfo.State.SUCCEEDED -> AnalysisRunState.DONE
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> AnalysisRunState.FAILED
                    else -> AnalysisRunState.IDLE
                }
            }

    private companion object {
        const val MANUAL_WORK_NAME = "media-analysis-manual"
    }
}
