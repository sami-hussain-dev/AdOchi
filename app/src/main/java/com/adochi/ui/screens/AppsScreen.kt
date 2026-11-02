package com.adochi.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.adochi.AdOchiApp
import com.adochi.ui.components.ScreenHeader
import com.adochi.vpn.VpnController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppInfo(val packageName: String, val label: String, val icon: ImageBitmap)

/** The same app picker serves two lists. */
enum class AppListMode(val title: String, val description: String) {
    EXCLUDED(
        "Excluded apps",
        "Apps turned on here skip AdOchi completely. Some banking apps refuse to open while any VPN is active; add them here.",
    ),
    NO_INTERNET(
        "Ad-free offline games",
        "Apps turned on here can't reach the internet, so games that work offline can't load ads or videos at all. " +
            "Online games and apps that need login will stop working; turn them off again here.",
    ),
}

@Composable
fun AppsScreen(mode: AppListMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = AdOchiApp.instance.settings
    val original = remember {
        if (mode == AppListMode.EXCLUDED) settings.bypassApps else settings.noInternetApps
    }
    var selected by remember { mutableStateOf(original) }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }
    // Re-create the tunnel once when leaving, not on every switch. The offline list is read per lookup.
    DisposableEffect(Unit) {
        onDispose { if (mode == AppListMode.EXCLUDED && settings.bypassApps != original) VpnController.reload(context) }
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            ScreenHeader(mode.title, modifier = Modifier.weight(1f))
        }
        Text(
            mode.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (mode == AppListMode.NO_INTERNET && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Needs Android 10 or newer: older versions don't tell AdOchi which app is connecting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val shown = list
                .filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
                .sortedByDescending { it.packageName in selected }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = app.packageName in selected,
                            onCheckedChange = { on ->
                                selected = if (on) selected + app.packageName else selected - app.packageName
                                if (mode == AppListMode.EXCLUDED) settings.bypassApps = selected else settings.noInternetApps = selected
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { info ->
            AppInfo(
                packageName = info.packageName,
                label = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}
