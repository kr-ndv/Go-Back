package com.goback.focus.ui.viewmodel

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.FocusSessionEntity
import com.goback.focus.data.entity.SettingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class AnalyticsPeriod(val days: Int, val label: String) {
    TODAY(1, "Today"),
    WEEK(7, "7 Days"),
    MONTH(30, "30 Days")
}

enum class UsageCategory(val id: String, val label: String, val colorHex: Long) {
    PRODUCTIVE("productive", "Productive", 0xFF34D399),
    DISTRACTING("distracting", "Distracting", 0xFFF87171),
    NEUTRAL("neutral", "Neutral", 0xFF9CA3AF)
}

data class DailyChartPoint(
    val date: String,
    val label: String,
    val focusMinutes: Int,
    val usageMinutes: Int
)

data class ClassifiedAppItem(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val category: UsageCategory
)

enum class SessionHistoryFilter(val label: String) {
    ALL("All"),
    FOCUS("Focus"),
    BREAK("Breaks")
}

data class SessionLogItem(
    val id: String,
    val startedAt: String,
    val formattedDate: String,
    val durationSeconds: Int,
    val formattedDuration: String,
    val completed: Boolean,
    val sessionType: String,
    val mode: String,
    val tag: String,
    val notes: String
)

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.WEEK,
    val hasUsageAccess: Boolean = false,
    val focusSeconds: Long = 0L,
    val focusPercentChange: Int? = null,
    val screenTimeMs: Long = 0L,
    val screenPercentChange: Int? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val unlocksToday: Int = 0,
    val dailyChartPoints: List<DailyChartPoint> = emptyList(),
    val maxMinutesInChart: Int = 60,
    val productiveMs: Long = 0L,
    val distractingMs: Long = 0L,
    val neutralMs: Long = 0L,
    val productivePercent: Int = 0,
    val distractingPercent: Int = 0,
    val neutralPercent: Int = 0,
    val classifiedApps: List<ClassifiedAppItem> = emptyList(),
    val appSearchQuery: String = "",
    val historyFilter: SessionHistoryFilter = SessionHistoryFilter.ALL,
    val filteredSessions: List<SessionLogItem> = emptyList(),
    val rangeTotalFocusSeconds: Long = 0L,
    val rangeCompletedSessions: Int = 0,
    val rangeTotalSessions: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class AnalyticsViewModel(
    private val context: Context,
    private val database: GoBackDatabase
) : ViewModel() {

    private val _period = MutableStateFlow(AnalyticsPeriod.WEEK)
    private val _appSearchQuery = MutableStateFlow("")
    private val _historyFilter = MutableStateFlow(SessionHistoryFilter.ALL)
    private val _refreshToken = MutableStateFlow(0L)
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val sessionDao = database.focusSessionDao()
    private val settingDao = database.settingDao()

    init {
        observeAndCalculate()
    }

    private fun observeAndCalculate() {
        combine(
            sessionDao.getAll(),
            settingDao.observeValue("usage.categories"),
            _period,
            _appSearchQuery,
            _historyFilter,
            _refreshToken
        ) { sessions, categoriesJson, period, searchQuery, historyFilter, _ ->
            calculateAnalytics(sessions, categoriesJson, period, searchQuery, historyFilter)
        }
        .flowOn(Dispatchers.IO)
        .onEach { newState -> _uiState.value = newState }
        .launchIn(viewModelScope)
    }

    private suspend fun calculateAnalytics(
        sessions: List<FocusSessionEntity>,
        categoriesJson: String?,
        period: AnalyticsPeriod,
        searchQuery: String,
        historyFilter: SessionHistoryFilter
    ): AnalyticsUiState {
        val hasAccess = checkUsageAccess()
        val categoriesMap = parseCategories(categoriesJson)

        // 1. Date Range Boundaries
        val now = System.currentTimeMillis()
        val currentStartMs = startOfDayTimestamp(-(period.days - 1))
        val prevStartMs = startOfDayTimestamp(-(2 * period.days - 1))
        val prevEndMs = startOfDayTimestamp(-period.days)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val currentStartKey = dateFormat.format(Date(currentStartMs))
        val currentEndKey = dateFormat.format(Date(now))
        val prevStartKey = dateFormat.format(Date(prevStartMs))
        val prevEndKey = dateFormat.format(Date(prevEndMs))

        // 2. Focus Time Aggregations
        var currentFocusSeconds = 0L
        var prevFocusSeconds = 0L
        val focusByDay = mutableMapOf<String, Long>()

        sessions.forEach { s ->
            if (s.sessionType == "focus") {
                val dayKey = s.startedAt.take(10)
                val duration = max(0, s.durationSeconds).toLong()
                if (dayKey in currentStartKey..currentEndKey) {
                    currentFocusSeconds += duration
                    focusByDay[dayKey] = (focusByDay[dayKey] ?: 0L) + duration
                } else if (dayKey in prevStartKey..prevEndKey) {
                    prevFocusSeconds += duration
                }
            }
        }

        val focusPercentChange = calculatePercentChange(currentFocusSeconds, prevFocusSeconds)

        // 3. Screen Time & Unlocks Aggregations from UsageStatsManager & UsageEvents
        var currentUsageMs = 0L
        var prevUsageMs = 0L
        val usageByDay = mutableMapOf<String, Long>()
        val appUsageAggregated = mutableMapOf<String, Long>()
        var unlocksToday = 0

        if (hasAccess) {
            try {
                val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                if (usageManager != null) {
                    // Fetch the selected period in one UsageStatsManager query rather than
                    // issuing one Binder query per calendar day.
                    usageManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        currentStartMs,
                        now
                    )?.forEach { stat ->
                        if (stat.packageName != context.packageName && stat.totalTimeInForeground > 0L) {
                            val bucketTime = stat.firstTimeStamp.takeIf { it in currentStartMs..now }
                                ?: stat.lastTimeUsed.coerceIn(currentStartMs, now)
                            val dateKey = dateFormat.format(Date(bucketTime))
                            usageByDay[dateKey] = (usageByDay[dateKey] ?: 0L) + stat.totalTimeInForeground
                            currentUsageMs += stat.totalTimeInForeground
                            appUsageAggregated[stat.packageName] =
                                (appUsageAggregated[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                        }
                    }

                    // Previous period total usage
                    usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, prevStartMs, prevEndMs)?.forEach { stat ->
                        if (stat.packageName != context.packageName && stat.totalTimeInForeground > 0L) {
                            prevUsageMs += stat.totalTimeInForeground
                        }
                    }

                    // Unlocks query today via UsageEvents (KEYGUARD_HIDDEN = 18)
                    val todayStart = startOfDayTimestamp(0)
                    try {
                        val events = usageManager.queryEvents(todayStart, now)
                        val event = UsageEvents.Event()
                        while (events.hasNextEvent()) {
                            events.getNextEvent(event)
                            if (event.eventType == 18) {
                                unlocksToday++
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (_: Throwable) {}
        }

        if (unlocksToday == 0) {
            unlocksToday = context.getSharedPreferences("go_back_unlocks", 0).getInt("today_unlocks", 0)
        }

        val screenPercentChange = if (hasAccess) calculatePercentChange(currentUsageMs, prevUsageMs) else null

        // 4. Streak Calculation
        val streakStats = TodayViewModel.calculateFocusGoalStats(sessions, 120, Date(now))

        // 5. Daily Dual-Bar Chart Points
        val chartDaysCount = min(period.days, 14) // Display up to 14 bars for readability
        val dailyChartPoints = mutableListOf<DailyChartPoint>()
        val cal = Calendar.getInstance()

        for (i in (chartDaysCount - 1) downTo 0) {
            val dCal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dKey = dateFormat.format(dCal.time)
            val weekdayNarrow = SimpleDateFormat("EEEEE", Locale.getDefault()).format(dCal.time)
            val dayOfMonth = dCal.get(Calendar.DAY_OF_MONTH).toString()
            val label = if (period.days <= 7) weekdayNarrow else dayOfMonth

            val fMins = ((focusByDay[dKey] ?: 0L) / 60L).toInt()
            val uMins = ((usageByDay[dKey] ?: 0L) / 60000L).toInt()

            dailyChartPoints.add(DailyChartPoint(dKey, label, fMins, uMins))
        }

        val maxMinutesInChart = max(60, dailyChartPoints.maxOfOrNull { max(it.focusMinutes, it.usageMinutes) } ?: 60)

        // 6. App Categorization Breakdown
        var productiveMs = 0L
        var distractingMs = 0L
        var neutralMs = 0L
        val pm = context.packageManager
        val classifiedAppsList = mutableListOf<ClassifiedAppItem>()

        appUsageAggregated.entries.sortedByDescending { it.value }.forEach { entry ->
            val pkg = entry.key
            val timeMs = entry.value
            val category = categoriesMap[pkg] ?: UsageCategory.NEUTRAL

            when (category) {
                UsageCategory.PRODUCTIVE -> productiveMs += timeMs
                UsageCategory.DISTRACTING -> distractingMs += timeMs
                UsageCategory.NEUTRAL -> neutralMs += timeMs
            }

            val appLabel = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }

            classifiedAppsList.add(ClassifiedAppItem(pkg, appLabel, timeMs, category))
        }

        val totalCategorizedMs = productiveMs + distractingMs + neutralMs
        val prodPct = if (totalCategorizedMs > 0) ((productiveMs.toDouble() / totalCategorizedMs.toDouble()) * 100).roundToInt() else 0
        val distPct = if (totalCategorizedMs > 0) ((distractingMs.toDouble() / totalCategorizedMs.toDouble()) * 100).roundToInt() else 0
        val neutPct = max(0, 100 - prodPct - distPct)

        val filteredApps = if (searchQuery.isBlank()) {
            classifiedAppsList
        } else {
            val q = searchQuery.trim().lowercase()
            classifiedAppsList.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }

        // 7. Session History Filterable Log
        val logItems = mutableListOf<SessionLogItem>()
        var rangeFocusSum = 0L
        var rangeCompletedCount = 0
        var rangeTotalCount = 0

        val displayDateFormat = SimpleDateFormat("EEE, d MMM · HH:mm", Locale.getDefault())

        sessions.forEach { s ->
            val dayKey = s.startedAt.take(10)
            if (dayKey in currentStartKey..currentEndKey) {
                val matchesFilter = when (historyFilter) {
                    SessionHistoryFilter.ALL -> true
                    SessionHistoryFilter.FOCUS -> s.sessionType == "focus"
                    SessionHistoryFilter.BREAK -> s.sessionType != "focus"
                }

                if (s.sessionType == "focus") {
                    rangeFocusSum += max(0, s.durationSeconds)
                    if (s.completed == 1) rangeCompletedCount++
                    rangeTotalCount++
                }

                if (matchesFilter) {
                    val formattedDate = try {
                        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s.startedAt.take(19))
                            ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dayKey)
                        displayDateFormat.format(parsed ?: Date())
                    } catch (_: Exception) {
                        dayKey
                    }

                    val dur = max(0, s.durationSeconds)
                    val formattedDuration = if (dur >= 3600) {
                        "${dur / 3600}h ${(dur % 3600) / 60}m"
                    } else {
                        "${dur / 60}m ${dur % 60}s"
                    }

                    logItems.add(
                        SessionLogItem(
                            id = s.id,
                            startedAt = s.startedAt,
                            formattedDate = formattedDate,
                            durationSeconds = dur,
                            formattedDuration = formattedDuration,
                            completed = s.completed == 1,
                            sessionType = s.sessionType,
                            mode = s.mode,
                            tag = s.tag,
                            notes = s.notes
                        )
                    )
                }
            }
        }

        return AnalyticsUiState(
            period = period,
            hasUsageAccess = hasAccess,
            focusSeconds = currentFocusSeconds,
            focusPercentChange = focusPercentChange,
            screenTimeMs = currentUsageMs,
            screenPercentChange = screenPercentChange,
            currentStreak = streakStats.currentStreak,
            bestStreak = streakStats.bestStreak,
            unlocksToday = unlocksToday,
            dailyChartPoints = dailyChartPoints,
            maxMinutesInChart = maxMinutesInChart,
            productiveMs = productiveMs,
            distractingMs = distractingMs,
            neutralMs = neutralMs,
            productivePercent = prodPct,
            distractingPercent = distPct,
            neutralPercent = neutPct,
            classifiedApps = filteredApps,
            appSearchQuery = searchQuery,
            historyFilter = historyFilter,
            filteredSessions = logItems,
            rangeTotalFocusSeconds = rangeFocusSum,
            rangeCompletedSessions = rangeCompletedCount,
            rangeTotalSessions = rangeTotalCount,
            isLoading = false
        )
    }

    private fun checkUsageAccess(): Boolean {
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    private fun startOfDayTimestamp(dayOffset: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        return cal.timeInMillis
    }

    private fun calculatePercentChange(current: Long, previous: Long): Int? {
        if (previous <= 0L) return if (current > 0L) null else 0
        return (((current - previous).toDouble() / previous.toDouble()) * 100.0).roundToInt()
    }

    private fun parseCategories(json: String?): Map<String, UsageCategory> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, UsageCategory>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val catStr = obj.optString(pkg, "neutral")
                val cat = when (catStr.lowercase()) {
                    "productive" -> UsageCategory.PRODUCTIVE
                    "distracting" -> UsageCategory.DISTRACTING
                    else -> UsageCategory.NEUTRAL
                }
                result[pkg] = cat
            }
        } catch (_: Exception) {}
        return result
    }

    fun setPeriod(period: AnalyticsPeriod) {
        _period.value = period
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        _refreshToken.value = _refreshToken.value + 1L
    }

    fun setAppSearch(query: String) {
        _appSearchQuery.value = query
    }

    fun setSessionFilter(filter: SessionHistoryFilter) {
        _historyFilter.value = filter
    }

    fun assignCategory(packageName: String, category: UsageCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentJson = settingDao.getValue("usage.categories")
            val obj = try {
                if (currentJson != null) JSONObject(currentJson) else JSONObject()
            } catch (_: Exception) {
                JSONObject()
            }
            obj.put(packageName, category.id)
            settingDao.setValue(SettingEntity("usage.categories", obj.toString()))
        }
    }

    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}

class AnalyticsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = GoBackDatabase.getDatabase(context)
        return AnalyticsViewModel(context.applicationContext, database) as T
    }
}
