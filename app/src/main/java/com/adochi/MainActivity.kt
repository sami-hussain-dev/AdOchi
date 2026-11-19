package com.adochi

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.adochi.browser.BackgroundPlayback
import com.adochi.ui.AppRoot
import com.adochi.ui.theme.AdOchiTheme

class MainActivity : ComponentActivity() {

    /** A link shared to AdOchi or tapped in a scam alert; opens the link checker. */
    private val sharedText = mutableStateOf<String?>(null)

    /** A web link opened with AdOchi as the browser; opens in the Clean Browser. */
    private val viewUrl = mutableStateOf<String?>(null)

    /** Picture-in-picture: only the video is drawn. */
    private val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readLink(intent)
        setContent {
            AdOchiTheme {
                AppRoot(
                    sharedText = sharedText.value,
                    onSharedTextHandled = { sharedText.value = null },
                    viewUrl = viewUrl.value,
                    onViewUrlHandled = { viewUrl.value = null },
                    inPip = inPip.value,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        BackgroundPlayback.activityVisible = true
    }

    override fun onStop() {
        BackgroundPlayback.activityVisible = false
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) BackgroundPlayback.stop(this)
        super.onDestroy()
    }

    /** Leaving AdOchi (Home, recent apps) while a Clean Browser video plays: keep watching in a floating window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!AdOchiApp.instance.settings.pictureInPicture || !BackgroundPlayback.playing) return
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        } catch (e: IllegalStateException) {
            // the system refused (e.g. picture-in-picture switched off for AdOchi in system settings)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        BackgroundPlayback.pipLayout(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLink(intent)
    }

    private fun readLink(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND, ACTION_CHECK_LINK -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (intent.component?.className?.endsWith(".PlayAdFree") == true) {
                    // "Play ad-free in AdOchi" from the Share menu: open the link instead of checking it
                    Regex("https?://\\S+").find(text.orEmpty())?.value?.let { viewUrl.value = it }
                } else {
                    sharedText.value = text
                }
            }
            Intent.ACTION_VIEW -> {
                val url = intent.dataString
                if (url != null && (url.startsWith("http://", true) || url.startsWith("https://", true))) viewUrl.value = url
            }
        }
    }

    companion object {
        const val ACTION_CHECK_LINK = "com.adochi.action.CHECK_LINK"
    }
}
