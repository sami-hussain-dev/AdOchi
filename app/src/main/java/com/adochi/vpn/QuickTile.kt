package com.adochi.vpn

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.adochi.R

/** Asks Android to put the AdOchi tile in Quick Settings with one tap (Android 13+). */
object QuickTile {

    val canRequest: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** [onResult] gets true when the tile is there now (just added or already present). */
    fun request(context: Context, onResult: (Boolean) -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return onResult(false)
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return onResult(false)
        try {
            manager.requestAddTileService(
                ComponentName(context, ShieldTileService::class.java),
                context.getString(R.string.tile_label),
                Icon.createWithResource(context, R.drawable.ic_stat_shield),
                context.mainExecutor,
            ) { result ->
                onResult(
                    result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                )
            }
        } catch (e: Exception) {
            // Only allowed while AdOchi is on screen; some phones' launchers don't support it.
            onResult(false)
        }
    }
}
