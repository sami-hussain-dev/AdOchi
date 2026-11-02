package com.adochi.vpn

/** Just enough DNS wire-format handling to read a question and answer it. */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val BLOCKED_TTL = 60

    class Question(val name: String, val type: Int, val end: Int)

    fun id(msg: ByteArray): Int = u16(msg, 0)

    fun parseQuestion(msg: ByteArray): Question? {
        if (msg.size < 17) return null
        if (u16(msg, 2) and 0x8000 != 0) return null // already a response
        if (u16(msg, 4) < 1) return null
        val name = StringBuilder()
        var p = 12
        while (true) {
            if (p >= msg.size) return null
            val len = msg[p].toInt() and 0xFF
            p++
            if (len == 0) break
            if (len and 0xC0 != 0 || p + len > msg.size) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in 0 until len) {
                var c = msg[p + i].toInt() and 0xFF
                if (c in 'A'.code..'Z'.code) c += 32
                name.append(c.toChar())
            }
            p += len
            if (name.length > 253) return null
        }
        if (p + 4 > msg.size) return null
        return Question(name.toString(), u16(msg, p), p + 4)
    }

    /** Answers A/AAAA with 0.0.0.0 / :: so apps fail fast instead of waiting; other types get an empty answer. */
    fun blockedResponse(query: ByteArray, q: Question): ByteArray {
        val rdLength = when (q.type) {
            TYPE_A -> 4
            TYPE_AAAA -> 16
            else -> 0
        }
        val out = ByteArray(q.end + if (rdLength > 0) 12 + rdLength else 0)
        System.arraycopy(query, 0, out, 0, q.end)
        writeHeader(out, query, answers = if (rdLength > 0) 1 else 0, truncated = false)
        if (rdLength > 0) {
            val p = q.end
            put16(out, p, 0xC00C) // pointer to the question name
            put16(out, p + 2, q.type)
            put16(out, p + 4, 1) // IN
            put32(out, p + 6, BLOCKED_TTL)
            put16(out, p + 10, rdLength)
        }
        return out
    }

    /** Header plus question with TC set, for answers that don't fit the tunnel MTU. */
    fun truncatedResponse(query: ByteArray, q: Question): ByteArray {
        val out = query.copyOf(q.end)
        writeHeader(out, query, answers = 0, truncated = true)
        return out
    }

    fun withId(msg: ByteArray, id: Int): ByteArray = msg.copyOf().also { put16(it, 0, id) }

    /** Smallest answer TTL of a successful response, or null when it shouldn't be cached. */
    fun minTtl(msg: ByteArray): Int? {
        if (msg.size < 12 || u16(msg, 2) and 0x000F != 0) return null
        val questions = u16(msg, 4)
        val answers = u16(msg, 6)
        if (answers == 0) return null
        var p = 12
        repeat(questions) {
            p = skipName(msg, p) ?: return null
            p += 4
        }
        var min = Int.MAX_VALUE
        repeat(answers) {
            p = skipName(msg, p) ?: return null
            if (p + 10 > msg.size) return null
            val ttl = ((u16(msg, p + 4) shl 16) or u16(msg, p + 6))
            min = minOf(min, ttl)
            p += 10 + u16(msg, p + 8)
        }
        return if (min == Int.MAX_VALUE || min < 0) null else min
    }

    private fun writeHeader(out: ByteArray, query: ByteArray, answers: Int, truncated: Boolean) {
        val requestFlags = u16(query, 2)
        // QR + copied opcode/RD + RA, RCODE 0
        var flags = 0x8000 or (requestFlags and 0x7900) or 0x0080
        if (truncated) flags = flags or 0x0200
        put16(out, 2, flags)
        put16(out, 4, 1)
        put16(out, 6, answers)
        put16(out, 8, 0)
        put16(out, 10, 0)
    }

    private fun skipName(msg: ByteArray, start: Int): Int? {
        var p = start
        while (p < msg.size) {
            val len = msg[p].toInt() and 0xFF
            when {
                len == 0 -> return p + 1
                len and 0xC0 == 0xC0 -> return p + 2
                else -> p += len + 1
            }
        }
        return null
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v ushr 8).toByte()
        b[i + 1] = v.toByte()
    }

    private fun put32(b: ByteArray, i: Int, v: Int) {
        put16(b, i, v ushr 16)
        put16(b, i + 2, v)
    }
}
