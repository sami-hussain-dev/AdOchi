package com.adochi.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.adochi.MainActivity

/** Quick Settings toggle so the shield can be switched without opening the app. */
class ShieldTileService : TileService() {

    override fun onStartListening() {
        refresh(VpnController.state.value != ShieldState.STOPPED)
    }

    override fun onClick() {
        val running = VpnController.state.value != ShieldState.STOPPED
        when {
            running -> {
                VpnController.stop(this)
                refresh(false)
            }
            VpnService.prepare(this) == null -> try {
                VpnController.start(this)
                refresh(true)
            } catch (e: IllegalStateException) {
                openApp()
            }
            else -> openApp() // Needs the one-time VPN permission dialog.
        }
    }

    private fun refresh(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AdOchi"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (on) "Protected" else "Off"
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent overload is only used below Android 14.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
