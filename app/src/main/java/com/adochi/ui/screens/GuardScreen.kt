package com.adochi.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.adochi.AdOchiApp
import com.adochi.BuildConfig
import com.adochi.core.MessageGuard
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.components.StatusPill
import com.adochi.ui.formatAgo
import com.adochi.ui.theme.warning

@Composable
fun GuardScreen(onBack: () -> Unit, onCheckLink: (String) -> Unit) {
    val context = LocalContext.current
    val settings = AdOchiApp.instance.settings
    val tick by settings.changes.collectAsStateWithLifecycle()
    val enabled = remember(tick) { settings.messageGuard }
    val catches by MessageGuard.catches.collectAsStateWithLifecycle()
    var hasAccess by remember { mutableStateOf(MessageGuard.hasAccess(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val colors = MaterialTheme.colorScheme

    // Re-check after the user comes back from the system "Notification access" page.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { hasAccess = MessageGuard.hasAccess(context) }
    }
    BackHandler(onBack = onBack)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                ScreenHeader("Message Guard", "Warns you about scam links in WhatsApp, SMS and other chats", Modifier.weight(1f))
            }
        }
        if (!BuildConfig.MESSAGE_GUARD) {
            item { ShareToCheckCard(onCheck = { onCheckLink("") }) }
        } else {
            item {
                AppCard(border = if (hasAccess && enabled) colors.primary.copy(alpha = 0.5f) else colors.warning.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.MarkChatRead, if (hasAccess && enabled) colors.primary else colors.warning)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    !hasAccess -> "Needs one permission"
                                    enabled -> "Watching for scam links"
                                    else -> "Paused"
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (hasAccess) "Checks links the moment a message arrives." else "Allow AdOchi in \"Notification access\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (hasAccess) Switch(checked = enabled, onCheckedChange = { settings.messageGuard = it })
                    }
                    if (!hasAccess) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Allow notification access") }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Your messages never leave the phone and are not saved. AdOchi only looks for links and checks them offline.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Scam links caught", Modifier.weight(1f))
                    if (catches.isNotEmpty()) {
                        IconButton(onClick = { MessageGuard.clear() }) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear") }
                    }
                }
            }
            if (catches.isEmpty()) {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Sms, colors.secondary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "No scam links yet. When one arrives you'll get an alert right away, and it will be listed here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            items(catches) { catch ->
                val tint = if (catch.danger) colors.error else colors.warning
                AppCard(onClick = { onCheckLink(catch.url) }, border = tint.copy(alpha = 0.35f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(catch.host, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(catch.appLabel, catch.sender, formatAgo(catch.time)).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        StatusPill(if (catch.danger) "SCAM" else "SUSPICIOUS", tint)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(catch.reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * The direct-download build has no notification access (Play Protect blocks sideloaded apps that ask
 * for it), so messages are checked by sharing them to AdOchi instead.
 */
@Composable
private fun ShareToCheckCard(onCheck: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    AppCard(border = colors.primary.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.MarkChatRead, colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Check any message in 2 taps", style = MaterialTheme.typography.titleSmall)
                Text(
                    "In WhatsApp or SMS, long-press the message, tap Share, then pick AdOchi. " +
                        "Or copy it and tap the button below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onCheck) { Text("Paste & check a message") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing leaves your phone. Links are checked offline against scam and phishing lists.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}
