package com.adochi.core

import java.io.BufferedReader

/** Reads hosts files, plain or wildcard domain lists, and AdGuard `||domain^` rules. */
object ListParser {

    private val IGNORED = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts", "0.0.0.0",
    )

    fun parse(reader: BufferedReader, onBlock: (String) -> Unit, onAllow: (String) -> Unit) {
        reader.forEachLine { parseLine(it, onBlock, onAllow) }
    }

    private fun parseLine(raw: String, onBlock: (String) -> Unit, onAllow: (String) -> Unit) {
        var line = raw.trim()
        if (line.isEmpty()) return
        when (line[0]) {
            '#', '!', '[' -> return
        }
        if (line.startsWith("@@||")) {
            adblockDomain(line, 4)?.let(onAllow)
            return
        }
        if (line.startsWith("||")) {
            adblockDomain(line, 2)?.let(onBlock)
            return
        }

        val comment = line.indexOf('#')
        if (comment >= 0) line = line.substring(0, comment).trim()
        if (line.isEmpty()) return

        val tokens = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (tokens.size == 1) {
            normalize(tokens[0])?.let(onBlock)
        } else if (isIpToken(tokens[0])) {
            // Hosts format: "0.0.0.0 a.com b.com"
            for (i in 1 until tokens.size) normalize(tokens[i])?.let(onBlock)
        }
    }

    /** `||example.com^` and `||example.com^$important`; rules with paths or other modifiers are skipped. */
    private fun adblockDomain(line: String, start: Int): String? {
        val caret = line.indexOf('^', start)
        val end = if (caret >= 0) caret else line.length
        if (caret >= 0) {
            val rest = line.substring(caret + 1)
            if (rest.isNotEmpty() && rest != "|" && rest != "\$important") return null
        }
        val domain = line.substring(start, end)
        if (domain.contains('/') || domain.contains('*') || domain.contains('$')) return null
        return normalize(domain)
    }

    private fun isIpToken(token: String): Boolean =
        token.contains(':') || token.all { it.isDigit() || it == '.' }

    /** Lower-cases and validates a domain; returns null for anything that isn't a blockable name. */
    fun normalize(input: String): String? {
        var d = input.trim().lowercase()
        if (d.startsWith("*.")) d = d.substring(2)
        d = d.trimStart('.').trimEnd('.')
        if (d.length !in 3..253 || '.' !in d || d in IGNORED) return null
        var hasLetter = false
        for (c in d) {
            when (c) {
                in 'a'..'z' -> hasLetter = true
                in '0'..'9', '-', '.', '_' -> Unit
                else -> return null
            }
        }
        if (!hasLetter || d.contains("..")) return null
        return d
    }
}
