const fs = require('fs');
const path = require('path');
const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');

const usageModule = `package com.goback.focus

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.os.Process
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoBackUsageModule(private val appContext: ReactApplicationContext) : ReactContextBaseJavaModule(appContext) {
  override fun getName() = "GoBackUsage"

  private fun hasAccess(): Boolean {
    val ops = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName) == AppOpsManager.MODE_ALLOWED
  }

  private fun startOfDay(dayOffset: Int): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
    return calendar.timeInMillis
  }

  @ReactMethod fun hasUsageAccess(promise: Promise) {
    try { promise.resolve(hasAccess()) } catch (error: Exception) { promise.reject("E_USAGE_ACCESS", error) }
  }

  @ReactMethod fun openUsageAccessSettings(promise: Promise) {
    try {
      appContext.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_USAGE_SETTINGS", error) }
  }

  @ReactMethod fun getUsageStats(days: Int, promise: Promise) {
    try {
      if (!hasAccess()) { promise.reject("E_USAGE_PERMISSION", "Usage access has not been granted in Android settings."); return }
      val count = days.coerceIn(1, 60)
      val start = startOfDay(-(count - 1))
      val now = System.currentTimeMillis()
      val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val totals = mutableMapOf<String, Long>()
      manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)?.forEach { stat ->
        if (stat.packageName != appContext.packageName && stat.totalTimeInForeground > 0L) totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
      }
      val result = Arguments.createArray()
      totals.entries.sortedByDescending { it.value }.forEach { entry ->
        val item = Arguments.createMap()
        val label = try { val info = appContext.packageManager.getApplicationInfo(entry.key, 0); appContext.packageManager.getApplicationLabel(info).toString() } catch (_: Exception) { entry.key }
        item.putString("packageName", entry.key)
        item.putString("appName", label)
        item.putDouble("foregroundTimeMs", entry.value.toDouble())
        result.pushMap(item)
      }
      promise.resolve(result)
    } catch (error: Exception) { promise.reject("E_USAGE_STATS", error) }
  }

  @ReactMethod fun getDailyUsageStats(days: Int, promise: Promise) {
    try {
      if (!hasAccess()) { promise.reject("E_USAGE_PERMISSION", "Usage access has not been granted in Android settings."); return }
      val count = days.coerceIn(1, 60)
      val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val result = Arguments.createArray()
      val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      for (offset in (count - 1) downTo 0) {
        val start = startOfDay(-offset)
        val end = if (offset == 0) System.currentTimeMillis() else startOfDay(-offset + 1)
        val totals = mutableMapOf<String, Long>()
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)?.forEach { stat ->
          if (stat.packageName != appContext.packageName && stat.totalTimeInForeground > 0L) totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
        }
        val date = formatter.format(Date(start))
        totals.forEach { entry ->
          val item = Arguments.createMap()
          val label = try { val info = appContext.packageManager.getApplicationInfo(entry.key, 0); appContext.packageManager.getApplicationLabel(info).toString() } catch (_: Exception) { entry.key }
          item.putString("date", date)
          item.putString("packageName", entry.key)
          item.putString("appName", label)
          item.putDouble("foregroundTimeMs", entry.value.toDouble())
          result.pushMap(item)
        }
      }
      promise.resolve(result)
    } catch (error: Exception) { promise.reject("E_USAGE_DAILY", error) }
  }

  @ReactMethod fun getUnlockCount(days: Int, promise: Promise) {
    try {
      if (!hasAccess()) {
        val prefs = appContext.getSharedPreferences("go_back_unlocks", 0)
        promise.resolve(prefs.getInt("today_unlocks", 0))
        return
      }
      val count = days.coerceIn(1, 60)
      val start = startOfDay(-(count - 1))
      val now = System.currentTimeMillis()
      val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val events = manager.queryEvents(start, now)
      var unlockCount = 0
      val event = android.app.usage.UsageEvents.Event()
      while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == 18) { // KEYGUARD_HIDDEN
          unlockCount++
        }
      }
      val prefs = appContext.getSharedPreferences("go_back_unlocks", 0)
      val fallback = prefs.getInt("today_unlocks", 0)
      val resolved = if (unlockCount > 0) unlockCount else fallback
      promise.resolve(resolved)
    } catch (error: Exception) { promise.reject("E_UNLOCK_COUNT", error) }
  }

  @ReactMethod fun setUsageLimits(configJson: String, promise: Promise) {
    try {
      val input = JSONArray(configJson)
      val clean = JSONArray()
      for (index in 0 until input.length()) {
        val source = input.optJSONObject(index) ?: continue
        val packageName = source.optString("packageName").trim().take(180)
        val appName = source.optString("appName").trim().take(120)
        val limitMinutes = source.optInt("limitMinutes", 0).coerceIn(1, 1440)
        val warningPercent = source.optInt("warningPercent", 80).coerceIn(1, 99)
        if (!packageName.contains('.') || !packageName.matches(Regex("""[A-Za-z0-9_.]+"""))) continue
        val item = org.json.JSONObject()
        item.put("packageName", packageName)
        item.put("appName", if (appName.isEmpty()) packageName else appName)
        item.put("limitMinutes", limitMinutes)
        item.put("warningPercent", warningPercent)
        clean.put(item)
      }
      appContext.getSharedPreferences("go_back_usage_limits", 0).edit().putString("limits", clean.toString()).apply()
      GoBackMaintenanceJobService.schedule(appContext)
      promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_USAGE_LIMITS", error) }
  }

  @ReactMethod fun getSentLimitStates(promise: Promise) {
    try {
      val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val prefs = appContext.getSharedPreferences("go_back_usage_limits", 0)
      promise.resolve(prefs.getString("sent_" + day, "{}") ?: "{}")
    } catch (error: Exception) { promise.reject("E_USAGE_SENT_LIMITS", error) }
  }

  @ReactMethod fun markLimitNotified(packageName: String, level: Int, promise: Promise) {
    try {
      val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val prefs = appContext.getSharedPreferences("go_back_usage_limits", 0)
      val sent = JSONObject(prefs.getString("sent_" + day, "{}") ?: "{}")
      sent.put(packageName, level.coerceIn(1, 2))
      prefs.edit().putString("sent_" + day, sent.toString()).apply()
      promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_USAGE_MARK_LIMIT", error) }
  }
}
`;

const blockingModule = `package com.goback.focus

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONArray
import org.json.JSONObject
import java.util.function.Consumer
import java.util.Locale

class GoBackBlockingModule(private val appContext: ReactApplicationContext) : ReactContextBaseJavaModule(appContext) {
  override fun getName() = "GoBackBlocking"

  @ReactMethod fun isAccessibilityEnabled(promise: Promise) {
    try {
      val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
      val enabledViaList = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.packageName == appContext.packageName && it.resolveInfo.serviceInfo.name.endsWith("GoBackBlockingService") }
      val enabledServices = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
      val enabledViaSettings = enabledServices.split(':').any { it.contains("GoBackBlockingService", ignoreCase = true) && it.contains(appContext.packageName, ignoreCase = true) }
      promise.resolve(enabledViaList || enabledViaSettings)
    } catch (error: Exception) { promise.reject("E_BLOCKING_ACCESSIBILITY", error) }
  }

  @ReactMethod fun openAccessibilitySettings(promise: Promise) {
    try { appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); promise.resolve(true) }
    catch (error: Exception) { promise.reject("E_BLOCKING_SETTINGS", error) }
  }

  @ReactMethod fun getConfig(promise: Promise) {
    try {
      val value = JSONObject(appContext.getSharedPreferences("go_back_blocking", 0).getString("config", "{}") ?: "{}")
      val result = Arguments.createMap()
      result.putBoolean("blockYoutube", value.optBoolean("blockYoutube", false))
      result.putBoolean("blockShorts", value.optBoolean("blockShorts", false))
      result.putBoolean("blockReels", value.optBoolean("blockReels", false))
      result.putBoolean("blockSpotlight", value.optBoolean("blockSpotlight", false))
      result.putBoolean("blockAdultSites", value.optBoolean("blockAdultSites", false))
      result.putBoolean("blockSplitScreen", value.optBoolean("blockSplitScreen", false))
      result.putBoolean("strictUninstallProtection", value.optBoolean("strictUninstallProtection", false))
      result.putBoolean("strictMode", value.optBoolean("strictMode", false))
      result.putBoolean("blockOnlyDuringFocus", value.optBoolean("blockOnlyDuringFocus", false))
      val channels = value.optJSONArray("allowedChannels") ?: JSONArray(); val channelArray = Arguments.createArray()
      for (i in 0 until channels.length()) channelArray.pushString(channels.optString(i))
      result.putArray("allowedChannels", channelArray)
      val blockedPackages = value.optJSONArray("blockedPackages") ?: JSONArray(); val blockedArray = Arguments.createArray()
      for (i in 0 until blockedPackages.length()) blockedArray.pushString(blockedPackages.optString(i))
      result.putArray("blockedPackages", blockedArray)
      promise.resolve(result)
    } catch (error: Exception) { promise.reject("E_BLOCKING_CONFIG", error) }
  }

  @ReactMethod fun getDiagnostics(promise: Promise) {
    try {
      val prefs = appContext.getSharedPreferences("go_back_blocking", 0)
      val result = Arguments.createMap()
      result.putString("reason", prefs.getString("last_action_reason", "") ?: "")
      result.putDouble("at", prefs.getLong("last_action_at", 0L).toDouble())
      promise.resolve(result)
    } catch (error: Exception) { promise.reject("E_BLOCKING_DIAGNOSTICS", error) }
  }

  @ReactMethod fun getLaunchableApps(promise: Promise) {
    try {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val apps = appContext.packageManager.queryIntentActivities(intent, 0).mapNotNull { resolve ->
        val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
        if (info.packageName == appContext.packageName) return@mapNotNull null
        val label = resolve.loadLabel(appContext.packageManager)?.toString()?.trim().orEmpty()
        if (label.isEmpty()) return@mapNotNull null
        Pair(info.packageName, label)
      }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
      val result = Arguments.createArray()
      apps.forEach { pair -> val item = Arguments.createMap(); item.putString("packageName", pair.first); item.putString("appName", pair.second); result.pushMap(item) }
      promise.resolve(result)
    } catch (error: Exception) { promise.reject("E_BLOCKING_APPS", error) }
  }

  @ReactMethod fun setFocusActive(active: Boolean, promise: Promise) {
    try { val state = JSONObject(); state.put("active", active); state.put("mode", "timer"); setFocusStateInternal(state.toString()); promise.resolve(true) }
    catch (error: Exception) { promise.reject("E_BLOCKING_FOCUS", error) }
  }

  @ReactMethod fun setFocusState(stateJson: String, promise: Promise) {
    try { setFocusStateInternal(stateJson); promise.resolve(true) } catch (error: Exception) { promise.reject("E_BLOCKING_FOCUS_STATE", error) }
  }

  private fun setFocusStateInternal(stateJson: String) {
    val parsed = JSONObject(stateJson); val clean = JSONObject()
    clean.put("active", parsed.optBoolean("active", false)); clean.put("mode", parsed.optString("mode", "timer").take(20))
    if (parsed.has("endAt") && !parsed.isNull("endAt")) clean.put("endAt", parsed.optLong("endAt", 0L))
    clean.put("elapsedSeconds", parsed.optInt("elapsedSeconds", 0).coerceAtLeast(0))
    clean.put("durationSeconds", parsed.optInt("durationSeconds", 0).coerceAtLeast(0))
    clean.put("remainingSeconds", parsed.optInt("remainingSeconds", 0).coerceAtLeast(0))
    clean.put("sessionType", parsed.optString("sessionType", "focus").take(20))
    clean.put("paused", parsed.optBoolean("paused", false))
    if (parsed.has("stopwatchRunStartedAt") && !parsed.isNull("stopwatchRunStartedAt")) clean.put("stopwatchRunStartedAt", parsed.optLong("stopwatchRunStartedAt", 0L))
    clean.put("title", parsed.optString("title", "Focus session").take(80))
    val prefs = appContext.getSharedPreferences("go_back_blocking", 0)
    prefs.edit().putBoolean("focus_active", clean.optBoolean("active", false)).putString("focus_state", clean.toString()).apply()
    if (clean.optBoolean("active", false) || clean.optBoolean("paused", false)) showFocusNotification(clean) else cancelFocusNotification()
    GoBackFocusWidgetProvider.refresh(appContext)
    TileService.requestListeningState(appContext, ComponentName(appContext, GoBackFocusTileService::class.java))
  }

  private fun showFocusNotification(state: JSONObject) {
    if (Build.VERSION.SDK_INT >= 33 && appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "go-back-focus-status-v2"
    if (Build.VERSION.SDK_INT >= 26) {
      val channel = NotificationChannel(channelId, "Focus timer status", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Silent live status for an active focus session"
        setSound(null, null)
        enableVibration(false)
        setShowBadge(false)
      }
      manager.createNotificationChannel(channel)
    }
    val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
      action = Intent.ACTION_VIEW
      data = android.net.Uri.parse("goback:///focus")
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentIntent = launch?.let {
      PendingIntent.getActivity(appContext, 8401, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    val now = System.currentTimeMillis()
    val mode = state.optString("mode", "timer")
    val isStopwatch = mode == "stopwatch"
    val endAt = state.optLong("endAt", 0L)
    val durationMs = state.optLong("durationSeconds", 0L) * 1000L
    val paused = state.optBoolean("paused", false)
    val remainingMs = if (paused) state.optLong("remainingSeconds", 0L) * 1000L else (endAt - now).coerceAtLeast(0L)
    val elapsedMs = if (isStopwatch) {
      state.optLong("elapsedSeconds", 0L) * 1000L + if (paused) 0L else (now - state.optLong("stopwatchRunStartedAt", now)).coerceAtLeast(0L)
    } else (durationMs - remainingMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
    val phase = when (state.optString("sessionType", "focus")) {
      "short-break" -> "Short break"
      "long-break" -> "Long break"
      else -> if (mode == "pomodoro") "Pomodoro focus" else "Focus session"
    }
    fun clock(seconds: Long): String {
      val total = (seconds / 1000L).coerceAtLeast(0L)
      return String.format(Locale.getDefault(), "%02d:%02d", total / 60L, total % 60L)
    }
    val detail = when {
      paused && isStopwatch -> "Stopwatch · Paused · " + clock(elapsedMs) + " elapsed"
      paused -> "$phase · Paused · " + clock(remainingMs) + " remaining"
      isStopwatch -> "Stopwatch · elapsed time"
      else -> "$phase · time remaining"
    }
    val builder = NotificationCompat.Builder(appContext, channelId)
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle(state.optString("title", phase).ifBlank { phase })
      .setContentText(detail)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(true)
      .setUsesChronometer(!paused)
      .setWhen(if (paused) now else if (isStopwatch) now - elapsedMs else now + remainingMs)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    if (Build.VERSION.SDK_INT >= 24 && !isStopwatch && !paused) builder.setChronometerCountDown(true)
    if (!isStopwatch && durationMs > 0L) {
      val progress = ((elapsedMs * 100L) / durationMs).toInt().coerceIn(0, 100)
      builder.setProgress(100, progress, false)
    }
    if (contentIntent != null) builder.setContentIntent(contentIntent)
    manager.notify(8401, builder.build())
  }

  private fun cancelFocusNotification() {
    (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(8401)
  }

  @ReactMethod fun setSessionBlockedPackages(json: String, promise: Promise) {
    try {
      val input = JSONArray(json); val clean = JSONArray()
      for (index in 0 until input.length()) {
        val packageName = input.optString(index).trim().take(180)
        if (packageName.contains('.') && packageName.matches(Regex("""[A-Za-z0-9_.]+""")) && packageName != appContext.packageName && !contains(clean, packageName)) clean.put(packageName)
      }
      appContext.getSharedPreferences("go_back_blocking", 0).edit().putString("session_blocked_packages", clean.toString()).apply(); promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_BLOCKING_SESSION_PACKAGES", error) }
  }

  @ReactMethod fun setConfig(configJson: String, promise: Promise) {
    try {
      val input = JSONObject(configJson)
      val channels = input.optJSONArray("allowedChannels") ?: JSONArray(); val cleanChannels = JSONArray()
      for (i in 0 until channels.length()) { val name = channels.optString(i).trim().replace(Regex("""\s+"""), " ").take(80); if (name.isNotEmpty() && !contains(cleanChannels, name)) cleanChannels.put(name) }
      val blockedInput = input.optJSONArray("blockedPackages") ?: JSONArray(); val blocked = JSONArray()
      for (i in 0 until blockedInput.length()) { val packageName = blockedInput.optString(i).trim().take(180); if (packageName.contains('.') && packageName.matches(Regex("""[A-Za-z0-9_.]+""")) && packageName != appContext.packageName && !contains(blocked, packageName)) blocked.put(packageName) }
      val output = JSONObject()
      output.put("blockYoutube", input.optBoolean("blockYoutube", false))
      output.put("blockShorts", input.optBoolean("blockShorts", false))
      output.put("blockReels", input.optBoolean("blockReels", false))
      output.put("blockSpotlight", input.optBoolean("blockSpotlight", false))
      output.put("blockAdultSites", input.optBoolean("blockAdultSites", false))
      output.put("blockSplitScreen", input.optBoolean("blockSplitScreen", false))
      output.put("strictUninstallProtection", input.optBoolean("strictUninstallProtection", false))
      output.put("strictMode", input.optBoolean("strictMode", false))
      output.put("blockOnlyDuringFocus", input.optBoolean("blockOnlyDuringFocus", false))
      output.put("allowedChannels", cleanChannels)
      output.put("blockedPackages", blocked)
      appContext.getSharedPreferences("go_back_blocking", 0).edit().putString("config", output.toString()).apply(); promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_BLOCKING_CONFIG", error) }
  }

  private fun contains(array: JSONArray, value: String): Boolean { for (i in 0 until array.length()) if (array.optString(i).equals(value, true)) return true; return false }
}

class GoBackSystemModule(private val appContext: ReactApplicationContext) : ReactContextBaseJavaModule(appContext) {
  override fun getName() = "GoBackSystem"
  @ReactMethod fun scheduleMaintenance(promise: Promise) { try { GoBackMaintenanceJobService.schedule(appContext); promise.resolve(true) } catch (error: Exception) { promise.reject("E_MAINTENANCE_SCHEDULE", error) } }
  @ReactMethod fun runMaintenanceNow(promise: Promise) { try { GoBackMaintenanceJobService.runNow(appContext); promise.resolve(true) } catch (error: Exception) { promise.reject("E_MAINTENANCE_RUN", error) } }
  @ReactMethod fun setUpdateChecksEnabled(enabled: Boolean, promise: Promise) { try { appContext.getSharedPreferences("go_back_maintenance", 0).edit().putBoolean("updates_enabled", enabled).apply(); GoBackMaintenanceJobService.schedule(appContext); promise.resolve(true) } catch (error: Exception) { promise.reject("E_UPDATE_PREF", error) } }
  @ReactMethod fun requestFocusTile(promise: Promise) {
    if (Build.VERSION.SDK_INT < 33) { promise.resolve(false); return }
    try {
      val manager = appContext.getSystemService(StatusBarManager::class.java); val component = ComponentName(appContext, GoBackFocusTileService::class.java); val icon = Icon.createWithResource(appContext, R.mipmap.ic_launcher)
      manager.requestAddTileService(component, "Go Back Focus", icon, appContext.mainExecutor, Consumer { result -> promise.resolve(result) })
    } catch (error: Exception) { promise.reject("E_TILE_REQUEST", error) }
  }
  @ReactMethod fun updateWidgetData(dataJson: String, promise: Promise) {
    try {
      appContext.getSharedPreferences("go_back_widget_data", 0).edit().putString("data", dataJson).apply()
      GoBackWidgetProvider.refresh(appContext)
      GoBackFocusWidgetProvider.refresh(appContext)
      promise.resolve(true)
    } catch (error: Exception) { promise.reject("E_WIDGET_DATA", error) }
  }
}
`;

const usagePackage = `package com.goback.focus
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
class GoBackUsagePackage : ReactPackage {
  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> = listOf(GoBackUsageModule(reactContext), GoBackBlockingModule(reactContext), GoBackSystemModule(reactContext))
  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()
}
`;

const blockingService = `package com.goback.focus
import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class GoBackBlockingService : AccessibilityService() {
  private val handler = Handler(Looper.getMainLooper())
  private var lastActionAt = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    val info = android.accessibilityservice.AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
              AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
              AccessibilityEvent.TYPE_VIEW_SCROLLED
      feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
              android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
              android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
      notificationTimeout = 50
    }
    serviceInfo = info
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val eventType = event.eventType
    if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
        eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
        eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      return
    }
    val pkg = event.packageName?.toString() ?: return
    val prefs = getSharedPreferences("go_back_blocking", 0)
    val config = try { JSONObject(prefs.getString("config", "{}") ?: "{}") } catch (_: Exception) { return }
    val focusActive = effectiveFocusActive(prefs)
    val focusOnly = config.optBoolean("blockOnlyDuringFocus", false)

    // 1. Session-blocked packages (preset rules)
    if (focusActive && packageIn(pkg, prefs.getString("session_blocked_packages", "[]") ?: "[]")) {
      record("preset app closed")
      sendHome()
      return
    }

    // 2. Settings / Uninstall protection
    if (pkg == "com.android.settings") {
      val strictProtect = config.optBoolean("strictUninstallProtection", false) || (focusActive && config.optBoolean("strictMode", false))
      if (strictProtect) {
        val root = rootInActiveWindow
        if (root != null) {
          val text = collectText(root, 0, 4).lowercase(Locale.ROOT)
          if (text.contains("com.goback.focus") || text.contains("go back")) {
            record("Settings protection active: blocked access to Go Back controls")
            sendHome()
            return
          }
        }
      }
    }

    // 3. Split-screen / Floating window protection
    if (focusActive && config.optBoolean("blockSplitScreen", false)) {
      if (Build.VERSION.SDK_INT >= 24) {
        val allWindows = windows
        if (allWindows != null) {
          val appWindows = allWindows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
          if (appWindows.size > 1) {
            val blockedList = (config.optJSONArray("blockedPackages") ?: JSONArray()).toString()
            for (win in appWindows) {
              val winRoot = win.root ?: continue
              val winPkg = winRoot.packageName?.toString() ?: ""
              if (winPkg.isNotEmpty() && winPkg != packageName && (packageIn(winPkg, blockedList) || winPkg == YOUTUBE || winPkg == INSTAGRAM || winPkg == SNAPCHAT)) {
                record("split-screen blocked app closed")
                sendHome()
                return
              }
            }
          }
        }
      }
    }

    if (focusOnly && !focusActive) return

    // 4. Whole-app blocking
    if (packageIn(pkg, config.optJSONArray("blockedPackages"))) {
      record("blocked app closed: $pkg")
      sendHome()
      return
    }

    // 5. Adult site blocking in web browsers
    if (BROWSER_PACKAGES.contains(pkg) && config.optBoolean("blockAdultSites", false)) {
      val root = rootInActiveWindow ?: return
      val text = collectText(root, 0, 5).lowercase(Locale.ROOT)
      if (containsAdultSiteKeyword(text)) {
        record("Adult website blocked")
        sendHome()
        return
      }
    }

    if (pkg != YOUTUBE && pkg != INSTAGRAM && pkg != SNAPCHAT) return
    val root = rootInActiveWindow ?: return

    // 6. YouTube Shorts & Video allowlist
    if (pkg == YOUTUBE) {
      if (config.optBoolean("blockShorts", false) && (hasSelectedLabel(root, "shorts") || rootTextContains(root, "shorts", 4))) {
        record("YouTube Shorts closed")
        sendBack()
        return
      }
      if (config.optBoolean("blockYoutube", false) && isVideoSurface(root)) {
        val allowed = config.optJSONArray("allowedChannels") ?: JSONArray()
        if (!matchesChannel(root, allowed)) {
          record("disallowed YouTube video closed")
          closeDisallowedVideo()
          return
        }
      }
    }

    // 7. Instagram Reels
    if (pkg == INSTAGRAM && config.optBoolean("blockReels", false) && (hasSelectedLabel(root, "reels") || rootTextContains(root, "reels", 4))) {
      record("Instagram Reels closed")
      sendBack()
      return
    }

    // 8. Snapchat Spotlight
    if (pkg == SNAPCHAT && config.optBoolean("blockSpotlight", false) && (hasSelectedLabel(root, "spotlight") || rootTextContains(root, "spotlight", 4))) {
      record("Snapchat Spotlight closed")
      sendBack()
      return
    }
  }

  private fun effectiveFocusActive(prefs: SharedPreferences): Boolean {
    if (!prefs.getBoolean("focus_active", false)) return false
    val state = try { JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    if (!state.optBoolean("active", false)) {
      prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()
      return false
    }
    if (state.optString("mode", "timer") != "stopwatch") {
      val endAt = state.optLong("endAt", 0L)
      if (endAt <= 0L || endAt <= System.currentTimeMillis()) {
        state.put("active", false)
        prefs.edit().putBoolean("focus_active", false).putString("focus_state", state.toString()).remove("session_blocked_packages").apply()
        return false
      }
    }
    return true
  }

  private fun packageIn(pkg: String, jsonOrArray: Any?): Boolean {
    if (jsonOrArray == null) return false
    val array = when (jsonOrArray) {
      is JSONArray -> jsonOrArray
      is String -> try { JSONArray(jsonOrArray) } catch (_: Exception) { return false }
      else -> return false
    }
    for (index in 0 until array.length()) {
      if (pkg.equals(array.optString(index), ignoreCase = true)) return true
    }
    return false
  }

  private fun record(reason: String) {
    getSharedPreferences("go_back_blocking", 0).edit().putString("last_action_reason", reason).putLong("last_action_at", System.currentTimeMillis()).apply()
  }

  private fun sendHome() {
    val now = System.currentTimeMillis()
    if (now - lastActionAt < 300) return
    lastActionAt = now
    performGlobalAction(GLOBAL_ACTION_HOME)
  }

  private fun sendBack() {
    val now = System.currentTimeMillis()
    if (now - lastActionAt < 300) return
    lastActionAt = now
    performGlobalAction(GLOBAL_ACTION_BACK)
  }

  private fun containsAdultSiteKeyword(text: String): Boolean {
    for (kw in ADULT_KEYWORDS) {
      if (text.contains(kw)) return true
    }
    return false
  }

  private fun rootTextContains(node: AccessibilityNodeInfo, label: String, maxDepth: Int, currentDepth: Int = 0): Boolean {
    if (currentDepth > maxDepth) return false
    val t = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
    if (t.contains(label)) return true
    val count = Math.min(node.childCount, 25)
    for (i in 0 until count) {
      val child = node.getChild(i) ?: continue
      val found = rootTextContains(child, label, maxDepth, currentDepth + 1)
      if (found) return true
    }
    return false
  }

  private fun closeDisallowedVideo() {
    val now = System.currentTimeMillis()
    if (now - lastActionAt < 1400) return
    lastActionAt = now
    val current = rootInActiveWindow
    if (current != null && clickPlayerClose(current)) return
    performGlobalAction(GLOBAL_ACTION_BACK)
    handler.postDelayed({
      val afterBack = rootInActiveWindow ?: return@postDelayed
      if (afterBack.packageName?.toString() != YOUTUBE) return@postDelayed
      if (clickPlayerClose(afterBack)) return@postDelayed
      if (isVideoSurface(afterBack) || isMiniPlayer(afterBack)) {
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
          val finalRoot = rootInActiveWindow ?: return@postDelayed
          if (finalRoot.packageName?.toString() != YOUTUBE) return@postDelayed
          if (clickPlayerClose(finalRoot)) return@postDelayed
          if (isVideoSurface(finalRoot) || isMiniPlayer(finalRoot)) performGlobalAction(GLOBAL_ACTION_BACK)
        }, 420)
      }
    }, 320)
  }

  private fun clickPlayerClose(node: AccessibilityNodeInfo): Boolean {
    val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT).replace('-', ' ')
    val closeLabel = text.contains("close mini player") || text.contains("close miniplayer") || text.contains("dismiss mini player") || text.contains("dismiss miniplayer") || text == "close" || text.contains("close player")
    if (closeLabel && node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    for (index in 0 until node.childCount) {
      val child = node.getChild(index) ?: continue
      val clicked = clickPlayerClose(child)
      if (clicked) return true
    }
    return false
  }

  private fun isMiniPlayer(node: AccessibilityNodeInfo): Boolean {
    val text = collectText(node, 0, 6).lowercase(Locale.ROOT).replace('-', ' ')
    return text.contains("mini player") || text.contains("miniplayer") || text.contains("expand player")
  }

  private fun hasSelectedLabel(node: AccessibilityNodeInfo, label: String, currentDepth: Int = 0, maxDepth: Int = 4): Boolean {
    if (currentDepth > maxDepth) return false
    val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
    if (text.contains(label) && (node.isSelected || node.isChecked || node.isAccessibilityFocused)) return true
    val count = Math.min(node.childCount, 20)
    for (i in 0 until count) {
      val child = node.getChild(i) ?: continue
      val found = hasSelectedLabel(child, label, currentDepth + 1, maxDepth)
      if (found) return true
    }
    return false
  }

  private fun matchesChannel(node: AccessibilityNodeInfo, channels: JSONArray): Boolean {
    if (channels.length() == 0) return false
    val collected = collectText(node, 0, 6).lowercase(Locale.ROOT)
    val wordText = " " + collected.replace(Regex("""[^\p{L}\p{N}@]+"""), " ").trim() + " "
    for (i in 0 until channels.length()) {
      val configured = channels.optString(i).trim().removePrefix("@").lowercase(Locale.ROOT)
      val words = configured.replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
      if (configured.length >= 3 && (collected.contains("@" + configured) || wordText.contains(" @" + configured + " "))) return true
      if (words.length >= 3 && wordText.contains(" " + words + " ")) return true
    }
    return false
  }

  private fun isVideoSurface(node: AccessibilityNodeInfo): Boolean {
    val text = collectText(node, 0, 6).lowercase(Locale.ROOT)
    return text.contains("video player") || text.contains("seek bar") || text.contains("pause video") || text.contains("play video") || (text.contains("comments") && text.contains("subscribe") && text.contains("share")) || isMiniPlayer(node)
  }

  private fun collectText(node: AccessibilityNodeInfo, currentDepth: Int = 0, maxDepth: Int = 5, builder: StringBuilder = StringBuilder()): String {
    if (currentDepth > maxDepth || builder.length > 800) return builder.toString()
    val text = node.text?.toString()
    val desc = node.contentDescription?.toString()
    if (!text.isNullOrBlank()) builder.append(text).append(' ')
    if (!desc.isNullOrBlank()) builder.append(desc).append(' ')
    val count = Math.min(node.childCount, 25)
    for (i in 0 until count) {
      if (builder.length > 800) break
      val child = node.getChild(i) ?: continue
      collectText(child, currentDepth + 1, maxDepth, builder)
    }
    return builder.toString()
  }

  override fun onInterrupt() = Unit

  companion object {
    private const val YOUTUBE = "com.google.android.youtube"
    private const val INSTAGRAM = "com.instagram.android"
    private const val SNAPCHAT = "com.snapchat.android"
    private val BROWSER_PACKAGES = setOf(
      "com.android.chrome",
      "com.sec.android.app.sbrowser",
      "com.brave.browser",
      "com.microsoft.emmx",
      "org.mozilla.firefox",
      "com.opera.browser",
      "com.opera.mini.native",
      "com.duckduckgo.mobile.android",
      "com.mi.globalbrowser",
      "com.UCMobile.intl"
    )
    private val ADULT_KEYWORDS = listOf(
      "porn", "xxx", "xvideos", "xnxx", "pornhub", "redtube", "chaturbate",
      "onlyfans", "erome", "rule34", "spankbang", "hentai", "beeg", "youporn",
      "brazzers", "cam4", "stripchat"
    )
  }
}
`;

const maintenanceService = `package com.goback.focus
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class GoBackMaintenanceJobService : JobService() {
  override fun onStartJob(params: JobParameters?): Boolean {
    thread(name = "GoBackMaintenance") {
      try { runChecks(applicationContext) }
      finally { if (params != null) jobFinished(params, false) }
    }
    return true
  }
  override fun onStopJob(params: JobParameters?): Boolean = true
  companion object {
    private const val JOB_ID = 19627
    private const val UPDATE_CHANNEL = "go-back-updates-native-v1"
    private const val LIMIT_CHANNEL = "screen-time-limits-native-v1"

    fun schedule(context: Context) {
      val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
      val info = JobInfo.Builder(JOB_ID, ComponentName(context, GoBackMaintenanceJobService::class.java))
        .setPersisted(true)
        .setPeriodic(30L * 60L * 1000L)
        .build()
      scheduler.schedule(info)
    }

    fun runNow(context: Context) {
      thread(name = "GoBackMaintenanceManual") { runChecks(context.applicationContext) }
    }

    private fun runChecks(context: Context) {
      ensureChannels(context)
      checkStableRelease(context)
      checkUsageLimits(context)
    }

    private fun ensureChannels(context: Context) {
      if (Build.VERSION.SDK_INT < 26) return
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(NotificationChannel(UPDATE_CHANNEL, "Go Back updates", NotificationManager.IMPORTANCE_DEFAULT))
      manager.createNotificationChannel(NotificationChannel(LIMIT_CHANNEL, "Screen-time limit alerts", NotificationManager.IMPORTANCE_DEFAULT))
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
      for (index in 0 until 4) if (left[index] != right[index]) return left[index] > right[index]
      return false
    }

    private fun checkStableRelease(context: Context) {
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
        connection.setRequestProperty("User-Agent", "Go-Back-Android")
        if (connection.responseCode !in 200..299) { connection.disconnect(); return }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
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
        val pending = PendingIntent.getActivity(context, 7201, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL)
          .setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle("A Go Back update is available")
          .setContentText(tag + " is available. Tap to view the official release.")
          .setContentIntent(pending)
          .setAutoCancel(true)
          .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(7201, notification)
        prefs.edit().putString("last_notified_version", tag).apply()
      } catch (_: Exception) { }
    }

    private fun hasUsageAccess(context: Context): Boolean {
      val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
      return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    private fun checkUsageLimits(context: Context) {
      if (!hasUsageAccess(context)) return
      val prefs = context.getSharedPreferences("go_back_usage_limits", 0)
      val limits = try { JSONArray(prefs.getString("limits", "[]") ?: "[]") } catch (_: Exception) { return }
      if (limits.length() == 0) return
      val calendar = Calendar.getInstance()
      calendar.set(Calendar.HOUR_OF_DAY, 0)
      calendar.set(Calendar.MINUTE, 0)
      calendar.set(Calendar.SECOND, 0)
      calendar.set(Calendar.MILLISECOND, 0)
      val start = calendar.timeInMillis
      val now = System.currentTimeMillis()
      val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
      val totals = mutableMapOf<String, Long>()
      manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)?.forEach { stat ->
        if (stat.totalTimeInForeground > 0L) totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
      }
      val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
      val sent = try { JSONObject(prefs.getString("sent_" + day, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
      var changed = false
      for (index in 0 until limits.length()) {
        val limit = limits.optJSONObject(index) ?: continue
        val packageName = limit.optString("packageName")
        val appName = limit.optString("appName", packageName)
        val limitMinutes = limit.optInt("limitMinutes", 0)
        val warningPercent = limit.optInt("warningPercent", 80).coerceIn(1, 99)
        if (limitMinutes <= 0) continue
        val usedMinutes = ((totals[packageName] ?: 0L) / 60000L).toInt()
        val state = sent.optInt(packageName, 0)
        val warningAt = (limitMinutes * warningPercent / 100).coerceAtLeast(1)
        val nextState = if (usedMinutes >= limitMinutes) 2 else if (usedMinutes >= warningAt) 1 else 0
        if (nextState <= state) continue
        val title = if (nextState == 2) appName + " reached its daily limit" else appName + " is nearing its daily limit"
        val text = usedMinutes.toString() + " of " + limitMinutes + " minutes used today."
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launchIntent?.let { PendingIntent.getActivity(context, 7300 + index, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(context, LIMIT_CHANNEL)
          .setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle(title)
          .setContentText(text)
          .setAutoCancel(true)
          .apply { if (pending != null) setContentIntent(pending) }
          .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(7300 + index, notification)
        sent.put(packageName, nextState)
        changed = true
      }
      if (changed) prefs.edit().putString("sent_" + day, sent.toString()).apply()
    }
  }
}

class GoBackBootReceiver : android.content.BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
      GoBackMaintenanceJobService.schedule(context)
    } else if (action == Intent.ACTION_USER_PRESENT) {
      val prefs = context.getSharedPreferences("go_back_unlocks", 0)
      val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
      val recordedDay = prefs.getString("last_unlock_day", "")
      val current = if (recordedDay == day) prefs.getInt("today_unlocks", 0) else 0
      prefs.edit().putString("last_unlock_day", day).putInt("today_unlocks", current + 1).apply()
    }
  }
}
`;

const tileService = `package com.goback.focus
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
class GoBackFocusTileService : TileService() {
  override fun onStartListening() { super.onStartListening(); updateTileState() }
  override fun onClick() {
    super.onClick()
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///focus"), this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    if (Build.VERSION.SDK_INT >= 34) {
      val pending = PendingIntent.getActivity(this, 8101, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      startActivityAndCollapse(pending)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(intent)
    }
  }
  private fun updateTileState() {
    val tile = qsTile ?: return
    val prefs = getSharedPreferences("go_back_blocking", 0)
    val state = try { org.json.JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { org.json.JSONObject() }
    val mode = state.optString("mode", "timer")
    val endAt = state.optLong("endAt", 0L)
    val active = state.optBoolean("active", false) && (mode == "stopwatch" || (endAt > 0L && endAt > System.currentTimeMillis()))
    if (!active && prefs.getBoolean("focus_active", false)) prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()
    tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    tile.label = if (active) "Focus active" else "Go Back Focus"
    if (Build.VERSION.SDK_INT >= 30) tile.stateDescription = if (active) "Focus session active" else "Open Focus"
    tile.updateTile()
  }
}
`;

const accessibilityXml = `<?xml version="1.0" encoding="utf-8"?><accessibility-service xmlns:android="http://schemas.android.com/apk/res/android" android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled|typeViewSelected|typeViewFocused" android:accessibilityFeedbackType="feedbackGeneric" android:notificationTimeout="50" android:canRetrieveWindowContent="true" android:description="@string/go_back_accessibility_description" android:accessibilityFlags="flagDefault|flagReportViewIds|flagRetrieveInteractiveWindows|flagIncludeNotImportantViews" />`;

const provider = `package com.goback.focus
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

class GoBackWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    val prefs = context.getSharedPreferences("go_back_widget_data", 0)
    val data = try { JSONObject(prefs.getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    val focusMin = data.optInt("focusMinutesToday", 0)
    val streak = data.optInt("streakDays", 0)
    val nextExam = data.optString("nextExamTitle", "")
    val nextExamDays = data.optInt("nextExamDays", -1)
    val notesCount = data.optInt("totalNotes", 0)
    val syllabus = data.optString("syllabusProgress", "")

    val focusLabel = if (focusMin > 0) "Focus · ${focusMin}m" else if (streak > 0) "Focus · 🔥${streak}d" else "Focus"
    val syllabusLabel = if (syllabus.isNotEmpty()) "Syllabus · $syllabus" else "Syllabus"
    val examsLabel = if (nextExam.isNotEmpty() && nextExamDays >= 0) "Exams · ${nextExam.take(7)} ${nextExamDays}d" else "Exams"
    val notesLabel = if (notesCount > 0) "Notes · $notesCount" else "Notes"

    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_widget)
      views.setTextViewText(R.id.widget_focus, focusLabel)
      views.setTextViewText(R.id.widget_syllabus, syllabusLabel)
      views.setTextViewText(R.id.widget_exams, examsLabel)
      views.setTextViewText(R.id.widget_notes, notesLabel)
      bindAction(context, views, R.id.widget_focus, "focus", 100)
      bindAction(context, views, R.id.widget_syllabus, "syllabus", 101)
      bindAction(context, views, R.id.widget_exams, "exams", 102)
      bindAction(context, views, R.id.widget_notes, "notes", 103)
      manager.updateAppWidget(widgetId, views)
    }
  }
  private fun bindAction(context: Context, views: RemoteViews, viewId: Int, route: String, requestCode: Int) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///" + route), context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(viewId, pendingIntent)
  }
  companion object {
    fun refresh(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val component = ComponentName(context, GoBackWidgetProvider::class.java)
      val ids = manager.getAppWidgetIds(component)
      if (ids.isNotEmpty()) GoBackWidgetProvider().onUpdate(context, manager, ids)
    }
  }
}

abstract class GoBackSingleActionWidgetProvider : AppWidgetProvider() {
  protected abstract val route: String; protected abstract val title: String; protected abstract val subtitle: String
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_single_action_widget)
      views.setTextViewText(R.id.widget_action_title, title)
      views.setTextViewText(R.id.widget_action_subtitle, subtitle)
      bind(context, views, R.id.widget_action_button, route)
      manager.updateAppWidget(widgetId, views)
    }
  }
  protected fun bind(context: Context, views: RemoteViews, viewId: Int, target: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///" + target), context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(context, 200 + target.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(viewId, pending)
  }
}

class GoBackFocusWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) { appWidgetIds.forEach { updateOne(context, manager, it) } }
  private fun updateOne(context: Context, manager: AppWidgetManager, widgetId: Int) {
    val views = RemoteViews(context.packageName, R.layout.go_back_focus_widget)
    val prefs = context.getSharedPreferences("go_back_blocking", 0)
    val state = try { JSONObject(prefs.getString("focus_state", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    val modeForState = state.optString("mode", "timer")
    val endAtForState = state.optLong("endAt", 0L)
    val active = state.optBoolean("active", false) && (modeForState == "stopwatch" || (endAtForState > 0L && endAtForState > System.currentTimeMillis()))
    if (!active && prefs.getBoolean("focus_active", false)) prefs.edit().putBoolean("focus_active", false).remove("session_blocked_packages").apply()

    views.setTextViewText(R.id.widget_focus_title, if (active) state.optString("title", "Focus session") else "Daily Focus")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///focus"), context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(context, 310, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.widget_focus_button, pending)

    if (!active) {
      views.setViewVisibility(R.id.widget_focus_timer, View.GONE)
      val widgetData = try { JSONObject(context.getSharedPreferences("go_back_widget_data", 0).getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
      val focusMin = widgetData.optInt("focusMinutesToday", 0)
      val goalMin = widgetData.optInt("dailyGoalMinutes", 120)
      val streak = widgetData.optInt("streakDays", 0)
      val subtitleText = if (focusMin > 0 || streak > 0) {
        "${focusMin}m of ${goalMin}m goal" + (if (streak > 0) " · 🔥 ${streak}d streak" else "")
      } else {
        "Goal: ${goalMin}m · Tap to start deep work"
      }
      views.setTextViewText(R.id.widget_focus_subtitle, subtitleText)
    } else {
      views.setViewVisibility(R.id.widget_focus_timer, View.VISIBLE)
      if (modeForState == "stopwatch") {
        val accumulated = state.optLong("elapsedSeconds", 0L) * 1000L
        val runStartedAt = state.optLong("stopwatchRunStartedAt", 0L)
        val runningExtra = if (runStartedAt > 0L) (System.currentTimeMillis() - runStartedAt).coerceAtLeast(0L) else 0L
        views.setChronometer(R.id.widget_focus_timer, SystemClock.elapsedRealtime() - accumulated - runningExtra, null, true)
        if (Build.VERSION.SDK_INT >= 24) views.setChronometerCountDown(R.id.widget_focus_timer, false)
        views.setTextViewText(R.id.widget_focus_subtitle, "Stopwatch running")
      } else {
        val remaining = (endAtForState - System.currentTimeMillis()).coerceAtLeast(0L)
        views.setChronometer(R.id.widget_focus_timer, SystemClock.elapsedRealtime() + remaining, null, true)
        if (Build.VERSION.SDK_INT >= 24) views.setChronometerCountDown(R.id.widget_focus_timer, true)
        views.setTextViewText(R.id.widget_focus_subtitle, if (modeForState == "pomodoro") "Pomodoro in progress" else "Focus in progress")
      }
    }
    manager.updateAppWidget(widgetId, views)
  }
  companion object {
    fun refresh(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      val component = ComponentName(context, GoBackFocusWidgetProvider::class.java)
      val ids = manager.getAppWidgetIds(component)
      if (ids.isNotEmpty()) GoBackFocusWidgetProvider().onUpdate(context, manager, ids)
    }
  }
}

class GoBackSyllabusWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "syllabus"
  override val title = "Syllabus tracker"
  override val subtitle = "Track your chapters"
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    val widgetData = try { JSONObject(context.getSharedPreferences("go_back_widget_data", 0).getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    val syllabus = widgetData.optString("syllabusProgress", "")
    val displaySub = if (syllabus.isNotEmpty()) "$syllabus completed" else subtitle
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_single_action_widget)
      views.setTextViewText(R.id.widget_action_title, title)
      views.setTextViewText(R.id.widget_action_subtitle, displaySub)
      bind(context, views, R.id.widget_action_button, route)
      manager.updateAppWidget(widgetId, views)
    }
  }
}

class GoBackExamsWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "exams"
  override val title = "Exam planner"
  override val subtitle = "Keep the next date in sight"
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    val widgetData = try { JSONObject(context.getSharedPreferences("go_back_widget_data", 0).getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    val nextExam = widgetData.optString("nextExamTitle", "")
    val nextExamDays = widgetData.optInt("nextExamDays", -1)
    val displayTitle = if (nextExam.isNotEmpty()) "Next: $nextExam" else title
    val displaySub = if (nextExamDays >= 0) (if (nextExamDays == 0) "Exam is today!" else "$nextExamDays days to go") else subtitle
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_single_action_widget)
      views.setTextViewText(R.id.widget_action_title, displayTitle)
      views.setTextViewText(R.id.widget_action_subtitle, displaySub)
      bind(context, views, R.id.widget_action_button, route)
      manager.updateAppWidget(widgetId, views)
    }
  }
}

class GoBackNotesWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "notes"
  override val title = "Quick notes"
  override val subtitle = "Save an idea before it slips away"
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    val widgetData = try { JSONObject(context.getSharedPreferences("go_back_widget_data", 0).getString("data", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    val count = widgetData.optInt("totalNotes", 0)
    val displaySub = if (count > 0) "$count saved ${if (count == 1) "thought" else "thoughts"}" else subtitle
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_single_action_widget)
      views.setTextViewText(R.id.widget_action_title, title)
      views.setTextViewText(R.id.widget_action_subtitle, displaySub)
      bind(context, views, R.id.widget_action_button, route)
      manager.updateAppWidget(widgetId, views)
    }
  }
}

class GoBackInsightsWidgetProvider : GoBackSingleActionWidgetProvider() { override val route = "usage"; override val title = "Usage insights"; override val subtitle = "Review your time and trends" }
class GoBackBlockingWidgetProvider : GoBackSingleActionWidgetProvider() { override val route = "blocking"; override val title = "App blocking"; override val subtitle = "Manage distraction rules" }
`;

const layout = `<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="14dp" android:background="@drawable/go_back_widget_bg"><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="GO BACK" android:textColor="#B8F36B" android:textSize="10sp" android:textStyle="bold" android:letterSpacing="0.15" /><TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="3dp" android:text="Focus &amp; Study" android:textColor="#F5FAF2" android:textSize="18sp" android:textStyle="bold" /><LinearLayout android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1" android:layout_marginTop="10dp" android:orientation="horizontal" android:gravity="center_vertical" android:weightSum="4"><TextView android:id="@+id/widget_focus" style="@style/GoBackWidgetAction" android:text="Focus" /><TextView android:id="@+id/widget_syllabus" style="@style/GoBackWidgetAction" android:text="Syllabus" /><TextView android:id="@+id/widget_exams" style="@style/GoBackWidgetAction" android:text="Exams" /><TextView android:id="@+id/widget_notes" style="@style/GoBackWidgetAction" android:text="Notes" /></LinearLayout></LinearLayout>`;
const singleActionLayout = `<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="14dp" android:gravity="center_vertical" android:background="@drawable/go_back_widget_bg"><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="GO BACK · QUICK ACTION" android:textColor="#B8F36B" android:textSize="10sp" android:textStyle="bold" android:letterSpacing="0.08" /><TextView android:id="@+id/widget_action_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="7dp" android:text="Your action" android:textColor="#F5FAF2" android:textSize="20sp" android:textStyle="bold" /><TextView android:id="@+id/widget_action_subtitle" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="3dp" android:text="Open Go Back" android:textColor="#C8D9CC" android:textSize="12sp" /><TextView android:id="@+id/widget_action_button" android:layout_width="wrap_content" android:layout_height="38dp" android:layout_marginTop="10dp" android:paddingStart="16dp" android:paddingEnd="16dp" android:gravity="center" android:background="@drawable/go_back_widget_action_bg" android:text="Open" android:textColor="#F5FAF2" android:textSize="12sp" android:textStyle="bold" /></LinearLayout>`;
const focusWidgetLayout = `<?xml version="1.0" encoding="utf-8"?><LinearLayout xmlns:android="http://schemas.android.com/apk/res/android" android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="14dp" android:gravity="center_vertical" android:background="@drawable/go_back_widget_bg"><TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="GO BACK · FOCUS" android:textColor="#B8F36B" android:textSize="10sp" android:textStyle="bold" android:letterSpacing="0.08" /><TextView android:id="@+id/widget_focus_title" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="6dp" android:text="Focus session" android:textColor="#F5FAF2" android:textSize="17sp" android:textStyle="bold" /><Chronometer android:id="@+id/widget_focus_timer" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="3dp" android:textColor="#F5FAF2" android:textSize="25sp" android:textStyle="bold" /><TextView android:id="@+id/widget_focus_subtitle" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="2dp" android:text="Make space for deep work" android:textColor="#C8D9CC" android:textSize="11sp" /><TextView android:id="@+id/widget_focus_button" android:layout_width="wrap_content" android:layout_height="36dp" android:layout_marginTop="8dp" android:paddingStart="14dp" android:paddingEnd="14dp" android:gravity="center" android:background="@drawable/go_back_widget_action_bg" android:text="Open Focus" android:textColor="#F5FAF2" android:textSize="11sp" android:textStyle="bold" /></LinearLayout>`;
const styles = `<?xml version="1.0" encoding="utf-8"?><resources><style name="GoBackWidgetAction"><item name="android:layout_width">0dp</item><item name="android:layout_height">42dp</item><item name="android:layout_weight">1</item><item name="android:layout_marginEnd">5dp</item><item name="android:gravity">center</item><item name="android:background">@drawable/go_back_widget_action_bg</item><item name="android:textColor">#F5FAF2</item><item name="android:textSize">11sp</item><item name="android:textStyle">bold</item></style></resources>`;
const widgetBackground = `<?xml version="1.0" encoding="utf-8"?><shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#17352A" /><corners android:radius="22dp" /></shape>`;
const actionBackground = `<?xml version="1.0" encoding="utf-8"?><shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#294638" /><corners android:radius="12dp" /></shape>`;
const widgetInfo = `<?xml version="1.0" encoding="utf-8"?><appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" android:minWidth="250dp" android:minHeight="120dp" android:targetCellWidth="4" android:targetCellHeight="2" android:updatePeriodMillis="0" android:initialLayout="@layout/go_back_widget" android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen" />`;
const singleActionInfo = `<?xml version="1.0" encoding="utf-8"?><appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" android:minWidth="160dp" android:minHeight="110dp" android:targetCellWidth="2" android:targetCellHeight="2" android:updatePeriodMillis="0" android:initialLayout="@layout/go_back_single_action_widget" android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen" />`;
const focusWidgetInfo = `<?xml version="1.0" encoding="utf-8"?><appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" android:minWidth="180dp" android:minHeight="120dp" android:targetCellWidth="2" android:targetCellHeight="2" android:updatePeriodMillis="0" android:initialLayout="@layout/go_back_focus_widget" android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen" />`;

function withGoBackWidget(config) {
  config = withAndroidManifest(config, config => {
    const manifest = config.modResults.manifest;
    manifest['uses-permission'] = manifest['uses-permission'] ?? [];
    manifest.queries = manifest.queries ?? [];
    const addPermission = name => {
      if (!manifest['uses-permission'].some(item => item.$?.['android:name'] === name)) {
        manifest['uses-permission'].push({ $: { 'android:name': name } });
      }
    };
    addPermission('android.permission.PACKAGE_USAGE_STATS');
    addPermission('android.permission.RECEIVE_BOOT_COMPLETED');
    addPermission('android.permission.SCHEDULE_EXACT_ALARM');

    const hasLauncherQuery = manifest.queries.some(query => (query.intent ?? []).some(intent => (intent.action ?? []).some(action => action.$?.['android:name'] === 'android.intent.action.MAIN') && (intent.category ?? []).some(category => category.$?.['android:name'] === 'android.intent.category.LAUNCHER')));
    if (!hasLauncherQuery) {
      manifest.queries.push({ intent: [{ action: [{ $: { 'android:name': 'android.intent.action.MAIN' } }], category: [{ $: { 'android:name': 'android.intent.category.LAUNCHER' } }] }] });
    }

    const application = manifest.application?.[0];
    if (!application) throw new Error('Go Back native plugin: Android application node was not found.');
    application.service = application.service ?? [];

    if (!application.service.some(service => service.$?.['android:name'] === '.GoBackBlockingService')) {
      application.service.push({
        $: { 'android:name': '.GoBackBlockingService', 'android:label': 'Go Back app blocking', 'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE', 'android:exported': 'true' },
        'intent-filter': [{ action: [{ $: { 'android:name': 'android.accessibilityservice.AccessibilityService' } }] }],
        'meta-data': [{ $: { 'android:name': 'android.accessibilityservice', 'android:resource': '@xml/go_back_accessibility_service' } }]
      });
    }

    if (!application.service.some(service => service.$?.['android:name'] === '.GoBackFocusTileService')) {
      application.service.push({
        $: { 'android:name': '.GoBackFocusTileService', 'android:label': 'Go Back Focus', 'android:icon': '@mipmap/ic_launcher', 'android:permission': 'android.permission.BIND_QUICK_SETTINGS_TILE', 'android:exported': 'true' },
        'intent-filter': [{ action: [{ $: { 'android:name': 'android.service.quicksettings.action.QS_TILE' } }] }]
      });
    }

    if (!application.service.some(service => service.$?.['android:name'] === '.GoBackMaintenanceJobService')) {
      application.service.push({
        $: { 'android:name': '.GoBackMaintenanceJobService', 'android:permission': 'android.permission.BIND_JOB_SERVICE', 'android:exported': 'false' }
      });
    }

    application.receiver = application.receiver ?? [];
    if (!application.receiver.some(receiver => receiver.$?.['android:name'] === '.GoBackBootReceiver')) {
      application.receiver.push({
        $: { 'android:name': '.GoBackBootReceiver', 'android:exported': 'false' },
        'intent-filter': [
          { action: [{ $: { 'android:name': 'android.intent.action.BOOT_COMPLETED' } }, { $: { 'android:name': 'android.intent.action.MY_PACKAGE_REPLACED' } }, { $: { 'android:name': 'android.intent.action.USER_PRESENT' } }] }
        ]
      });
    }

    if (!application.receiver.some(receiver => receiver.$?.['android:name'] === '.GoBackWidgetProvider')) {
      application.receiver.push({
        $: { 'android:name': '.GoBackWidgetProvider', 'android:label': 'Go Back Quick Actions', 'android:exported': 'true' },
        'intent-filter': [{ action: [{ $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } }] }],
        'meta-data': [{ $: { 'android:name': 'android.appwidget.provider', 'android:resource': '@xml/go_back_widget_info' } }]
      });
    }

    const individualWidgets = [
      ['.GoBackFocusWidgetProvider', 'Focus session', '@xml/go_back_focus_widget_info'],
      ['.GoBackSyllabusWidgetProvider', 'Syllabus tracker', '@xml/go_back_single_action_widget_info'],
      ['.GoBackExamsWidgetProvider', 'Exam planner', '@xml/go_back_single_action_widget_info'],
      ['.GoBackNotesWidgetProvider', 'Quick notes', '@xml/go_back_single_action_widget_info'],
      ['.GoBackInsightsWidgetProvider', 'Usage insights', '@xml/go_back_single_action_widget_info'],
      ['.GoBackBlockingWidgetProvider', 'App blocking', '@xml/go_back_single_action_widget_info']
    ];
    for (const [name, label, info] of individualWidgets) {
      if (!application.receiver.some(receiver => receiver.$?.['android:name'] === name)) {
        application.receiver.push({
          $: { 'android:name': name, 'android:label': label, 'android:exported': 'true' },
          'intent-filter': [{ action: [{ $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } }] }],
          'meta-data': [{ $: { 'android:name': 'android.appwidget.provider', 'android:resource': info } }]
        });
      }
    }
    return config;
  });

  config = withDangerousMod(config, ['android', async config => {
    const root = config.modRequest.platformProjectRoot;
    const javaRoot = path.join(root, 'app', 'src', 'main', 'java', 'com', 'goback', 'focus');
    const resRoot = path.join(root, 'app', 'src', 'main', 'res');
    const write = (file, value) => {
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, value);
    };

    write(path.join(javaRoot, 'GoBackWidgetProvider.kt'), provider);
    write(path.join(javaRoot, 'GoBackUsageModule.kt'), usageModule);
    write(path.join(javaRoot, 'GoBackUsagePackage.kt'), usagePackage);
    write(path.join(javaRoot, 'GoBackBlockingModule.kt'), blockingModule);
    write(path.join(javaRoot, 'GoBackBlockingService.kt'), blockingService);
    write(path.join(javaRoot, 'GoBackMaintenanceJobService.kt'), maintenanceService);
    write(path.join(javaRoot, 'GoBackFocusTileService.kt'), tileService);

    const mainApplication = path.join(javaRoot, 'MainApplication.kt');
    if (!fs.existsSync(mainApplication)) throw new Error('Go Back native plugin: MainApplication.kt was not found after Expo prebuild.');
    const mainSource = fs.readFileSync(mainApplication, 'utf8');
    if (!mainSource.includes('add(GoBackUsagePackage())')) {
      const anchor = 'PackageList(this).packages.apply {';
      if (!mainSource.includes(anchor)) throw new Error('Go Back native plugin: could not find PackageList registration in MainApplication.kt.');
      fs.writeFileSync(mainApplication, mainSource.replace(anchor, anchor + '\n              add(GoBackUsagePackage())'));
    }

    write(path.join(resRoot, 'xml', 'go_back_accessibility_service.xml'), accessibilityXml);
    write(path.join(resRoot, 'values', 'go_back_blocking_strings.xml'), '<?xml version="1.0" encoding="utf-8"?><resources><string name="go_back_accessibility_description">Go Back checks visible YouTube, Instagram, Snapchat and selected apps or websites to apply focus blocking rules configured on this device.</string></resources>');
    write(path.join(resRoot, 'layout', 'go_back_widget.xml'), layout);
    write(path.join(resRoot, 'layout', 'go_back_single_action_widget.xml'), singleActionLayout);
    write(path.join(resRoot, 'layout', 'go_back_focus_widget.xml'), focusWidgetLayout);
    write(path.join(resRoot, 'xml', 'go_back_single_action_widget_info.xml'), singleActionInfo);
    write(path.join(resRoot, 'xml', 'go_back_focus_widget_info.xml'), focusWidgetInfo);
    write(path.join(resRoot, 'values', 'go_back_widget_styles.xml'), styles);
    write(path.join(resRoot, 'drawable', 'go_back_widget_bg.xml'), widgetBackground);
    write(path.join(resRoot, 'drawable', 'go_back_widget_action_bg.xml'), actionBackground);
    write(path.join(resRoot, 'xml', 'go_back_widget_info.xml'), widgetInfo);
    return config;
  }]);

  return config;
}

module.exports = withGoBackWidget;
