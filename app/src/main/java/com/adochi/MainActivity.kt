package com.adochi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.adochi.ui.AppRoot
import com.adochi.ui.theme.AdOchiTheme

class MainActivity : ComponentActivity() {

    /** A link shared to AdOchi or tapped in a scam alert; opens the link checker. */
    private val sharedText = mutableStateOf<String?>(null)

    /** A web link opened with AdOchi as the browser; opens in the Clean Browser. */
    private val viewUrl = mutableStateOf<String?>(null)

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
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLink(intent)
    }

    private fun readLink(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND, ACTION_CHECK_LINK -> sharedText.value = intent.getStringExtra(Intent.EXTRA_TEXT)
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
