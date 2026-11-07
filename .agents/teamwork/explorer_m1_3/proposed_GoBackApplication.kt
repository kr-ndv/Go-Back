package com.goback.focus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.service.GoBackMaintenanceWorker
import java.util.concurrent.TimeUnit

class GoBackApplication : Application() {

    val database: GoBackDatabase by lazy {
        GoBackDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
        scheduleMaintenanceWorker()
    }

    private fun scheduleMaintenanceWorker() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "go_back_maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (_: Exception) {
            // Protect application startup against WorkManager initialization failures on custom/restricted devices
        }
    }

    companion object {
        const val CHANNEL_FOCUS_STATUS = "go-back-focus-status-v2"
        const val CHANNEL_FOCUS_ALERTS = "go-back-focus-alerts-v2"
        const val CHANNEL_SCHEDULE = "go-back-schedule-v3"
        const val CHANNEL_USAGE_LIMITS = "screen-time-limits-native-v2"
        const val CHANNEL_EXAMS = "go-back-exams-v1"
        const val CHANNEL_GOAL_COMPLETED = "go-back-goal-completed-v1"
        const val CHANNEL_UPDATES = "go-back-updates-native-v1"

        /**
         * Idempotent and defensive creation of all 7 notification channels.
         * Safe to call from Application.onCreate() or any service/receiver entry point.
         */
        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                // 1. Focus Timer status (ongoing silent chronometer)
                val focusChannel = NotificationChannel(
                    CHANNEL_FOCUS_STATUS,
                    "Focus Session Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows live progress during an ongoing focus countdown or stopwatch"
                    setSound(null, null)
                    enableVibration(false)
                }

                // 2. Focus Session alert (completed timers / breaks)
                val alertChannel = NotificationChannel(
                    CHANNEL_FOCUS_ALERTS,
                    "Focus Session Completions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifies when a focus session or break ends"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 3. Focus schedule reminders with audible notification
                val scheduleChannel = NotificationChannel(
                    CHANNEL_SCHEDULE,
                    "Focus Schedule Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible notification when scheduled focus blocks start or are approaching"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 4. Screen-time limits
                val limitsChannel = NotificationChannel(
                    CHANNEL_USAGE_LIMITS,
                    "Screen-Time Limit Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when apps approach or exceed daily usage limits"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 5. Exam timetable reminders
                val examsChannel = NotificationChannel(
                    CHANNEL_EXAMS,
                    "Exam Timetable Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Audible reminders for upcoming exams 7 days, 1 day, and exam day"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 6. Focus goal celebrations
                val goalChannel = NotificationChannel(
                    CHANNEL_GOAL_COMPLETED,
                    "Daily Goal Celebrations",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Celebration notification when achieving your daily focus goal"
                    enableVibration(true)
                    soundUri?.let { setSound(it, audioAttributes) }
                }

                // 7. Go Back Updates
                val updatesChannel = NotificationChannel(
                    CHANNEL_UPDATES,
                    "Go Back Updates",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notifies when a new stable release of Go Back is available on GitHub"
                }

                manager.createNotificationChannels(
                    listOf(
                        focusChannel,
                        alertChannel,
                        scheduleChannel,
                        limitsChannel,
                        examsChannel,
                        goalChannel,
                        updatesChannel
                    )
                )
            }
        }
    }
}
