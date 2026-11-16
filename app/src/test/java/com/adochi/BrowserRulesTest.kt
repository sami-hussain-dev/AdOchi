package com.adochi

import com.adochi.browser.BrowserRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRulesTest {

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
