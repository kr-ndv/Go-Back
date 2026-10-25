const fs = require('fs');
const path = require('path');
const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');

const usageModule = "package com.goback.focus\n\nimport android.app.AppOpsManager\nimport android.app.usage.UsageStatsManager\nimport android.content.Context\nimport android.content.Intent\nimport android.icu.util.Calendar\nimport android.os.Process\nimport android.provider.Settings\nimport com.facebook.react.bridge.Arguments\nimport com.facebook.react.bridge.Promise\nimport com.facebook.react.bridge.ReactApplicationContext\nimport com.facebook.react.bridge.ReactContextBaseJavaModule\nimport com.facebook.react.bridge.ReactMethod\n\nclass GoBackUsageModule(private val appContext: ReactApplicationContext) : ReactContextBaseJavaModule(appContext) {\n  override fun getName() = \"GoBackUsage\"\n  private fun hasAccess(): Boolean {\n    val ops = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager\n    return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName) == AppOpsManager.MODE_ALLOWED\n  }\n  @ReactMethod fun hasUsageAccess(promise: Promise) {\n    try { promise.resolve(hasAccess()) } catch (error: Exception) { promise.reject(\"E_USAGE_ACCESS\", error) }\n  }\n  @ReactMethod fun openUsageAccessSettings(promise: Promise) {\n    try { appContext.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); promise.resolve(true) }\n    catch (error: Exception) { promise.reject(\"E_USAGE_SETTINGS\", error) }\n  }\n  @ReactMethod fun getUsageStats(days: Int, promise: Promise) {\n    try {\n      if (!hasAccess()) { promise.reject(\"E_USAGE_PERMISSION\", \"Usage access has not been granted in Android settings.\"); return }\n      val now = System.currentTimeMillis()\n      val calendar = Calendar.getInstance()\n      calendar.timeInMillis = now\n      calendar.set(Calendar.HOUR_OF_DAY, 0)\n      calendar.set(Calendar.MINUTE, 0)\n      calendar.set(Calendar.SECOND, 0)\n      calendar.set(Calendar.MILLISECOND, 0)\n      calendar.add(Calendar.DAY_OF_YEAR, -(days.coerceIn(1, 30) - 1))\n      val start = calendar.timeInMillis\n      val manager = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager\n      val totals = mutableMapOf<String, Long>()\n      manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)?.forEach { stat ->\n        if (stat.packageName != appContext.packageName && stat.totalTimeInForeground > 0L) totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground\n      }\n      val result = Arguments.createArray()\n      totals.entries.sortedByDescending { it.value }.forEach { entry ->\n        val item = Arguments.createMap()\n        val label = try { val info = appContext.packageManager.getApplicationInfo(entry.key, 0); appContext.packageManager.getApplicationLabel(info).toString() } catch (_: Exception) { entry.key }\n        item.putString(\"packageName\", entry.key); item.putString(\"appName\", label); item.putDouble(\"foregroundTimeMs\", entry.value.toDouble())\n        result.pushMap(item)\n      }\n      promise.resolve(result)\n    } catch (error: Exception) { promise.reject(\"E_USAGE_STATS\", error) }\n  }\n}\n";

const usagePackage = "package com.goback.focus\n\nimport com.facebook.react.ReactPackage\nimport com.facebook.react.bridge.NativeModule\nimport com.facebook.react.bridge.ReactApplicationContext\nimport com.facebook.react.uimanager.ViewManager\n\nclass GoBackUsagePackage : ReactPackage {\n  override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> = listOf(GoBackUsageModule(reactContext), GoBackBlockingModule(reactContext))\n  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> = emptyList()\n}\n";

const blockingModule = "package com.goback.focus\n\nimport android.content.Context\nimport android.content.Intent\nimport android.provider.Settings\nimport android.accessibilityservice.AccessibilityServiceInfo\nimport android.view.accessibility.AccessibilityManager\nimport com.facebook.react.bridge.Arguments\nimport com.facebook.react.bridge.Promise\nimport com.facebook.react.bridge.ReactApplicationContext\nimport com.facebook.react.bridge.ReactContextBaseJavaModule\nimport com.facebook.react.bridge.ReactMethod\nimport org.json.JSONArray\nimport org.json.JSONObject\n\nclass GoBackBlockingModule(private val appContext: ReactApplicationContext) : ReactContextBaseJavaModule(appContext) {\n  override fun getName() = \"GoBackBlocking\"\n  @ReactMethod fun isAccessibilityEnabled(promise: Promise) {\n    try {\n      val manager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager\n      promise.resolve(manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.packageName == appContext.packageName && it.resolveInfo.serviceInfo.name.endsWith(\"GoBackBlockingService\") })\n    } catch (error: Exception) { promise.reject(\"E_BLOCKING_ACCESSIBILITY\", error) }\n  }\n  @ReactMethod fun openAccessibilitySettings(promise: Promise) {\n    try { appContext.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); promise.resolve(true) }\n    catch (error: Exception) { promise.reject(\"E_BLOCKING_SETTINGS\", error) }\n  }\n  @ReactMethod fun getConfig(promise: Promise) {\n    try {\n      val value = JSONObject(appContext.getSharedPreferences(\"go_back_blocking\", 0).getString(\"config\", \"{}\") ?: \"{}\")\n      val result = Arguments.createMap()\n      result.putBoolean(\"blockYoutube\", value.optBoolean(\"blockYoutube\", false)); result.putBoolean(\"blockInstagram\", value.optBoolean(\"blockInstagram\", false))\n      result.putBoolean(\"blockShorts\", value.optBoolean(\"blockShorts\", false)); result.putBoolean(\"blockReels\", value.optBoolean(\"blockReels\", false))\n      result.putBoolean(\"blockOnlyDuringFocus\", value.optBoolean(\"blockOnlyDuringFocus\", false))\n      val channels = value.optJSONArray(\"allowedChannels\") ?: JSONArray(); val array = Arguments.createArray()\n      for (i in 0 until channels.length()) array.pushString(channels.optString(i))\n      result.putArray(\"allowedChannels\", array)\n      val blockedPackages = value.optJSONArray(\"blockedPackages\") ?: JSONArray(); val blockedArray = Arguments.createArray()\n      for (i in 0 until blockedPackages.length()) blockedArray.pushString(blockedPackages.optString(i))\n      result.putArray(\"blockedPackages\", blockedArray); promise.resolve(result)\n    } catch (error: Exception) { promise.reject(\"E_BLOCKING_CONFIG\", error) }\n  }\n  @ReactMethod fun getLaunchableApps(promise: Promise) {\n    try {\n      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)\n      val apps = appContext.packageManager.queryIntentActivities(intent, 0)\n        .mapNotNull { resolve ->\n          val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null\n          if (info.packageName == appContext.packageName) return@mapNotNull null\n          val label = resolve.loadLabel(appContext.packageManager)?.toString()?.trim().orEmpty()\n          if (label.isEmpty()) return@mapNotNull null\n          Pair(info.packageName, label)\n        }\n        .distinctBy { it.first }\n        .sortedBy { it.second.lowercase() }\n      val result = Arguments.createArray()\n      apps.forEach { (packageName, label) ->\n        val item = Arguments.createMap()\n        item.putString(\"packageName\", packageName)\n        item.putString(\"appName\", label)\n        result.pushMap(item)\n      }\n      promise.resolve(result)\n    } catch (error: Exception) { promise.reject(\"E_BLOCKING_APPS\", error) }\n  }\n\n  @ReactMethod fun setFocusActive(active: Boolean, promise: Promise) {\n    try { appContext.getSharedPreferences(\"go_back_blocking\", 0).edit().putBoolean(\"focus_active\", active).apply(); promise.resolve(true) }\n    catch (error: Exception) { promise.reject(\"E_BLOCKING_FOCUS\", error) }\n  }\n  @ReactMethod fun setConfig(configJson: String, promise: Promise) {\n    try {\n      val input = JSONObject(configJson); val channels = input.optJSONArray(\"allowedChannels\") ?: JSONArray(); val clean = JSONArray()\n      for (i in 0 until channels.length()) { val name = channels.optString(i).trim().take(80); if (name.isNotEmpty() && !contains(clean, name)) clean.put(name) }\n      val blockedInput = input.optJSONArray(\"blockedPackages\") ?: JSONArray(); val blocked = JSONArray()\n      for (i in 0 until blockedInput.length()) {\n        val packageName = blockedInput.optString(i).trim().take(180)\n        if (packageName.contains('.') && packageName.matches(Regex(\"[A-Za-z0-9_.]+\")) && packageName != appContext.packageName && !contains(blocked, packageName)) blocked.put(packageName)\n      }\n      val output = JSONObject(); output.put(\"blockYoutube\", input.optBoolean(\"blockYoutube\", false)); output.put(\"blockInstagram\", input.optBoolean(\"blockInstagram\", false))\n      output.put(\"blockShorts\", input.optBoolean(\"blockShorts\", false)); output.put(\"blockReels\", input.optBoolean(\"blockReels\", false)); output.put(\"blockOnlyDuringFocus\", input.optBoolean(\"blockOnlyDuringFocus\", false)); output.put(\"allowedChannels\", clean); output.put(\"blockedPackages\", blocked)\n      appContext.getSharedPreferences(\"go_back_blocking\", 0).edit().putString(\"config\", output.toString()).apply(); promise.resolve(true)\n    } catch (error: Exception) { promise.reject(\"E_BLOCKING_CONFIG\", error) }\n  }\n  private fun contains(array: JSONArray, value: String): Boolean { for (i in 0 until array.length()) if (array.optString(i).equals(value, true)) return true; return false }\n}\n";

const blockingService = `package com.goback.focus

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class GoBackBlockingService : AccessibilityService() {
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private var lastActionAt = 0L
  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return
    val pkg = event.packageName?.toString() ?: return
    val prefs = getSharedPreferences("go_back_blocking", 0)
    val config = try { JSONObject(prefs.getString("config", "{}") ?: "{}") } catch (_: Exception) { return }
    if (config.optBoolean("blockOnlyDuringFocus", false) && !prefs.getBoolean("focus_active", false)) return
    val blockedPackages = config.optJSONArray("blockedPackages") ?: JSONArray()
    for (i in 0 until blockedPackages.length()) {
      if (blockedPackages.optString(i) == pkg) { sendHome(); return }
    }
    if (pkg != YOUTUBE && pkg != INSTAGRAM) return
    
    val root = rootInActiveWindow ?: return
    if (pkg == YOUTUBE) {
      if (config.optBoolean("blockShorts", false) && hasSelectedLabel(root, "shorts")) { sendBack(); return }
      if (config.optBoolean("blockYoutube", false) && isVideoPlaying(root)) {
        val allowed = config.optJSONArray("allowedChannels") ?: JSONArray()
        if (!matchesChannel(root, allowed)) { closeVideoAndMiniPlayer(); return }
      }
    }
    if (pkg == INSTAGRAM && config.optBoolean("blockReels", false) && hasSelectedLabel(root, "reels")) sendBack()
  }
  private fun sendHome() { val now = System.currentTimeMillis(); if (now - lastActionAt < 1200) return; lastActionAt = now; performGlobalAction(GLOBAL_ACTION_HOME) }
  private fun sendBack() { val now = System.currentTimeMillis(); if (now - lastActionAt < 1500) return; lastActionAt = now; performGlobalAction(GLOBAL_ACTION_BACK) }
  private fun closeVideoAndMiniPlayer() { val now = System.currentTimeMillis(); if (now - lastActionAt < 1500) return; lastActionAt = now; performGlobalAction(GLOBAL_ACTION_BACK); handler.postDelayed({ if (rootInActiveWindow?.packageName?.toString() == YOUTUBE) performGlobalAction(GLOBAL_ACTION_BACK) }, 350) }
  private fun closeVideoAndMiniPlayer() { val now = System.currentTimeMillis(); if (now - lastActionAt < 1500) return; lastActionAt = now; performGlobalAction(GLOBAL_ACTION_BACK); handler.postDelayed({ if (rootInActiveWindow?.packageName?.toString() == YOUTUBE) performGlobalAction(GLOBAL_ACTION_BACK) }, 350) }
  private fun hasSelectedLabel(node: AccessibilityNodeInfo, label: String): Boolean {
    val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase(Locale.ROOT)
    if (text.contains(label) && (node.isSelected || node.isChecked || node.isAccessibilityFocused)) return true
    for (i in 0 until node.childCount) { val child = node.getChild(i) ?: continue; val found = hasSelectedLabel(child, label); child.recycle(); if (found) return true }
    return false
  }
  private fun matchesChannel(node: AccessibilityNodeInfo, channels: JSONArray): Boolean {
    if (channels.length() == 0) return false
    val visibleText = " " + collectText(node).lowercase(Locale.ROOT).replace(Regex("[^\\\\p{L}\\\\p{N}@]+"), " ").trim() + " "
    for (i in 0 until channels.length()) {
      val raw = channels.optString(i).trim().removePrefix("@").lowercase(Locale.ROOT)
      val name = raw.replace(Regex("[^\\\\p{L}\\\\p{N}]+"), " ").trim()
      if (name.length >= 3 && visibleText.contains(" $name ")) return true
      if (raw.length >= 3 && visibleText.contains(" @$raw ")) return true
    }
    return false
  }
  private fun isVideoPlaying(node: AccessibilityNodeInfo): Boolean {
    val text = collectText(node).lowercase(Locale.ROOT)
    // YouTube often hides playback controls from its accessibility tree until tapped.
    // Detect the watch page itself instead of requiring a visible Pause button, which
    // caused the channel limit to silently do nothing on many devices.
    return text.contains("video player") ||
      text.contains("seek bar") ||
      (text.contains("comments") && text.contains("subscribe") && text.contains("share"))
  }
  private fun collectText(node: AccessibilityNodeInfo): String {
    val result = StringBuilder(); val text = node.text?.toString(); val desc = node.contentDescription?.toString()
    if (!text.isNullOrBlank()) result.append(text).append(' '); if (!desc.isNullOrBlank()) result.append(desc).append(' ')
    for (i in 0 until node.childCount) { val child = node.getChild(i) ?: continue; result.append(collectText(child)).append(' '); child.recycle() }
    return result.toString()
  }
  override fun onInterrupt() = Unit
  companion object { private const val YOUTUBE = "com.google.android.youtube"; private const val INSTAGRAM = "com.instagram.android" }
}
`;

const accessibilityXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<accessibility-service xmlns:android=\"http://schemas.android.com/apk/res/android\" android:accessibilityEventTypes=\"typeWindowStateChanged|typeWindowContentChanged|typeViewSelected|typeViewFocused\" android:accessibilityFeedbackType=\"feedbackGeneric\" android:notificationTimeout=\"150\" android:canRetrieveWindowContent=\"true\" android:description=\"@string/go_back_accessibility_description\" android:accessibilityFlags=\"flagReportViewIds|flagRetrieveInteractiveWindows\" />\n";

const provider = `package com.goback.focus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class GoBackWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_widget)
      bindAction(context, views, R.id.widget_focus, "focus", 100)
      bindAction(context, views, R.id.widget_tasks, "tasks", 101)
      bindAction(context, views, R.id.widget_exams, "exams", 102)
      bindAction(context, views, R.id.widget_habits, "habits", 103)
      manager.updateAppWidget(widgetId, views)
    }
  }

  private fun bindAction(context: Context, views: RemoteViews, viewId: Int, route: String, requestCode: Int) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///" + route), context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      requestCode,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    views.setOnClickPendingIntent(viewId, pendingIntent)
  }
}
`;

const singleProvider = `abstract class GoBackSingleActionWidgetProvider : AppWidgetProvider() {
  protected abstract val route: String
  protected abstract val title: String
  protected abstract val subtitle: String

  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    appWidgetIds.forEach { widgetId ->
      val views = RemoteViews(context.packageName, R.layout.go_back_single_action_widget)
      views.setTextViewText(R.id.widget_action_title, title)
      views.setTextViewText(R.id.widget_action_subtitle, subtitle)
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse("goback:///" + route), context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(context, 200 + route.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
      views.setOnClickPendingIntent(R.id.widget_action_button, pendingIntent)
      manager.updateAppWidget(widgetId, views)
    }
  }
}

class GoBackFocusWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "focus"
  override val title = "Focus session"
  override val subtitle = "Make space for deep work"
}
class GoBackTasksWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "tasks"
  override val title = "Your tasks"
  override val subtitle = "Capture the next small step"
}
class GoBackNotesWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "notes"
  override val title = "Quick notes"
  override val subtitle = "Save an idea before it slips away"
}
class GoBackExamsWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "exams"
  override val title = "Exam planner"
  override val subtitle = "Keep the next date in sight"
}
class GoBackSyllabusWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "syllabus"
  override val title = "Syllabus tracker"
  override val subtitle = "Pick up where you left off"
}
class GoBackHabitsWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "habits"
  override val title = "Daily habits"
  override val subtitle = "Show up for yourself today"
}
class GoBackGoalsWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "goals"
  override val title = "Goals"
  override val subtitle = "Make progress visible"
}
class GoBackInsightsWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "usage"
  override val title = "Usage insights"
  override val subtitle = "Review your time and trends"
}
class GoBackBlockingWidgetProvider : GoBackSingleActionWidgetProvider() {
  override val route = "blocking"
  override val title = "App blocking"
  override val subtitle = "Manage your distraction rules"
}`;

const layout = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="14dp"
    android:background="@drawable/go_back_widget_bg">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="GO BACK"
        android:textColor="#B8F36B"
        android:textSize="10sp"
        android:textStyle="bold"
        android:letterSpacing="0.15" />
    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:text="Make today count."
        android:textColor="#F5FAF2"
        android:textSize="18sp"
        android:textStyle="bold" />
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginTop="10dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:weightSum="4">
        <TextView android:id="@+id/widget_focus" style="@style/GoBackWidgetAction" android:text="Focus" />
        <TextView android:id="@+id/widget_tasks" style="@style/GoBackWidgetAction" android:text="Tasks" />
        <TextView android:id="@+id/widget_exams" style="@style/GoBackWidgetAction" android:text="Exams" />
        <TextView android:id="@+id/widget_habits" style="@style/GoBackWidgetAction" android:text="Habits" />
    </LinearLayout>
</LinearLayout>
`;

const styles = `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="GoBackWidgetAction">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">42dp</item>
        <item name="android:layout_weight">1</item>
        <item name="android:layout_marginEnd">5dp</item>
        <item name="android:gravity">center</item>
        <item name="android:background">@drawable/go_back_widget_action_bg</item>
        <item name="android:textColor">#F5FAF2</item>
        <item name="android:textSize">11sp</item>
        <item name="android:textStyle">bold</item>
    </style>
</resources>
`;

const widgetBackground = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#17352A" />
    <corners android:radius="22dp" />
</shape>
`;

const actionBackground = `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#294638" />
    <corners android:radius="12dp" />
</shape>
`;

const widgetInfo = `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="120dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/go_back_widget"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
`;

const singleActionLayout = `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="14dp"
    android:gravity="center_vertical"
    android:background="@drawable/go_back_widget_bg">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="GO BACK · QUICK ACTION"
        android:textColor="#B8F36B"
        android:textSize="10sp"
        android:textStyle="bold"
        android:letterSpacing="0.08" />
    <TextView
        android:id="@+id/widget_action_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="7dp"
        android:text="Your action"
        android:textColor="#F5FAF2"
        android:textSize="20sp"
        android:textStyle="bold" />
    <TextView
        android:id="@+id/widget_action_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="3dp"
        android:text="Open Go Back"
        android:textColor="#C8D9CC"
        android:textSize="12sp" />
    <TextView
        android:id="@+id/widget_action_button"
        android:layout_width="wrap_content"
        android:layout_height="38dp"
        android:layout_marginTop="10dp"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:gravity="center"
        android:background="@drawable/go_back_widget_action_bg"
        android:text="Open"
        android:textColor="#F5FAF2"
        android:textSize="12sp"
        android:textStyle="bold" />
</LinearLayout>
`;

const singleActionInfo = `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="160dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="0"
    android:initialLayout="@layout/go_back_single_action_widget"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
`;

function withGoBackWidget(config) {
  config = withAndroidManifest(config, config => {
    config.modResults.manifest['uses-permission'] = config.modResults.manifest['uses-permission'] ?? [];
    config.modResults.manifest.queries = config.modResults.manifest.queries ?? [];
    const hasLauncherQuery = config.modResults.manifest.queries.some(query =>
      (query.intent ?? []).some(intent =>
        (intent.action ?? []).some(action => action.$?.['android:name'] === 'android.intent.action.MAIN') &&
        (intent.category ?? []).some(category => category.$?.['android:name'] === 'android.intent.category.LAUNCHER')
      )
    );
    if (!hasLauncherQuery) {
      config.modResults.manifest.queries.push({
        intent: [{
          action: [{ $: { 'android:name': 'android.intent.action.MAIN' } }],
          category: [{ $: { 'android:name': 'android.intent.category.LAUNCHER' } }],
        }],
      });
    }

    if (!config.modResults.manifest['uses-permission'].some(item => item.$?.['android:name'] === 'android.permission.PACKAGE_USAGE_STATS')) {
      config.modResults.manifest['uses-permission'].push({ $: { 'android:name': 'android.permission.PACKAGE_USAGE_STATS' } });
    }
    const application = config.modResults.manifest.application?.[0];
    if (!application) throw new Error('Go Back widget: Android application node was not found.');
    application.service = application.service ?? [];
    if (!application.service.some(service => service.$?.['android:name'] === '.GoBackBlockingService')) {
      application.service.push({ $: { 'android:name': '.GoBackBlockingService', 'android:label': 'Go Back app blocking', 'android:permission': 'android.permission.BIND_ACCESSIBILITY_SERVICE', 'android:exported': 'true' }, 'intent-filter': [{ action: [{ $: { 'android:name': 'android.accessibilityservice.AccessibilityService' } }] }], 'meta-data': [{ $: { 'android:name': 'android.accessibilityservice', 'android:resource': '@xml/go_back_accessibility_service' } }] });
    }
    application.receiver = application.receiver ?? [];
    const exists = application.receiver.some(receiver => receiver.$?.['android:name'] === '.GoBackWidgetProvider');
    if (!exists) {
      application.receiver.push({
        $: {
          'android:name': '.GoBackWidgetProvider',
          'android:label': 'Go Back Quick Actions',
          'android:exported': 'true',
        },
        'intent-filter': [{ action: [{ $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } }] }],
        'meta-data': [{ $: { 'android:name': 'android.appwidget.provider', 'android:resource': '@xml/go_back_widget_info' } }],
      });
    }
    const individualWidgets = [
      ['.GoBackFocusWidgetProvider', 'Focus session'],
      ['.GoBackTasksWidgetProvider', 'Tasks'],
      ['.GoBackNotesWidgetProvider', 'Quick notes'],
      ['.GoBackExamsWidgetProvider', 'Exam planner'],
      ['.GoBackSyllabusWidgetProvider', 'Syllabus tracker'],
      ['.GoBackHabitsWidgetProvider', 'Daily habits'],
      ['.GoBackGoalsWidgetProvider', 'Goals'],
      ['.GoBackInsightsWidgetProvider', 'Usage insights'],
      ['.GoBackBlockingWidgetProvider', 'App blocking'],
    ];
    for (const [name, label] of individualWidgets) {
      if (!application.receiver.some(receiver => receiver.$?.['android:name'] === name)) {
        application.receiver.push({
          $: { 'android:name': name, 'android:label': label, 'android:exported': 'true' },
          'intent-filter': [{ action: [{ $: { 'android:name': 'android.appwidget.action.APPWIDGET_UPDATE' } }] }],
          'meta-data': [{ $: { 'android:name': 'android.appwidget.provider', 'android:resource': '@xml/go_back_single_action_widget_info' } }],
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
    write(path.join(javaRoot, 'GoBackWidgetProvider.kt'), provider + singleProvider);
    write(path.join(javaRoot, 'GoBackUsageModule.kt'), usageModule);
    write(path.join(javaRoot, 'GoBackUsagePackage.kt'), usagePackage);
    write(path.join(javaRoot, 'GoBackBlockingModule.kt'), blockingModule);
    write(path.join(javaRoot, 'GoBackBlockingService.kt'), blockingService);
    const mainApplication = path.join(javaRoot, 'MainApplication.kt');
    if (!fs.existsSync(mainApplication)) throw new Error('Go Back usage stats: MainApplication.kt was not found after Expo prebuild.');
    const mainSource = fs.readFileSync(mainApplication, 'utf8');
    if (!mainSource.includes('add(GoBackUsagePackage())')) {
      const packageAnchor = 'PackageList(this).packages.apply {';
      if (!mainSource.includes(packageAnchor)) throw new Error('Go Back usage stats: could not find PackageList registration in MainApplication.kt.');
      fs.writeFileSync(mainApplication, mainSource.replace(packageAnchor, packageAnchor + '\n              add(GoBackUsagePackage())'));
    }
    write(path.join(resRoot, 'xml', 'go_back_accessibility_service.xml'), accessibilityXml);
    write(path.join(resRoot, 'values', 'go_back_blocking_strings.xml'), '<?xml version="1.0" encoding="utf-8"?><resources><string name="go_back_accessibility_description">Go Back checks visible YouTube and Instagram screens to apply blocking rules configured on this device.</string></resources>');
    write(path.join(resRoot, 'layout', 'go_back_widget.xml'), layout);
    write(path.join(resRoot, 'layout', 'go_back_single_action_widget.xml'), singleActionLayout);
    write(path.join(resRoot, 'xml', 'go_back_single_action_widget_info.xml'), singleActionInfo);
    write(path.join(resRoot, 'values', 'go_back_widget_styles.xml'), styles);
    write(path.join(resRoot, 'drawable', 'go_back_widget_bg.xml'), widgetBackground);
    write(path.join(resRoot, 'drawable', 'go_back_widget_action_bg.xml'), actionBackground);
    write(path.join(resRoot, 'xml', 'go_back_widget_info.xml'), widgetInfo);
    return config;
  }]);
  return config;
}

module.exports = withGoBackWidget;
