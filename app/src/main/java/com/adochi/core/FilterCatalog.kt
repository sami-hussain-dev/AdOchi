package com.adochi.core

enum class ListKind { ADS, THREATS }

/** A blocklist. Built-in lists ship in assets; the rest are downloaded free from GitHub and other public mirrors. */
data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    val kind: ListKind,
    val urls: List<String> = emptyList(),
    val asset: String? = null,
    val defaultOn: Boolean,
)

object FilterCatalog {

    private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/"

    val all = listOf(
        FilterList(
            id = "essentials",
            name = "AdOchi Essentials",
            description = "Built-in list of major ad networks, game ad SDKs and pop-up networks. Works offline.",
            kind = ListKind.ADS,
            asset = "filters/essentials.txt",
            defaultOn = true,
        ),
        FilterList(
            id = "social",
            name = "Social media trackers",
            description = "Facebook Audience Network, Meta pixel, TikTok/X/Snapchat ad and tracking endpoints.",
            kind = ListKind.ADS,
            asset = "filters/social.txt",
            defaultOn = true,
        ),
        FilterList(
            id = "hagezi_pro",
            name = "HaGeZi Multi PRO",
            description = "Ads, trackers, telemetry and analytics across apps, games and websites.",
            kind = ListKind.ADS,
            urls = listOf(HAGEZI + "pro-onlydomains.txt"),
            defaultOn = true,
        ),
        FilterList(
            id = "hagezi_popup",
            name = "Pop-up & redirect ads",
            description = "Aggressive pop-under, redirect and push-notification ad networks.",
            kind = ListKind.ADS,
            urls = listOf(HAGEZI + "popupads-onlydomains.txt"),
            defaultOn = true,
        ),
        FilterList(
            id = "native_trackers",
            name = "Phone maker trackers",
            description = "Built-in telemetry of Xiaomi, Samsung, Oppo, Realme, Vivo and Huawei phones.",
            kind = ListKind.ADS,
            urls = listOf(
                HAGEZI + "native.xiaomi-onlydomains.txt",
                HAGEZI + "native.samsung-onlydomains.txt",
                HAGEZI + "native.oppo-realme-onlydomains.txt",
                HAGEZI + "native.vivo-onlydomains.txt",
                HAGEZI + "native.huawei-onlydomains.txt",
            ),
            defaultOn = false,
        ),
        FilterList(
            id = "oisd_small",
            name = "OISD Small",
            description = "Carefully curated ad list with almost no false positives.",
            kind = ListKind.ADS,
            urls = listOf("https://small.oisd.nl/domainswild"),
            defaultOn = false,
        ),
        FilterList(
            id = "adguard_dns",
            name = "AdGuard DNS filter",
            description = "AdGuard's DNS-level ad and tracker filter.",
            kind = ListKind.ADS,
            urls = listOf("https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
            defaultOn = false,
        ),
        FilterList(
            id = "stevenblack",
            name = "StevenBlack Unified hosts",
            description = "Classic hosts file combining many well-known ad and malware lists.",
            kind = ListKind.ADS,
            urls = listOf("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
            defaultOn = false,
        ),
        FilterList(
            id = "hagezi_tif",
            name = "Threat Intelligence",
            description = "Phishing, scam, malware, crypto-jacking and stalkerware domains, updated daily.",
            kind = ListKind.THREATS,
            urls = listOf(HAGEZI + "tif.medium-onlydomains.txt"),
            defaultOn = true,
        ),
        FilterList(
            id = "hagezi_fake",
            name = "Fake shops & scams",
            description = "Fake online shops, fake investment and support scams, fraud sites.",
            kind = ListKind.THREATS,
            urls = listOf(HAGEZI + "fake-onlydomains.txt"),
            defaultOn = true,
        ),
        FilterList(
            id = "phishing_army",
            name = "Phishing Army",
            description = "Sites that steal bank, card and account logins.",
            kind = ListKind.THREATS,
            urls = listOf("https://phishing.army/download/phishing_army_blocklist.txt"),
            defaultOn = true,
        ),
        FilterList(
            id = "malware_filter",
            name = "Malware & phishing URLs",
            description = "Live feeds of malware downloads (URLhaus) and phishing pages.",
            kind = ListKind.THREATS,
            urls = listOf(
                "https://malware-filter.gitlab.io/malware-filter/phishing-filter-domains.txt",
                "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-domains.txt",
            ),
            defaultOn = true,
        ),
        FilterList(
            id = "phishing_db",
            name = "Phishing Database (large)",
            description = "Very large community list of active phishing domains. Uses more memory.",
            kind = ListKind.THREATS,
            urls = listOf("https://raw.githubusercontent.com/Phishing-Database/Phishing.Database/master/phishing-domains-ACTIVE.txt"),
            defaultOn = false,
        ),
    )

    val defaultIds: Set<String> = all.filter { it.defaultOn }.map { it.id }.toSet()
}
