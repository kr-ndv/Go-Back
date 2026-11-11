package com.goback.focus.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity

class FocusTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIFICATION_ID)
            stopSelf()
            return START_NOT_STICKY
        }

        // Defensive: ensure notification channels are initialized before posting
        GoBackApplication.ensureNotificationChannels(this)

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Focus session in progress"
        val isStopwatch = intent?.getBooleanExtra(EXTRA_IS_STOPWATCH, false) ?: false

        // Flexible extras: support both millisecond and second keys
        val durationMs = intent?.getLongExtra(EXTRA_DURATION_MS, 0L)?.takeIf { it > 0L }
            ?: ((intent?.getLongExtra(EXTRA_DURATION_SECONDS, 0L) ?: 0L) * 1000L)
        val endAtMs = intent?.getLongExtra(EXTRA_END_AT_MS, 0L)?.takeIf { it > 0L }
            ?: (intent?.getLongExtra(EXTRA_END_AT_MILLIS, 0L) ?: 0L)

        val paused = when (action) {
            ACTION_PAUSE -> true
            ACTION_RESUME -> false
            else -> intent?.getBooleanExtra(EXTRA_PAUSED, false) ?: false
        }

        val notification = buildTimerNotification(title, isStopwatch, durationMs, endAtMs, paused)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34/35): Explicitly provide FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Guard against ForegroundServiceStartNotAllowedException on API 31+
            // or MissingForegroundServiceTypeException / SecurityException on API 34+
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun buildTimerNotification(
        title: String,
        isStopwatch: Boolean,
        durationMs: Long,
        endAtMs: Long,
        paused: Boolean
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "focus")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            8401,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = System.currentTimeMillis()
        val remainingMs = if (endAtMs > 0L) (endAtMs - now).coerceAtLeast(0L) else 0L
        val elapsedMs = if (durationMs > 0L) (durationMs - remainingMs).coerceIn(0L, durationMs) else 0L
        val progress = if (durationMs > 0L) ((elapsedMs * 100L) / durationMs).toInt().coerceIn(0, 100) else 0
        val statusText = when {
            paused -> "Session paused · Tap to return"
            isStopwatch -> "Stopwatch running · Live elapsed time"
            endAtMs > 0L -> "Focus countdown · Live remaining time"
            else -> "Focus session active"
        }

        val builder = NotificationCompat.Builder(this, GoBackApplication.CHANNEL_FOCUS_STATUS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSubText("Go Back · Focus session")
            .setStyle(NotificationCompat.BigTextStyle().bigText(statusText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setUsesChronometer(!paused)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setColor(0xFFB8F36B.toInt())
            .setContentIntent(pendingIntent)

        if (isStopwatch) {
            if (!paused) builder.setWhen(now - durationMs)
            builder.setProgress(0, 0, true)
        } else if (endAtMs > 0L) {
            builder.setProgress(100, progress, false)
            builder.setWhen(if (paused) now else endAtMs)
            if (!paused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
        }

        val stopIntent = Intent(this, FocusTimerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            8402,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPending)

        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 8401
        const val ACTION_START = "com.goback.focus.action.START_TIMER"
        const val ACTION_PAUSE = "com.goback.focus.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.goback.focus.action.RESUME_TIMER"
        const val ACTION_STOP = "com.goback.focus.action.STOP_TIMER"

        const val EXTRA_TITLE = "title"
        const val EXTRA_IS_STOPWATCH = "is_stopwatch"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_END_AT_MS = "end_at_ms"
        const val EXTRA_END_AT_MILLIS = "end_at_millis"
        const val EXTRA_PAUSED = "paused"
    }
}
