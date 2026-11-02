package com.adochi.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import com.adochi.AdOchiApp
import com.adochi.MainActivity
import com.adochi.R
import com.adochi.core.Stats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Local "VPN" that only captures DNS. Nothing leaves the phone through us: blocked names are answered
 * locally, allowed names go straight to the chosen public resolver.
 */
class ShieldVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var tunnel: DnsTunnel? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null

    private val settings get() = AdOchiApp.instance.settings

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.shieldWanted = false
            shutdown()
            return START_NOT_STICKY
        }
        // Started with startForegroundService, so this must happen even if the tunnel is already up.
        startInForeground()
        // Null intent = restarted by the system; SERVICE_INTERFACE = started as always-on VPN.
        if (intent?.action == ACTION_RELOAD || tunnel == null) startTunnel() else applyNotificationChoice()
        return START_STICKY
    }

    private fun startTunnel() {
        VpnController.setState(ShieldState.STARTING)
        stopTunnel()
        if (prepare(this) != null) {
            // Permission was taken by another VPN app.
            settings.shieldWanted = false
            shutdown()
            return
        }
        val pfd = try {
            buildInterface()
        } catch (e: Exception) {
            Log.e("AdOchi", "Could not start the shield", e)
            null
        }
        if (pfd == null) {
            shutdown()
            return
        }
        tun = pfd
        tunnel = DnsTunnel(this, pfd, settings).also { it.start() }
        settings.shieldWanted = true
        VpnController.setState(ShieldState.RUNNING)
        applyNotificationChoice()
    }

    /**
     * Android binds an active VPN service as a foreground service itself, so once the tunnel is up the
     * notification is optional. Without it the only thing in the status bar is the system's key icon.
     */
    private fun applyNotificationChoice() {
        notificationJob?.cancel()
        if (settings.statusNotification) {
            keepNotificationFresh()
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun buildInterface(): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .setBlocking(true)
            .addAddress(V4_ADDRESS, 24)
            .addDnsServer(V4_DNS)
            .addRoute(V4_DNS, 32)
            .addAddress(V6_ADDRESS, 64)
            .addDnsServer(V6_DNS)
            .addRoute(V6_DNS, 128)
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        // Our own downloads and the Clean Browser filter themselves, so they skip the tunnel.
        builder.addDisallowedApplication(packageName)
        for (pkg in settings.bypassApps) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // App was uninstalled.
            }
        }
        return builder.establish()
    }

    // The type is declared in the main manifest; lint only reads the "full" flavor's small manifest here.
    @SuppressLint("ForegroundServiceType")
    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.SHIELD_ID, Notifications.shield(this, Stats.state.value), type)
    }

    private fun keepNotificationFresh() {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            var shown = -1L
            while (isActive) {
                val snapshot = Stats.state.value
                if (snapshot.blockedToday != shown) {
                    shown = snapshot.blockedToday
                    Notifications.updateShield(this@ShieldVpnService, snapshot)
                }
                delay(15_000)
            }
        }
    }

    private fun stopTunnel() {
        tunnel?.stop()
        tunnel = null
        try {
            tun?.close()
        } catch (e: Exception) {
            // Ignore.
        }
        tun = null
    }

    private fun shutdown() {
        notificationJob?.cancel()
        stopTunnel()
        Stats.persist()
        VpnController.setState(ShieldState.STOPPED)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        settings.shieldWanted = false
        shutdown()
    }

    override fun onDestroy() {
        scope.cancel()
        stopTunnel()
        Stats.persist()
        VpnController.setState(ShieldState.STOPPED)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.adochi.action.START"
        const val ACTION_STOP = "com.adochi.action.STOP"
        const val ACTION_RELOAD = "com.adochi.action.RELOAD"

        const val MTU = 1500

        // TEST-NET-1 and a private IPv6 range: never used by real networks, so they can't clash.
        private const val V4_ADDRESS = "192.0.2.1"
        private const val V4_DNS = "192.0.2.2"
        private const val V6_ADDRESS = "fd00:ad0c:a1::1"
        private const val V6_DNS = "fd00:ad0c:a1::2"
    }
}
