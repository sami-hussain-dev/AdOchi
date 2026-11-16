package com.adochi.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.adochi.AdOchiApp
import com.adochi.MainActivity
import com.adochi.R
import java.lang.ref.WeakReference

/**
 * Background play for the Clean Browser. While a video plays, a small service with a play/pause
 * notification keeps AdOchi running, so YouTube keeps playing with the screen off or another app open.
 * It only starts while AdOchi is on screen (Android doesn't allow it from the background) and ends
 * when playback is paused with the app open, or with "Close".
 */
object BackgroundPlayback {

    private val main = Handler(Looper.getMainLooper())
    private var webView = WeakReference<WebView>(null)

    @Volatile var activityVisible = false
    @Volatile var running = false
        private set
    @Volatile var playing = false
        private set
    @Volatile var title = ""
        private set

    fun attach(view: WebView) {
        webView = WeakReference(view)
    }

    val enabled: Boolean get() = AdOchiApp.instance.settings.backgroundPlay

    /** Called by the page (any thread) when its video starts or stops. */
    fun onMediaState(context: Context, isPlaying: Boolean, newTitle: String) {
        playing = isPlaying
        if (newTitle.isNotBlank()) title = newTitle
        main.post {
            when {
                !enabled -> stop(context)
                isPlaying && (activityVisible || running) -> show(context)
                !isPlaying && activityVisible -> stop(context)
                !isPlaying && running -> show(context) // paused from the notification or lock screen
            }
        }
    }

    private fun show(context: Context) {
        val intent = Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_SHOW)
        try {
            ContextCompat.startForegroundService(context, intent)
            running = true
        } catch (e: Exception) {
            // Android refused to start it from the background; playback just stops as before.
        }
    }

    fun stop(context: Context) {
        if (!running) return
        running = false
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    fun togglePlay() = runJs("(function(){var v=document.querySelector('#movie_player video, video');if(!v)return;if(v.paused)v.play();else v.pause();})()")

    fun pause() = runJs("(function(){var v=document.querySelector('#movie_player video, video');if(v)v.pause();})()")

    fun next() = runJs("(function(){var b=document.querySelector('.ytp-next-button, button[aria-label^=\"Next\"], .player-controls-next');if(b)b.click();})()")

    private fun runJs(script: String) {
        main.post { webView.get()?.evaluateJavascript(script, null) }
    }
}

class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> BackgroundPlayback.togglePlay()
            ACTION_NEXT -> BackgroundPlayback.next()
            ACTION_CLOSE -> {
                BackgroundPlayback.pause()
                BackgroundPlayback.stop(this)
                return START_NOT_STICKY
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        createChannel(this)
        val open = PendingIntent.getActivity(
            this, 20, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(action: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playing = BackgroundPlayback.playing
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(BackgroundPlayback.title.ifBlank { "Playing in AdOchi" })
            .setContentText(if (playing) "Playing ad-free in the background" else "Paused")
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (playing) "Pause" else "Play", action(ACTION_TOGGLE, 21))
            .addAction(0, "Next", action(ACTION_NEXT, 22))
            .addAction(0, "Close", action(ACTION_CLOSE, 23))
            .build()
    }

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.adochi.playback.SHOW"
        const val ACTION_TOGGLE = "com.adochi.playback.TOGGLE"
        const val ACTION_NEXT = "com.adochi.playback.NEXT"
        const val ACTION_CLOSE = "com.adochi.playback.CLOSE"
        private const val NOTIFICATION_ID = 7
        private const val CHANNEL = "playback"

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Background play", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Play / pause for videos playing in the Clean Browser"
                    setShowBadge(false)
                }
            )
        }
    }
}
