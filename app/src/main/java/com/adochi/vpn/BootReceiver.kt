package com.adochi.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.adochi.AdOchiApp

/** Turns the shield back on after a reboot or app update, if it was on before. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = AdOchiApp.instance.settings
        if (!settings.startOnBoot || !settings.shieldWanted) return
        if (VpnService.prepare(context) != null) return
        try {
            VpnController.start(context)
        } catch (e: IllegalStateException) {
            // Background start refused; the user can turn it on from the app or tile.
        }
    }
}
