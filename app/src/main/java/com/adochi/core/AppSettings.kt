package com.adochi.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** All user choices, stored on the device only. [changes] ticks on every write so Compose can re-read. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("adochi_settings", Context.MODE_PRIVATE)
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    // Held in a field: SharedPreferences keeps listeners weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _changes.update { it + 1 }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    var dnsProvider: String
        get() = prefs.getString("dns_provider", null) ?: DnsProviders.DEFAULT.id
        set(value) = prefs.edit { putString("dns_provider", value) }

    var encryptedDns: Boolean
        get() = prefs.getBoolean("encrypted_dns", true)
        set(value) = prefs.edit { putBoolean("encrypted_dns", value) }

    var startOnBoot: Boolean
        get() = prefs.getBoolean("start_on_boot", true)
        set(value) = prefs.edit { putBoolean("start_on_boot", value) }

    var threatAlerts: Boolean
        get() = prefs.getBoolean("threat_alerts", true)
        set(value) = prefs.edit { putBoolean("threat_alerts", value) }

    /** True while the user wants the shield on; used to restore it after a reboot. */
    var shieldWanted: Boolean
        get() = prefs.getBoolean("shield_wanted", false)
        set(value) = prefs.edit { putBoolean("shield_wanted", value) }

    var lastUpdate: Long
        get() = prefs.getLong("last_update", 0L)
        set(value) = prefs.edit { putLong("last_update", value) }

    var enabledLists: Set<String>
        get() = prefs.getStringSet("enabled_lists", null)?.toSet() ?: FilterCatalog.defaultIds
        set(value) = prefs.edit { putStringSet("enabled_lists", value) }

    var userAllow: Set<String>
        get() = prefs.getStringSet("user_allow", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("user_allow", value) }

    var userBlock: Set<String>
        get() = prefs.getStringSet("user_block", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("user_block", value) }

    /** Package names that bypass the shield entirely. */
    var bypassApps: Set<String>
        get() = prefs.getStringSet("bypass_apps", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("bypass_apps", value) }

    /** Check links in chat/SMS notifications (needs notification access too). */
    var messageGuard: Boolean
        get() = prefs.getBoolean("message_guard", true)
        set(value) = prefs.edit { putBoolean("message_guard", value) }

    /** Apps whose every lookup is refused, so offline games can't fetch ads at all. */
    var noInternetApps: Set<String>
        get() = prefs.getStringSet("no_internet_apps", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("no_internet_apps", value) }

    /** Keep the "AdOchi is protecting you" notification (and its status bar icon) while the shield runs. */
    var statusNotification: Boolean
        get() = prefs.getBoolean("status_notification", false)
        set(value) = prefs.edit { putBoolean("status_notification", value) }

    /** The one-time "add AdOchi to Quick Settings" prompt was shown. */
    var tilePrompted: Boolean
        get() = prefs.getBoolean("tile_prompted", false)
        set(value) = prefs.edit { putBoolean("tile_prompted", value) }

    /** Apps the user marked as trusted in the phone scan. */
    var trustedApps: Set<String>
        get() = prefs.getStringSet("trusted_apps", null)?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet("trusted_apps", value) }

    fun listCount(id: String): Int = prefs.getInt("count_$id", 0)

    fun setListCount(id: String, count: Int) = prefs.edit { putInt("count_$id", count) }

    fun etag(key: String): String? = prefs.getString("etag_$key", null)

    fun setEtag(key: String, value: String?) = prefs.edit {
        if (value == null) remove("etag_$key") else putString("etag_$key", value)
    }
}
