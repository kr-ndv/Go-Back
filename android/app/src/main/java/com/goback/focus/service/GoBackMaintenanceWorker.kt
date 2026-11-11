package com.goback.focus.service

import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goback.focus.BuildConfig
import com.goback.focus.GoBackApplication
import com.goback.focus.MainActivity
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.logic.ScheduleEvaluationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class GoBackMaintenanceWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            checkStableRelease()
        } catch (e: Exception) {
            Log.e(TAG, "checkStableRelease encountered an unexpected error", e)
        }

        try {
            checkUsageLimits()
        } catch (e: Exception) {
            Log.e(TAG, "checkUsageLimits encountered an unexpected error", e)
        }

        try {
            val db = GoBackDatabase.getDatabase(context)
            val schedules = db.focusScheduleDao().getAll().first()
            ScheduleEvaluationEngine.evaluateAndSync(context, schedules)
        } catch (e: Exception) {
            Log.e(TAG, "evaluateAndSync schedules encountered an unexpected error", e)
        }

        Result.success()
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            info != null && info.isConnected
        }
    }

    private fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private suspend fun checkUsageLimits() {
        val db = GoBackDatabase.getDatabase(context)
        val limits = db.usageLimitDao().getEnabledLimitsSync()
        if (limits.isEmpty()) {
            context.getSharedPreferences("go_back_usage_limits", Context.MODE_PRIVATE)
                .edit().putString("limits", "[]").apply()
            context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
                .edit().putStringSet("usage_limit_blocked_packages", emptySet()).apply()
            return
        }
        if (!hasUsageAccess()) return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val totals = mutableMapOf<String, Long>()
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)?.forEach { stat ->
            if (stat.totalTimeInForeground > 0L) {
                totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
            }
        }

        val prefs = context.getSharedPreferences("go_back_usage_limits", 0)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val sent = try { JSONObject(prefs.getString("sent_$day", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        var changed = false
        val exceededPackages = mutableSetOf<String>()

        val limitsJson = JSONArray()
        limits.forEach { limit ->
            limitsJson.put(JSONObject().apply {
                put("packageName", limit.packageName)
                put("appName", limit.appName)
                put("limitMinutes", limit.limitMinutes)
                put("warningPercent", limit.warningPercent)
            })
        }
        context.getSharedPreferences("go_back_usage_limits", Context.MODE_PRIVATE)
            .edit()
            .putString("limits", limitsJson.toString())
            .apply()

        limits.forEachIndexed { index, limit ->
            val usedMinutes = ((totals[limit.packageName] ?: 0L) / 60000L).toInt()
            val state = sent.optInt(limit.packageName, 0)
            val warningAt = (limit.limitMinutes * limit.warningPercent / 100).coerceAtLeast(1)
            val nextState = if (usedMinutes >= limit.limitMinutes) 2 else if (usedMinutes >= warningAt) 1 else 0

            if (usedMinutes >= limit.limitMinutes) {
                exceededPackages.add(limit.packageName.lowercase(Locale.ROOT))
            }

            if (nextState > state) {
                val title = if (nextState == 2) {
                    "${limit.appName} reached its daily limit"
                } else {
                    "${limit.appName} is nearing its daily limit"
                }
                val text = "$usedMinutes of ${limit.limitMinutes} minutes used today."

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pending = PendingIntent.getActivity(
                    context,
                    7300 + index,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, GoBackApplication.CHANNEL_USAGE_LIMITS)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()

                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(7300 + index, notification)

                sent.put(limit.packageName, nextState)
                changed = true
            }
        }

        if (changed) {
            prefs.edit().putString("sent_$day", sent.toString()).apply()
        }

        val blockingPrefs = context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)
        val currentBlocked = blockingPrefs.getStringSet("usage_limit_blocked_packages", emptySet()) ?: emptySet()
        if (currentBlocked != exceededPackages) {
            blockingPrefs.edit()
                .putStringSet("usage_limit_blocked_packages", exceededPackages)
                .apply()
        }
    }

    private fun checkStableRelease() {
        val prefs = context.getSharedPreferences("go_back_maintenance", 0)
        if (!prefs.getBoolean("updates_enabled", true)) return
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_release_check_at", 0L)
        if (now - lastCheck < 6L * 60L * 60L * 1000L) return

        if (!isNetworkConnected()) {
            Log.d(TAG, "Skipping release check: no active internet connection")
            return
        }

        try {
            val connection = URL("https://api.github.com/repos/kr-ndv/Go-Back/releases").openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Go-Back-Android-Native")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                Log.w(TAG, "GitHub releases API returned non-success response code: $responseCode")
                connection.disconnect()
                return
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            // Update timestamp on successful communication
            prefs.edit().putLong("last_release_check_at", now).apply()

            val releases = JSONArray(body)
            var tag = ""
            var url = ""
            for (index in 0 until releases.length()) {
                val item = releases.optJSONObject(index) ?: continue
                if (item.optBoolean("draft", false) || item.optBoolean("prerelease", false)) continue
                val candidateTag = item.optString("tag_name")
                val candidateUrl = item.optString("html_url")
                if (versionParts(candidateTag) == null || !candidateUrl.startsWith("https://github.com/kr-ndv/Go-Back/releases/")) continue
                tag = candidateTag.removePrefix("v").removePrefix("V")
                url = candidateUrl
                break
            }

            if (tag.isEmpty() || !newer(tag, BuildConfig.VERSION_NAME) || prefs.getString("last_notified_version", "") == tag) return

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            val pending = PendingIntent.getActivity(
                context,
                7201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, GoBackApplication.CHANNEL_UPDATES)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("A Go Back update is available")
                .setContentText("$tag is available. Tap to view the official release.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()

            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(7201, notification)

            prefs.edit().putString("last_notified_version", tag).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query GitHub releases API: ${e.message}", e)
        }
    }

    private fun versionParts(value: String): List<Int>? {
        val clean = value.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        if (!clean.matches(Regex("""\d+(\.\d+){0,3}"""))) return null
        val parts = clean.split('.').map { it.toIntOrNull() ?: return null }.toMutableList()
        while (parts.size < 4) parts.add(0)
        return parts
    }

    private fun newer(candidate: String, installed: String): Boolean {
        val left = versionParts(candidate) ?: return false
        val right = versionParts(installed) ?: return false
        for (index in 0 until 4) {
            if (left[index] != right[index]) return left[index] > right[index]
        }
        return false
    }

    companion object {
        private const val TAG = "GoBackMaintenanceWorker"
    }
}
