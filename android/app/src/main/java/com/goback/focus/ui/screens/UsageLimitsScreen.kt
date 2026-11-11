package com.goback.focus.ui.screens

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

@OptIn(ExperimentalMaterial3Api::class)
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
                val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateBack() },
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "Usage limits",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                hasUsageAccess = checkUsagePermission()
                                if (hasUsageAccess) {
                                    scope.launch {
                                        foregroundUsageMap = withContext(Dispatchers.IO) { queryTodayUsage() }
                                    }
                                }
                            },
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
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
