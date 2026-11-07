package com.goback.focus.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.goback.focus.MainActivity
import com.goback.focus.logic.FocusEngine
import com.goback.focus.widget.GoBackFocusWidget
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.max

/**
 * FocusTileService
 *
 * Android Quick Settings Tile (TileService) enabling users to view and toggle
 * active focus sessions directly from the system Quick Settings shade.
 *
 * Features:
 * - Reflects active focus state (STATE_ACTIVE vs. STATE_INACTIVE)
 * - Dynamic label (session title / tag) and subtitle (remaining countdown time or stopwatch status)
 * - Tapping when inactive starts a 25-minute quick focus session and collapses QS
 * - Tapping when active opens the app directly into the active session management screen
 * - Fully compatible with Android 14+ (API 34/35) PendingIntent collapse requirement
 */
class FocusTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val blockingPrefs = getSharedPreferences(PREFS_BLOCKING, Context.MODE_PRIVATE)
        val widgetPrefs = getSharedPreferences(PREFS_WIDGET_DATA, Context.MODE_PRIVATE)

        val isActive = blockingPrefs.getBoolean("focus_active", false)
            || widgetPrefs.getBoolean("is_active", false)

        if (isActive) {
            // Active session: Open app to focus screen to view, pause, or end session
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "focus")
                putExtra("action", "manage_focus")
            }
            safeStartActivityAndCollapse(intent)
        } else {
            // Inactive session: Start quick 25m focus session and open app
            startQuickFocusSession()

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "focus")
                putExtra("action", "quick_25_started")
            }
            safeStartActivityAndCollapse(intent)
        }
    }

    private fun startQuickFocusSession() {
        val now = System.currentTimeMillis()
        val durationSeconds = 25 * 60L
        val endAt = now + (durationSeconds * 1000L)
        val sessionId = UUID.randomUUID().toString()

        val sessionJson = JSONObject().apply {
            put("sessionId", sessionId)
            put("mode", "timer")
            put("sessionType", "focus")
            put("selectedMinutes", 25)
            put("remainingSeconds", durationSeconds)
            put("elapsedSeconds", 0)
            put("startedAt", Instant.now().toString())
            put("endAt", endAt)
            put("active", true)
            put("title", "Quick Focus")
        }

        // 1. Update SharedPreferences
        getSharedPreferences(PREFS_BLOCKING, Context.MODE_PRIVATE).edit()
            .putBoolean("focus_active", true)
            .putString("focus_state", sessionJson.toString())
            .apply()

        getSharedPreferences(PREFS_WIDGET_DATA, Context.MODE_PRIVATE).edit()
            .putBoolean("is_active", true)
            .putLong("remaining_seconds", durationSeconds)
            .apply()

        // 2. Start Foreground Service
        val serviceIntent = Intent(this, FocusTimerService::class.java).apply {
            action = FocusTimerService.ACTION_START
            putExtra(FocusTimerService.EXTRA_TITLE, "Quick Focus")
            putExtra(FocusTimerService.EXTRA_IS_STOPWATCH, false)
            putExtra(FocusTimerService.EXTRA_DURATION_SECONDS, durationSeconds)
            putExtra(FocusTimerService.EXTRA_END_AT_MILLIS, endAt)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: Exception) {}

        // 3. Immediately reflect active state in Tile
        updateTileState()

        // 4. Trigger Widget Update
        GoBackFocusWidget.triggerUpdate(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val blockingPrefs = getSharedPreferences(PREFS_BLOCKING, Context.MODE_PRIVATE)
        val widgetPrefs = getSharedPreferences(PREFS_WIDGET_DATA, Context.MODE_PRIVATE)

        val isActive = blockingPrefs.getBoolean("focus_active", false)
            || widgetPrefs.getBoolean("is_active", false)
        val stateJson = blockingPrefs.getString("focus_state", null)

        var sessionTitle = "Go Back Focus"
        var mode = "timer"
        var remainingSeconds = widgetPrefs.getLong("remaining_seconds", 0L)
        var endAtMs = 0L

        if (!stateJson.isNullOrEmpty()) {
            try {
                val json = JSONObject(stateJson)
                sessionTitle = json.optString("title", "Focus Session").ifBlank { "Focus Session" }
                mode = json.optString("mode", "timer")
                endAtMs = json.optLong("endAt", 0L)
                val jsonRemaining = json.optLong("remainingSeconds", 0L)
                if (jsonRemaining > 0) remainingSeconds = jsonRemaining
            } catch (_: Exception) {}
        }

        if (isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = sessionTitle

            val liveRemaining = if (endAtMs > 0L) {
                max(0L, (endAtMs - System.currentTimeMillis()) / 1000L)
            } else {
                remainingSeconds
            }

            val subtitleText = when (mode) {
                "stopwatch" -> "Stopwatch active"
                else -> {
                    val formatted = FocusEngine.formatFocusTime(liveRemaining.toInt())
                    "$formatted remaining"
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = subtitleText
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = subtitleText
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Go Back Focus"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to start (25m)"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.stateDescription = "Inactive · Tap to start 25m session"
            }
        }

        tile.updateTile()
    }

    private fun safeStartActivityAndCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                9401,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val PREFS_BLOCKING = "go_back_blocking"
        const val PREFS_WIDGET_DATA = "go_back_widget_data"

        /**
         * Requests the system to refresh the Quick Settings tile when focus session
         * state changes within the application or background services.
         */
        fun requestTileUpdate(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    TileService.requestListeningState(
                        context,
                        ComponentName(context, FocusTileService::class.java)
                    )
                }
            } catch (_: Exception) {}
        }
    }
}
