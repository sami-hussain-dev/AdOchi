package com.adochi

import android.app.Application
import android.net.Uri
import com.adochi.browser.BrowserRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BrowserRulesTest {

    private fun tracking(url: String): Boolean {
        val uri = Uri.parse(url)
        return BrowserRules.isTrackingUrl(uri.host!!, uri)
    }

    @Test
    fun chatGptAdCallsAreBlocked() {
        assertTrue(tracking("https://chatgpt.com/backend-api/bazaar/v2/ads"))
        assertTrue(tracking("https://chatgpt.com/backend-api/conversation/abc/message_supplements"))
        // The chat itself keeps working.
        assertFalse(tracking("https://chatgpt.com/backend-api/conversation"))
        assertFalse(tracking("https://chatgpt.com/backend-api/me"))
        assertFalse(tracking("https://chatgpt.com/c/ads-and-marketing-ideas"))
    }

    @Test
    fun youTubePagesKeepTheirOwnAdChecks() {
        // Blocking these makes YouTube hold the video on a black screen; the page script skips the ads.
        for (host in listOf("googleads.g.doubleclick.net", "static.doubleclick.net", "www.youtube.com", "www.google.com", "tpc.googlesyndication.com")) {
            assertTrue(host, BrowserRules.isYouTubeAdCheck("m.youtube.com", host))
            assertTrue(host, BrowserRules.isYouTubeAdCheck("www.youtube.com", host))
        }
    }

    @Test
    fun otherPagesAreStillFiltered() {
        assertFalse(BrowserRules.isYouTubeAdCheck("m.facebook.com", "googleads.g.doubleclick.net"))
        assertFalse(BrowserRules.isYouTubeAdCheck("example.com", "doubleclick.net"))
        assertFalse(BrowserRules.isYouTubeAdCheck(null, "doubleclick.net"))
        // Look-alike hosts are not YouTube.
        assertFalse(BrowserRules.isYouTubeAdCheck("notyoutube.com", "doubleclick.net"))
        assertFalse(BrowserRules.isYouTubeAdCheck("youtube.com.evil.io", "doubleclick.net"))
        // On YouTube, other ad networks stay blocked.
        assertFalse(BrowserRules.isYouTubeAdCheck("m.youtube.com", "adnxs.com"))
        assertFalse(BrowserRules.isYouTubeAdCheck("m.youtube.com", "notdoubleclick.net"))
    }
}
