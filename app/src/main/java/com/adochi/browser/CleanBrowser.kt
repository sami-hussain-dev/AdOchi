package com.adochi.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.adochi.core.FilterEngine
import com.adochi.core.Stats
import com.adochi.core.Verdict
import java.io.ByteArrayInputStream

/**
 * Sets up a WebView that blocks ad/tracker requests with the same lists as the shield and injects
 * a script that removes sponsored posts and video ads on Facebook, Instagram, YouTube, TikTok and X.
 */
object CleanBrowser {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, state: BrowserState) {
        val script = webView.context.assets.open("cleaner.js").bufferedReader().use { it.readText() }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false // no pop-up windows
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            safeBrowsingEnabled = true
            // Without the WebView marker sites serve their normal mobile pages instead of "open in app" prompts.
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

        webView.addJavascriptInterface(JsBridge(state), "AdOchiBridge")
        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
        }

        webView.webViewClient = CleanWebViewClient(state, if (documentStart) null else script)
        webView.webChromeClient = CleanChromeClient(state)
    }
}

/** Methods the page script may call. It can only increase counters. */
class JsBridge(private val state: BrowserState) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onAdHidden() {
        Stats.recordBrowserBlocked()
        main.post { state.blockedOnPage++ }
    }
}

private class CleanWebViewClient(
    private val state: BrowserState,
    private val fallbackScript: String?,
) : WebViewClient() {

    private val main = Handler(Looper.getMainLooper())

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame) return null
        val uri = request.url
        val host = uri.host?.lowercase() ?: return null
        if (FilterEngine.check(host) == Verdict.ALLOWED && !BrowserRules.isTrackingUrl(host, uri)) return null
        Stats.recordBrowserBlocked()
        main.post { state.blockedOnPage++ }
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val host = uri.host?.lowercase() ?: return false
                if (request.isForMainFrame && host !in state.acceptedRisks && FilterEngine.check(host) == Verdict.THREAT) {
                    state.showWarning(uri.toString(), host)
                    return true
                }
                return false
            }
            WarningPage.SCHEME -> {
                handleWarningAction(view, uri)
                return true
            }
            "tel", "mailto", "sms", "geo" -> {
                try {
                    view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: ActivityNotFoundException) {
                    // Nothing can open it.
                }
                return true
            }
            // fb://, intent:// and similar would hand the user back to the ad-filled apps.
            else -> return true
        }
    }

    private fun handleWarningAction(view: WebView, uri: Uri) {
        // Only our own warning page may use these links, so a site can't skip the warning for itself.
        if (view.url?.startsWith(WarningPage.BASE_URL) != true) return
        when (uri.host) {
            "back" -> if (view.canGoBack()) view.goBack() else state.goHome()
            "proceed" -> {
                val target = uri.getQueryParameter("url") ?: return
                Uri.parse(target).host?.lowercase()?.let { state.acceptedRisks += it }
                view.loadUrl(target)
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (url != null && !url.startsWith(WarningPage.BASE_URL)) state.url = url
        state.blockedOnPage = 0
        fallbackScript?.let { view.evaluateJavascript(it, null) }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        fallbackScript?.let { view.evaluateJavascript(it, null) }
        state.canGoBack = view.canGoBack()
        state.canGoForward = view.canGoForward()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (url != null && !url.startsWith(WarningPage.BASE_URL) && url != "about:blank") state.url = url
        state.canGoBack = view.canGoBack()
        state.canGoForward = view.canGoForward()
    }
}

private class CleanChromeClient(private val state: BrowserState) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        state.progress = newProgress
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        state.title = title.orEmpty()
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (state.customView != null) {
            callback.onCustomViewHidden()
            return
        }
        state.customView = view
        state.customViewCallback = callback
    }

    override fun onHideCustomView() {
        state.customView = null
        state.customViewCallback = null
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        state.fileCallback?.onReceiveValue(null)
        state.fileCallback = filePathCallback
        state.fileChooserIntent = fileChooserParams.createIntent()
        return true
    }
}

/** Ad and tracking calls that share a host with real content, so they're matched by path. */
object BrowserRules {
    fun isTrackingUrl(host: String, uri: Uri): Boolean {
        val path = uri.path ?: return false
        return when {
            host.endsWith("facebook.com") -> path == "/tr" || path.startsWith("/tr/")
            host == "connect.facebook.net" -> path.endsWith("/fbevents.js")
            host.endsWith("youtube.com") ->
                path.startsWith("/api/stats/ads") || path.startsWith("/pagead/") ||
                    path == "/ptracking" || path.startsWith("/get_midroll_")
            host.endsWith("google.com") -> path.startsWith("/pagead/")
            else -> false
        }
    }
}
