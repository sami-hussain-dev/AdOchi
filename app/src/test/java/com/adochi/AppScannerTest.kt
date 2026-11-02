package com.adochi

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.adochi.core.AppScanner
import com.adochi.core.AppScanner.Risk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppScannerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private var nextUid = 10_100

    private fun install(pkg: String, granted: List<String>, installer: String?, launcher: Boolean) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                uid = nextUid++
                flags = 0
            }
            requestedPermissions = granted.toTypedArray()
            requestedPermissionsFlags = IntArray(granted.size) { PackageInfo.REQUESTED_PERMISSION_GRANTED }
        })
        if (installer != null) pm.setInstallSourceInfo(pkg, installer, installer)
        if (launcher) {
            val component = ComponentName(pkg, "$pkg.Main")
            pm.addActivityIfNotPresent(component)
            pm.addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    private fun scan(trusted: Set<String> = emptySet()) =
        AppScanner.scan(context, trusted).apps.associateBy { it.packageName }

    @Test
    fun bankingTrojanIsHighRisk() {
        // Sideloaded "BISP payment" app: reads SMS, Accessibility on, hides its icon.
        install("com.fake.bisp", listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS), installer = null, launcher = false)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.fake.bisp/.Spy")

        val app = scan().getValue("com.fake.bisp")
        assertEquals(app.findings.toString(), Risk.DANGER, app.risk)
        assertEquals(true, app.accessibilityOn)
    }

    @Test
    fun normalStoreAppsAreFine() {
        install("com.whatsapp", listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CAMERA), "com.android.vending", launcher = true)
        install("com.some.game", emptyList(), installer = null, launcher = true)

        val result = scan()
        val whatsapp = result.getValue("com.whatsapp")
        val game = result.getValue("com.some.game")
        assertEquals(whatsapp.findings.toString(), Risk.OK, whatsapp.risk)
        assertEquals(game.findings.toString(), Risk.OK, game.risk) // sideloaded alone isn't alarming
    }

    @Test
    fun smsReaderFromOutsideStoreNeedsReview() {
        install("com.random.sms", listOf(Manifest.permission.READ_SMS), installer = null, launcher = true)
        val app = scan().getValue("com.random.sms")
        assertEquals(app.findings.toString(), Risk.REVIEW, app.risk)
    }

    @Test
    fun trustedAppsAreNotFlagged() {
        install("com.fake.bisp", listOf(Manifest.permission.READ_SMS), installer = null, launcher = false)
        assertEquals(Risk.OK, scan(trusted = setOf("com.fake.bisp")).getValue("com.fake.bisp").risk)
    }
}
