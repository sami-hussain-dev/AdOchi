package com.adochi.skipper

/**
 * What the Ad Skipper looks for inside the YouTube and Facebook apps. Plain text rules with no
 * Android types, so they can be unit tested.
 *
 * The apps never tell anyone "an ad is playing", so the skipper reads what is on screen: the
 * "Skip ad" button and the ad countdown label ("Ad · 0:15", "Sponsored · 1 of 2").
 */
object AdSkipRules {

    const val YOUTUBE = "com.google.android.youtube"
    const val FACEBOOK = "com.facebook.katana"

    /** Apps the skipper works in. Nothing outside these apps is ever read or touched. */
    val APPS = setOf(YOUTUBE, FACEBOOK)

    /** View IDs of the skip button (fast lookup; the text match below covers renamed IDs). */
    val SKIP_IDS = listOf(
        "$YOUTUBE:id/skip_ad_button",
        "$YOUTUBE:id/modern_skip_ad_text",
        "$YOUTUBE:id/skip_ad_button_text",
        "$YOUTUBE:id/skip_ad_button_container",
    )

    /** View IDs that only exist while an ad plays in the YouTube player. */
    val AD_IDS = listOf(
        "$YOUTUBE:id/ad_progress_text",
        "$YOUTUBE:id/ad_countdown",
        "$YOUTUBE:id/player_learn_more_button",
        "$YOUTUBE:id/ad_badge",
    )

    /** Words searched for (the system search matches text and descriptions, ignoring case). */
    val SEARCH_WORDS = listOf("Skip", "Saltar", "Passer", "Überspringen", "Pular", "Lewati", "Atla", "Пропустить", "تخطي", "छोड़ें")

    private val SKIP_LABEL = Regex(
        "^(skip|skip ad|skip ads|skip ad[s]? ?[›>»]?|saltar|saltar anuncio|saltar anuncios|passer|passer l'annonce|passer les annonces|" +
            "überspringen|werbung überspringen|pular|pular anúncio|pular anúncios|lewati|lewati iklan|atla|reklamı atla|" +
            "пропустить|пропустить рекламу|تخطي|تخطي الإعلان|छोड़ें|विज्ञापन छोड़ें)$",
        RegexOption.IGNORE_CASE,
    )

    // "Ad · 0:15", "Ad 1 of 2", "Sponsored · 1 of 2 · 0:12", "Ad • 5"
    private val AD_LABEL = Regex(
        "^(ad|ads|sponsored|anuncio|publicité|werbung|anúncio|iklan|reklam|реклама|إعلان|विज्ञापन)\\s*([·•:|]\\s*)?(\\d|\\d+\\s*(of|de|sur|von|dari)\\s*\\d)",
        RegexOption.IGNORE_CASE,
    )

    /** A label that means "this button skips the ad". Countdown text like "Skip in 5" is not one. */
    fun isSkipLabel(text: CharSequence?): Boolean {
        val t = clean(text) ?: return false
        return t.length <= 40 && SKIP_LABEL.matches(t)
    }

    /** A label shown only while a video ad plays ("Ad · 0:15"). A plain "Sponsored" feed tag is not. */
    fun isAdLabel(text: CharSequence?): Boolean {
        val t = clean(text) ?: return false
        return t.length <= 60 && AD_LABEL.containsMatchIn(t)
    }

    private fun clean(text: CharSequence?): String? {
        if (text.isNullOrBlank()) return null
        // invisible marks the apps put around labels, and the "›" arrow after "Skip ad"
        return text.toString().replace(Regex("[\\u200B-\\u200F\\u2066-\\u2069\\uFEFF]"), "").trim()
    }
}
