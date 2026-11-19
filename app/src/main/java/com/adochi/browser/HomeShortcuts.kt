package com.adochi.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.adochi.MainActivity
import com.adochi.R

/** "YouTube (ad-free)" / "Facebook (ad-free)" icons on the home screen that open the Clean Browser. */
object HomeShortcuts {

    enum class Site(val id: String, val label: String, val url: String, val icon: Int) {
        YOUTUBE("pin_youtube", "YouTube ad-free", "https://m.youtube.com/", R.drawable.ic_shortcut_video),
        FACEBOOK("pin_facebook", "Facebook ad-free", "https://m.facebook.com/", R.drawable.ic_shortcut_feed),
    }

    fun canPin(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to add the icon; the launcher shows its own confirmation. */
    fun pin(context: Context, site: Site): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(site.url), context, MainActivity::class.java)
        val info = ShortcutInfoCompat.Builder(context, site.id)
            .setShortLabel(site.label)
            .setLongLabel(site.label)
            .setIcon(IconCompat.createWithResource(context, site.icon))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
