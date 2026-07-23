package com.swipedelete.zero

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.swipedelete.zero.domain.scanner.AnalysisScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Wires Hilt, supplies a Hilt-aware [HiltWorkerFactory] to WorkManager (the
 * default initializer is disabled in the manifest), and schedules the
 * battery-constrained media-analysis job on first launch.
 */
@HiltAndroidApp
class SwipeDeleteApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var analysisScheduler: AnalysisScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        analysisScheduler.ensureScheduled()
    }
}
