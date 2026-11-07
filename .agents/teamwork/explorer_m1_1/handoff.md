# Handoff Report: AndroidManifest Permissions & GoBackMaintenanceWorker Stability

## 1. Observation
1. **Missing `INTERNET` and `ACCESS_NETWORK_STATE` Permissions in AndroidManifest.xml**:
   - File: `android/app/src/main/AndroidManifest.xml:5-22`
   ```xml
   <!-- Screen-time tracking & focus limits -->
   <uses-permission
       android:name="android.permission.PACKAGE_USAGE_STATS"
       tools:ignore="ProtectedPermissions" />

   <!-- Android 13+ Notification permission -->
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

   <!-- Android 12+ Exact Alarms for focus timer completions -->
   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
   <uses-permission android:name="android.permission.USE_EXACT_ALARM" />

   <!-- Foreground service for continuous Doze-resistant timers -->
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
   <uses-permission android:name="android.permission.WAKE_LOCK" />
   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
   ```
   Neither `<uses-permission android:name="android.permission.INTERNET" />` nor `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` is present.

2. **Network Call in GoBackMaintenanceWorker.kt**:
   - File: `android/app/src/main/java/com/goback/focus/service/GoBackMaintenanceWorker.kt:121-179`
   ```kotlin
   private fun checkStableRelease() {
       val prefs = context.getSharedPreferences("go_back_maintenance", 0)
       if (!prefs.getBoolean("updates_enabled", true)) return
       val now = System.currentTimeMillis()
       val lastCheck = prefs.getLong("last_release_check_at", 0L)
       if (now - lastCheck < 6L * 60L * 60L * 1000L) return
       prefs.edit().putLong("last_release_check_at", now).apply()

       try {
           val connection = URL("https://api.github.com/repos/kr-ndv/Go-Back/releases").openConnection() as HttpURLConnection
           connection.connectTimeout = 8000
           connection.readTimeout = 8000
           connection.setRequestProperty("Accept", "application/vnd.github+json")
           connection.setRequestProperty("User-Agent", "Go-Back-Android-Native")
           if (connection.responseCode !in 200..299) {
               connection.disconnect()
               return
           }
           val body = connection.inputStream.bufferedReader().use { it.readText() }
           connection.disconnect()
           ...
       } catch (_: Exception) {}
   }
   ```
   - At line 127, `prefs.edit().putLong("last_release_check_at", now).apply()` writes the timestamp **before** the network call begins.
   - At line 130, `URL.openConnection()` attempts to establish an HTTPS socket connection to `api.github.com`.
   - At line 178, `catch (_: Exception) {}` completely suppresses the exception with no logging.

3. **CoroutineWorker Execution & Threading**:
   - File: `android/app/src/main/java/com/goback/focus/service/GoBackMaintenanceWorker.kt:32-36`
   ```kotlin
   override suspend fun doWork(): Result {
       checkStableRelease()
       checkUsageLimits()
       return Result.success()
   }
   ```
   - `checkStableRelease()` is a synchronous, blocking network call executed directly on `CoroutineWorker`'s default dispatcher (`Dispatchers.Default`), risking thread starvation.
   - `checkUsageLimits()` line 50 executes `val limits = db.usageLimitDao().getEnabledLimitsSync()`, a synchronous Room database disk query, also executed without `Dispatchers.IO`.

4. **WorkManager Scheduling**:
   - File: `android/app/src/main/java/com/goback/focus/GoBackApplication.kt:118-128` and `receiver/BootReceiver.kt:15-25`
   ```kotlin
   val workRequest = PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>(
       30, TimeUnit.MINUTES
   ).build()
   ```
   - The worker runs unconstrained every 30 minutes.

5. **Android 14/15 Foreground Service Type**:
   - File: `android/app/src/main/AndroidManifest.xml:61-68`
   ```xml
   <service
       android:name=".service.FocusTimerService"
       android:exported="false"
       android:foregroundServiceType="specialUse">
       <property
           android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
           android:value="Focus session timer and countdown tracking" />
   </service>
   ```
   - File: `android/app/src/main/java/com/goback/focus/service/FocusTimerService.kt:32`
   ```kotlin
   startForeground(NOTIFICATION_ID, notification)
   ```
   - `FocusTimerService` calls the 2-parameter `startForeground()` rather than explicitly specifying `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for Android 14+ (API 34/35).

---

## 2. Logic Chain
1. **From Observation 1 to Socket Failure**:
   On the Android platform, socket operations (`java.net.Socket`, `HttpURLConnection`) require `android.permission.INTERNET`. When an application without this permission attempts to open an HTTPS connection to `https://api.github.com`, the Android security manager and kernel GID enforcement reject the socket creation with `java.lang.SecurityException: Permission denied (missing INTERNET permission)`.

2. **From Observation 2 to Broken Release Checker**:
   Because `INTERNET` is missing:
   - Every execution of `checkStableRelease()` immediately throws `SecurityException`.
   - Because `last_release_check_at` was committed at line 127 prior to the network call, the worker considers the check attempted and enforces the 6-hour cooldown (`now - lastCheck < 6L * 60L * 60L * 1000L`).
   - The empty `catch (_: Exception) {}` hides this error from logcat, preventing diagnostics and leaving GitHub release notifications non-functional.

3. **From Observation 1 and 4 to WorkManager Constraint Strategy**:
   - `GoBackMaintenanceWorker` handles two distinct functions:
     1. Local offline screen-time limit checks (`checkUsageLimits()`), which must run every 30 minutes even when the device is disconnected or in airplane mode.
     2. Online GitHub release checks (`checkStableRelease()`), which only require execution once every 6+ hours when connected.
   - If a WorkManager `NetworkType.CONNECTED` constraint is added to `PeriodicWorkRequestBuilder<GoBackMaintenanceWorker>`, WorkManager will halt the worker whenever the user is offline, completely breaking offline daily screen-time limit enforcement.
   - Therefore, the worker itself must remain **unconstrained** in WorkManager, but `checkStableRelease()` must check network connectivity via `ConnectivityManager` (requiring `android.permission.ACCESS_NETWORK_STATE`) before attempting network operations.

4. **From Observation 3 to Dispatcher Starvation**:
   Running synchronous HTTP I/O and synchronous Room SQLite queries (`getEnabledLimitsSync()`) inside `doWork()` on `Dispatchers.Default` blocks worker threads. Wrapping worker tasks in `withContext(Dispatchers.IO)` offloads blocking I/O to the optimized Kotlin I/O thread pool.

5. **From Observation 5 to Android 14/15 FGS Compliance**:
   With `compileSdk = 35` and `targetSdk = 35`, calling `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` ensures full compliance with Android 14/15 foreground service strict typing rules.

---

## 3. Caveats
1. **GitHub API Rate Limiting**: The public GitHub releases API endpoint (`https://api.github.com/repos/kr-ndv/Go-Back/releases`) has an unauthenticated rate limit of 60 requests per hour per IP. GoBack's 6-hour interval per device (`6L * 60L * 60L * 1000L`) is well within this limit, but users on shared NAT networks could occasionally receive HTTP 403. Handling non-2xx codes cleanly is essential.
2. **Runtime Notification Permission on Android 13+**: Even with network fixes, notification delivery requires `POST_NOTIFICATIONS` runtime permission. `MainActivity.kt` currently lacks a runtime permission prompt; if the user has not granted it, `NotificationManager.notify()` will be dropped by the OS silently.
3. **No Other Background Workers**: The codebase contains only one worker (`GoBackMaintenanceWorker`). No other workers or background network services exist.

---

## 4. Conclusion & Actionable Fix Recommendations

### Recommendation 1: Update `android/app/src/main/AndroidManifest.xml`
Add `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`.

#### Code Snippet (Diff)
```xml
--- a/android/app/src/main/AndroidManifest.xml
+++ b/android/app/src/main/AndroidManifest.xml
@@ -10,6 +10,10 @@
     <!-- Android 13+ Notification permission -->
     <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 
+    <!-- Network access for periodic update checks -->
+    <uses-permission android:name="android.permission.INTERNET" />
+    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
+
     <!-- Android 12+ Exact Alarms for focus timer completions -->
     <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
     <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
```

---

### Recommendation 2: Refactor `android/app/src/main/java/com/goback/focus/service/GoBackMaintenanceWorker.kt`
1. Switch execution context in `doWork()` to `withContext(Dispatchers.IO)`.
2. Introduce `isNetworkConnected()` pre-check using `ConnectivityManager`.
3. Commit `last_release_check_at` only after successful execution or server response.
4. Replace silent exception swallowing with structured logging (`Log.e` / `Log.w`).

#### Proposed Code Snippet
```kotlin
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
import kotlinx.coroutines.Dispatchers
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
    
    // ... checkUsageLimits, versionParts, newer remain unchanged ...

    companion object {
        private const val TAG = "GoBackMaintenanceWorker"
    }
}
```

---

### Recommendation 3: Foreground Service Launch Type in `FocusTimerService.kt`
Ensure `startForeground` explicitly supplies `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
} else {
    startForeground(NOTIFICATION_ID, notification)
}
```

---

## 5. Verification Method
1. **Manifest Inspection**:
   - Inspect `android/app/src/main/AndroidManifest.xml` and verify `<uses-permission android:name="android.permission.INTERNET" />` and `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` are present under `<manifest>`.
2. **Build Verification**:
   - Run in terminal:
     ```powershell
     cd android
     ./gradlew assembleDebug
     ```
   - Verify Gradle compilation succeeds with code 0 and APK is produced at `android/app/build/outputs/apk/debug/app-debug.apk`.
3. **Runtime Verification**:
   - Install APK on Android 14/15 emulator or device:
     ```powershell
     adb install -r android/app/build/outputs/apk/debug/app-debug.apk
     ```
   - Trigger worker via adb WorkManager test helper:
     ```powershell
     adb shell cmd jobscheduler run -f com.goback.focus <job_id>
     ```
   - Inspect logcat:
     ```powershell
     adb logcat -s GoBackMaintenanceWorker
     ```
   - Verify no `SecurityException` is thrown, release tag is parsed successfully, and usage limits check executes without crash.
4. **Invalidation Conditions**:
   - If `android.permission.INTERNET` is not included in the merged manifest (`build/intermediates/merged_manifests/`), any network attempt will throw `SecurityException`.
   - If WorkManager request is modified to require `NetworkType.CONNECTED`, usage-limit monitoring will fail when the device is disconnected.
