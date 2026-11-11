package com.goback.focus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.service.GoBackMaintenanceWorker
import java.util.concurrent.TimeUnit

/**
 * GoBackApplication
 *
 * Standalone application entry point for the Go Back Android app.
 *
 * Responsibilities:
 * 1. Room Database lazy singleton initialization
 * 2. Idempotent initialization of all 7 required notification channels (with legacy & survey aliases)
 * 3. Enqueuing periodic maintenance background tasks via WorkManager
 * 4. Centralized notification dispatch helper methods for goal celebrations and exam alerts
 */
class GoBackApplication : Application() {

    val database: GoBackDatabase by lazy {
        GoBackDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        initCrashHandler()
        try {
            ensureNotificationChannels(this)
        } catch (e: Throwable) {
            android.util.Log.e("GoBackApplication", "Failed to initialize notification channels", e)
        }
        try {
            scheduleMaintenanceWorker()
        } catch (e: Throwable) {
            android.util.Log.e("GoBackApplication", "Failed to schedule maintenance worker", e)
        }
    }

    private fun initCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("GoBackCrash", "CRITICAL UNCAUGHT EXCEPTION in thread ${thread.name}", throwable)
                val crashFile = java.io.File(filesDir, "crash_log.txt")
                java.io.FileOutputStream(crashFile).use { fos ->
                    java.io.PrintWriter(fos).use { pw ->
                        pw.println("--- Go Back Crash Report ---")
                        pw.println("Time: ${java.util.Date()}")
                        pw.println("Thread: ${thread.name} (id: ${thread.id})")
                        pw.println("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                        throwable.printStackTrace(pw)
                        var cause = throwable.cause
                        while (cause != null) {
                            pw.println("Caused by: ${cause.javaClass.name}: ${cause.message}")
                            cause.printStackTrace(pw)
                            cause = cause.cause
                        }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("GoBackCrash", "Failed to write crash log", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun scheduleMaintenanceWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "go_back_maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (_: Exception) {
            // Guard against WorkManager initialization failures on restricted OEM firmware
        }
    }

    companion object {
        // =========================================================================
        // Notification Channel Constants (7 Core Channels + Compatibility Aliases)
        // =========================================================================

        // 1. Focus Timer Status (Silent ongoing chronometer notification)
        const val CHANNEL_FOCUS_STATUS = "go-back-focus-status-v2"

        // 2. Focus Session Alerts (High importance audible alerts on session end)
        const val CHANNEL_FOCUS_ALERTS = "go-back-focus-alerts-v2"
        const val CHANNEL_ALERTS = "go-back-alerts-v2"

        // 3. Daily Focus Goal Completed (High importance celebratory notification)
        const val CHANNEL_GOAL_COMPLETED = "go-back-goal-completed-v1"
        const val CHANNEL_GOAL_COMPLETED_V2 = "go-back-goal-completed"

        // 4. Exam Timetable Reminders (High importance 7d, 1d, and exam-day alerts)
        const val CHANNEL_EXAMS = "go-back-exams-v1"
        const val CHANNEL_EXAM_REMINDERS = "go-back-exam-reminders"

        // 5. Scheduled Focus Block Alerts (High importance pre-session alerts)
        const val CHANNEL_SCHEDULE = "go-back-schedule-v3"
        const val CHANNEL_SCHEDULE_ALERTS = "go-back-schedule-alerts"

        // 6. Maintenance & Release Updates (Low/Default importance GitHub update checks)
        const val CHANNEL_UPDATES = "go-back-updates-native-v1"
        const val CHANNEL_MAINTENANCE = "go-back-maintenance"

        // 7. App Screen-Time Limits (High importance usage threshold warnings)
        const val CHANNEL_USAGE_LIMITS = "screen-time-limits-native-v2"
        const val CHANNEL_APP_LIMITS = "go-back-app-limits"

        /**
         * Creates a dedicated notification channel for the selected finish sound.
         * Android locks a channel's sound after creation, so each sound choice gets its own ID.
         */
        fun ensureFocusAlarmChannel(context: Context, soundKey: String): String {
            val resourceName = soundKey.lowercase().removeSuffix(".wav").replace(Regex("[^a-z0-9_-]"), "")
            val safeKey = resourceName.ifBlank { "default" }
            val channelId = "go-back-focus-alerts-sound-$safeKey"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val manager = context.getSystemService(NotificationManager::class.java)
                        ?: return channelId
                    val soundUri = if (safeKey == "default") {
                        runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()
                    } else {
                        Uri.parse("android.resource://${context.packageName}/raw/$safeKey")
                    }
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    val label = when (safeKey) {
                        "focus_bell" -> "Soft bell"
                        "focus_chime" -> "Warm chime"
                        "focus_digital" -> "Digital alert"
                        else -> "System default"
                    }
                    val channel = NotificationChannel(
                        channelId,
                        "Focus finish · $label",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Sound used when a focus timer or Pomodoro phase finishes"
                        enableVibration(true)
                        soundUri?.let { setSound(it, attributes) }
                    }
                    manager.createNotificationChannel(channel)
                } catch (e: Throwable) {
                    android.util.Log.e("GoBackApplication", "Failed to create focus alarm channel: $channelId", e)
                }
            }
            return channelId
        }

        // Notification IDs
        const val NOTIFICATION_ID_GOAL_CELEBRATION = 9103
        const val NOTIFICATION_ID_EXAM_BASE = 8200
        const val NOTIFICATION_ID_SCHEDULE_BASE = 9200
        const val NOTIFICATION_ID_APP_LIMIT_BASE = 7300

        /**
         * Idempotent and defensive creation of all required notification channels.
         * Safe to call repeatedly from Application.onCreate() or service/receiver entry points.
         */
        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val manager = context.getSystemService(NotificationManager::class.java) ?: return

                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    val soundUri = runCatching { RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) }.getOrNull()

                    val channels = mutableListOf<NotificationChannel>()

                // 1. Focus Timer status (ongoing silent chronometer)
                channels += NotificationChannel(
                    CHANNEL_FOCUS_STATUS,
                    "Focus Session Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live progress during an ongoing focus countdown or stopwatch"
                    setSound(null, null)
                    enableVibration(false)
                }

                // 2. Focus Session Alerts (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_FOCUS_ALERTS,
                    "Focus Session Completions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when a focus session or break ends"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }
                channels += NotificationChannel(
                    CHANNEL_ALERTS,
                    "Focus Session Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when a focus session or break ends"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 3. Goal Completed celebrations (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_GOAL_COMPLETED,
                    "Daily Goal Celebrations",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Celebration notification when achieving your daily focus goal"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }
                channels += NotificationChannel(
                    CHANNEL_GOAL_COMPLETED_V2,
                    "Daily Goal Celebrations",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Celebration notification when achieving your daily focus goal"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 4. Exam Timetable Reminders (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_EXAMS,
                    "Exam Timetable Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible reminders for upcoming exams 7 days, 1 day, and exam day"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }
                channels += NotificationChannel(
                    CHANNEL_EXAM_REMINDERS,
                    "Exam Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible reminders for upcoming exams 7 days, 1 day, and exam day"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 5. Schedule Alerts (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_SCHEDULE,
                    "Focus Schedule Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible notification when scheduled focus blocks start or are approaching"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }
                channels += NotificationChannel(
                    CHANNEL_SCHEDULE_ALERTS,
                    "Focus Schedule Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible notification when scheduled focus blocks start or are approaching"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 6. Maintenance & Updates (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_UPDATES,
                    "Go Back Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifies when a new stable release of Go Back is available on GitHub"
                }
                channels += NotificationChannel(
                    CHANNEL_MAINTENANCE,
                    "System Maintenance",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Periodic background maintenance and update notifications"
                }

                // 7. App Screen-Time Limits (primary and alias)
                channels += NotificationChannel(
                    CHANNEL_USAGE_LIMITS,
                    "Screen-Time Limit Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when apps approach or exceed daily usage limits"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }
                channels += NotificationChannel(
                    CHANNEL_APP_LIMITS,
                    "App Usage Limits",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when apps approach or exceed daily usage limits"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                manager.createNotificationChannels(channels)
            } catch (e: Throwable) {
                android.util.Log.e("GoBackApplication", "Failed to create notification channels", e)
            }
        }
    }

        // =========================================================================
        // Centralized Notification Dispatch Helpers
        // =========================================================================

        /**
         * Dispatches a high-importance celebration notification when the user crosses
         * their configured daily focus goal.
         */
        fun postGoalCelebrationNotification(context: Context, goalMinutes: Int) {
            ensureNotificationChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "focus")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_GOAL_CELEBRATION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val goalMessage = "You completed your ${goalMinutes}-minute daily focus target. Your effort is saved locally in Session History. Take a mindful break, then decide what deserves your attention next."
            val notification = NotificationCompat.Builder(context, CHANNEL_GOAL_COMPLETED)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🎉 Daily Focus Goal Completed")
                .setContentText("Your ${goalMinutes}-minute focus goal is complete.")
                .setSubText("Go Back · Daily progress")
                .setStyle(NotificationCompat.BigTextStyle().bigText(goalMessage))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(NOTIFICATION_ID_GOAL_CELEBRATION, notification)
        }

        /**
         * Dispatches a 3-stage exam reminder notification (7 days before, 1 day before, or morning of exam).
         */
        fun postExamReminderNotification(
            context: Context,
            examTitle: String,
            daysRemaining: Int,
            details: String = "",
            examId: String = ""
        ) {
            ensureNotificationChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val (title, body) = when (daysRemaining) {
                7 -> "📅 Upcoming Exam in 7 Days: $examTitle" to
                        (if (details.isNotBlank()) "$details · One week remaining to prepare." else "One week remaining. Keep up your daily focus sessions.")
                1 -> "⏰ Tomorrow: $examTitle Exam!" to
                        (if (details.isNotBlank()) "$details · Final review day." else "Final review day. Rest well tonight and stay confident.")
                0 -> "🎯 Today is Exam Day: $examTitle!" to
                        (if (details.isNotBlank()) "$details · You are ready." else "You are prepared. Take a deep breath and do your best.")
                else -> "📅 Exam in $daysRemaining days: $examTitle" to
                        (if (details.isNotBlank()) details else "Keep up your regular study schedule.")
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "exams")
            }
            val notifId = NOTIFICATION_ID_EXAM_BASE + (examId.hashCode() and 0x7FFF)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_EXAMS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(notifId, notification)
        }

        /**
         * Dispatches an alert when a scheduled focus block is starting or approaching.
         */
        fun postScheduleAlertNotification(
            context: Context,
            scheduleTitle: String,
            minutesUntilStart: Int,
            scheduleId: Long = 0L
        ) {
            ensureNotificationChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val (title, body) = if (minutesUntilStart > 0) {
                "⏳ Focus Block Starting Soon" to "$scheduleTitle begins in $minutesUntilStart minutes. Prepare your study space."
            } else {
                "🎯 Time to Focus: $scheduleTitle" to "Your scheduled focus block has started. Tap to begin your timer."
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("route", "focus")
            }
            val notifId = NOTIFICATION_ID_SCHEDULE_BASE + (scheduleId.toInt() and 0x7FFF)
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_SCHEDULE)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setSubText("Go Back · Focus schedule")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            if (minutesUntilStart > 0) {
                val startAt = System.currentTimeMillis() + minutesUntilStart * 60_000L
                notificationBuilder
                    .setWhen(startAt)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            val notification = notificationBuilder.build()

            manager.notify(notifId, notification)
        }
    }
}
