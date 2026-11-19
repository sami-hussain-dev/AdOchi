package com.adochi.skipper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adochi.AdOchiApp
import com.adochi.core.Stats

/**
 * Ad Skipper (full build only). Inside the YouTube and Facebook apps it taps "Skip ad" as soon as
 * it appears, and mutes ads that cannot be skipped until they end.
 *
 * Ads in those apps come from the same servers as the videos, so no blocker can stop them without
 * root. This is the next best thing. It only ever reads and touches the two apps in
 * [AdSkipRules.APPS] (also limited in res/xml/ad_skipper.xml), stores nothing and sends nothing.
 */
class AdSkipperService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val audio by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var scanQueued = false
    private var lastClickAt = 0L
    private var adActive = false
    private var mutedByUs = false

    private val scan = Runnable {
        scanQueued = false
        check()
    }

    /** While AdOchi has muted an ad, keep checking: the ad may end with no event, or the user may leave the app. */
    private val watchMute = object : Runnable {
        override fun run() {
            if (!mutedByUs) return
            check()
            if (mutedByUs) main.postDelayed(this, 1200)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() !in AdSkipRules.APPS) return
        // the apps fire dozens of events per second: look at the screen at most every 250 ms
        if (!scanQueued) {
            scanQueued = true
            main.postDelayed(scan, 250)
        }
    }

    override fun onInterrupt() = restoreSound()

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        restoreSound()
        super.onDestroy()
    }

    private fun check() {
        if (!AdOchiApp.instance.settings.adSkipper) {
            restoreSound()
            return
        }
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        }
        if (root == null || root.packageName?.toString() !in AdSkipRules.APPS) {
            adActive = false
            restoreSound() // the user left YouTube / Facebook
            return
        }

        val skip = findSkipButton(root)
        val adShowing = skip != null || isAdShowing(root)

        if (adShowing && !adActive) {
            adActive = true
            Stats.recordAppAdSkipped()
        }
        if (!adShowing) {
            adActive = false
            restoreSound()
            return
        }

        if (skip != null) {
            if (SystemClock.elapsedRealtime() - lastClickAt > 800) {
                lastClickAt = SystemClock.elapsedRealtime()
                tap(skip)
            }
        } else {
            mute() // an ad you cannot skip: no sound until it ends
        }
    }

    private fun findSkipButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in AdSkipRules.SKIP_IDS) {
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (word in AdSkipRules.SEARCH_WORDS) {
            root.findAccessibilityNodeInfosByText(word).firstOrNull {
                it.isVisibleToUser && (AdSkipRules.isSkipLabel(it.text) || AdSkipRules.isSkipLabel(it.contentDescription))
            }?.let { return it }
        }
        return null
    }

    private fun isAdShowing(root: AccessibilityNodeInfo): Boolean {
        for (id in AdSkipRules.AD_IDS) {
            if (root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }) return true
        }
        return listOf("Ad", "Sponsored").any { word ->
            root.findAccessibilityNodeInfosByText(word).any {
                it.isVisibleToUser && (AdSkipRules.isAdLabel(it.text) || AdSkipRules.isAdLabel(it.contentDescription))
            }
        }
    }

    /** Click the button (or its clickable parent); if the app ignores that, tap its centre. */
    private fun tap(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        var depth = 0
        while (target != null && !target.isClickable && depth < 4) {
            target = target.parent
            depth++
        }
        if (target != null && target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return

        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.isEmpty) return
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build(), null, null)
    }

    private fun mute() {
        if (mutedByUs) return
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return // the user muted it themselves: leave it alone
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        mutedByUs = true
        main.postDelayed(watchMute, 1200)
    }

    private fun restoreSound() {
        if (!mutedByUs) return
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        mutedByUs = false
        main.removeCallbacks(watchMute)
    }

    companion object {
        /** True when the user has switched AdOchi on under Settings > Accessibility. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, AdSkipperService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
