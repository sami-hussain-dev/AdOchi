package com.adochi.vpn

/** A UDP port-53 query read from the tunnel, with what's needed to address the reply. */
class DnsQuery(
    val ipVersion: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

/** Minimal IPv4/IPv6 + UDP parsing and building. Only DNS traffic is routed into the tunnel. */
object IpPacket {

    private const val UDP = 17

    fun parseDnsQuery(buf: ByteArray, length: Int): DnsQuery? {
        if (length < 1) return null
        val version = (buf[0].toInt() ushr 4) and 0xF
        val udpStart: Int
        val total: Int
        val source: ByteArray
        val destination: ByteArray
        when (version) {
            4 -> {
                if (length < 28) return null
                val ihl = (buf[0].toInt() and 0xF) * 4
                if (ihl < 20 || length < ihl + 8) return null
                if (buf[9].toInt() and 0xFF != UDP) return null
                if (u16(buf, 6) and 0x3FFF != 0) return null // fragmented
                total = minOf(u16(buf, 2), length)
                source = buf.copyOfRange(12, 16)
                destination = buf.copyOfRange(16, 20)
                udpStart = ihl
            }
            6 -> {
                if (length < 48) return null
                if (buf[6].toInt() and 0xFF != UDP) return null
                total = minOf(40 + u16(buf, 4), length)
                source = buf.copyOfRange(8, 24)
                destination = buf.copyOfRange(24, 40)
                udpStart = 40
            }
            else -> return null
        }
        val destinationPort = u16(buf, udpStart + 2)
        if (destinationPort != 53) return null
        val payloadEnd = minOf(udpStart + u16(buf, udpStart + 4), total)
        if (payloadEnd <= udpStart + 8) return null
        return DnsQuery(
            ipVersion = version,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = u16(buf, udpStart),
            destinationPort = destinationPort,
            payload = buf.copyOfRange(udpStart + 8, payloadEnd),
        )
    }

    fun headerSize(ipVersion: Int) = if (ipVersion == 4) 28 else 48

    /** Builds the reply packet: addresses and ports swapped around [payload]. */
    fun buildResponse(query: DnsQuery, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        return if (query.ipVersion == 4) {
            val packet = ByteArray(20 + udpLength)
            packet[0] = 0x45
            put16(packet, 2, packet.size)
            put16(packet, 6, 0x4000) // don't fragment
            packet[8] = 64
            packet[9] = UDP.toByte()
            System.arraycopy(query.destinationAddress, 0, packet, 12, 4)
            System.arraycopy(query.sourceAddress, 0, packet, 16, 4)
            put16(packet, 10, fold(sum(packet, 0, 20, 0)))
            writeUdp(packet, 20, query, payload)
            // UDP checksum is optional over IPv4; zero means "not computed".
            packet
        } else {
            val packet = ByteArray(40 + udpLength)
            packet[0] = 0x60
            put16(packet, 4, udpLength)
            packet[6] = UDP.toByte()
            packet[7] = 64
            System.arraycopy(query.destinationAddress, 0, packet, 8, 16)
            System.arraycopy(query.sourceAddress, 0, packet, 24, 16)
            writeUdp(packet, 40, query, payload)
            val pseudo = sum(packet, 8, 32, udpLength.toLong() + UDP)
            val checksum = fold(sum(packet, 40, udpLength, pseudo))
            put16(packet, 46, if (checksum == 0) 0xFFFF else checksum)
            packet
        }
    }

    private fun writeUdp(packet: ByteArray, start: Int, query: DnsQuery, payload: ByteArray) {
        put16(packet, start, query.destinationPort)
        put16(packet, start + 2, query.sourcePort)
        put16(packet, start + 4, 8 + payload.size)
        System.arraycopy(payload, 0, packet, start + 8, payload.size)
    }

    private fun sum(b: ByteArray, offset: Int, length: Int, initial: Long): Long {
        var s = initial
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            s += ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) s += (b[i].toInt() and 0xFF) shl 8
        return s
    }

    private fun fold(sum: Long): Int {
        var s = sum
        while (s ushr 16 != 0L) s = (s and 0xFFFF) + (s ushr 16)
        return (s.inv() and 0xFFFF).toInt()
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }
}
