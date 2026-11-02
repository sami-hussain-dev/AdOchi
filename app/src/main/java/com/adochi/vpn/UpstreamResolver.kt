package com.adochi.vpn

import android.net.VpnService
import com.adochi.core.AppSettings
import com.adochi.core.DnsProviders
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/** Sends allowed lookups to the chosen resolver, over HTTPS when possible and plain UDP as a fallback. */
class UpstreamResolver(private val vpn: VpnService, private val settings: AppSettings) {

    fun resolve(query: ByteArray): ByteArray? {
        val provider = DnsProviders.byId(settings.dnsProvider)
        if (settings.encryptedDns && provider.dohUrl != null) {
            try {
                return overHttps(provider.dohUrl, query)
            } catch (e: IOException) {
                // Some networks block DoH; plain DNS keeps the device online.
            }
        }
        for (server in provider.servers) {
            try {
                return overUdp(server, query)
            } catch (e: IOException) {
                // Try the next server.
            }
        }
        return null
    }

    private fun overUdp(server: String, query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            vpn.protect(socket)
            socket.soTimeout = 4000
            socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
            val buffer = ByteArray(4096)
            val id = DnsMessage.id(query)
            while (true) {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                if (packet.length >= 12 && DnsMessage.id(buffer) == id) return buffer.copyOf(packet.length)
            }
        }
    }

    internal fun overHttps(url: String, query: ByteArray): ByteArray {
        val id = DnsMessage.id(query)
        val body = DnsMessage.withId(query, 0) // RFC 8484: ID 0 makes responses cache-friendly
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("DoH HTTP ${conn.responseCode}")
            val response = conn.inputStream.use { it.readBytes() }
            if (response.size < 12) throw IOException("Short DoH response")
            return DnsMessage.withId(response, id)
        } finally {
            conn.disconnect()
        }
    }
}

/** Small TTL-aware cache so repeated lookups are answered instantly on the device. */
class DnsCache(private val maxEntries: Int = 1024) {

    private class Entry(val response: ByteArray, val expiresAt: Long)

    private val map = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    fun get(q: DnsMessage.Question, id: Int): ByteArray? = synchronized(map) {
        val key = "${q.name}/${q.type}"
        val entry = map[key] ?: return null
        if (entry.expiresAt < System.currentTimeMillis()) {
            map.remove(key)
            return null
        }
        DnsMessage.withId(entry.response, id)
    }

    fun put(q: DnsMessage.Question, response: ByteArray) {
        val ttl = DnsMessage.minTtl(response) ?: return
        val seconds = ttl.coerceIn(5, 600)
        synchronized(map) {
            map["${q.name}/${q.type}"] = Entry(response, System.currentTimeMillis() + seconds * 1000L)
        }
    }

    fun clear() = synchronized(map) { map.clear() }
}
