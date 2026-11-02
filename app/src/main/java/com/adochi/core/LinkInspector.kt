package com.adochi.core

import android.net.Uri

/** Offline check of links someone received (WhatsApp, SMS, email) before they are opened. */
object LinkInspector {

    enum class Level { DANGER, WARNING, SAFE, INVALID }

    /** [risk] is a 0–100+ score; the message guard alerts from [ALERT_RISK] upwards. */
    data class Report(val level: Level, val url: String?, val host: String?, val reasons: List<String>, val risk: Int)

    const val ALERT_RISK = 40
    private const val DANGER_RISK = 70

    private val URL_PATTERN = Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>"']+|[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\.[a-z]{2,}(?:/[^\s<>"']*)?)"""
    )

    private val ABUSED_TLDS = setOf(
        "zip", "mov", "top", "xyz", "click", "link", "rest", "sbs", "cfd", "buzz", "icu", "cyou",
        "monster", "quest", "bond", "support", "lol", "cam", "gq", "tk", "ml", "cf", "ga", "work", "kim",
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.ly", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "tiny.cc",
        "ow.ly", "s.id", "rebrand.ly", "t.co", "goo.su", "clck.ru", "v.gd", "shorturl.asia",
    )

    /** Brands scammers copy most, with the domains they really use. */
    private val BRANDS = mapOf(
        "facebook" to setOf("facebook.com", "fb.com", "fb.me", "meta.com", "fbcdn.net"),
        "whatsapp" to setOf("whatsapp.com", "whatsapp.net", "wa.me"),
        "instagram" to setOf("instagram.com"),
        "google" to setOf("google.com", "google.com.pk", "goo.gl", "g.co"),
        "youtube" to setOf("youtube.com", "youtu.be"),
        "apple" to setOf("apple.com", "icloud.com"),
        "microsoft" to setOf("microsoft.com", "live.com", "office.com"),
        "paypal" to setOf("paypal.com"),
        "amazon" to setOf("amazon.com", "amazon.ae", "amazon.co.uk"),
        "netflix" to setOf("netflix.com"),
        "tiktok" to setOf("tiktok.com"),
        "binance" to setOf("binance.com"),
        "daraz" to setOf("daraz.pk", "daraz.com"),
        "jazzcash" to setOf("jazzcash.com.pk"),
        "easypaisa" to setOf("easypaisa.com.pk"),
        "sadapay" to setOf("sadapay.pk"),
        "nayapay" to setOf("nayapay.com"),
        "hbl" to setOf("hbl.com", "hblibank.com.pk"),
        "meezan" to setOf("meezanbank.com"),
        "ubl" to setOf("ubldigital.com"),
        "mcb" to setOf("mcb.com.pk"),
        "alfalah" to setOf("bankalfalah.com"),
        "nadra" to setOf("nadra.gov.pk"),
        "bisp" to setOf("bisp.gov.pk"),
        "ehsaas" to setOf("pass.gov.pk", "bisp.gov.pk"),
        "fbr" to setOf("fbr.gov.pk"),
        "pta" to setOf("pta.gov.pk"),
    )

    /** Words that, next to a link, are typical of prize, aid-money and account-block scams (English and Roman Urdu). */
    private val SCAM_WORDS = listOf(
        "otp", "pin code", "cvv", "kyc", "account blocked", "account suspended", "account band", "account block",
        "verify your account", "you have won", "you won", "congratulations", "lucky draw", "lottery", "prize",
        "inaam", "mubarak ho", "jeet", "bisp", "ehsaas", "8171", "benazir", "free recharge", "free balance",
        "claim now", "limited time", "urgent", "foran",
    )

    private val SECOND_LEVELS = setOf("com", "net", "org", "gov", "edu", "co", "ac", "gob", "web")

    fun extractUrl(text: String): String? = extractUrls(text).firstOrNull()

    fun extractUrls(text: String): List<String> =
        URL_PATTERN.findAll(text).map { it.value.trimEnd('.', ',', ')', '!', '?', '*') }.distinct().toList()

    fun inspect(input: String): Report {
        val raw = extractUrl(input)
            ?: return Report(Level.INVALID, null, null, listOf("No link found. Paste the full link."), 0)
        return inspectUrl(raw, scamWordsIn(input))
    }

    /** Every link in a chat message, with the message's wording taken into account. */
    fun inspectMessage(text: String): List<Report> {
        val words = scamWordsIn(text)
        return extractUrls(text).take(5).map { inspectUrl(it, words) }.filter { it.level != Level.INVALID }
    }

    private fun scamWordsIn(text: String): List<String> {
        val lower = text.lowercase()
        return SCAM_WORDS.filter { word -> Regex("\\b" + Regex.escape(word) + "\\b").containsMatchIn(lower) }
    }

    private fun inspectUrl(raw: String, scamWords: List<String>): Report {
        val url = if (raw.contains("://")) raw else "https://$raw"
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: return Report(Level.INVALID, null, null, listOf("This doesn't look like a valid link."), 0)

        val reasons = mutableListOf<String>()
        var risk = 0
        fun flag(points: Int, reason: String) {
            risk += points
            reasons += reason
        }

        val verdict = FilterEngine.check(host)
        when (verdict) {
            Verdict.THREAT -> flag(100, "Listed in AdOchi's phishing, scam and malware database.")
            Verdict.BLOCKED -> flag(10, "This is a known ad or tracking domain.")
            Verdict.ALLOWED -> Unit
        }

        val labels = host.split('.')
        val registrable = registrableDomain(labels)
        val words = host.split('.', '-').toSet()
        val isOfficial = BRANDS.values.any { official -> official.any { host == it || host.endsWith(".$it") } }

        for ((brand, official) in BRANDS) {
            if (brand in words && official.none { host == it || host.endsWith(".$it") }) {
                flag(50, "Uses the name \"${brand.replaceFirstChar { it.uppercase() }}\" but is not their official website ($registrable).")
                break
            }
        }
        if (labels.any { it.startsWith("xn--") }) flag(40, "Uses look-alike international letters to imitate another site.")
        if (uri.userInfo != null) flag(40, "Hides the real address behind an @ sign.")
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) flag(30, "Uses a raw IP address instead of a website name.")
        if (labels.last() in ABUSED_TLDS) flag(25, "Ends in .${labels.last()}, a domain ending often used by scammers.")
        if (labels.count { it.length > 2 && it.count(Char::isDigit) > it.length / 2 } > 0) {
            flag(15, "Contains random-looking numbers, typical of throwaway scam sites.")
        }
        if (uri.scheme.equals("http", ignoreCase = true)) flag(15, "Not encrypted (http). Never type passwords, PINs or card numbers here.")
        if (registrable in SHORTENERS) flag(15, "Short link: the real destination is hidden.")
        if (labels.size > 5 || host.length > 60) flag(10, "Unusually long address, a common trick to look legitimate.")
        if (scamWords.isNotEmpty() && !isOfficial) {
            flag(40, "The message uses typical scam words (${scamWords.take(3).joinToString { "\"$it\"" }}).")
        }

        val level = when {
            verdict == Verdict.THREAT || risk >= DANGER_RISK -> Level.DANGER
            risk > 0 -> Level.WARNING
            else -> Level.SAFE
        }
        return Report(level, url, host, reasons, risk)
    }

    private fun registrableDomain(labels: List<String>): String {
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val take = if (tld.length == 2 && second in SECOND_LEVELS) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }
}
