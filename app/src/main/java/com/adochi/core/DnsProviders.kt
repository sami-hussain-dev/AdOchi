package com.adochi.core

/**
 * Public resolvers used for allowed lookups. All are free; DoH hides lookups from the ISP or Wi-Fi owner.
 * [dohUrl] is null when the provider has no HTTP/1.1 DoH endpoint (Android's HttpURLConnection can't speak HTTP/2).
 */
data class DnsProvider(
    val id: String,
    val name: String,
    val description: String,
    val servers: List<String>,
    val dohUrl: String?,
)

object DnsProviders {

    val CLOUDFLARE_SECURITY = DnsProvider(
        id = "cloudflare_security",
        name = "Cloudflare Security",
        description = "Fast, private, and blocks known malware on its side too",
        servers = listOf("1.1.1.2", "1.0.0.2"),
        dohUrl = "https://security.cloudflare-dns.com/dns-query",
    )

    val all = listOf(
        CLOUDFLARE_SECURITY,
        DnsProvider(
            id = "cloudflare",
            name = "Cloudflare",
            description = "Fastest in most places, privacy-first",
            servers = listOf("1.1.1.1", "1.0.0.1"),
            dohUrl = "https://cloudflare-dns.com/dns-query",
        ),
        DnsProvider(
            id = "adguard",
            name = "AdGuard DNS",
            description = "Blocks extra ads on its side",
            servers = listOf("94.140.14.14", "94.140.15.15"),
            dohUrl = "https://dns.adguard-dns.com/dns-query",
        ),
        DnsProvider(
            id = "google",
            name = "Google",
            description = "Very reliable, but Google sees your lookups",
            servers = listOf("8.8.8.8", "8.8.4.4"),
            dohUrl = "https://dns.google/dns-query",
        ),
        DnsProvider(
            id = "quad9",
            name = "Quad9",
            description = "Non-profit, blocks malware. Not encrypted in AdOchi (needs HTTP/2)",
            servers = listOf("9.9.9.9", "149.112.112.112"),
            dohUrl = null,
        ),
    )

    val DEFAULT = CLOUDFLARE_SECURITY

    fun byId(id: String): DnsProvider = all.firstOrNull { it.id == id } ?: DEFAULT
}
