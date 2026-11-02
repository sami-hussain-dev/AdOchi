package com.adochi

import android.app.Application
import com.adochi.core.CompiledLists
import com.adochi.core.DomainSet
import com.adochi.core.FilterEngine
import com.adochi.core.LinkInspector
import com.adochi.core.LinkInspector.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LinkInspectorTest {

    @Before
    fun lists() {
        FilterEngine.setLists(
            CompiledLists(
                ads = DomainSet.Builder().apply { add("doubleclick.net") }.build(),
                threats = DomainSet.Builder().apply { add("evil-phish.com") }.build(),
                exceptions = DomainSet.EMPTY,
            )
        )
        FilterEngine.setUserRules(emptySet(), emptySet())
    }

    private fun alerting(message: String) = LinkInspector.inspectMessage(message).filter { it.risk >= LinkInspector.ALERT_RISK }

    @Test
    fun knownPhishingDomainIsDangerous() {
        assertEquals(Level.DANGER, LinkInspector.inspect("https://login.evil-phish.com/bank").level)
    }

    @Test
    fun officialSitesAreSafe() {
        listOf("https://www.hbl.com", "https://m.facebook.com/", "https://www.daraz.pk/orders", "jazzcash.com.pk").forEach {
            val report = LinkInspector.inspect(it)
            assertEquals("$it -> ${report.reasons}", Level.SAFE, report.level)
        }
    }

    @Test
    fun fakeBankDomainIsDangerous() {
        val report = LinkInspector.inspect("hbl-reward.top/claim")
        assertEquals(report.reasons.toString(), Level.DANGER, report.level)
    }

    @Test
    fun bispPrizeScamMessageAlerts() {
        val caught = alerting("Mubarak ho! Aap ka BISP 8171 inaam 25000 nikla hai. Foran claim karein: http://bisp-8171.xyz/form")
        assertEquals(1, caught.size)
        assertEquals(Level.DANGER, caught[0].level)
    }

    @Test
    fun shortLinkWithPrizeWordsAlerts() {
        assertEquals(1, alerting("Congratulations! You have won a prize, open https://bit.ly/3xYz12").size)
    }

    @Test
    fun normalMessagesDoNotAlert() {
        listOf(
            "Meeting link: https://meet.google.com/abc-defg-hij",
            "Theek hai Mr.Ali, kal milte hain",
            "Your Daraz order is shipped. Track it: https://www.daraz.pk/orders/123",
            "Your HBL account was debited Rs.500. Never share your OTP.",
            "Dekho ye video https://youtu.be/dQw4w9WgXcQ",
        ).forEach { assertTrue(it, alerting(it).isEmpty()) }
    }

    @Test
    fun lookAlikeAndHiddenAddressesAreFlagged() {
        assertTrue(LinkInspector.inspect("https://xn--fcebook-8va.com").risk >= LinkInspector.ALERT_RISK)
        assertTrue(LinkInspector.inspect("https://www.google.com@203.0.113.9/login").risk >= LinkInspector.ALERT_RISK)
    }
}
