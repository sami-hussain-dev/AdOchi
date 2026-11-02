package com.adochi.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adochi.ui.screens.AboutScreen
import com.adochi.ui.screens.ActivityScreen
import com.adochi.ui.screens.AppListMode
import com.adochi.ui.screens.AppsScreen
import com.adochi.ui.screens.BrowserScreen
import com.adochi.ui.screens.FiltersScreen
import com.adochi.ui.screens.FullscreenVideo
import com.adochi.ui.screens.GuardScreen
import com.adochi.ui.screens.HomeScreen
import com.adochi.ui.screens.LinkCheckDialog
import com.adochi.ui.screens.PrivacyReportScreen
import com.adochi.ui.screens.ScannerScreen
import com.adochi.ui.screens.SettingsScreen
import com.adochi.ui.screens.rememberBrowserState
import com.adochi.vpn.ShieldState
import com.adochi.vpn.VpnController

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Shield", Icons.Rounded.Shield),
    ACTIVITY("Activity", Icons.Rounded.Insights),
    BROWSER("Clean", Icons.Rounded.Public),
    FILTERS("Filters", Icons.Rounded.FilterList),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

/** Full screens opened on top of the tabs. */
private enum class SubScreen { EXCLUDED_APPS, OFFLINE_APPS, SCANNER, GUARD, REPORT, ABOUT }

@Composable
fun AppRoot(sharedText: String?, onSharedTextHandled: () -> Unit, viewUrl: String?, onViewUrlHandled: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sub by rememberSaveable { mutableStateOf<SubScreen?>(null) }
    val showApps = sub != null
    var linkToCheck by remember { mutableStateOf<String?>(null) }
    val browser = rememberBrowserState()
    val toggleShield = rememberShieldToggle()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            linkToCheck = sharedText
            onSharedTextHandled()
        }
    }

    LaunchedEffect(viewUrl) {
        if (viewUrl != null) {
            sub = null
            tab = Tab.BROWSER
            browser.open(viewUrl) // Known scam sites get the warning page instead.
            onViewUrlHandled()
        }
    }

    BackHandler(enabled = !showApps && tab != Tab.HOME && !(tab == Tab.BROWSER && !browser.isStartPage)) {
        tab = Tab.HOME
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!showApps && !imeVisible) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                val back = { sub = null }
                val current = sub
                if (current != null) {
                    when (current) {
                        SubScreen.EXCLUDED_APPS -> AppsScreen(AppListMode.EXCLUDED, onBack = back)
                        SubScreen.OFFLINE_APPS -> AppsScreen(AppListMode.NO_INTERNET, onBack = back)
                        SubScreen.SCANNER -> ScannerScreen(onBack = back)
                        SubScreen.GUARD -> GuardScreen(onBack = back, onCheckLink = { linkToCheck = it })
                        SubScreen.REPORT -> PrivacyReportScreen(onBack = back)
                        SubScreen.ABOUT -> AboutScreen(onBack = back, onOpenUrl = { url ->
                            sub = null
                            browser.open(url)
                            tab = Tab.BROWSER
                        })
                    }
                } else {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            Tab.HOME -> HomeScreen(
                                onToggleShield = toggleShield,
                                onOpenSite = { url ->
                                    browser.open(url)
                                    tab = Tab.BROWSER
                                },
                                onCheckLink = { linkToCheck = "" },
                                onOpenFilters = { tab = Tab.FILTERS },
                                onOpenScanner = { sub = SubScreen.SCANNER },
                                onOpenGuard = { sub = SubScreen.GUARD },
                                onOpenReport = { sub = SubScreen.REPORT },
                                onOpenAbout = { sub = SubScreen.ABOUT },
                            )
                            Tab.ACTIVITY -> ActivityScreen(onOpenReport = { sub = SubScreen.REPORT })
                            Tab.BROWSER -> BrowserScreen(browser)
                            Tab.FILTERS -> FiltersScreen()
                            Tab.SETTINGS -> SettingsScreen(
                                onOpenApps = { sub = SubScreen.EXCLUDED_APPS },
                                onOpenOfflineApps = { sub = SubScreen.OFFLINE_APPS },
                                onOpenAbout = { sub = SubScreen.ABOUT },
                            )
                        }
                    }
                }
            }
        }
        FullscreenVideo(browser)
    }

    linkToCheck?.let { initial ->
        LinkCheckDialog(
            initial = initial,
            onDismiss = { linkToCheck = null },
            onOpen = { url ->
                linkToCheck = null
                browser.open(url)
                tab = Tab.BROWSER
            },
        )
    }
}

/** Turns the shield on (asking for notification and VPN permission the first time) or off. */
@Composable
private fun rememberShieldToggle(): () -> Unit {
    val context = LocalContext.current
    val shield by VpnController.state.collectAsStateWithLifecycle()

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) VpnController.start(context)
    }

    fun startShield() {
        val consent = VpnService.prepare(context)
        if (consent != null) vpnPermission.launch(consent) else VpnController.start(context)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startShield() // Works either way; notifications only add scam alerts.
    }

    return {
        when {
            shield != ShieldState.STOPPED -> VpnController.stop(context)
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> startShield()
        }
    }
}
