package com.adochi.ui.screens

import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adochi.AdOchiApp
import com.adochi.BuildConfig
import com.adochi.Credits
import com.adochi.core.DnsProviders
import com.adochi.core.PrivateDns
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.components.SettingRow
import com.adochi.vpn.QuickTile
import com.adochi.vpn.ShieldState
import com.adochi.vpn.VpnController

@Composable
fun SettingsScreen(onOpenApps: () -> Unit, onOpenOfflineApps: () -> Unit, onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val settings = AdOchiApp.instance.settings
    val tick by settings.changes.collectAsStateWithLifecycle()
    val shield by VpnController.state.collectAsStateWithLifecycle()
    val provider = remember(tick) { settings.dnsProvider }
    val encrypted = remember(tick) { settings.encryptedDns }
    val boot = remember(tick) { settings.startOnBoot }
    val alerts = remember(tick) { settings.threatAlerts }
    val statusNotification = remember(tick) { settings.statusNotification }
    val bypassCount = remember(tick) { settings.bypassApps.size }
    val offlineCount = remember(tick) { settings.noInternetApps.size }
    var showLightMode by remember { mutableStateOf(false) }
    var browserTick by remember { mutableIntStateOf(0) }
    val isDefaultBrowser = remember(browserTick) { isDefaultBrowser(context) }
    val browserRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { browserTick++ }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Settings", "Everything is stored only on this phone.")

        SectionTitle("Block more ads")
        AppCard {
            SettingRow(
                icon = Icons.Rounded.OpenInBrowser,
                title = if (isDefaultBrowser) "AdOchi is your browser" else "Open all links in AdOchi",
                subtitle = "Links you tap in WhatsApp, SMS, Facebook and other apps open ad-free, and scam links are stopped first.",
                onClick = {
                    val request = browserRoleRequest(context)
                    if (request != null) browserRole.launch(request) else openSystemSettings(context, android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                },
            ) {
                if (isDefaultBrowser) {
                    Text("ON", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                } else {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            SettingRow(
                icon = Icons.Rounded.SportsEsports,
                title = "Ad-free offline games",
                subtitle = if (offlineCount == 0) {
                    "Cut the internet for games that work offline, so they can't show any ads."
                } else {
                    "$offlineCount app(s) have no internet"
                },
                tint = colors.tertiary,
                onClick = onOpenOfflineApps,
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
        }

        SectionTitle("Look & feel")
        AppCard {
            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = "Status bar notification",
                subtitle = if (statusNotification) {
                    "Shows \"AdOchi is protecting you\" with today's count."
                } else {
                    "Hidden. Android still shows its small key icon for any app that filters like this; no app can remove it."
                },
                onClick = { setStatusNotification(context, !statusNotification, shield) },
            ) { Switch(checked = statusNotification, onCheckedChange = { setStatusNotification(context, it, shield) }) }
            SettingRow(
                icon = Icons.Rounded.ToggleOn,
                title = "Quick Settings button",
                subtitle = if (QuickTile.canRequest) {
                    "Turn AdOchi on or off from the pull-down panel."
                } else {
                    "Swipe down twice, tap the pencil (Edit), then drag \"AdOchi Shield\" up."
                },
                tint = colors.secondary,
                onClick = {
                    if (QuickTile.canRequest) {
                        QuickTile.request(context) { added ->
                            if (added) Toast.makeText(context, "AdOchi is in Quick Settings", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Swipe down twice, tap Edit, then drag AdOchi Shield up.", Toast.LENGTH_LONG).show()
                    }
                },
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SettingRow(
                    icon = Icons.Rounded.Eco,
                    title = "Light mode (no VPN key icon)",
                    subtitle = "Block ads with Android's Private DNS instead. Lighter, but without AdOchi's scam alerts and app report.",
                    tint = colors.tertiary,
                    onClick = { showLightMode = true },
                ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
            }
        }

        SectionTitle("Secure DNS")
        AppCard {
            SettingRow(
                icon = Icons.Rounded.Lock,
                title = "Encrypted DNS",
                subtitle = "Hides the sites you visit from your mobile or Wi-Fi provider (DNS-over-HTTPS).",
                onClick = { settings.encryptedDns = !encrypted },
            ) { Switch(checked = encrypted, onCheckedChange = { settings.encryptedDns = it }) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
            Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dns, contentDescription = null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("Resolver for allowed sites", style = MaterialTheme.typography.titleSmall)
            }
            DnsProviders.all.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { settings.dnsProvider = option.id }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = provider == option.id, onClick = { settings.dnsProvider = option.id })
                    Column(Modifier.weight(1f)) {
                        Text(option.name, style = MaterialTheme.typography.bodyLarge)
                        Text(option.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }

        SectionTitle("Protection")
        AppCard {
            SettingRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Dangerous site alerts",
                subtitle = "Notify me when a scam or phishing site is stopped.",
                tint = colors.error,
                onClick = { settings.threatAlerts = !alerts },
            ) { Switch(checked = alerts, onCheckedChange = { settings.threatAlerts = it }) }
            SettingRow(
                icon = Icons.Rounded.RestartAlt,
                title = "Start after reboot",
                subtitle = "Turn the shield back on when the phone restarts.",
                onClick = { settings.startOnBoot = !boot },
            ) { Switch(checked = boot, onCheckedChange = { settings.startOnBoot = it }) }
            SettingRow(
                icon = Icons.Rounded.Apps,
                title = "Excluded apps",
                subtitle = if (bypassCount == 0) "None. Add an app here if it stops working." else "$bypassCount app(s) bypass the shield",
                tint = colors.secondary,
                onClick = onOpenApps,
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
            SettingRow(
                icon = Icons.Rounded.VpnKey,
                title = "Always-on protection",
                subtitle = "Set AdOchi as Always-on VPN so Android keeps it running. Leave \"Block connections without VPN\" off.",
                tint = colors.tertiary,
                onClick = { openSystemSettings(context, android.provider.Settings.ACTION_VPN_SETTINGS) },
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
        }

        SectionTitle("About")
        AppCard {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "AdOchi ${BuildConfig.VERSION_NAME} by ${Credits.COMPANY}",
                subtitle = "Made by ${Credits.FOUNDER} at ${Credits.WEBSITE_LABEL}. Free forever: no account, no servers, no ads.",
                onClick = onOpenAbout,
            ) { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLightMode) LightModeDialog(onDismiss = { showLightMode = false })
}

/** The service picks the change up on its next start command; nothing restarts. */
private fun setStatusNotification(context: Context, on: Boolean, shield: ShieldState) {
    AdOchiApp.instance.settings.statusNotification = on
    if (shield != ShieldState.STOPPED) VpnController.start(context)
}

@Composable
private fun LightModeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(PrivateDns.SERVERS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Light mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Android's Private DNS sends lookups to a free ad-blocking server. There's no VPN key icon and it uses no battery, " +
                        "but AdOchi's own lists, scam alerts, app report and offline-games mode don't work in this mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrivateDns.SERVERS.forEach { server ->
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = server },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen == server, onClick = { chosen = server })
                        Column(Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.bodyLarge)
                            Text(server.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    "Next: open \"Private DNS\" (under Network or Connections > More), choose \"Private DNS provider hostname\" " +
                        "and paste. To go back, set it to Automatic and turn the shield on.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("Private DNS", chosen.host))
                VpnController.stop(context) // Both at once would only fight each other.
                Toast.makeText(context, "Copied ${chosen.host}. Paste it in Private DNS.", Toast.LENGTH_LONG).show()
                openSystemSettings(context, android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                onDismiss()
            }) { Text("Copy & open settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun isDefaultBrowser(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_BROWSER)
    } else {
        val probe = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))
        context.packageManager.resolveActivity(probe, 0)?.activityInfo?.packageName == context.packageName
    }
} catch (e: Exception) {
    false
}

/** The system "Set as default browser?" dialog, or null where it doesn't exist. */
private fun browserRoleRequest(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val roles = context.getSystemService(RoleManager::class.java) ?: return null
    if (!roles.isRoleAvailable(RoleManager.ROLE_BROWSER) || roles.isRoleHeld(RoleManager.ROLE_BROWSER)) return null
    return roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
}

private fun openSystemSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
