package com.adochi.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.adochi.AdOchiApp
import com.adochi.core.AppScanner
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.AppIcon
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.components.StatusPill
import com.adochi.ui.theme.warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<AppScanner.Result?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scans by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun rescan() {
        scope.launch {
            scanning = true
            result = withContext(Dispatchers.Default) { AppScanner.scan(context) }
            scanning = false
            scans++
        }
    }
    // Scan on open and again when returning from an uninstall or settings screen.
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { rescan() } }
    BackHandler(onBack = onBack)

    val current = result
    val flagged = current?.apps?.filter { it.risk != AppScanner.Risk.OK }.orEmpty()
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                ScreenHeader("Phone scan", "Finds apps that can steal OTPs, passwords and bank details", Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScanBadge(scanning, danger = flagged.any { it.risk == AppScanner.Risk.DANGER }, empty = current != null && flagged.isEmpty())
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                scanning && current == null -> "Scanning your apps…"
                                current == null -> "Ready to scan"
                                flagged.isEmpty() -> "No risky apps found"
                                else -> "${flagged.size} app(s) need your attention"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        current?.let {
                            Text(
                                "${it.checked} installed apps checked",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = ::rescan, enabled = !scanning) { Text(if (scans == 0) "Scan" else "Scan again") }
            }
        }

        if (flagged.isNotEmpty()) item { SectionTitle("Needs attention") }
        items(flagged, key = { it.packageName }) { app ->
            RiskCard(app, onTrust = {
                val settings = AdOchiApp.instance.settings
                settings.trustedApps = settings.trustedApps + app.packageName
                rescan()
            })
        }

        item {
            SectionTitle("How phones get robbed")
            AppCard {
                Text(
                    "Fake apps (\"BISP payment\", \"loan\", \"bank update\", modded games) ask for SMS and Accessibility access. " +
                        "With that they read your OTP codes and press buttons in your banking app while you sleep.\n\n" +
                        "Only install apps from the Play Store, and never give Accessibility to an app you don't fully trust.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScanBadge(scanning: Boolean, danger: Boolean, empty: Boolean) {
    val colors = MaterialTheme.colorScheme
    if (scanning) {
        val rotation by rememberInfiniteTransition(label = "scan").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
            label = "rotation",
        )
        Box(Modifier.rotate(rotation)) { IconBadge(Icons.Rounded.Radar, colors.secondary, size = 56.dp) }
    } else {
        when {
            danger -> IconBadge(Icons.Rounded.GppBad, colors.error, size = 56.dp)
            empty -> IconBadge(Icons.Rounded.GppGood, colors.primary, size = 56.dp)
            else -> IconBadge(Icons.Rounded.Radar, colors.warning, size = 56.dp)
        }
    }
}

@Composable
private fun RiskCard(app: AppScanner.AppRisk, onTrust: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val danger = app.risk == AppScanner.Risk.DANGER
    val tint = if (danger) colors.error else colors.warning

    AppCard(border = tint.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            StatusPill(if (danger) "HIGH RISK" else "REVIEW", tint)
        }
        Spacer(Modifier.height(10.dp))
        app.findings.forEach { finding ->
            Text("• ${finding.text}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { uninstall(context, app) },
                colors = ButtonDefaults.buttonColors(containerColor = tint, contentColor = colors.surface),
            ) { Text("Uninstall") }
            OutlinedButton(onClick = { openAppInfo(context, app.packageName) }) { Text("App info") }
        }
        TextButton(onClick = onTrust) { Text("I trust this app") }
    }
}

private fun uninstall(context: Context, app: AppScanner.AppRisk) {
    // Device admins must be deactivated before Android allows uninstalling them.
    val intent = if (app.deviceAdmin) {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    } else {
        @Suppress("DEPRECATION")
        Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        openAppInfo(context, app.packageName)
    }
}

private fun openAppInfo(context: Context, pkg: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
