package com.adochi.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adochi.browser.BrowserState
import com.adochi.browser.CleanBrowser
import com.adochi.ui.components.AppCard
import com.adochi.ui.components.IconBadge
import com.adochi.ui.components.ScreenHeader
import com.adochi.ui.components.SiteBadge
import com.adochi.ui.components.SocialSite
import com.adochi.ui.components.StatusPill
import com.adochi.ui.formatCount

@Composable
fun rememberBrowserState(): BrowserState {
    val context = LocalContext.current
    val state = remember {
        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        BrowserState(webView).also { CleanBrowser.configure(webView, it) }
    }
    DisposableEffect(Unit) {
        onDispose { state.webView.destroy() }
    }
    return state
}

@Composable
fun BrowserScreen(state: BrowserState) {
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris: Array<Uri>? = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        state.fileCallback?.onReceiveValue(if (result.resultCode == Activity.RESULT_OK) uris else null)
        state.fileCallback = null
    }
    LaunchedEffect(state.fileChooserIntent) {
        val intent = state.fileChooserIntent ?: return@LaunchedEffect
        state.fileChooserIntent = null
        try {
            fileLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            state.fileCallback?.onReceiveValue(null)
            state.fileCallback = null
        }
    }

    BackHandler(enabled = state.canGoBack && !state.isStartPage) { state.webView.goBack() }
    BackHandler(enabled = !state.canGoBack && !state.isStartPage) { state.goHome() }

    Column(Modifier.fillMaxSize()) {
        AddressBar(state)
        if (state.isStartPage) {
            StartPage(onOpen = state::open)
        } else {
            if (state.progress < 100) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                )
            }
            AndroidView(
                factory = {
                    (state.webView.parent as? ViewGroup)?.removeView(state.webView)
                    state.webView
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            NavigationRow(state)
        }
    }
}

@Composable
private fun AddressBar(state: BrowserState) {
    val focusManager = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val shown = if (editing) text else displayUrl(state.url)

    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = shown,
            onValueChange = { text = it },
            placeholder = { Text("Search or type a website") },
            leadingIcon = {
                Icon(
                    if (state.url.startsWith("https://") && !editing) Icons.Rounded.Lock else Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            trailingIcon = if (editing && text.isNotEmpty()) {
                { IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") } }
            } else {
                null
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                state.open(text)
                focusManager.clearFocus()
            }),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused && !editing) text = if (state.isStartPage) "" else state.url
                    editing = focus.isFocused
                },
        )
        if (!state.isStartPage) {
            Spacer(Modifier.width(6.dp))
            StatusPill("${formatCount(state.blockedOnPage)} blocked", MaterialTheme.colorScheme.primary)
            IconButton(onClick = { state.webView.reload() }) { Icon(Icons.Rounded.Refresh, contentDescription = "Reload") }
        }
    }
}

@Composable
private fun NavigationRow(state: BrowserState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = { state.webView.goBack() }, enabled = state.canGoBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = { state.webView.goForward() }, enabled = state.canGoForward) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward")
        }
        IconButton(onClick = { state.goHome() }) {
            Icon(Icons.Rounded.Home, contentDescription = "Start page")
        }
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        ScreenHeader("Clean Browser", "Social media without sponsored posts, video ads or trackers.")
        SocialSite.entries.forEach { site ->
            AppCard(onClick = { onOpen(site.url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SiteBadge(site, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(site.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (site) {
                                SocialSite.YOUTUBE -> "Video ads skipped, promoted videos hidden"
                                SocialSite.FACEBOOK -> "Sponsored posts removed, video ad breaks skipped"
                                SocialSite.INSTAGRAM -> "Sponsored posts removed from your feed"
                                else -> "Promoted posts and trackers removed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        AppCard {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Rounded.Shield, MaterialTheme.colorScheme.primary, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Sign in normally: your login stays on this phone. Pages are filtered with the same lists as the shield, " +
                        "and known scam sites show a warning instead of opening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        AppCard {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(Icons.Rounded.Warning, MaterialTheme.colorScheme.tertiary, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Why not inside the Facebook or YouTube apps? Those apps load ads from the same servers as your feed and videos, " +
                        "so no blocker can remove them without breaking the app. Use them here for a fully ad-free feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Full-screen video from the browser (YouTube fullscreen button). */
@Composable
fun FullscreenVideo(state: BrowserState) {
    val view = state.customView ?: return
    BackHandler { state.exitFullscreen() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                android.widget.FrameLayout(ctx).apply {
                    (view.parent as? ViewGroup)?.removeView(view)
                    addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun displayUrl(url: String): String {
    if (url.isEmpty() || url == "about:blank") return ""
    return url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}
