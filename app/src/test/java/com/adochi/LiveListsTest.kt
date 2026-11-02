package com.adochi

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adochi.core.AppSettings
import com.adochi.core.DnsProviders
import com.adochi.core.FilterEngine
import com.adochi.core.FilterRepository
import com.adochi.core.Verdict
import com.adochi.vpn.DnsMessage
import com.adochi.vpn.ShieldVpnService
import com.adochi.vpn.UpstreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Downloads the real default blocklists and checks they block ads and scams without
 * breaking the apps and sites people depend on. Needs internet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveListsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    /** Domains that must keep working: social apps, messaging, Google services, Pakistani banks and wallets. */
    private val mustWork = listOf(
        "facebook.com", "www.facebook.com", "m.facebook.com", "graph.facebook.com", "static.xx.fbcdn.net",
        "scontent.xx.fbcdn.net", "edge-mqtt.facebook.com", "www.messenger.com",
        "whatsapp.com", "web.whatsapp.com", "g.whatsapp.net", "mmg.whatsapp.net", "media.fkhi1-1.fna.whatsapp.net", "wa.me",
        "www.instagram.com", "i.instagram.com", "www.tiktok.com", "x.com", "api.x.com", "t.me", "web.telegram.org",
        "www.youtube.com", "m.youtube.com", "i.ytimg.com", "rr3---sn-4g5e6nzz.googlevideo.com", "youtubei.googleapis.com",
        "www.google.com", "accounts.google.com", "play.google.com", "play.googleapis.com", "android.clients.google.com",
        "fcm.googleapis.com", "mtalk.google.com", "firebaseinstallations.googleapis.com", "connectivitycheck.gstatic.com",
        "mail.google.com", "drive.google.com", "maps.googleapis.com", "outlook.live.com", "login.microsoftonline.com",
        "www.hbl.com", "hblibank.com.pk", "www.meezanbank.com", "www.ubldigital.com", "www.mcb.com.pk", "www.bankalfalah.com",
        "jazzcash.com.pk", "easypaisa.com.pk", "sadapay.pk", "nayapay.com", "www.daraz.pk", "www.bisp.gov.pk",
        "www.nadra.gov.pk", "www.fbr.gov.pk", "raw.githubusercontent.com", "github.com",
        "dns.quad9.net", "cloudflare-dns.com", "chatgpt.com", "www.wikipedia.org", "zoom.us", "www.netflix.com",
    )

    private val mustBlock = listOf(
        "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com", "an.facebook.com",
        "d.applovin.com", "unityads.unity3d.com", "ads.unity3d.com", "app-measurement.com", "adcolony.com",
        "ads.mopub.com", "analytics.tiktok.com", "popads.net", "propellerads.com",
    )

    @Test
    fun realListsBlockAdsAndThreatsButNotEssentials() {
        val settings = AppSettings(context)
        val repo = FilterRepository(context, settings)
        val result = runBlocking { repo.update() }
        assertEquals("lists failed to download", 0, result.failed)

        val totals = FilterEngine.totals.value
        println("Loaded ${totals.ads} ad/tracker and ${totals.threats} threat domains")
        assertTrue(totals.ads > 100_000)
        assertTrue(totals.threats > 100_000)

        val wronglyBlocked = mustWork.filter { FilterEngine.check(it) != Verdict.ALLOWED }
        assertTrue("Essential domains blocked: $wronglyBlocked", wronglyBlocked.isEmpty())

        val missed = mustBlock.filter { FilterEngine.check(it) == Verdict.ALLOWED }
        assertTrue("Ad domains not blocked: $missed", missed.isEmpty())

        // A live entry from the threat feed must come back as THREAT, not just BLOCKED.
        val tif = File(context.filesDir, "filters/raw/hagezi_tif-0.txt")
        val sample = tif.useLines { lines -> lines.first { it.isNotBlank() && !it.startsWith("#") } }.trim()
        assertEquals(sample, Verdict.THREAT, FilterEngine.check(sample))
    }

    @Test
    fun encryptedDnsWorksForEveryProvider() {
        val service = Robolectric.setupService(ShieldVpnService::class.java)
        val settings = AppSettings(context).apply { encryptedDns = true }
        val query = byteArrayOf(
            0x5a, 0x5a, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0,
            7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0,
            0, 1, 0, 1,
        )
        // Plain UDP needs VpnService.protect(), which only exists on a device; DoH is what's tested here.
        val resolver = UpstreamResolver(service, settings)
        val failures = DnsProviders.all.filter { it.dohUrl != null }.mapNotNull { provider ->
            try {
                val response = resolver.overHttps(provider.dohUrl!!, query)
                when {
                    DnsMessage.id(response) != 0x5a5a -> "${provider.name}: wrong id"
                    DnsMessage.minTtl(response) == null -> "${provider.name}: no records"
                    else -> null
                }
            } catch (e: Exception) {
                "${provider.name}: $e"
            }
        }
        assertTrue("DoH failures: $failures", failures.isEmpty())
    }
}
