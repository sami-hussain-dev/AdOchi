package com.adochi

import com.adochi.core.DomainSet
import com.adochi.core.ListParser
import com.adochi.vpn.DnsMessage
import com.adochi.vpn.IpPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilteringTest {

    private fun parse(text: String): Pair<List<String>, List<String>> {
        val block = mutableListOf<String>()
        val allow = mutableListOf<String>()
        ListParser.parse(text.reader().buffered(), { block += it }, { allow += it })
        return block to allow
    }

    @Test
    fun parsesAllListFormats() {
        val (block, allow) = parse(
            """
            # comment
            ! adblock comment
            0.0.0.0 ads.example.com tracker.example.com
            127.0.0.1 localhost
            *.Popup.NET
            plain-domain.org # trailing comment
            ||doubleclick.net^
            ||path.example.com/ads^
            ||modifier.com^${'$'}dnstype=AAAA
            @@||allowed.example.com^
            192.168.1.1
            not a domain
            """.trimIndent()
        )
        assertEquals(listOf("ads.example.com", "tracker.example.com", "popup.net", "plain-domain.org", "doubleclick.net"), block)
        assertEquals(listOf("allowed.example.com"), allow)
    }

    @Test
    fun domainSetMatchesParentsOnly() {
        val set = DomainSet.Builder().apply {
            add("doubleclick.net")
            add("ads.example.com")
            add("ads.example.com")
        }.build()
        assertEquals(2, set.size)
        assertFalse(set.matches("ads.example.org"))
        assertTrue(set.matches("doubleclick.net"))
        assertTrue(set.matches("googleads.g.doubleclick.net"))
        assertTrue(set.matches("x.ads.example.com"))
        assertFalse(set.matches("example.com"))
        assertFalse(set.matches("notdoubleclick.net"))
    }

    @Test
    fun domainSetSurvivesDisk() {
        val file = kotlin.io.path.createTempFile().toFile()
        DomainSet.Builder().apply { add("a.com"); add("b.org") }.build().writeTo(file)
        val loaded = DomainSet.readFrom(file)!!
        assertTrue(loaded.matches("x.b.org"))
        assertFalse(loaded.matches("c.net"))
        file.delete()
    }

    /** Builds an IPv4/UDP DNS query for [name], like the system resolver sends to our tunnel. */
    private fun ipv4Query(name: String, type: Int): ByteArray {
        val dns = buildList<Byte> {
            addAll(listOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0).map { it.toByte() })
            name.split('.').forEach { label ->
                add(label.length.toByte())
                label.forEach { add(it.code.toByte()) }
            }
            add(0)
            add(0); add(type.toByte()); add(0); add(1)
        }.toByteArray()
        val packet = ByteArray(28 + dns.size)
        packet[0] = 0x45
        packet[2] = (packet.size shr 8).toByte(); packet[3] = packet.size.toByte()
        packet[9] = 17
        byteArrayOf(10, 0, 0, 5).copyInto(packet, 12)
        byteArrayOf(192.toByte(), 0, 2, 2).copyInto(packet, 16)
        packet[20] = 0xC3.toByte(); packet[21] = 0x50 // source port 50000
        packet[23] = 53
        packet[24] = ((8 + dns.size) shr 8).toByte(); packet[25] = (8 + dns.size).toByte()
        dns.copyInto(packet, 28)
        return packet
    }

    @Test
    fun blockedQueryGetsZeroAddressReply() {
        val raw = ipv4Query("Ads.Example.com", DnsMessage.TYPE_A)
        val query = IpPacket.parseDnsQuery(raw, raw.size)
        assertNotNull(query)
        query!!
        assertEquals(50000, query.sourcePort)

        val question = DnsMessage.parseQuestion(query.payload)!!
        assertEquals("ads.example.com", question.name)
        assertEquals(DnsMessage.TYPE_A, question.type)

        val answer = DnsMessage.blockedResponse(query.payload, question)
        assertEquals(0x1234, DnsMessage.id(answer))
        assertEquals(0x81, answer[2].toInt() and 0xFF) // QR + RD
        assertEquals(1, answer[7].toInt()) // one answer
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), answer.copyOfRange(answer.size - 4, answer.size))
        assertEquals(60, DnsMessage.minTtl(answer))

        val reply = IpPacket.buildResponse(query, answer)
        assertArrayEquals(byteArrayOf(192.toByte(), 0, 2, 2), reply.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 0, 0, 5), reply.copyOfRange(16, 20))
        assertEquals(53, ((reply[20].toInt() and 0xFF) shl 8) or (reply[21].toInt() and 0xFF))
        assertEquals(50000, ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF))
        // A valid IPv4 header sums to 0xFFFF including its checksum.
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((reply[i].toInt() and 0xFF) shl 8) or (reply[i + 1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }

    @Test
    fun ignoresNonDnsTraffic() {
        val raw = ipv4Query("example.com", DnsMessage.TYPE_A)
        raw[23] = 80 // destination port 80
        assertNull(IpPacket.parseDnsQuery(raw, raw.size))
    }
}
