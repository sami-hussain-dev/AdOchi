package com.adochi.vpn

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ShieldState { STOPPED, STARTING, RUNNING }

object VpnController {

    private val _state = MutableStateFlow(ShieldState.STOPPED)
    val state: StateFlow<ShieldState> = _state.asStateFlow()

    internal fun setState(value: ShieldState) {
        _state.value = value
    }

    /** Caller must have VPN permission already (VpnService.prepare returned null). */
    fun start(context: Context) {
        ContextCompat.startForegroundService(context, intent(context, ShieldVpnService.ACTION_START))
    }

    fun stop(context: Context) {
        try {
            context.startService(intent(context, ShieldVpnService.ACTION_STOP))
        } catch (e: IllegalStateException) {
            // Not running; nothing to stop.
        }
    }

    /** Re-creates the tunnel so changes such as excluded apps take effect. */
    fun reload(context: Context) {
        if (_state.value == ShieldState.STOPPED) return
        ContextCompat.startForegroundService(context, intent(context, ShieldVpnService.ACTION_RELOAD))
    }

    private fun intent(context: Context, action: String) =
        Intent(context, ShieldVpnService::class.java).setAction(action)
}
