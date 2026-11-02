package com.adochi.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/** Turns the uid that made a network request into a stable app key and a readable name. */
object AppNames {

    private const val SYSTEM_KEY = "android"
    private val keyByUid = ConcurrentHashMap<Int, String>()
    private val labels = ConcurrentHashMap<String, String>()

    fun keyForUid(context: Context, uid: Int): String? {
        if (uid < 0) return null
        keyByUid[uid]?.let { return it }
        val packages = context.packageManager.getPackagesForUid(uid)
        val key = when {
            uid == Process.SYSTEM_UID || uid == 0 -> SYSTEM_KEY
            packages.isNullOrEmpty() -> "uid:$uid"
            else -> packages.sorted().first()
        }
        keyByUid[uid] = key
        return key
    }

    fun label(context: Context, key: String): String = labels.getOrPut(key) {
        when {
            key == SYSTEM_KEY -> "Android System"
            key.startsWith("uid:") -> "System service"
            else -> try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                key
            }
        }
    }

    /** True for keys that map to a real installed package (so it has an icon and settings page). */
    fun isPackage(key: String) = key != SYSTEM_KEY && !key.startsWith("uid:")
}
