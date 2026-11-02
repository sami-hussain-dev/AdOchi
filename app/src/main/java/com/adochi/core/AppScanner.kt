package com.adochi.core

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.adochi.AdOchiApp

/**
 * Finds installed apps with the abilities banking trojans and stalkerware rely on: reading SMS
 * (OTP theft), controlling the screen through Accessibility, drawing over other apps, device admin,
 * reading all notifications, hiding their icon, and being installed from outside an app store.
 */
object AppScanner {

    enum class Risk { DANGER, REVIEW, OK }

    data class Finding(val points: Int, val text: String)

    data class AppRisk(
        val packageName: String,
        val label: String,
        val risk: Risk,
        val score: Int,
        val findings: List<Finding>,
        val accessibilityOn: Boolean,
        val deviceAdmin: Boolean,
    )

    data class Result(val checked: Int, val apps: List<AppRisk>)

    private const val DANGER_SCORE = 60
    private const val REVIEW_SCORE = 30

    private val TRUSTED_STORES = setOf(
        "com.android.vending", "com.google.android.feedback", "com.sec.android.app.samsungapps",
        "com.huawei.appmarket", "com.xiaomi.market", "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market",
        "com.vivo.appstore", "com.bbk.appstore", "com.transsion.phoenix", "com.amazon.venezia", "org.fdroid.fdroid",
    )

    fun scan(context: Context, trusted: Set<String> = AdOchiApp.instance.settings.trustedApps): Result {
        val pm = context.packageManager
        val accessibility = enabledPackages(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val listeners = enabledPackages(context, "enabled_notification_listeners")
        val admins = context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins
            ?.map { it.packageName }?.toSet().orEmpty()
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val withIcon = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .toSet()

        @Suppress("DEPRECATION")
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val userApps = packages.filter { info ->
            val app = info.applicationInfo ?: return@filter false
            app.flags and ApplicationInfo.FLAG_SYSTEM == 0 && info.packageName != context.packageName
        }

        val results = userApps.map { info ->
            assess(context, pm, info, accessibility, listeners, admins, appOps, info.packageName in withIcon, info.packageName in trusted)
        }
        val ranked = results.sortedWith(compareBy<AppRisk> { it.risk.ordinal }.thenByDescending { it.score })
        return Result(userApps.size, ranked)
    }

    private fun assess(
        context: Context,
        pm: PackageManager,
        info: PackageInfo,
        accessibility: Set<String>,
        listeners: Set<String>,
        admins: Set<String>,
        appOps: AppOpsManager?,
        hasIcon: Boolean,
        trusted: Boolean,
    ): AppRisk {
        val pkg = info.packageName
        val app = info.applicationInfo!!
        val findings = mutableListOf<Finding>()

        val granted = grantedPermissions(info)
        val requested = info.requestedPermissions?.toSet().orEmpty()
        val accessibilityOn = pkg in accessibility
        val admin = pkg in admins
        if (accessibilityOn) findings += Finding(40, "Accessibility is ON: it can read your screen and tap buttons for you.")
        if (admin) findings += Finding(35, "Device admin: it can lock the phone and is hard to uninstall.")
        if (Manifest.permission.READ_SMS in granted || Manifest.permission.RECEIVE_SMS in granted) {
            findings += Finding(30, "Can read your SMS, including bank OTP codes.")
        }
        if (pkg in listeners) findings += Finding(20, "Can read all your notifications, including OTPs and chats.")
        // Only apps that ask for the overlay permission can use it; the app-op default alone means nothing.
        if (Manifest.permission.SYSTEM_ALERT_WINDOW in requested && canDrawOverlays(appOps, app.uid, pkg)) findings +=Finding(15, "Can draw over other apps (used for fake login screens).")
        val installer = installerOf(pm, pkg)
        val fromStore = installer != null && installer in TRUSTED_STORES
        if (!fromStore) findings += Finding(25, "Installed from outside an app store (APK file or link).")
        if (!hasIcon) findings +=Finding(20, "Hides its icon from the app drawer.")
        if (Manifest.permission.REQUEST_INSTALL_PACKAGES in requested) {
            findings += Finding(10, "Can install other apps.")
        }
        if (Manifest.permission.READ_CONTACTS in granted && Manifest.permission.READ_CALL_LOG in granted) {
            findings += Finding(10, "Reads your contacts and call history.")
        }

        var score = findings.sumOf { it.points }
        if (fromStore) score /= 2 // Store apps are reviewed; still shown if they hold several powers.
        val risk = when {
            trusted -> Risk.OK
            score >= DANGER_SCORE -> Risk.DANGER
            score >= REVIEW_SCORE -> Risk.REVIEW
            else -> Risk.OK
        }
        return AppRisk(pkg, AppNames.label(context, pkg), risk, score, findings, accessibilityOn, admin)
    }

    private fun grantedPermissions(info: PackageInfo): Set<String> {
        val names = info.requestedPermissions ?: return emptySet()
        val flags = info.requestedPermissionsFlags ?: return emptySet()
        return names.indices
            .filter { flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            .map { names[it] }
            .toSet()
    }

    @Suppress("DEPRECATION") // The newer checkOp variants need API 36; these still work everywhere.
    private fun canDrawOverlays(appOps: AppOpsManager?, uid: Int, pkg: String): Boolean {
        appOps ?: return false
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
            } else {
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, pkg)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun installerOf(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkg)
        }
    } catch (e: Exception) {
        null
    }

    private fun enabledPackages(context: Context, key: String): Set<String> =
        Settings.Secure.getString(context.contentResolver, key).orEmpty()
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
            .toSet()
}
