package com.adochi.guard

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.adochi.AdOchiApp
import com.adochi.core.AppNames
import com.adochi.core.LinkInspector
import com.adochi.core.MessageGuard
import com.adochi.core.Stats
import com.adochi.vpn.Notifications

/**
 * Reads the text of new chat/SMS notifications on the device, checks any links in them and warns
 * about scam links before the user taps them. Nothing is stored or sent anywhere.
 */
class MessageGuardService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!AdOchiApp.instance.settings.messageGuard) return
        if (sbn.packageName !in MessageGuard.WATCHED_APPS) return
        val extras = sbn.notification.extras ?: return
        val text = messageText(extras)
        if (text.isBlank()) return

        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val appLabel = AppNames.label(this, sbn.packageName)
        for (report in LinkInspector.inspectMessage(text)) {
            val url = report.url ?: continue
            val host = report.host ?: continue
            if (report.risk < LinkInspector.ALERT_RISK || !MessageGuard.shouldAlert(url)) continue
            val reason = report.reasons.firstOrNull().orEmpty()
            MessageGuard.add(
                MessageGuard.Catch(
                    time = System.currentTimeMillis(),
                    appLabel = appLabel,
                    sender = sender,
                    url = url,
                    host = host,
                    danger = report.level == LinkInspector.Level.DANGER,
                    reason = reason,
                )
            )
            Stats.recordScamMessage()
            Notifications.scamMessage(this, appLabel, sender, url, host, reason)
        }
    }

    private fun messageText(extras: Bundle): String = buildString {
        for (key in listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)) {
            extras.getCharSequence(key)?.let { append(it).append('\n') }
        }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { append(it).append('\n') }
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { message ->
            (message as? Bundle)?.getCharSequence("text")?.let { append(it).append('\n') }
        }
    }
}
