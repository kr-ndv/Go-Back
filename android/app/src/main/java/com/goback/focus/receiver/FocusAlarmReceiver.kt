package com.goback.focus.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.FocusSessionEntity
import com.goback.focus.logic.AlarmScheduler
import com.goback.focus.logic.FocusEngine
import com.goback.focus.service.FocusTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.max

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: "timer"
        val title = intent.getStringExtra("title") ?: "Focus session complete!"
        val body = intent.getStringExtra("body") ?: "Great job staying focused. Take a mindful break."
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        when (kind) {
            "exam" -> {
                val examId = intent.getStringExtra("exam_id") ?: ""
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("route", "exams")
                }
                val notifId = 8200 + (examId.hashCode() and 0x7FFF)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, GoBackApplication.CHANNEL_EXAMS)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSubText("Go Back · Exam reminder")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(pendingIntent)
                    .build()

                notifManager.notify(notifId, notification)
            }

            "schedule" -> {
                val scheduleId = intent.getStringExtra("schedule_id") ?: ""
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("route", "focus")
                }
                val notifId = 9200 + (scheduleId.hashCode() and 0x7FFF)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, GoBackApplication.CHANNEL_SCHEDULE)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSubText("Go Back · Focus schedule")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setContentIntent(pendingIntent)
                    .build()

                notifManager.notify(notifId, notification)

                // Reschedule the next occurrence for this recurring schedule
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = GoBackDatabase.getDatabase(context)
                        val all = db.focusScheduleDao().getAll().firstOrNull() ?: emptyList()
                        val matching = all.firstOrNull { it.id == scheduleId }
                        if (matching != null) {
                            AlarmScheduler.scheduleNextOccurrence(context, matching)
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { pendingResult.finish() } catch (_: Exception) {}
                    }
                }
            }

            else -> {
                // Timer completion alarm
                // 1. Stop ongoing chronometer foreground notification
                context.stopService(Intent(context, FocusTimerService::class.java))

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("route", "focus")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    9102,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val selectedSound = context.getSharedPreferences("go_back_preferences", Context.MODE_PRIVATE)
                    .getString("focus_alarm_sound", "default") ?: "default"
                val channel = GoBackApplication.ensureFocusAlarmChannel(context, selectedSound)

                val notification = NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setSubText("Go Back · Session complete")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$body Your session has been saved to your local history. Take a short reset before your next task."))
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()

                notifManager.notify(9102, notification)

                // 2. Persist session to Room database and check goal celebration
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val prefs = context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
                        val stateJson = prefs.getString("focus_state", null)

                        if (!stateJson.isNullOrEmpty()) {
                            val json = JSONObject(stateJson)
                            val sId = json.optString("sessionId", UUID.randomUUID().toString())
                            val sStart = json.optString("startedAt", Instant.now().toString())
                            val sMinutes = json.optInt("selectedMinutes", 25)
                            val sMode = json.optString("mode", "timer")
                            val sType = json.optString("sessionType", "focus")
                            val sTag = json.optString("tag", "")
                            val sNotes = json.optString("notes", "")

                            val duration = max(1, sMinutes * 60)
                            val db = GoBackDatabase.getDatabase(context)

                            val entity = FocusSessionEntity(
                                id = sId,
                                startedAt = sStart,
                                durationSeconds = duration,
                                completed = 1,
                                sessionType = sType,
                                mode = sMode,
                                tag = sTag.trim().take(40),
                                notes = sNotes.trim().take(300)
                            )
                            db.focusSessionDao().insert(entity)

                            // Check daily focus goal
                            if (sType == "focus") {
                                val rawGoal = db.settingDao().getValue("focus.dailyGoalMinutes")?.toIntOrNull() ?: 120
                                val goalMins = rawGoal.coerceIn(15, 480)
                                val goalSeconds = goalMins * 60

                                val todaySessions = db.focusSessionDao().getTodaySessions().firstOrNull() ?: emptyList()
                                val priorSecondsToday = todaySessions
                                    .filter { it.sessionType == "focus" && it.id != sId }
                                    .sumOf { max(0, it.durationSeconds) }
                                val totalSecondsToday = priorSecondsToday + duration

                                if (FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday, totalSecondsToday, goalSeconds)) {
                                    GoBackApplication.postGoalCelebrationNotification(context, goalMins)
                                }
                            }
                        }

                        // Clear active focus session state in SharedPreferences
                        prefs.edit()
                            .putBoolean("focus_active", false)
                            .remove("focus_state")
                            .remove("session_blocked_packages")
                            .apply()
                    } catch (_: Exception) {
                    } finally {
                        try { pendingResult.finish() } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
