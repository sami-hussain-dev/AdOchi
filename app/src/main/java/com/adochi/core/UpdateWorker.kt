package com.adochi.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adochi.AdOchiApp
import java.util.concurrent.TimeUnit

/** Keeps blocklists fresh. Lists come straight from GitHub/GitLab, so there is no server to pay for. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = AdOchiApp.instance.filters.update()
        return if (result.total > 0 && result.failed == result.total) Result.retry() else Result.success()
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(2, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("filter-update", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("filter-update-now", ExistingWorkPolicy.KEEP, request)
        }
    }
}
