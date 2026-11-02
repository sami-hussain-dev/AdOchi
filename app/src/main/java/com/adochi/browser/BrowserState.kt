package com.adochi.browser

import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adochi.core.FilterEngine
import com.adochi.core.Verdict
import java.net.URLEncoder

/** UI state of the Clean Browser. The WebView lives as long as the activity so tabs keep their page. */
class BrowserState internal constructor(val webView: WebView) {

    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blockedOnPage by mutableIntStateOf(0)

    var customView by mutableStateOf<View?>(null)
    internal var customViewCallback: WebChromeClient.CustomViewCallback? = null

    var fileChooserIntent by mutableStateOf<Intent?>(null)
    internal var fileCallback: ValueCallback<Array<Uri>>? = null

    /** Hosts the user chose to open despite the warning, for this session only. */
    internal val acceptedRisks = mutableSetOf<String>()

    val isStartPage: Boolean get() = url.isEmpty() || url == "about:blank"

    fun open(input: String) {
        val target = toUrl(input) ?: return
        url = target
        val host = Uri.parse(target).host?.lowercase()
        if (host != null && host !in acceptedRisks && FilterEngine.check(host) == Verdict.THREAT) {
            showWarning(target, host)
        } else {
            webView.loadUrl(target)
        }
    }

    fun goHome() {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        url = ""
        title = ""
        blockedOnPage = 0
    }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
    }

    internal fun showWarning(target: String, host: String) {
        webView.loadDataWithBaseURL(WarningPage.BASE_URL, WarningPage.html(target, host), "text/html", "utf-8", null)
    }

    companion object {
        fun toUrl(input: String): String? {
            val text = input.trim()
            if (text.isEmpty()) return null
            if (text.startsWith("http://", true) || text.startsWith("https://", true)) return text
            val looksLikeAddress = !text.contains(' ') && text.contains('.') && !text.endsWith('.')
            return if (looksLikeAddress) {
                "https://$text"
            } else {
                "https://duckduckgo.com/?q=" + URLEncoder.encode(text, "UTF-8")
            }
        }
    }
}
