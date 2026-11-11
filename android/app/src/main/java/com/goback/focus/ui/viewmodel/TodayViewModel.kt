package com.goback.focus.ui.viewmodel

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.ExamEntity
import com.goback.focus.data.entity.FocusSessionEntity
import com.goback.focus.data.entity.NoteEntity
import com.goback.focus.data.entity.SubjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Streak tiers gamification model.
 * 6 progressive milestones matching reference specification:
 * Spark (3d ✨), Momentum (7d 🔥), Commitment (14d ⚡), Habit Builder (30d 🛡️), Mastery (50d 💎), Centurion (100d 👑)
 */
data class StreakMilestone(
    val tier: String,
    val label: String,
    val badge: String,
    val days: Int,
    val nextMilestoneDays: Int,
    val daysToNext: Int,
    val progressPercent: Int
)

data class TodayUiState(
    val profileName: String = "",
    val profileImageUri: String = "",
    val focusMinutesToday: Int = 0,
    val dailyGoalMinutes: Int = 120,
    val goalProgress: Float = 0f,
    val currentStreak: Int = 0,
    val streakLabel: String = "Getting Started",
    val streakBadge: String = "🌱",
    val streakShieldActive: Boolean = false,
    val screenTimeTodayMs: Long = 0L,
    val unlocksToday: Int = 0,
    val hasUsageAccess: Boolean = false,
    val chaptersDone: Int = 0,
    val chaptersTotal: Int = 0,
    val syllabusProgress: Float = 0f,
    val nextExamTitle: String? = null,
    val nextExamFormattedDate: String? = null,
    val nextExamDaysRemaining: Int? = null,
    val totalNotes: Int = 0,
    val isLoading: Boolean = true
)

class TodayViewModel(
    private val context: Context,
    private val database: GoBackDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    // UsageStatsManager queries are relatively expensive. Reuse the dashboard snapshot
    // briefly when unrelated Room tables (notes, profile, syllabus) emit updates.
    private var cachedUsageSnapshot: Pair<Long, Int>? = null
    private var cachedUsageSnapshotAt: Long = 0L

    init {
        observeDatabaseAndUsage()
    }

    private fun observeDatabaseAndUsage() {
        val sessionDao = database.focusSessionDao()
        val examDao = database.examDao()
        val subjectDao = database.subjectDao()
        val noteDao = database.noteDao()
        val settingDao = database.settingDao()

        val settingsFlow = combine(
            settingDao.observeValue("focus.dailyGoalMinutes"),
            settingDao.observeValue("profile.name"),
            settingDao.observeValue("profile.imageUri")
        ) { goal, name, imageUri -> Triple(goal, name, imageUri) }

        // Combine reactive Room flows into a unified dashboard state
        combine(
            sessionDao.getAll(),
            examDao.getNextUpcoming(),
            subjectDao.getAll(),
            noteDao.getAll(),
            settingsFlow
        ) { sessions, nextExam, subjects, notes, profileSettings ->
            val goalSetting = profileSettings.first
            val nameSetting = profileSettings.second
            val profileImageUri = profileSettings.third ?: ""
            val dailyGoal = goalSetting?.toIntOrNull()?.coerceIn(15, 480) ?: 120
            val profileName = nameSetting ?: ""
            val totalNotes = notes.size

            // 1. Calculate Syllabus Progress
            var chaptersTotal = 0
            var chaptersDone = 0
            subjects.forEach { subject ->
                try {
                    val array = JSONArray(subject.chaptersJson)
                    for (i in 0 until array.length()) {
                        chaptersTotal++
                        val obj = array.optJSONObject(i)
                        if (obj != null && obj.optBoolean("done", false)) {
                            chaptersDone++
                        }
                    }
                } catch (_: Exception) {}
            }
            val syllabusProgress = if (chaptersTotal > 0) chaptersDone.toFloat() / chaptersTotal.toFloat() else 0f

            // 2. Calculate Exam Countdown
            val (nextExamTitle, formattedDate, daysRemaining) = calculateExamDetails(nextExam)

            // 3. Calculate Streak, 1-Day Streak Shield, and Today's Focus Minutes
            val goalStats = calculateFocusGoalStats(sessions, dailyGoal)
            val focusMinutesToday = goalStats.todaySeconds / 60
            val goalProgress = (focusMinutesToday.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)

            // 4. Query Real Screen Time & Unlocks
            val hasAccess = checkUsageAccess()
            val (screenMs, unlocks) = if (hasAccess) queryUsageAndUnlocks() else Pair(0L, 0)

            // 5. Sync to SharedPreferences for Glance AppWidget
            syncWidgetSnapshot(
                focusMinutesToday = focusMinutesToday,
                dailyGoalMinutes = dailyGoal,
                streakDays = goalStats.currentStreak,
                nextExamTitle = nextExamTitle ?: "",
                nextExamDays = daysRemaining ?: -1
            )

            TodayUiState(
                profileName = profileName,
                profileImageUri = profileImageUri,
                focusMinutesToday = focusMinutesToday,
                dailyGoalMinutes = dailyGoal,
                goalProgress = goalProgress,
                currentStreak = goalStats.currentStreak,
                streakLabel = goalStats.milestone.label,
                streakBadge = goalStats.milestone.badge,
                streakShieldActive = goalStats.streakShieldActive,
                screenTimeTodayMs = screenMs,
                unlocksToday = unlocks,
                hasUsageAccess = hasAccess,
                chaptersDone = chaptersDone,
                chaptersTotal = chaptersTotal,
                syllabusProgress = syllabusProgress,
                nextExamTitle = nextExamTitle,
                nextExamFormattedDate = formattedDate,
                nextExamDaysRemaining = daysRemaining,
                totalNotes = totalNotes,
                isLoading = false
            )
        }
        .flowOn(Dispatchers.IO)
        .onEach { newState -> _uiState.value = newState }
        .launchIn(viewModelScope)
    }

    private fun calculateExamDetails(exam: ExamEntity?): Triple<String?, String?, Int?> {
        if (exam == null || exam.examDate.isBlank()) return Triple(null, null, null)
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = format.parse(exam.examDate.trim()) ?: return Triple(exam.title, exam.examDate, null)
            val outFormat = SimpleDateFormat("d MMM", Locale.getDefault())
            val formatted = outFormat.format(date)

            val now = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val diffMs = date.time - now
            val days = ceil(diffMs.toDouble() / 86400000.0).toInt()
            Triple(exam.title, formatted, days)
        } catch (_: Exception) {
            Triple(exam.title, exam.examDate, null)
        }
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

    private fun queryUsageAndUnlocks(): Pair<Long, Int> {
        val now = System.currentTimeMillis()
        cachedUsageSnapshot?.let { cached ->
            if (now - cachedUsageSnapshotAt < 30_000L) return cached
        }

        val result = try {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis

            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Pair(0L, 0)

            var screenTimeMs = 0L
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)?.forEach { stat ->
                if (stat.packageName != context.packageName && stat.totalTimeInForeground > 0L) {
                    screenTimeMs += stat.totalTimeInForeground
                }
            }

            var unlockCount = 0
            try {
                val events = manager.queryEvents(startOfDay, now)
                val event = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == 18) unlockCount++
                }
            } catch (_: Throwable) {}

            val fallback = context.getSharedPreferences("go_back_unlocks", 0)
                .getInt("today_unlocks", 0)
            Pair(screenTimeMs, if (unlockCount > 0) unlockCount else fallback)
        } catch (_: Throwable) {
            Pair(0L, 0)
        }

        cachedUsageSnapshot = result
        cachedUsageSnapshotAt = now
        return result
    }

    private fun syncWidgetSnapshot(
        focusMinutesToday: Int,
        dailyGoalMinutes: Int,
        streakDays: Int,
        nextExamTitle: String,
        nextExamDays: Int
    ) {
        try {
            context.getSharedPreferences("go_back_widget_data", Context.MODE_PRIVATE)
                .edit()
                .putInt("today_focus_minutes", focusMinutesToday)
                .putInt("daily_goal_minutes", dailyGoalMinutes)
                .putInt("streak_days", streakDays)
                .putString("next_exam_name", nextExamTitle)
                .putInt("next_exam_days", nextExamDays)
                .apply()
        } catch (_: Exception) {}
    }

    companion object {
        data class GoalStatsResult(
            val currentStreak: Int,
            val bestStreak: Int,
            val todaySeconds: Int,
            val milestone: StreakMilestone,
            val streakShieldActive: Boolean
        )

        /**
         * Calculates consecutive streak days, evaluates the 1-day streak shield,
         * and assigns the corresponding gamification milestone tier.
         */
        fun calculateFocusGoalStats(
            rows: List<FocusSessionEntity>,
            dailyGoalMinutes: Int,
            todayDate: Date = Date()
        ): GoalStatsResult {
            val goalSeconds = max(1, dailyGoalMinutes * 60)
            val dailyTotals = mutableMapOf<String, Int>()

            // Aggregate daily focus seconds for completed or recorded focus sessions
            rows.forEach { row ->
                if (row.sessionType == "focus") {
                    val dateKey = row.startedAt.take(10) // "YYYY-MM-DD"
                    if (dateKey.length == 10) {
                        dailyTotals[dateKey] = (dailyTotals[dateKey] ?: 0) + max(0, row.durationSeconds)
                    }
                }
            }

            val todayKey = try {
                todayDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString()
            } catch (_: Exception) {
                LocalDate.now().toString()
            }
            val todaySeconds = dailyTotals[todayKey] ?: 0
            val todayGoalMet = todaySeconds >= goalSeconds

            var currentStreak = 0
            var cursorKey = todayKey
            var streakShieldActive = false

            // If today hasn't met the goal yet, start evaluating from yesterday
            if (!todayGoalMet) {
                cursorKey = previousDateKey(cursorKey, 1)
            }

            // Loop backwards up to 3650 days (10 years)
            for (guard in 0 until 3650) {
                val daySeconds = dailyTotals[cursorKey] ?: 0
                if (daySeconds >= goalSeconds) {
                    currentStreak++
                    cursorKey = previousDateKey(cursorKey, 1)
                } else if (!streakShieldActive && guard > 0) {
                    // 1-Day Streak Shield: check if the previous day was met
                    val dayBefore = previousDateKey(cursorKey, 1)
                    if ((dailyTotals[dayBefore] ?: 0) >= goalSeconds) {
                        streakShieldActive = true
                        cursorKey = dayBefore
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            // Calculate historical best streak
            val successfulDays = dailyTotals.filter { it.value >= goalSeconds }.keys.sorted()
            var bestStreak = 0
            var running = 0
            var prevKey = ""
            for (dateKey in successfulDays) {
                running = if (prevKey.isNotEmpty() && previousDateKey(dateKey, 1) == prevKey) running + 1 else 1
                bestStreak = max(bestStreak, running)
                prevKey = dateKey
            }
            bestStreak = max(bestStreak, currentStreak)

            val milestone = calculateStreakMilestone(currentStreak)

            return GoalStatsResult(
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                todaySeconds = todaySeconds,
                milestone = milestone,
                streakShieldActive = streakShieldActive
            )
        }

        private fun previousDateKey(dateKey: String, days: Int): String {
            return try {
                LocalDate.parse(dateKey).minusDays(days.toLong()).toString()
            } catch (_: Exception) {
                dateKey
            }
        }

        fun calculateStreakMilestone(currentStreak: Int): StreakMilestone {
            if (currentStreak < 3) {
                return StreakMilestone(
                    tier = "none",
                    label = "Getting Started",
                    badge = "🌱",
                    days = currentStreak,
                    nextMilestoneDays = 3,
                    daysToNext = max(1, 3 - currentStreak),
                    progressPercent = ((currentStreak.toDouble() / 3.0) * 100).toInt()
                )
            }

            val milestones = listOf(
                StreakMilestone("spark", "Spark", "✨", 3, 7, 0, 0),
                StreakMilestone("momentum", "Momentum", "🔥", 7, 14, 0, 0),
                StreakMilestone("commitment", "Commitment", "⚡", 14, 30, 0, 0),
                StreakMilestone("habit", "Habit Builder", "🛡️", 30, 50, 0, 0),
                StreakMilestone("mastery", "Focus Mastery", "💎", 50, 100, 0, 0),
                StreakMilestone("centurion", "Centurion", "👑", 100, 150, 0, 0)
            )

            var currentTier = milestones[0]
            var nextTier = milestones[1]

            for (i in milestones.indices) {
                if (currentStreak >= milestones[i].days) {
                    currentTier = milestones[i]
                    nextTier = if (i + 1 < milestones.size) {
                        milestones[i + 1]
                    } else {
                        StreakMilestone("centurion", "Grandmaster", "👑", currentTier.days + 50, currentTier.days + 100, 0, 100)
                    }
                }
            }

            val range = max(1, nextTier.days - currentTier.days)
            val progressIntoTier = currentStreak - currentTier.days
            val progressPercent = min(100, ((progressIntoTier.toDouble() / range.toDouble()) * 100).toInt())

            return StreakMilestone(
                tier = currentTier.tier,
                label = currentTier.label,
                badge = currentTier.badge,
                days = currentStreak,
                nextMilestoneDays = nextTier.days,
                daysToNext = max(0, nextTier.days - currentStreak),
                progressPercent = progressPercent
            )
        }
    }
}

class TodayViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = GoBackDatabase.getDatabase(context)
        return TodayViewModel(context.applicationContext, database) as T
    }
}
