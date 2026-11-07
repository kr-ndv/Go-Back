package com.goback.focus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.goback.focus.service.GoBackMaintenanceWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule maintenance worker on boot
            val workRequest = PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>(
                30, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "go_back_maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
