package com.adochi.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.adochi.AdOchiApp
import com.adochi.BuildConfig
import com.adochi.browser.HomeShortcuts
import com.adochi.skipper.AdSkipperService
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.IconBadge

/** Use YouTube / Facebook in the Clean Browser like apps: home-screen icons and the Share shortcut. */
@Composable
fun UseLikeAppsCard() {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    AppCard(border = colors.primary.copy(alpha = 0.4f)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(Icons.Rounded.AddToHomeScreen, colors.primary, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Use them like apps, with no ads", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Put ad-free YouTube and Facebook on your home screen. Got a video link in WhatsApp or the YouTube app? " +
                        "Tap Share, then \"Play ad-free in AdOchi\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (HomeShortcuts.canPin(context)) {
            Spacer(Modifier.height(10.dp))
            Row {
                HomeShortcuts.Site.entries.forEachIndexed { i, site ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        if (!HomeShortcuts.pin(context, site)) {
                            Toast.makeText(context, "Your launcher did not allow adding the icon.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(site.label.substringBefore(' ') + " icon") }
                }
            }
        }
    }
}

/**
 * Ad Skipper status and setup (full build only). On Android 13+ a sideloaded app's Accessibility
 * switch is greyed out until the user allows "restricted settings" in App info, so that is explained.
 */
@Composable
fun AdSkipperCard() {
    if (!BuildConfig.AD_SKIPPER) return
    val context = LocalContext.current
    val settings = AdOchiApp.instance.settings
    val tick by settings.changes.collectAsStateWithLifecycle()
    val wanted = remember(tick) { settings.adSkipper }
    var enabled by remember { mutableStateOf(AdSkipperService.isEnabled(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme

    // re-check after the user comes back from the Accessibility settings page
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { enabled = AdSkipperService.isEnabled(context) }
    }

    val active = enabled && wanted
    AppCard(border = if (active) colors.primary.copy(alpha = 0.5f) else colors.tertiary.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.FastForward, if (active) colors.primary else colors.tertiary, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !enabled -> "Ad Skipper for the YouTube & Facebook apps"
                        wanted -> "Ad Skipper is on"
                        else -> "Ad Skipper is paused"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Inside the real apps it taps \"Skip ad\" the moment it appears and mutes ads that can't be skipped. " +
                        "An ad may still show for a second or two.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (enabled) Switch(checked = wanted, onCheckedChange = { settings.adSkipper = it })
        }
        if (!enabled) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Turn on in Accessibility") }
            Spacer(Modifier.height(8.dp))
            Text(
                "Find AdOchi Ad Skipper under Installed apps / Downloaded apps and switch it on.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Switch greyed out? Android protects apps installed outside the Play Store. Open App info, tap ⋮ (top right), " +
                        "choose \"Allow restricted settings\", then come back and switch it on.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Open App info") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "AdOchi only looks at the YouTube and Facebook apps, saves nothing and sends nothing.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
