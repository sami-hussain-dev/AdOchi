package com.adochi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adochi.core.AppNames
import com.adochi.core.Stats
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.AppIcon
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.formatCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PrivacyReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val stats by Stats.state.collectAsStateWithLifecycle()
    var week by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val weekUsage by produceState<List<Stats.AppUsage>?>(null, week) {
        if (week) value = withContext(Dispatchers.IO) { Stats.usageForDays(7) }
    }
    val usage = (if (week) weekUsage.orEmpty() else stats.appsToday).filter { it.blocked > 0 }
    val max = usage.maxOfOrNull { it.blocked }?.coerceAtLeast(1) ?: 1
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                ScreenHeader("Privacy report", "Which apps try to track you the most", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !week, onClick = { week = false }, label = { Text("Today") })
                FilterChip(selected = week, onClick = { week = true }, label = { Text("Last 7 days") })
            }
        }
        if (usage.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(Icons.Rounded.Insights, colors.secondary, size = 64.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("No tracking blocked yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep the shield on. Each app that tries to send ads or tracking data will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
        }
        items(usage, key = { it.app }) { app ->
            AppCard(onClick = if (week) null else ({ selected = app.app })) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.app, size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                AppNames.label(context, app.app),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(formatCount(app.blocked), style = MaterialTheme.typography.titleSmall, color = colors.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.surfaceVariant)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(app.blocked.toFloat() / max)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (app.threats > 0) colors.error else colors.primary)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            buildString {
                                append("${formatCount(app.blocked)} blocked of ${formatCount(app.total)} requests")
                                if (app.threats > 0) append(" · ${formatCount(app.threats)} dangerous")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    selected?.let { app ->
        val domains = remember(app) { Stats.blockedDomainsToday(app) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(AppNames.label(context, app)) },
            text = {
                LazyColumn {
                    items(domains) { (domain, count) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(domain, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(formatCount(count), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}
