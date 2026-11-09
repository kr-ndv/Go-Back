package com.goback.focus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.goback.focus.logic.AlarmScheduler
import com.goback.focus.service.GoBackMaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 1. Re-schedule maintenance worker on boot
            try {
                val workRequest = PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>(
                    30, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "go_back_maintenance",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {}

            // 2. Reschedule focus schedules and exam reminder alarms
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AlarmScheduler.rescheduleAllSchedules(context)
                    AlarmScheduler.rescheduleAllExams(context)
                } catch (_: Exception) {
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
