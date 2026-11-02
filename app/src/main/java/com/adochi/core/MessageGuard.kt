package com.adochi.core

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Scam links found in incoming chat and SMS notifications. Messages themselves are never stored. */
object MessageGuard {

    data class Catch(
        val time: Long,
        val appLabel: String,
        val sender: String,
        val url: String,
        val host: String,
        val danger: Boolean,
        val reason: String,
    )

    /** Chat, SMS and email apps whose notifications are checked. */
    val WATCHED_APPS = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.miui.mms", "com.oneplus.mms", "com.coloros.mms", "com.transsion.message",
        "com.truecaller", "org.telegram.messenger", "org.telegram.messenger.web", "com.facebook.orca",
        "com.facebook.mlite", "com.instagram.android", "org.thoughtcrime.securesms", "com.imo.android.imoim",
        "com.viber.voip", "com.snapchat.android", "com.google.android.gm", "com.microsoft.office.outlook",
        "com.discord", "com.zhiliaoapp.musically",
    )

    private const val MAX_CATCHES = 30
    private lateinit var prefs: SharedPreferences
    private val _catches = MutableStateFlow<List<Catch>>(emptyList())
    val catches: StateFlow<List<Catch>> = _catches.asStateFlow()
    private val recentUrls = LinkedHashMap<String, Long>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("adochi_guard", Context.MODE_PRIVATE)
        _catches.value = prefs.getString("catches", null)?.lines()?.mapNotNull(::decode).orEmpty()
    }

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.split(':').mapNotNull { ComponentName.unflattenFromString(it) }.any { it.packageName == context.packageName }
    }

    /** False when this link was already reported in the last 30 minutes (chat apps repost notifications). */
    @Synchronized
    fun shouldAlert(url: String): Boolean {
        val now = System.currentTimeMillis()
        recentUrls.entries.removeAll { now - it.value > 30 * 60_000L }
        if (url in recentUrls) return false
        recentUrls[url] = now
        return true
    }

    @Synchronized
    fun add(catch: Catch) {
        val updated = (listOf(catch) + _catches.value).take(MAX_CATCHES)
        _catches.value = updated
        prefs.edit { putString("catches", updated.joinToString("\n", transform = ::encode)) }
    }

    @Synchronized
    fun clear() {
        _catches.value = emptyList()
        prefs.edit { remove("catches") }
    }

    private fun encode(c: Catch) = listOf(c.time, c.appLabel, c.sender, c.url, c.host, c.danger, c.reason)
        .joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') }

    private fun decode(line: String): Catch? {
        val p = line.split('\t')
        if (p.size != 7) return null
        return Catch(p[0].toLongOrNull() ?: return null, p[1], p[2], p[3], p[4], p[5].toBoolean(), p[6])
    }
}
