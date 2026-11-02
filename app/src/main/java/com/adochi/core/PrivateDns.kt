package com.adochi.core

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings

/**
 * Android's own "Private DNS" setting. When it names a server, lookups go there over TLS and skip
 * AdOchi's tunnel. Pointing it at an ad-blocking server gives a lighter mode with no VPN icon.
 */
object PrivateDns {

    data class Server(val host: String, val name: String, val description: String)

    val SERVERS = listOf(
        Server("dns.adguard-dns.com", "AdGuard DNS", "Blocks ads and trackers. Free, no account."),
        Server("adblock.dns.mullvad.net", "Mullvad Ad-block", "Blocks ads and trackers. Free, no logs."),
        Server("family.adguard-dns.com", "AdGuard Family", "Ads, trackers and adult sites, with safe search."),
    )

    /** The server Private DNS is locked to, or null when it is Off or Automatic. */
    fun strictHost(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null // Private DNS arrived in Android 9.
        val fromSettings = try {
            val resolver = context.contentResolver
            if (Settings.Global.getString(resolver, "private_dns_mode") == "hostname") {
                Settings.Global.getString(resolver, "private_dns_specifier")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            null // Some Android versions hide these keys from apps.
        }
        if (fromSettings != null) return fromSettings
        return try {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION") // Need every network, not just the default (which is our VPN when on).
            connectivity.allNetworks.firstNotNullOfOrNull { connectivity.getLinkProperties(it)?.privateDnsServerName }
        } catch (e: Exception) {
            null
        }
    }

    fun isAdBlocking(host: String?) = host != null && SERVERS.any { it.host.equals(host, ignoreCase = true) }
}
