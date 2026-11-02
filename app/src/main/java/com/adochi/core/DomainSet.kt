package com.adochi.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Arrays

/** 64-bit FNV-1a with a murmur finalizer. Hashing a suffix (from [start]) equals hashing that substring. */
object DomainHash {
    private val FNV_OFFSET = 0xcbf29ce484222325uL.toLong()
    private const val FNV_PRIME = 0x100000001b3L
    private val MIX_1 = 0xff51afd7ed558ccduL.toLong()
    private val MIX_2 = 0xc4ceb9fe1a85ec53uL.toLong()

    fun of(domain: String, start: Int = 0): Long {
        var h = FNV_OFFSET
        for (i in start until domain.length) {
            h = (h xor domain[i].code.toLong()) * FNV_PRIME
        }
        h = (h xor (h ushr 33)) * MIX_1
        h = (h xor (h ushr 33)) * MIX_2
        return h xor (h ushr 33)
    }
}

/**
 * A compact set of domains stored as sorted 64-bit hashes: ~8 bytes per domain,
 * so a million blocked domains fit in 8 MB and a lookup is a binary search.
 */
class DomainSet private constructor(private val hashes: LongArray) {

    val size: Int get() = hashes.size

    /** True when [domain] or any of its parent domains is in the set. */
    fun matches(domain: String): Boolean {
        if (hashes.isEmpty()) return false
        var start = 0
        while (true) {
            if (Arrays.binarySearch(hashes, DomainHash.of(domain, start)) >= 0) return true
            val dot = domain.indexOf('.', start)
            if (dot < 0) return false
            start = dot + 1
        }
    }

    fun writeTo(file: File) {
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(hashes.size)
            val bytes = ByteBuffer.allocate(hashes.size * 8)
            bytes.asLongBuffer().put(hashes)
            out.write(bytes.array())
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not save ${file.name}")
        }
    }

    class Builder {
        private var data = LongArray(4096)
        private var count = 0

        fun add(domain: String) {
            if (count == data.size) data = data.copyOf(count * 2)
            data[count++] = DomainHash.of(domain)
        }

        fun build(): DomainSet {
            val sorted = data.copyOf(count)
            sorted.sort()
            var unique = 0
            for (i in sorted.indices) {
                if (unique == 0 || sorted[unique - 1] != sorted[i]) sorted[unique++] = sorted[i]
            }
            return DomainSet(sorted.copyOf(unique))
        }
    }

    companion object {
        private const val MAGIC = 0x0AD0C401
        val EMPTY = DomainSet(LongArray(0))

        fun readFrom(file: File): DomainSet? {
            if (!file.exists()) return null
            return try {
                DataInputStream(file.inputStream().buffered()).use { input ->
                    if (input.readInt() != MAGIC) return null
                    val size = input.readInt()
                    val bytes = ByteArray(size * 8)
                    input.readFully(bytes)
                    val hashes = LongArray(size)
                    ByteBuffer.wrap(bytes).asLongBuffer().get(hashes)
                    DomainSet(hashes)
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}

/** Suffix match for the small, user-managed rule sets. */
fun Set<String>.coversDomain(domain: String): Boolean {
    if (isEmpty()) return false
    var d = domain
    while (true) {
        if (d in this) return true
        val dot = d.indexOf('.')
        if (dot < 0) return false
        d = d.substring(dot + 1)
    }
}
