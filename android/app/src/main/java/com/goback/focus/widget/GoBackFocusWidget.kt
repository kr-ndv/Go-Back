package com.goback.focus.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.goback.focus.MainActivity
import com.goback.focus.logic.FocusEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.max

/**
 * GoBackFocusWidget
 *
 * Standalone Jetpack Glance AppWidget displaying real-time focus status,
 * today's focus progress vs. goal, gamified streak tier, upcoming exam countdown,
 * and quick-action triggers to launch the app or immediately start a 25-minute focus session.
 *
 * Data Contract:
 * - Reads from SharedPreferences "go_back_widget_data":
 *   - "is_active" (Boolean)
 *   - "remaining_seconds" (Long)
 *   - "today_focus_minutes" (Int)
 *   - "daily_goal_minutes" (Int)
 *   - "streak_days" (Int)
 *   - "streak_tier_emoji" (String)
 *   - "next_exam_name" (String)
 *   - "next_exam_days" (Int)
 * - Defensive fallback to "go_back_blocking" SharedPreferences ("focus_active", "focus_state")
 */
class GoBackFocusWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetPrefs = context.getSharedPreferences(PREFS_WIDGET_DATA, Context.MODE_PRIVATE)
        val blockingPrefs = context.getSharedPreferences(PREFS_BLOCKING, Context.MODE_PRIVATE)

        // Read active state with fallback
        val isActive = widgetPrefs.getBoolean(KEY_IS_ACTIVE, false)
            || blockingPrefs.getBoolean("focus_active", false)

        val rawRemainingSec = widgetPrefs.getLong(KEY_REMAINING_SECONDS, 0L)
        val todayMinutes = widgetPrefs.getInt(KEY_TODAY_FOCUS_MINUTES, 0)
            .takeIf { it > 0 } ?: widgetPrefs.getInt("focusMinutesToday", 0)
        val goalMinutes = widgetPrefs.getInt(KEY_DAILY_GOAL_MINUTES, 120)
            .takeIf { it > 0 } ?: widgetPrefs.getInt("dailyGoalMinutes", 120)
        val streakDays = widgetPrefs.getInt(KEY_STREAK_DAYS, 0)
            .takeIf { it > 0 } ?: widgetPrefs.getInt("streakDays", 0)
        val streakTierEmoji = widgetPrefs.getString(KEY_STREAK_TIER_EMOJI, null)
            ?: resolveDefaultTierEmoji(streakDays)
        val nextExamName = widgetPrefs.getString(KEY_NEXT_EXAM_NAME, null)
            ?: widgetPrefs.getString("nextExamTitle", "").orEmpty()
        val nextExamDays = widgetPrefs.getInt(KEY_NEXT_EXAM_DAYS, -1)
            .takeIf { it != -1 } ?: widgetPrefs.getInt("nextExamDays", -1)

        // Calculate live remaining seconds if endAt timestamp is available
        val remainingSeconds = if (isActive) {
            val stateJson = blockingPrefs.getString("focus_state", null)
            val endAt = try {
                if (!stateJson.isNullOrEmpty()) JSONObject(stateJson).optLong("endAt", 0L) else 0L
            } catch (_: Exception) { 0L }

            if (endAt > 0L) {
                max(0L, (endAt - System.currentTimeMillis()) / 1000L)
            } else if (rawRemainingSec > 0L) {
                rawRemainingSec
            } else {
                1500L // 25m fallback
            }
        } else {
            0L
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    isActive = isActive,
                    remainingSeconds = remainingSeconds,
                    todayMinutes = todayMinutes,
                    goalMinutes = goalMinutes,
                    streakDays = streakDays,
                    streakTierEmoji = streakTierEmoji,
                    nextExamName = nextExamName,
                    nextExamDays = nextExamDays
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        isActive: Boolean,
        remainingSeconds: Long,
        todayMinutes: Int,
        goalMinutes: Int,
        streakDays: Int,
        streakTierEmoji: String,
        nextExamName: String,
        nextExamDays: Int
    ) {
        val componentName = android.content.ComponentName(context, MainActivity::class.java)

        val progressFraction = (todayMinutes.toFloat() / goalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(componentName)),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            // Header Row: Brand & Status Chip
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GO BACK FOCUS",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (isActive) {
                    Text(
                        text = "⏱️ ACTIVE",
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else if (streakDays > 0) {
                    Text(
                        text = "$streakTierEmoji ${streakDays}d streak",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Body Section: Active Session Countdown vs. Daily Progress
            if (isActive) {
                val formattedTime = FocusEngine.formatFocusTime(remainingSeconds.toInt())
                Text(
                    text = formattedTime,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = "Session in progress · Tap to view",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            } else {
                Text(
                    text = if (todayMinutes > 0) "${todayMinutes}m / ${goalMinutes}m" else "Ready to Focus",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.height(4.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = progressFraction,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .cornerRadius(3.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Secondary hint: Exam countdown or Daily progress percent
                val hint = when {
                    nextExamName.isNotEmpty() && nextExamDays >= 0 ->
                        "📚 Next Exam: ${nextExamName.take(12)} (${nextExamDays}d)"
                    todayMinutes >= goalMinutes ->
                        "🎉 Daily goal reached! ($todayMinutes min)"
                    todayMinutes > 0 ->
                        "${goalMinutes - todayMinutes}m remaining to reach goal"
                    else ->
                        "Conquer distractions and do deep work"
                }

                Text(
                    text = hint,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Primary CTA Button
            val buttonText = if (isActive) "View Session" else "Quick 25m Focus"
            val buttonAction = if (isActive) {
                actionStartActivity(componentName)
            } else {
                actionRunCallback<QuickStartActionCallback>()
            }

            Button(
                text = buttonText,
                onClick = buttonAction,
                modifier = GlanceModifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = GlanceTheme.colors.primary,
                    contentColor = GlanceTheme.colors.onPrimary
                )
            )
        }
    }

    companion object {
        const val PREFS_WIDGET_DATA = "go_back_widget_data"
        const val PREFS_BLOCKING = "go_back_blocking"

        const val KEY_IS_ACTIVE = "is_active"
        const val KEY_REMAINING_SECONDS = "remaining_seconds"
        const val KEY_TODAY_FOCUS_MINUTES = "today_focus_minutes"
        const val KEY_DAILY_GOAL_MINUTES = "daily_goal_minutes"
        const val KEY_STREAK_DAYS = "streak_days"
        const val KEY_STREAK_TIER_EMOJI = "streak_tier_emoji"
        const val KEY_NEXT_EXAM_NAME = "next_exam_name"
        const val KEY_NEXT_EXAM_DAYS = "next_exam_days"

        fun resolveDefaultTierEmoji(streakDays: Int): String {
            return when {
                streakDays >= 100 -> "👑"
                streakDays >= 50 -> "💎"
                streakDays >= 30 -> "🛡️"
                streakDays >= 14 -> "⚡"
                streakDays >= 7 -> "🔥"
                streakDays >= 3 -> "✨"
                else -> "🌱"
            }
        }

        /**
         * Update all installed GoBackFocusWidget instances.
         */
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(GoBackFocusWidget::class.java)
            glanceIds.forEach { glanceId ->
                GoBackFocusWidget().update(context, glanceId)
            }
        }

        /**
         * Fire-and-forget background widget update trigger.
         */
        fun triggerUpdate(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    updateAll(context)
                } catch (_: Exception) {}
            }
        }
    }
}

/**
 * QuickStartActionCallback
 *
 * Executed when the user taps "Quick 25m Focus" on the home screen Glance widget.
 * Immediately saves active session state, launches MainActivity directly to Focus screen,
 * and updates widget instances.
 */
class QuickStartActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val now = System.currentTimeMillis()
        val durationSeconds = 25 * 60L
        val endAt = now + (durationSeconds * 1000L)
        val sessionId = UUID.randomUUID().toString()

        // Persist session to SharedPreferences
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
            put("title", "Quick Focus (25m)")
        }

        context.getSharedPreferences(GoBackFocusWidget.PREFS_BLOCKING, Context.MODE_PRIVATE).edit()
            .putBoolean("focus_active", true)
            .putString("focus_state", sessionJson.toString())
            .apply()

        context.getSharedPreferences(GoBackFocusWidget.PREFS_WIDGET_DATA, Context.MODE_PRIVATE).edit()
            .putBoolean(GoBackFocusWidget.KEY_IS_ACTIVE, true)
            .putLong(GoBackFocusWidget.KEY_REMAINING_SECONDS, durationSeconds)
            .apply()

        // Launch MainActivity into focus route
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", "focus")
            putExtra("action", "start_quick_25")
        }
        context.startActivity(launchIntent)

        // Refresh widget state
        GoBackFocusWidget().update(context, glanceId)
    }
}

/**
 * GoBackFocusWidgetReceiver
 *
 * Broadcast receiver listening for widget lifecycle events and system updates.
 */
class GoBackFocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoBackFocusWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_UPDATE_DATA) {
            GoBackFocusWidget.triggerUpdate(context)
        }
    }

    companion object {
        const val ACTION_WIDGET_UPDATE_DATA = "com.goback.focus.action.WIDGET_UPDATE_DATA"
    }
}
