package com.adochi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adochi.AdOchiApp
import com.adochi.core.FilterCatalog
import com.adochi.core.FilterEngine
import com.adochi.core.FilterList
import com.adochi.core.ListKind
import com.adochi.core.ListParser
import com.adochi.core.UpdateStatus
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.SectionTitle
import com.adochi.ui.formatAgo
import com.adochi.ui.formatCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen() {
    val app = AdOchiApp.instance
    val settings = app.settings
    val tick by settings.changes.collectAsStateWithLifecycle()
    val status by app.filters.status.collectAsStateWithLifecycle()
    val totals by FilterEngine.totals.collectAsStateWithLifecycle()
    val enabled = remember(tick) { settings.enabledLists }
    val lastUpdate = remember(tick) { settings.lastUpdate }
    var showAllowed by rememberSaveable { mutableStateOf(false) }
    val rules = remember(tick, showAllowed) { (if (showAllowed) settings.userAllow else settings.userBlock).sorted() }
    val busy = status is UpdateStatus.Working

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("Filters", "Updated ${formatAgo(lastUpdate)} · free lists from GitHub") {
                FilledTonalButton(onClick = { app.filters.updateAsync() }, enabled = !busy) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Update")
                }
            }
        }
        item {
            AppCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(formatCount(totals.ads), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Text("ad & tracker domains", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(formatCount(totals.threats), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                        Text("scam & malware domains", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when (val s = status) {
                    is UpdateStatus.Working -> {
                        Spacer(Modifier.height(14.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateStatus.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    UpdateStatus.Idle -> Unit
                }
            }
        }

        item { SectionTitle("Ads & trackers") }
        items(FilterCatalog.all.filter { it.kind == ListKind.ADS }) { list ->
            ListCard(list, list.id in enabled, settings.listCount(list.id), enabledToggle = !busy) { on ->
                settings.enabledLists = if (on) enabled + list.id else enabled - list.id
                app.filters.rebuildAsync()
            }
        }

        item { SectionTitle("Scams, phishing & malware") }
        items(FilterCatalog.all.filter { it.kind == ListKind.THREATS }) { list ->
            ListCard(list, list.id in enabled, settings.listCount(list.id), enabledToggle = !busy) { on ->
                settings.enabledLists = if (on) enabled + list.id else enabled - list.id
                app.filters.rebuildAsync()
            }
        }

        item { SectionTitle("My rules") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showAllowed, onClick = { showAllowed = false }, label = { Text("Always block") })
                FilterChip(selected = showAllowed, onClick = { showAllowed = true }, label = { Text("Always allow") })
            }
        }
        item {
            RuleInput(showAllowed) { domain ->
                if (showAllowed) app.filters.allowDomain(domain) else app.filters.blockDomain(domain)
            }
        }
        if (rules.isEmpty()) {
            item {
                Text(
                    if (showAllowed) "Allow a site here if AdOchi blocks something you need." else "Add any website or app server you want blocked everywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        items(rules) { domain ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(domain, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { app.filters.removeRule(domain) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ListCard(list: FilterList, on: Boolean, count: Int, enabledToggle: Boolean, onToggle: (Boolean) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(list.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (on && count > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatCount(count)} domains",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (list.kind == ListKind.THREATS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = onToggle, enabled = enabledToggle)
        }
    }
}

@Composable
private fun RuleInput(allow: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        val domain = ListParser.normalize(text.substringAfter("://").substringBefore('/'))
        if (domain == null) {
            error = true
        } else {
            onAdd(domain)
            text = ""
            error = false
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            error = false
        },
        placeholder = { Text(if (allow) "e.g. example.com" else "e.g. annoying-ads.com") },
        isError = error,
        supportingText = if (error) {
            { Text("Enter a domain like example.com") }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }) { Icon(Icons.Rounded.Add, contentDescription = "Add") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
