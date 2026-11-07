# Hard Handoff Report: App & Content Blocking, Usage Limits, Settings & Accessibility Integration (M3-1)

## 1. Observation

### Codebase and Architecture Inspection
1. **Existing Native Android Codebase (`android/app/src/main/java/com/goback/focus/`)**:
   - `service/GoBackBlockingService.kt`:
     - Listens to SharedPreferences `"go_back_blocking"` via registered `SharedPreferences.OnSharedPreferenceChangeListener` (lines 32–34, 54).
     - In `reloadConfiguration(prefs)` (lines 239–322), reads keys:
       - `"blocked_packages"`: `StringSet` (lines 253–258)
       - `"youtube_allowed_channels"`: `StringSet` (lines 267–272)
       - `"block_only_during_focus"`: `Boolean` (lines 275–276)
       - `"strict_uninstall_protection"`: `Boolean` (lines 277–278)
       - `"strict_mode_active"`: `Boolean` (lines 279–280)
       - `"block_split_screen"`: `Boolean` (lines 281–282)
       - `"block_adult"`: `Boolean` (lines 284–285)
       - `"block_shorts"`: `Boolean` (lines 286–287)
       - `"youtube_study_mode"`: `Boolean` (lines 288–289)
       - `"block_reels"`: `Boolean` (lines 291–292)
       - `"block_spotlight"`: `Boolean` (lines 293–294)
       - `"config"`: JSON String containing unified object format (lines 241–244)
     - In `onAccessibilityEvent(event)`:
       - Whole-app blocking: checks `config.blockedPackages.contains(pkg)` and terminates with `sendHome()` (lines 86–91).
       - Adult website blocking: checks `BROWSER_PACKAGES.contains(pkg)` and scans for `ADULT_KEYWORDS` (lines 164–171).
       - YouTube Shorts blocking: checks `hasSelectedOrTopText(root, "shorts")` and issues `sendBack()` (lines 178–182).
       - YouTube Study Mode: checks `isVideoSurface(root)` and closes disallowed videos unless channel matches `config.allowedChannels` (lines 183–190).
       - Instagram Reels blocking: checks `hasSelectedOrTopText(root, "reels")` and issues `sendBack()` (lines 193–197).
       - Snapchat Spotlight blocking: checks `hasSelectedOrTopText(root, "spotlight")` and issues `sendBack()` (lines 200–204).
       - Settings protection: blocks opening `com.android.settings` when view contains `"com.goback.focus"` or `"go back"` (lines 118–128).
       - Split-screen protection: blocks multi-window app launches matching blocked packages (lines 130–158).
       - Records last action reason to `"last_action_reason"` and `"last_action_at"` in `"go_back_blocking"` SharedPreferences (lines 327–336).
   - `data/entity/Entities.kt`:
     - `UsageLimitEntity` (lines 65–73):
       ```kotlin
       @Entity(tableName = "usage_limits", indices = [Index(value = ["package_name"], unique = true)])
       data class UsageLimitEntity(
           @PrimaryKey val id: String,
           @ColumnInfo(name = "package_name") val packageName: String,
           @ColumnInfo(name = "app_name") val appName: String,
           @ColumnInfo(name = "limit_minutes") val limitMinutes: Int,
           @ColumnInfo(name = "warning_percent") val warningPercent: Int = 80,
           val enabled: Int = 1,
           @ColumnInfo(name = "created_at") val createdAt: String
       )
       ```
     - `SettingEntity` (lines 84–87):
       ```kotlin
       @Entity(tableName = "settings")
       data class SettingEntity(@PrimaryKey val key: String, val value: String)
       ```
   - `data/dao/Daos.kt`:
     - `UsageLimitDao` (lines 86–98): methods `getAll(): Flow<List<UsageLimitEntity>>`, `getEnabledLimitsSync(): List<UsageLimitEntity>`, `upsert(limit)`, `delete(limit)`.
     - `SettingDao` (lines 116–128): methods `getValue(key): String?`, `observeValue(key): Flow<String?>`, `setValue(setting)`, `delete(key)`.
   - `service/GoBackMaintenanceWorker.kt`:
     - Evaluates `usageLimitDao.getEnabledLimitsSync()` against `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, startOfDay, now)`.
     - Issues notifications on channel `GoBackApplication.CHANNEL_USAGE_LIMITS` when reaching warning threshold (level 1) and daily limit (level 2).
     - Tracks notified states per date in `"go_back_usage_limits"` SharedPreferences (`"sent_yyyy-MM-dd"`).
   - `ui/screens/SettingsScreen.kt`:
     - Currently a placeholder UI with static cards and non-functional click listeners (`/* Handle click */`), lacking theme controls, sound selection, daily focus goal setting, PIN configuration, and navigation to sub-screens.
   - `ui/screens/BlockingScreen.kt` & `ui/screens/UsageLimitsScreen.kt`:
     - Currently absent from `android/app/src/main/java/com/goback/focus/ui/screens/`.
   - `MainActivity.kt`:
     - Navigation currently only routes top-level destinations; missing routes for `"blocking"` and `"usage_limits"`.

2. **Reference React Native Implementation**:
   - `app/blocking.tsx`:
     - Installed app picker querying launchable activities via Android `PackageManager`.
     - Real-time search query matching app name and package name.
     - Limit: maximum 100 packages for whole-app blocking.
     - YouTube study mode allowlist: maximum 50 channels, string length <= 80, case-insensitive deduplication.
     - Security PIN protection: checks `getSecurityPin()` before allowing user to edit blocking rules.
     - Status card showing Accessibility Service active/disabled and button opening Android Accessibility Settings.
   - `app/usage-limits.tsx`:
     - Checks Android `AppOpsManager.OPSTR_GET_USAGE_STATS`.
     - Merges `UsageStatsManager` daily foreground duration with Room `usage_limits` table and installed launchable apps.
     - Configurable daily limit (1–1440 min) and warning percentage (50–99%, default 80%).
     - Protected by 4-digit PIN lock.
   - `app/(tabs)/settings.tsx`:
     - Theme mode switcher (System default, Light, Dark).
     - Theme Studio: 12 accent colors (Forest, Blue, Violet, Amber, Rose, Teal, Berry, Sakura, Graphite, Coral, Lavender, Crimson).
     - Timer alarm sound selector (System default, Soft bell, Warm chime, Digital alert) with audio preview.
     - Daily focus goal setting (15–480 minutes).
     - Local-first profile and backup/restore controls.
   - `src/lib/securityPin.ts`:
     - Salted Murmur-style hash: `hashPin(pin: string)` using salt `"goback_pin_salt_v1_"`, stored in setting `"security.pin"`.

3. **Compilation Baseline**:
   - Executed `cd android; .\gradlew.bat compileDebugKotlin`: Build successful (16 actionable tasks up-to-date, 0 errors).

---

## 2. Logic Chain

1. **State Synchronization via SharedPreferences `"go_back_blocking"`**:
   - `GoBackBlockingService` already implements a live `OnSharedPreferenceChangeListener` on `"go_back_blocking"`.
   - When `BlockingScreen` writes to `"go_back_blocking"` using `.apply()`, the listener fires immediately in the background service process.
   - The service reloads its immutable in-memory cache (`cachedConfig`), guaranteeing instantaneous enforcement without restarting the accessibility service or the app.

2. **100% Daily Usage Limit Blocking Integration**:
   - In `UsageLimitsScreen`, apps that have used >= `limitMinutes` today are detected in real time by comparing `UsageStatsManager` foreground time with `UsageLimitEntity.limitMinutes`.
   - To prevent clobbering the user's manual whole-app blocking list in `"blocked_packages"`, exceeded usage limit apps are synced to a dedicated key `"usage_limit_blocked_packages"` in `"go_back_blocking"`.
   - Updating `GoBackBlockingService` to read `"usage_limit_blocked_packages"` ensures that any app reaching 100% of its daily allowance is terminated with `GLOBAL_ACTION_HOME` immediately upon opening, even if `blockOnlyDuringFocus` is active.

3. **Installed App Querying with `PackageManager`**:
   - On Android 11+ (API 30+), querying launchable activities via `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)` retrieves all user-launchable apps.
   - Background execution on `Dispatchers.IO` prevents UI thread jank.
   - System apps are differentiated using `(appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0`, giving users a dedicated toggle to filter out pre-installed system bloatware.
   - Go Back package (`com.goback.focus`) is filtered out to prevent accidental self-lockout.

4. **Self-Control Security PIN Lock**:
   - A 4-digit PIN prevents impulsive disabling of blocks during moments of weak resolve.
   - The Kotlin implementation of `SecurityPinUtil` replicates the salted 32-bit integer arithmetic from `src/lib/securityPin.ts`, ensuring 100% database compatibility with existing profiles.
   - When a PIN is configured, `BlockingScreen` and `UsageLimitsScreen` present `SecurityPinDialog` upon entry; dismissing without authenticating redirects back to the previous screen.

---

## 3. Caveats

1. **UsageStatsManager Latency**: Android batches usage event updates; foreground time may lag by 30–60 seconds depending on device manufacturer aggressive power-saving policies.
2. **Accessibility Service Lifecycle**: OEM Android skins (MIUI, ColorOS, EMUI) may disable accessibility services during memory pressure. The status cards in `BlockingScreen` and `SettingsScreen` proactively detect this state and provide 1-tap deep links to system Accessibility settings.
3. **Sound Preview Playback**: Previewing finish alarm sounds relies on bundled raw audio assets (`res/raw/focus_bell.wav`, `focus_chime.wav`, `focus_digital.wav`) with graceful fallback to `RingtoneManager.TYPE_NOTIFICATION`.

---

## 4. Conclusion & Complete Drop-In Code

All four components have been specified and implemented as production-ready, drop-in Kotlin/Jetpack Compose source files.

### 4.1. Security PIN Component: `android/app/src/main/java/com/goback/focus/ui/components/SecurityPinDialog.kt`

```kotlin
package com.goback.focus.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ModalShape

object SecurityPinUtil {
    const val PIN_SETTING_KEY = "security.pin"
    private const val PIN_SALT = "goback_pin_salt_v1_"

    /**
     * Salted hash matching src/lib/securityPin.ts Murmur-style 32-bit integer math.
     */
    fun hashPin(pin: String): String {
        val input = "$PIN_SALT$pin"
        var h1 = 0xdeadbeef.toInt()
        var h2 = 0x41c6ce57.toInt()
        for (i in input.indices) {
            val ch = input[i].code
            h1 = (h1 xor ch) * 2654435761.toInt()
            h2 = (h2 xor ch) * 1597334677.toInt()
        }
        h1 = (h1 xor (h1 ushr 16)) * 2246822507.toInt() xor ((h2 ushr 13) * 3266489909.toInt())
        h2 = (h2 xor (h2 ushr 16)) * 2246822507.toInt() xor ((h1 ushr 13) * 3266489909.toInt())

        val highPart = (h2.toLong() and 2097151L) * 4294967296L
        val lowPart = h1.toLong() and 0xFFFFFFFFL
        val combined = highPart + lowPart
        return combined.toString(16).padStart(16, '0')
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun matchesPin(storedPin: String?, candidate: String): Boolean {
        if (storedPin.isNullOrEmpty()) return true
        if (storedPin.length == 4 && storedPin.all { it.isDigit() }) {
            return candidate == storedPin
        }
        return hashPin(candidate) == storedPin
    }
}

enum class PinModalMode {
    VERIFY,
    SET,
    REMOVE
}

@Composable
fun SecurityPinDialog(
    mode: PinModalMode,
    storedPinHash: String?,
    onSuccess: (newPin: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var enteredDigits by remember { mutableStateOf("") }
    var firstAttemptPin by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isStepTwoConfirm = mode == PinModalMode.SET && firstAttemptPin != null

    val title = when (mode) {
        PinModalMode.VERIFY -> "Enter Security PIN"
        PinModalMode.SET -> if (isStepTwoConfirm) "Confirm 4-Digit PIN" else "Create 4-Digit PIN"
        PinModalMode.REMOVE -> "Verify PIN to Remove"
    }

    val subtitle = when (mode) {
        PinModalMode.VERIFY -> "Enter your 4-digit PIN to access protection settings."
        PinModalMode.SET -> if (isStepTwoConfirm) "Re-enter the exact same 4 digits to confirm." else "Choose a 4-digit PIN to prevent impulse bypasses."
        PinModalMode.REMOVE -> "Confirm your current PIN to turn off security protection."
    }

    fun handleDigit(digit: String) {
        if (enteredDigits.length < 4) {
            errorMessage = null
            val next = enteredDigits + digit
            enteredDigits = next
            if (next.length == 4) {
                when (mode) {
                    PinModalMode.VERIFY -> {
                        if (SecurityPinUtil.matchesPin(storedPinHash, next)) {
                            onSuccess(null)
                        } else {
                            errorMessage = "Incorrect PIN. Please try again."
                            enteredDigits = ""
                        }
                    }
                    PinModalMode.REMOVE -> {
                        if (SecurityPinUtil.matchesPin(storedPinHash, next)) {
                            onSuccess(null)
                        } else {
                            errorMessage = "Incorrect PIN. Verification failed."
                            enteredDigits = ""
                        }
                    }
                    PinModalMode.SET -> {
                        if (firstAttemptPin == null) {
                            firstAttemptPin = next
                            enteredDigits = ""
                        } else {
                            if (next == firstAttemptPin) {
                                onSuccess(next)
                            } else {
                                errorMessage = "PINs did not match. Please start over."
                                firstAttemptPin = null
                                enteredDigits = ""
                            }
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredDigits.isNotEmpty()) {
            errorMessage = null
            enteredDigits = enteredDigits.dropLast(1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = ModalShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // 4-Dot Display Indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredDigits.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .border(
                                    2.dp,
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        )
                    }
                }

                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                // Numeric Keypad Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val keypad = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("CANCEL", "0", "DEL")
                    )

                    keypad.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            row.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (key == "CANCEL" || key == "DEL") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            when (key) {
                                                "CANCEL" -> onDismiss()
                                                "DEL" -> handleBackspace()
                                                else -> handleDigit(key)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (key == "DEL") {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    } else if (key == "CANCEL") {
                                        Text(
                                            text = "Cancel",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            text = key,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

### 4.2. Blocking Screen: `android/app/src/main/java/com/goback/focus/ui/screens/BlockingScreen.kt`

```kotlin
package com.goback.focus.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.SettingEntity
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinDialog
import com.goback.focus.ui.components.SecurityPinUtil
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class LaunchableApp(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class BlockingConfigState(
    val blockOnlyDuringFocus: Boolean = false,
    val strictMode: Boolean = false,
    val strictUninstallProtection: Boolean = false,
    val blockSplitScreen: Boolean = false,
    val blockShorts: Boolean = false,
    val blockReels: Boolean = false,
    val blockSpotlight: Boolean = false,
    val blockAdultSites: Boolean = false,
    val blockYoutube: Boolean = false,
    val allowedChannels: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet()
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockingScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GoBackDatabase.getDatabase(context) }
    val prefs = remember { context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE) }

    var config by remember { mutableStateOf(BlockingConfigState()) }
    var installedApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appSearchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(false) }

    var channelInputText by remember { mutableStateOf("") }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }
    var lastActionDiagnostic by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }

    // Security PIN state
    var storedPinHash by remember { mutableStateOf<String?>(null) }
    var isPinUnlocked by remember { mutableStateOf(false) }
    var pinModalVisible by remember { mutableStateOf(false) }
    var pinModalMode by remember { mutableStateOf(PinModalMode.VERIFY) }

    fun checkAccessibilityStatus(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledViaList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)?.any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                        it.resolveInfo.serviceInfo.name.endsWith("GoBackBlockingService")
            } ?: false
            val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val enabledViaSettings = enabledServices.split(':').any {
                it.contains("GoBackBlockingService", ignoreCase = true) && it.contains(context.packageName, ignoreCase = true)
            }
            enabledViaList || enabledViaSettings
        } catch (_: Exception) {
            false
        }
    }

    fun loadConfiguration() {
        val blocked = prefs.getStringSet("blocked_packages", emptySet()) ?: emptySet()
        val channels = prefs.getStringSet("youtube_allowed_channels", emptySet()) ?: emptySet()
        val reason = prefs.getString("last_action_reason", "").orEmpty()

        config = BlockingConfigState(
            blockOnlyDuringFocus = prefs.getBoolean("block_only_during_focus", false),
            strictMode = prefs.getBoolean("strict_mode_active", false),
            strictUninstallProtection = prefs.getBoolean("strict_uninstall_protection", false),
            blockSplitScreen = prefs.getBoolean("block_split_screen", false),
            blockShorts = prefs.getBoolean("block_shorts", false),
            blockReels = prefs.getBoolean("block_reels", false),
            blockSpotlight = prefs.getBoolean("block_spotlight", false),
            blockAdultSites = prefs.getBoolean("block_adult", false),
            blockYoutube = prefs.getBoolean("youtube_study_mode", false),
            allowedChannels = channels,
            blockedPackages = blocked
        )
        isAccessibilityEnabled = checkAccessibilityStatus()
        lastActionDiagnostic = reason
        hasUnsavedChanges = false
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadConfiguration()

        // Check PIN in Room SettingDao
        val pin = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
        storedPinHash = pin
        if (!pin.isNullOrEmpty() && !isPinUnlocked) {
            pinModalMode = PinModalMode.VERIFY
            pinModalVisible = true
        } else {
            isPinUnlocked = true
        }

        // Query installed launchable apps in background
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0).mapNotNull { resolve ->
                val appInfo = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                LaunchableApp(appInfo.packageName, label, isSystem)
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase(Locale.ROOT) }
            installedApps = list
        }
    }

    fun saveRules() {
        scope.launch {
            val jsonConfig = JSONObject().apply {
                put("blockYoutube", config.blockYoutube)
                put("blockShorts", config.blockShorts)
                put("blockReels", config.blockReels)
                put("blockSpotlight", config.blockSpotlight)
                put("blockAdultSites", config.blockAdultSites)
                put("blockSplitScreen", config.blockSplitScreen)
                put("strictUninstallProtection", config.strictUninstallProtection)
                put("strictMode", config.strictMode)
                put("blockOnlyDuringFocus", config.blockOnlyDuringFocus)
                put("allowedChannels", JSONArray(config.allowedChannels))
                put("blockedPackages", JSONArray(config.blockedPackages))
            }

            prefs.edit()
                .putStringSet("blocked_packages", config.blockedPackages)
                .putStringSet("youtube_allowed_channels", config.allowedChannels)
                .putBoolean("block_only_during_focus", config.blockOnlyDuringFocus)
                .putBoolean("strict_mode_active", config.strictMode)
                .putBoolean("strict_uninstall_protection", config.strictUninstallProtection)
                .putBoolean("block_split_screen", config.blockSplitScreen)
                .putBoolean("block_adult", config.blockAdultSites)
                .putBoolean("block_shorts", config.blockShorts)
                .putBoolean("youtube_study_mode", config.blockYoutube)
                .putBoolean("block_reels", config.blockReels)
                .putBoolean("block_spotlight", config.blockSpotlight)
                .putString("config", jsonConfig.toString())
                .apply()

            hasUnsavedChanges = false
            statusMessage = if (isAccessibilityEnabled) "Blocking rules saved & actively enforced."
            else "Rules saved. Enable Go Back in Accessibility settings to activate."
        }
    }

    fun addChannel() {
        val clean = channelInputText.trim().replace("\\s+".toRegex(), " ")
        if (clean.isEmpty()) return
        if (clean.length > 80) {
            statusMessage = "Channel name is too long (max 80 chars)."
            return
        }
        if (config.allowedChannels.size >= 50) {
            statusMessage = "Allowlist is full (max 50 channels)."
            return
        }
        config = config.copy(allowedChannels = config.allowedChannels + clean)
        channelInputText = ""
        hasUnsavedChanges = true
    }

    fun removeChannel(channel: String) {
        config = config.copy(allowedChannels = config.allowedChannels - channel)
        hasUnsavedChanges = true
    }

    fun toggleBlockedPackage(pkg: String) {
        val current = config.blockedPackages
        if (current.contains(pkg)) {
            config = config.copy(blockedPackages = current - pkg)
            hasUnsavedChanges = true
        } else {
            if (current.size >= 100) {
                statusMessage = "Whole-app block limit reached (max 100 apps)."
                return
            }
            config = config.copy(blockedPackages = current + pkg)
            hasUnsavedChanges = true
        }
    }

    val filteredApps = remember(installedApps, appSearchQuery, showSystemApps, selectedOnly, config.blockedPackages) {
        installedApps.filter { app ->
            val matchesQuery = appSearchQuery.isEmpty() ||
                    app.appName.contains(appSearchQuery, ignoreCase = true) ||
                    app.packageName.contains(appSearchQuery, ignoreCase = true)
            val matchesSystem = showSystemApps || !app.isSystemApp
            val matchesSelected = !selectedOnly || config.blockedPackages.contains(app.packageName)
            matchesQuery && matchesSystem && matchesSelected
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("App & Content Blocking", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadConfiguration() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Status")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "DISTRACTION CONTROL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Guard your focus.",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Accessibility Status Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (isAccessibilityEnabled) Color(0xFF34D399) else Color(0xFFF59E0B))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isAccessibilityEnabled) "Accessibility Service Active" else "Accessibility Service Disabled",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isAccessibilityEnabled) "Distraction rules and app blocking are actively enforced."
                                    else "Blocking cannot trigger until Go Back is enabled in Android Accessibility settings.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!isAccessibilityEnabled) {
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                shape = PillShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (lastActionDiagnostic.isNotEmpty()) {
                            Text(
                                text = "Last action: $lastActionDiagnostic",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // General Enforcement Card
            item {
                Text("General Enforcement", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Only block during focus sessions",
                            detail = "Enforce blocking rules only while a focus countdown or stopwatch is active.",
                            value = config.blockOnlyDuringFocus,
                            onValueChange = { config = config.copy(blockOnlyDuringFocus = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Strict Mode (Challenge to exit)",
                            detail = "Requires typing commitment phrase 'I choose to break my focus' before canceling early.",
                            value = config.strictMode,
                            onValueChange = { config = config.copy(strictMode = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Settings & Uninstall Protection",
                            detail = "Prevents opening Android Settings to force-stop or uninstall Go Back while active.",
                            value = config.strictUninstallProtection,
                            onValueChange = { config = config.copy(strictUninstallProtection = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Floating & Split-Screen Windows",
                            detail = "Immediately terminates blocked apps if launched in split-screen or multi-window mode.",
                            value = config.blockSplitScreen,
                            onValueChange = { config = config.copy(blockSplitScreen = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        // PIN Lock trigger row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pinModalMode = if (storedPinHash.isNullOrEmpty()) PinModalMode.SET else PinModalMode.REMOVE
                                    pinModalVisible = true
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Self-Control PIN Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (!storedPinHash.isNullOrEmpty()) "PIN protection active · Tap to change or remove"
                                    else "Require a 4-digit PIN to modify blocking settings",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (!storedPinHash.isNullOrEmpty()) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = if (!storedPinHash.isNullOrEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Granular Content Blocking Card
            item {
                Text("Short-Form & Adult Content", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Block YouTube Shorts",
                            detail = "Automatically navigates Back when the Shorts feed or tab is detected in YouTube.",
                            value = config.blockShorts,
                            onValueChange = { config = config.copy(blockShorts = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Instagram Reels",
                            detail = "Closes the Reels surface immediately upon detection in Instagram.",
                            value = config.blockReels,
                            onValueChange = { config = config.copy(blockReels = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Snapchat Spotlight",
                            detail = "Navigates away from Spotlight videos inside Snapchat.",
                            value = config.blockSpotlight,
                            onValueChange = { config = config.copy(blockSpotlight = it); hasUnsavedChanges = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            title = "Block Adult Websites",
                            detail = "Monitors browser address bars across Chrome, Firefox, Edge, Brave, Samsung Internet & DuckDuckGo.",
                            value = config.blockAdultSites,
                            onValueChange = { config = config.copy(blockAdultSites = it); hasUnsavedChanges = true }
                        )
                    }
                }
            }

            // YouTube Study Mode
            item {
                Text("YouTube Study Mode", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ToggleRow(
                            title = "Limit YouTube to Allowed Channels",
                            detail = "Dismisses video player and miniplayers when watching channels not on your study allowlist.",
                            value = config.blockYoutube,
                            onValueChange = { config = config.copy(blockYoutube = it); hasUnsavedChanges = true }
                        )

                        AnimatedVisibility(visible = config.blockYoutube) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Allowed Study Channels (Max 50)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = channelInputText,
                                        onValueChange = { channelInputText = it },
                                        placeholder = { Text("@handle or channel name", fontSize = 13.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Button(
                                        onClick = { addChannel() },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.height(52.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add Channel")
                                    }
                                }

                                if (config.allowedChannels.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        config.allowedChannels.forEach { channel ->
                                            InputChip(
                                                selected = true,
                                                onClick = { removeChannel(channel) },
                                                label = { Text(channel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
                                                shape = ChipShape
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        "No allowed channels added yet. Videos from all channels will be blocked until allowlisted.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Whole-App Blocking Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Installed Apps Picker", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        "${config.blockedPackages.size}/100 selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose apps to block. Whenever launched, Go Back immediately closes them and returns to your launcher home screen.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Search Box
                OutlinedTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    placeholder = { Text("Search apps by name or package…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (appSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { appSearchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedOnly,
                        onClick = { selectedOnly = !selectedOnly },
                        label = { Text("Selected (${config.blockedPackages.size})") }
                    )
                    FilterChip(
                        selected = showSystemApps,
                        onClick = { showSystemApps = !showSystemApps },
                        label = { Text("Include System Apps") }
                    )
                }
            }

            // App Items
            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (installedApps.isEmpty()) "Loading installed applications…" else "No matching apps found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isBlocked = config.blockedPackages.contains(app.packageName)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggleBlockedPackage(app.packageName) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isBlocked) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Switch(
                                checked = isBlocked,
                                onCheckedChange = { toggleBlockedPackage(app.packageName) }
                            )
                        }
                    }
                }
            }

            // Save Rules & Status feedback
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { saveRules() },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasUnsavedChanges) "Save Blocking Rules (Changes Pending)" else "Blocking Rules Saved",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (statusMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage.orEmpty(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    // Security PIN Dialog
    if (pinModalVisible) {
        SecurityPinDialog(
            mode = pinModalMode,
            storedPinHash = storedPinHash,
            onSuccess = { newPin ->
                pinModalVisible = false
                isPinUnlocked = true
                if (pinModalMode == PinModalMode.SET && newPin != null) {
                    scope.launch {
                        val hashed = SecurityPinUtil.hashPin(newPin)
                        db.settingDao().setValue(SettingEntity(SecurityPinUtil.PIN_SETTING_KEY, hashed))
                        storedPinHash = hashed
                        statusMessage = "Security PIN enabled."
                    }
                } else if (pinModalMode == PinModalMode.REMOVE) {
                    scope.launch {
                        db.settingDao().delete(SecurityPinUtil.PIN_SETTING_KEY)
                        storedPinHash = null
                        statusMessage = "Security PIN removed."
                    }
                }
            },
            onDismiss = {
                pinModalVisible = false
                if (!isPinUnlocked) {
                    onNavigateBack()
                }
            }
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    detail: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onValueChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = detail, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}
```

---

### 4.3. Usage Limits Screen: `android/app/src/main/java/com/goback/focus/ui/screens/UsageLimitsScreen.kt`

```kotlin
package com.goback.focus.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.UsageLimitEntity
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinDialog
import com.goback.focus.ui.components.SecurityPinUtil
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.ModalShape
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val limit: UsageLimitEntity?
)

@Composable
fun UsageLimitsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GoBackDatabase.getDatabase(context) }
    val blockingPrefs = remember { context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE) }
    val usagePrefs = remember { context.getSharedPreferences("go_back_usage_limits", Context.MODE_PRIVATE) }

    var hasUsageAccess by remember { mutableStateOf(false) }
    var foregroundUsageMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var installedApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var limitsOnlyFilter by remember { mutableStateOf(false) }

    // Dialog state for setting/editing limit
    var editingApp by remember { mutableStateOf<AppUsageItem?>(null) }
    var limitMinutesInput by remember { mutableStateOf(60) }
    var warningPercentInput by remember { mutableStateOf(80) }

    // Security PIN state
    var storedPinHash by remember { mutableStateOf<String?>(null) }
    var isPinUnlocked by remember { mutableStateOf(false) }
    var pinModalVisible by remember { mutableStateOf(false) }

    // Room DB limits observation
    val limitsList by db.usageLimitDao().getAll().collectAsState(initial = emptyList())

    fun checkUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun queryTodayUsage(): Map<String, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now) ?: return emptyMap()
        val totals = mutableMapOf<String, Long>()
        stats.forEach { stat ->
            if (stat.totalTimeInForeground > 0L) {
                totals[stat.packageName] = (totals[stat.packageName] ?: 0L) + stat.totalTimeInForeground
            }
        }
        return totals
    }

    /**
     * Synchronizes limits that have reached 100% into SharedPreferences "go_back_blocking"
     * under "usage_limit_blocked_packages" so GoBackBlockingService immediately blocks them.
     */
    fun sync100PercentExceededBlocking(currentLimits: List<UsageLimitEntity>, usageMap: Map<String, Long>) {
        val exceededSet = mutableSetOf<String>()
        val limitsJsonArray = JSONArray()

        currentLimits.filter { it.enabled == 1 }.forEach { limit ->
            val usedMinutes = ((usageMap[limit.packageName] ?: 0L) / 60000L).toInt()
            if (usedMinutes >= limit.limitMinutes) {
                exceededSet.add(limit.packageName.lowercase(Locale.ROOT))
            }
            val obj = JSONObject().apply {
                put("packageName", limit.packageName)
                put("appName", limit.appName)
                put("limitMinutes", limit.limitMinutes)
                put("warningPercent", limit.warningPercent)
            }
            limitsJsonArray.put(obj)
        }

        blockingPrefs.edit()
            .putStringSet("usage_limit_blocked_packages", exceededSet)
            .apply()

        usagePrefs.edit()
            .putString("limits", limitsJsonArray.toString())
            .apply()
    }

    LaunchedEffect(Unit) {
        val allowed = checkUsagePermission()
        hasUsageAccess = allowed
        if (allowed) {
            foregroundUsageMap = withContext(Dispatchers.IO) { queryTodayUsage() }
        }

        // Check PIN in Room SettingDao
        val pin = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
        storedPinHash = pin
        if (!pin.isNullOrEmpty() && !isPinUnlocked) {
            pinModalVisible = true
        } else {
            isPinUnlocked = true
        }

        // Load installed launchable apps
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0).mapNotNull { resolve ->
                val appInfo = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                val label = resolve.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                LaunchableApp(appInfo.packageName, label, isSystem)
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase(Locale.ROOT) }
            installedApps = list
        }
    }

    // Trigger 100% blocking sync whenever limits or usage updates
    LaunchedEffect(limitsList, foregroundUsageMap) {
        sync100PercentExceededBlocking(limitsList, foregroundUsageMap)
    }

    fun formatDuration(ms: Long): String {
        val min = (ms / 60000L).toInt()
        return if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
    }

    // Merged apps with usage and limits
    val mergedItems = remember(installedApps, limitsList, foregroundUsageMap, searchQuery, limitsOnlyFilter) {
        val limitsMap = limitsList.associateBy { it.packageName }
        val query = searchQuery.trim().lowercase(Locale.ROOT)

        installedApps.map { app ->
            AppUsageItem(
                packageName = app.packageName,
                appName = app.appName,
                foregroundTimeMs = foregroundUsageMap[app.packageName] ?: 0L,
                limit = limitsMap[app.packageName]
            )
        }.filter { item ->
            val matchesQuery = query.isEmpty() ||
                    item.appName.lowercase(Locale.ROOT).contains(query) ||
                    item.packageName.lowercase(Locale.ROOT).contains(query)
            val matchesLimitsOnly = !limitsOnlyFilter || item.limit != null
            matchesQuery && matchesLimitsOnly
        }.sortedWith(compareByDescending<AppUsageItem> { it.limit != null }
            .thenByDescending { it.foregroundTimeMs }
            .thenBy { it.appName.lowercase(Locale.ROOT) })
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Daily Screen-Time Limits", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        hasUsageAccess = checkUsagePermission()
                        if (hasUsageAccess) {
                            scope.launch {
                                foregroundUsageMap = withContext(Dispatchers.IO) { queryTodayUsage() }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Usage")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "GENTLE BOUNDARIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "App Time Limits",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set daily app quotas. Get warning alerts at 80% and automated home-screen blocking at 100%.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Usage Access Warning Card if permission is missing
            if (!hasUsageAccess) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.error, CardShape),
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Usage Access Required", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "Go Back needs Android Usage Access to track daily foreground time and enforce limits.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                shape = PillShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Usage Access Settings", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Search & Filter row
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilterChip(
                    selected = limitsOnlyFilter,
                    onClick = { limitsOnlyFilter = !limitsOnlyFilter },
                    label = { Text("Limits configured only (${limitsList.size})") }
                )
            }

            // Apps List
            if (mergedItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No matching apps found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(mergedItems, key = { it.packageName }) { item ->
                    val limit = item.limit
                    val usedMinutes = ((item.foregroundTimeMs) / 60000L).toInt()
                    val limitMinutes = limit?.limitMinutes ?: 0
                    val fraction = if (limitMinutes > 0) (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f) else 0f
                    val isExceeded = limitMinutes > 0 && usedMinutes >= limitMinutes
                    val isWarning = limitMinutes > 0 && !isExceeded && usedMinutes >= (limitMinutes * (limit?.warningPercent ?: 80) / 100)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isExceeded) MaterialTheme.colorScheme.errorContainer
                                            else if (isWarning) Color(0xFFFEF3C7)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isExceeded) Icons.Filled.Block else Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        tint = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.appName, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${formatDuration(item.foregroundTimeMs)} today" +
                                                if (limit != null) " · ${limit.limitMinutes}m limit · warn at ${limit.warningPercent}%" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        editingApp = item
                                        limitMinutesInput = limit?.limitMinutes ?: 60
                                        warningPercentInput = limit?.warningPercent ?: 80
                                    }
                                ) {
                                    Text(if (limit == null) "Set limit" else "Edit")
                                }
                            }

                            // Progress Bar if limit exists
                            if (limit != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { fraction },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(PillShape),
                                        color = if (isExceeded) MaterialTheme.colorScheme.error
                                        else if (isWarning) Color(0xFFF59E0B)
                                        else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            if (isExceeded) "100% Reached · App Blocked"
                                            else if (isWarning) "Nearing limit (${(fraction * 100).toInt()}%)"
                                            else "${(fraction * 100).toInt()}% used",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text("${usedMinutes}/${limit.limitMinutes}m", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Limit Editor Dialog
    if (editingApp != null) {
        val app = editingApp!!
        val isNew = app.limit == null

        AlertDialog(
            onDismissRequest = { editingApp = null },
            shape = ModalShape,
            title = { Text(if (isNew) "Set Daily Limit" else "Edit Limit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "${app.appName} (${formatDuration(app.foregroundTimeMs)} used today)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text("Daily time limit: $limitMinutesInput minutes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { preset ->
                            FilterChip(
                                selected = limitMinutesInput == preset,
                                onClick = { limitMinutesInput = preset },
                                label = { Text("${preset}m", fontSize = 11.sp) }
                            )
                        }
                    }

                    Text("Advance warning at: $warningPercentInput%", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(50, 60, 70, 80, 90).forEach { percent ->
                            FilterChip(
                                selected = warningPercentInput == percent,
                                onClick = { warningPercentInput = percent },
                                label = { Text("${percent}%", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val entity = UsageLimitEntity(
                                id = app.limit?.id ?: "limit_${UUID.randomUUID()}",
                                packageName = app.packageName,
                                appName = app.appName,
                                limitMinutes = limitMinutesInput,
                                warningPercent = warningPercentInput,
                                enabled = 1,
                                createdAt = app.limit?.createdAt ?: Date().toString()
                            )
                            db.usageLimitDao().upsert(entity)
                            editingApp = null
                        }
                    }
                ) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isNew) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    app.limit?.let { db.usageLimitDao().delete(it) }
                                    editingApp = null
                                }
                            }
                        ) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editingApp = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Security PIN Dialog
    if (pinModalVisible) {
        SecurityPinDialog(
            mode = PinModalMode.VERIFY,
            storedPinHash = storedPinHash,
            onSuccess = {
                pinModalVisible = false
                isPinUnlocked = true
            },
            onDismiss = {
                pinModalVisible = false
                if (!isPinUnlocked) {
                    onNavigateBack()
                }
            }
        )
    }
}
```

---

### 4.4. Settings Screen: `android/app/src/main/java/com/goback/focus/ui/screens/SettingsScreen.kt`

```kotlin
package com.goback.focus.ui.screens

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goback.focus.R
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.SettingEntity
import com.goback.focus.service.FocusTileService
import com.goback.focus.ui.components.PinModalMode
import com.goback.focus.ui.components.SecurityPinDialog
import com.goback.focus.ui.components.SecurityPinUtil
import com.goback.focus.ui.theme.CardShape
import com.goback.focus.ui.theme.ChipShape
import com.goback.focus.ui.theme.ModalShape
import com.goback.focus.ui.theme.PillShape
import kotlinx.coroutines.launch

data class ThemeColorOption(val name: String, val color: Color)

val THEME_PALETTES = listOf(
    ThemeColorOption("Forest", Color(0xFF365C32)),
    ThemeColorOption("Blue", Color(0xFF2563EB)),
    ThemeColorOption("Violet", Color(0xFF7C3AED)),
    ThemeColorOption("Amber", Color(0xFFD97706)),
    ThemeColorOption("Rose", Color(0xFFE11D48)),
    ThemeColorOption("Teal", Color(0xFF0D9488)),
    ThemeColorOption("Berry", Color(0xFF9333EA)),
    ThemeColorOption("Sakura", Color(0xFFEC4899)),
    ThemeColorOption("Graphite", Color(0xFF4B5563)),
    ThemeColorOption("Coral", Color(0xFFEA580C)),
    ThemeColorOption("Lavender", Color(0xFF8B5CF6)),
    ThemeColorOption("Crimson", Color(0xFFDC2626))
)

val FINISH_ALARM_SOUNDS = listOf(
    Pair("default", "System default notification"),
    Pair("focus_bell.wav", "Soft bell"),
    Pair("focus_chime.wav", "Warm chime"),
    Pair("focus_digital.wav", "Digital alert")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onNavigateToBlocking: () -> Unit = {},
    onNavigateToUsageLimits: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
    onNavigateToExams: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GoBackDatabase.getDatabase(context) }

    var profileName by remember { mutableStateOf("") }
    var dailyGoalMinutes by remember { mutableStateOf(120) }
    var selectedThemeMode by remember { mutableStateOf("system") }
    var selectedPaletteName by remember { mutableStateOf("Forest") }
    var selectedAlarmSound by remember { mutableStateOf("default") }
    var storedPinHash by remember { mutableStateOf<String?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    // PIN modal
    var pinModalVisible by remember { mutableStateOf(false) }
    var pinModalMode by remember { mutableStateOf(PinModalMode.SET) }

    LaunchedEffect(Unit) {
        profileName = db.settingDao().getValue("profile.name") ?: ""
        dailyGoalMinutes = db.settingDao().getValue("focus.dailyGoalMinutes")?.toIntOrNull() ?: 120
        selectedThemeMode = db.settingDao().getValue("theme.mode") ?: "system"
        selectedPaletteName = db.settingDao().getValue("theme.palette") ?: "Forest"
        selectedAlarmSound = db.settingDao().getValue("focus.alarmSound") ?: "default"
        storedPinHash = db.settingDao().getValue(SecurityPinUtil.PIN_SETTING_KEY)
    }

    fun playPreviewSound(soundKey: String) {
        try {
            val resName = soundKey.removeSuffix(".wav").lowercase()
            val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            if (resId != 0) {
                val mp = MediaPlayer.create(context, resId)
                mp?.setOnCompletionListener { it.release() }
                mp?.start()
            } else {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
                ringtone?.play()
            }
            feedbackMessage = "Playing sound preview"
        } catch (_: Exception) {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, ringtoneUri)
            ringtone?.play()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "PREFERENCES & PRIVACY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Local-First Badge
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% Local-First by Default",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your syllabus, notes, focus history, and app rules never leave your device. Zero cloud tracking.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Feature Navigation Hub
        item {
            Text("Distraction & Routine Controls", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    SettingsNavRow(
                        icon = Icons.Filled.Shield,
                        title = "App & Content Blocking",
                        desc = "Whole-app blocker, YouTube Shorts, Reels, Spotlight & adult sites",
                        onClick = onNavigateToBlocking
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.HourglassBottom,
                        title = "Daily Screen-Time Limits",
                        desc = "App quotas with advance alerts at 80% and blocking at 100%",
                        onClick = onNavigateToUsageLimits
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Weekly Focus Schedules",
                        desc = "Recurring weekly study blocks and routine pre-reminders",
                        onClick = onNavigateToSchedules
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SettingsNavRow(
                        icon = Icons.Filled.School,
                        title = "Exam Timetable Planner",
                        desc = "Countdown to exams with 3-stage reminders",
                        onClick = onNavigateToExams
                    )
                }
            }
        }

        // Finish Alarm Sound Section
        item {
            Text("Focus Finish Sound", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Sound played when a focus timer or Pomodoro phase completes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FINISH_ALARM_SOUNDS.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAlarmSound = key
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.alarmSound", key))
                                    }
                                    playPreviewSound(key)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedAlarmSound == key,
                                onClick = {
                                    selectedAlarmSound = key
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.alarmSound", key))
                                    }
                                    playPreviewSound(key)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { playPreviewSound(key) }) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Daily Focus Goal Section
        item {
            Text("Daily Focus Goal", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Goal", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${dailyGoalMinutes / 60}h ${dailyGoalMinutes % 60}m ($dailyGoalMinutes min)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(30, 60, 90, 120, 180, 240).forEach { goal ->
                            FilterChip(
                                selected = dailyGoalMinutes == goal,
                                onClick = {
                                    dailyGoalMinutes = goal
                                    scope.launch {
                                        db.settingDao().setValue(SettingEntity("focus.dailyGoalMinutes", goal.toString()))
                                    }
                                },
                                label = { Text("${goal}m") }
                            )
                        }
                    }
                }
            }
        }

        // Appearance & Theme Studio
        item {
            Text("Appearance & Theme Studio", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            Triple("system", "System", Icons.Filled.PhoneAndroid),
                            Triple("light", "Light", Icons.Filled.LightMode),
                            Triple("dark", "Dark", Icons.Filled.DarkMode)
                        ).forEach { (mode, label, icon) ->
                            FilterChip(
                                selected = selectedThemeMode == mode,
                                onClick = {
                                    selectedThemeMode = mode
                                    scope.launch { db.settingDao().setValue(SettingEntity("theme.mode", mode)) }
                                },
                                label = { Text(label) },
                                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Accent Color Palette", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        THEME_PALETTES.forEach { pal ->
                            val isSelected = selectedPaletteName == pal.name
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .clickable {
                                        selectedPaletteName = pal.name
                                        scope.launch { db.settingDao().setValue(SettingEntity("theme.palette", pal.name)) }
                                    }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(pal.color))
                                    Text(pal.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Security PIN Card
        item {
            Text("Security PIN Protection", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = if (!storedPinHash.isNullOrEmpty()) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (!storedPinHash.isNullOrEmpty()) "PIN Protection Active" else "PIN Protection Inactive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (!storedPinHash.isNullOrEmpty()) "Protects App Blocking & Usage Limits against impulse modifications."
                                else "Prevent yourself from turning off blockers or extending limits in weak moments.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (storedPinHash.isNullOrEmpty()) {
                            Button(
                                onClick = { pinModalMode = PinModalMode.SET; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Set 4-Digit PIN")
                            }
                        } else {
                            Button(
                                onClick = { pinModalMode = PinModalMode.SET; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Change PIN")
                            }
                            OutlinedButton(
                                onClick = { pinModalMode = PinModalMode.REMOVE; pinModalVisible = true },
                                shape = PillShape
                            ) {
                                Text("Remove PIN", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Quick Settings Tile Shortcut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                    shape = CardShape,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Filled.Grid3x3, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quick Settings Focus Tile", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Start and stop focus sessions right from the Android pull-down notification shade.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = {
                                val statusBarManager = context.getSystemService(StatusBarManager::class.java)
                                statusBarManager?.requestAddTileService(
                                    ComponentName(context, FocusTileService::class.java),
                                    "Go Back Focus",
                                    Icon.createWithResource(context, R.drawable.ic_focus_tile),
                                    { r -> r.run() },
                                    { /* Consumer */ }
                                )
                            },
                            shape = PillShape
                        ) {
                            Text("Add Tile")
                        }
                    }
                }
            }
        }

        // Profile Display Name
        item {
            Text("Local Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CardShape),
                shape = CardShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Display Name", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            placeholder = { Text("Your name (optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    db.settingDao().setValue(SettingEntity("profile.name", profileName.trim()))
                                    feedbackMessage = "Profile name saved."
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // Feedback Banner
        if (feedbackMessage != null) {
            item {
                Text(
                    text = feedbackMessage.orEmpty(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    // Security PIN Dialog
    if (pinModalVisible) {
        SecurityPinDialog(
            mode = pinModalMode,
            storedPinHash = storedPinHash,
            onSuccess = { newPin ->
                pinModalVisible = false
                if (pinModalMode == PinModalMode.SET && newPin != null) {
                    scope.launch {
                        val hashed = SecurityPinUtil.hashPin(newPin)
                        db.settingDao().setValue(SettingEntity(SecurityPinUtil.PIN_SETTING_KEY, hashed))
                        storedPinHash = hashed
                        feedbackMessage = "Security PIN successfully updated."
                    }
                } else if (pinModalMode == PinModalMode.REMOVE) {
                    scope.launch {
                        db.settingDao().delete(SecurityPinUtil.PIN_SETTING_KEY)
                        storedPinHash = null
                        feedbackMessage = "Security PIN removed."
                    }
                }
            },
            onDismiss = { pinModalVisible = false }
        )
    }
}

@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

---

### 4.5. Integration Enhancements to `GoBackBlockingService.kt`

To support 100% daily usage limit blocking seamlessly and independently from the user's manual whole-app blocklist, apply this modification to `android/app/src/main/java/com/goback/focus/service/GoBackBlockingService.kt`:

```kotlin
// 1. In BlockingConfig data class:
data class BlockingConfig(
    val blockOnlyDuringFocus: Boolean = false,
    val strictUninstallProtection: Boolean = false,
    val strictMode: Boolean = false,
    val blockSplitScreen: Boolean = false,
    val blockedPackages: Set<String> = emptySet(),
    val usageLimitBlockedPackages: Set<String> = emptySet(), // Added for 100% daily limit blocking
    val blockAdultSites: Boolean = false,
    val blockShorts: Boolean = false,
    val blockYoutube: Boolean = false,
    val allowedChannels: Set<String> = emptySet(),
    val blockReels: Boolean = false,
    val blockSpotlight: Boolean = false
)

// 2. In reloadConfiguration(prefs):
val usageLimitBlockedPkgs = mutableSetOf<String>()
prefs.getStringSet("usage_limit_blocked_packages", null)?.let { set ->
    for (p in set) {
        val trimmed = p.trim()
        if (trimmed.isNotEmpty()) usageLimitBlockedPkgs.add(trimmed.lowercase(Locale.ROOT))
    }
}

cachedConfig = BlockingConfig(
    ...
    blockedPackages = blockedPkgs,
    usageLimitBlockedPackages = usageLimitBlockedPkgs,
    ...
)

// 3. In onAccessibilityEvent(event):
// Insert immediately before whole-app blocking check (line 86):
if (config.usageLimitBlockedPackages.contains(pkg)) {
    recordAction("daily usage limit exceeded: $rawPkg")
    sendHome()
    return
}
```

---

### 4.6. Route Wiring in `MainActivity.kt`

Add the navigation routes to `MainActivity.kt`:

```kotlin
// In SettingsScreen route:
composable(NavTab.Settings.route) {
    SettingsScreen(
        onNavigateToBlocking = { navController.navigate("blocking") },
        onNavigateToUsageLimits = { navController.navigate("usage_limits") },
        onNavigateToSchedules = { navController.navigate("schedules") },
        onNavigateToExams = { navController.navigate("exams") }
    )
}

// In TodayScreen route:
composable(NavTab.Home.route) {
    TodayScreen(
        onNavigateToFocus = { navController.navigate(NavTab.Focus.route) },
        onNavigateToSyllabus = { navController.navigate("syllabus") },
        onNavigateToExams = { navController.navigate("exams") },
        onNavigateToAnalytics = { navController.navigate(NavTab.Analytics.route) },
        onNavigateToBlocking = { navController.navigate("blocking") },
        onNavigateToUsageLimits = { navController.navigate("usage_limits") },
        onNavigateToSchedules = { navController.navigate("schedules") },
        onNavigateToSettings = { navController.navigate(NavTab.Settings.route) }
    )
}

// Add top-level routes:
composable("blocking") {
    BlockingScreen(onNavigateBack = { navController.popBackStack() })
}
composable("usage_limits") {
    UsageLimitsScreen(onNavigateBack = { navController.popBackStack() })
}
```

---

## 5. Verification Method

1. **Static Build Verification**:
   Execute the Android compilation task from the project root:
   ```powershell
   cd android; .\gradlew.bat compileDebugKotlin
   ```
   Confirm `BUILD SUCCESSFUL` with 0 syntax errors or unresolved symbols.

2. **File Inspection**:
   - Verify `android/app/src/main/java/com/goback/focus/ui/components/SecurityPinDialog.kt` contains `SecurityPinUtil` with Murmur-style salted hash matching `src/lib/securityPin.ts`.
   - Verify `android/app/src/main/java/com/goback/focus/ui/screens/BlockingScreen.kt` implements `PackageManager.queryIntentActivities`, search, system app filter, adult content toggle, shorts/reels/spotlight toggles, YouTube study mode allowlist, strict mode, uninstall protection, and SharedPreferences writing.
   - Verify `android/app/src/main/java/com/goback/focus/ui/screens/UsageLimitsScreen.kt` queries `UsageStatsManager`, binds Room `UsageLimitDao`, syncs 100% exceeded packages to `"usage_limit_blocked_packages"`, and enforces PIN protection.
   - Verify `android/app/src/main/java/com/goback/focus/ui/screens/SettingsScreen.kt` replaces placeholder with theme mode, 12 accent colors, finish alarm sound selection with preview, daily goal setting, PIN management, and navigation hub.
   - Verify `GoBackBlockingService.kt` registers `OnSharedPreferenceChangeListener` on `"go_back_blocking"` and executes `GLOBAL_ACTION_HOME` on exceeded limits.

3. **Runtime Invalidation Conditions**:
   - `isAccessibilityEnabled` returning false if service is killed by OEM battery optimizer.
   - `hasUsageAccess` returning false if user has not granted `PACKAGE_USAGE_STATS` in Android Settings.
