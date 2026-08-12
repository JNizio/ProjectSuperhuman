package com.projectsuperhuman.next

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal class MiniMetricsBackgroundWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (!MiniMetricsHealthConnect.backgroundReadAvailable(applicationContext)) return Result.success()
        if (!MiniMetricsHealthConnect.hasBackgroundReadPermission(applicationContext)) return Result.success()
        if (!MiniMetricsHealthConnect.hasAnyPermission(applicationContext)) return Result.success()

        val result = MiniMetricsHealthConnect.syncCurrent(applicationContext)
        if (result.success) {
            // Run after the raw mini-metric refresh so repaired total/active/resting values remain
            // the newest daily summaries in the Data Vault.
            CalorieAccuracyEngine.syncCurrent(applicationContext)
        }
        return when {
            result.success -> Result.success()
            runAttemptCount < 2 -> Result.retry()
            else -> Result.success()
        }
    }
}

internal object MiniMetricsBackgroundSync {
    private const val UNIQUE_WORK = "fit3-mini-metrics-background-sync"

    fun ensureScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
        val work = PeriodicWorkRequestBuilder<MiniMetricsBackgroundWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }
}
