package com.adochi.vpn

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adochi.AdOchiApp
import com.adochi.MainActivity
import com.adochi.R
import com.adochi.core.AppNames
import com.adochi.core.Stats
import com.adochi.ui.formatCount
import java.util.concurrent.ConcurrentHashMap

object Notifications {

    const val SHIELD_ID = 1
    private const val REPORT_ID = 2
    private const val CHANNEL_SHIELD = "shield"
    private const val CHANNEL_THREATS = "threats"
    private const val CHANNEL_REPORTS = "reports"
    private const val ALERT_REPEAT_MS = 10 * 60_000L
    private const val ALERT_GAP_MS = 5_000L
    private const val DANGER_COLOR = 0xFFFF5C7A.toInt()

    private val lastAlert = ConcurrentHashMap<String, Long>()
    @Volatile private var lastAnyAlert = 0L

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SHIELD, "Shield status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows that AdOchi is protecting this phone"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_THREATS, "Scam alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Warns about dangerous sites and scam links in messages"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REPORTS, "Weekly privacy report", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A short summary of what AdOchi blocked this week"
            }
        )
    }

    fun shield(context: Context, stats: Stats.Snapshot): Notification {
        val stop = PendingIntent.getService(
            context, 1,
            Intent(context, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_SHIELD)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle("AdOchi is protecting you")
            .setContentText("${formatCount(stats.blockedToday)} ads & trackers blocked today")
            .setContentIntent(openApp(context, 0))
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateShield(context: Context, stats: Stats.Snapshot) {
        if (!canNotify(context)) return
        post(context, SHIELD_ID, shield(context, stats))
    }

    fun threatBlocked(context: Context, domain: String, app: String?) {
        if (!AdOchiApp.instance.settings.threatAlerts || !canNotify(context)) return
        val now = System.currentTimeMillis()
        val last = lastAlert[domain] ?: 0L
        if (now - last < ALERT_REPEAT_MS || now - lastAnyAlert < ALERT_GAP_MS) return
        lastAlert[domain] = now
        lastAnyAlert = now

        val where = app?.let { " in ${AppNames.label(context, it)}" }.orEmpty()
        val text = "$domain$where is known for phishing, scams or malware. AdOchi stopped it before it could load. " +
            "Never enter bank details, OTP or passwords on links you didn't expect."
        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(DANGER_COLOR)
            .setContentTitle("Dangerous site blocked$where")
            .setContentText(domain)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, 2))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        post(context, 1000 + (domain.hashCode() and 0xFFF), notification)
    }

    /** A scam link arrived in a chat or SMS. Tapping opens the link checker with the details. */
    fun scamMessage(context: Context, appLabel: String, sender: String, url: String, host: String, reason: String) {
        if (!canNotify(context)) return
        val check = PendingIntent.getActivity(
            context, url.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_CHECK_LINK)
                .putExtra(Intent.EXTRA_TEXT, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val from = if (sender.isNotBlank()) " from $sender" else ""
        val text = "$host: $reason\nDon't open it, and never share your OTP, PIN or card number."
        val notification = NotificationCompat.Builder(context, CHANNEL_THREATS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(DANGER_COLOR)
            .setContentTitle("Scam link in $appLabel message$from")
            .setContentText(host)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(check)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        post(context, 5000 + (url.hashCode() and 0xFFF), notification)
    }

    fun weeklyReport(context: Context, title: String, text: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REPORTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, 3))
            .setAutoCancel(true)
            .build()
        post(context, REPORT_ID, notification)
    }

    private fun openApp(context: Context, requestCode: Int) = PendingIntent.getActivity(
        context, requestCode, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Every caller checks [canNotify] first; the user can still revoke it in between, hence the catch. */
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notification permission was just revoked.
        }
    }
}
