package com.goback.focus.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity
import com.goback.focus.service.FocusTimerService

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Stop ongoing chronometer foreground notification
        context.stopService(Intent(context, FocusTimerService::class.java))

        val title = intent.getStringExtra("title") ?: "Focus session complete!"
        val body = intent.getStringExtra("body") ?: "Great job staying focused. Take a mindful break."

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            9102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isSchedule = intent.getStringExtra("kind") == "schedule"
        val channel = if (isSchedule) GoBackApplication.CHANNEL_SCHEDULE else GoBackApplication.CHANNEL_FOCUS_ALERTS

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(9102, notification)

        // Clear active focus session state in SharedPreferences
        context.getSharedPreferences("go_back_blocking", 0).edit()
            .putBoolean("focus_active", false)
            .remove("session_blocked_packages")
            .apply()
    }
}
