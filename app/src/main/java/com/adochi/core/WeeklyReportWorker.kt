package com.adochi.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.adochi.ui.formatCount
import com.adochi.vpn.Notifications
import java.util.concurrent.TimeUnit

/** Once a week: "AdOchi blocked 12K trackers this week. Most from TikTok." */
class WeeklyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Stats.persist()
        val usage = Stats.usageForDays(7)
        val blocked = usage.sumOf { it.blocked }
        if (blocked == 0L) return Result.success()
        val threats = usage.sumOf { it.threats }
        val top = usage.filter { AppNames.isPackage(it.app) }.take(3)
            .joinToString { "${AppNames.label(applicationContext, it.app)} (${formatCount(it.blocked)})" }
        val text = buildString {
            append("${formatCount(blocked)} ads & trackers blocked")
            if (threats > 0) append(", ${formatCount(threats)} scam or malware sites stopped")
            append(".")
            if (top.isNotEmpty()) append(" Most tracking came from $top.")
        }
        Notifications.weeklyReport(applicationContext, "Your week with AdOchi", text)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(7, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("weekly-report", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
