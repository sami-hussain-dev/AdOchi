package com.adochi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.ui.platform.LocalContext
import com.adochi.AdOchiApp
import com.adochi.core.AppNames
import com.adochi.core.Stats
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.AppIcon
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.StatusPill
import com.adochi.ui.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) { ALL("All"), BLOCKED("Blocked"), THREATS("Scams"), ALLOWED("Allowed") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onOpenReport: () -> Unit) {
    val stats by Stats.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<Stats.Entry?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val entries = remember(stats.recent, filter, query) {
        stats.recent.filter { entry ->
            val kindOk = when (filter) {
                LogFilter.ALL -> true
                LogFilter.BLOCKED -> entry.kind == Stats.Kind.BLOCKED
                LogFilter.THREATS -> entry.kind == Stats.Kind.THREAT
                LogFilter.ALLOWED -> entry.kind == Stats.Kind.ALLOWED
            }
            kindOk && (query.isBlank() || entry.domain.contains(query.trim(), ignoreCase = true) ||
                entry.app?.contains(query.trim(), ignoreCase = true) == true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                title = "Activity",
                subtitle = "${formatCount(stats.queriesToday)} lookups today · ${formatCount(stats.blockedToday)} blocked",
            ) {
                IconButton(onClick = onOpenReport) {
                    Icon(Icons.Rounded.Leaderboard, contentDescription = "Privacy report")
                }
                IconButton(onClick = { Stats.clearLog() }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear log")
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search domains") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (filter == LogFilter.ALL && query.isBlank() && stats.topBlocked.isNotEmpty()) {
            item {
                AppCard {
                    Text("Most blocked", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    stats.topBlocked.forEach { (domain, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                domain,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatCount(count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(Icons.Rounded.Insights, MaterialTheme.colorScheme.secondary, size = 64.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn the shield on and use your phone. Every ad, tracker and scam site it stops shows up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
        }
        items(entries) { entry ->
            LogRow(entry, timeFormat.format(Date(entry.time))) { selected = entry }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    selected?.let { entry ->
        val filters = AdOchiApp.instance.filters
        val blocked = entry.kind != Stats.Kind.ALLOWED
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entry.domain, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Text(
                    when (entry.kind) {
                        Stats.Kind.THREAT -> "This domain is on a phishing, scam or malware list. Only allow it if you are completely sure it is safe."
                        Stats.Kind.BLOCKED -> "This is an ad or tracking domain. Allow it only if an app or site stops working."
                        Stats.Kind.ALLOWED -> "Block this domain on every app and browser on this phone?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (blocked) filters.allowDomain(entry.domain) else filters.blockDomain(entry.domain)
                    selected = null
                }) { Text(if (blocked) "Always allow" else "Always block") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LogRow(entry: Stats.Entry, time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (entry.kind) {
        Stats.Kind.THREAT -> "SCAM" to colors.error
        Stats.Kind.BLOCKED -> "BLOCKED" to colors.primary
        Stats.Kind.ALLOWED -> "ALLOWED" to colors.onSurfaceVariant
    }
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.app != null) {
            AppIcon(entry.app, size = 32.dp)
        } else {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (entry.kind == Stats.Kind.ALLOWED) Color.Gray.copy(alpha = 0.4f) else color))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.domain, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(entry.app?.let { AppNames.label(context, it) }, time).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(label, color)
    }
}
