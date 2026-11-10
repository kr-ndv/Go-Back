package com.goback.focus.logic

import android.content.Context
import com.goback.focus.data.entity.FocusScheduleEntity
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ScheduleEvaluationEngine
 *
 * Centralized evaluation engine for scheduled focus blocks.
 */
object ScheduleEvaluationEngine {
    val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /**
     * Checks if a schedule is active at this exact moment.
     * Evaluates enabled status, skip dates, day of week, and 24-hr time span (including midnight rollover).
     */
    fun isScheduleActive(
        schedule: FocusScheduleEntity,
        calendar: Calendar = Calendar.getInstance()
    ): Boolean {
        if (schedule.enabled != 1) return false

        // 1. Check skip dates
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        val skips = parseSkipDates(schedule.skipDatesJson)
        if (skips.contains(todayIso)) return false

        // 2. Check Day of Week (Calendar: 1=Sun, 2=Mon... 7=Sat -> 0-indexed: 0=Sun..6=Sat)
        val currentDayIndex = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val days = getActiveDays(schedule)
        if (!days.contains(currentDayIndex)) return false

        // 3. Check Time Window
        val parts = schedule.startTime.split(":")
        if (parts.size != 2) return false
        val startHour = parts[0].toIntOrNull() ?: return false
        val startMinute = parts[1].toIntOrNull() ?: return false

        val currentMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinuteOfDay = startHour * 60 + startMinute
        val endMinuteOfDay = startMinuteOfDay + schedule.durationMinutes

        return if (endMinuteOfDay <= 24 * 60) {
            currentMinuteOfDay in startMinuteOfDay until endMinuteOfDay
        } else {
            // Rollover past midnight
            currentMinuteOfDay >= startMinuteOfDay || currentMinuteOfDay < (endMinuteOfDay % (24 * 60))
        }
    }

    /**
     * Evaluates all schedules and returns the currently active one (if any).
     */
    fun findActiveSchedule(
        schedules: List<FocusScheduleEntity>,
        calendar: Calendar = Calendar.getInstance()
    ): FocusScheduleEntity? {
        return schedules.firstOrNull { isScheduleActive(it, calendar) }
    }

    /**
     * Syncs the active scheduled blocking state to "go_back_blocking" SharedPreferences
     * so that GoBackBlockingService instantly reflects scheduled focus enforcement.
     */
    fun evaluateAndSync(
        context: Context,
        schedules: List<FocusScheduleEntity>
    ): Boolean {
        val activeSchedule = findActiveSchedule(schedules)
        val isActive = activeSchedule != null
        val prefs = context.getSharedPreferences("go_back_blocking", Context.MODE_PRIVATE)

        val editor = prefs.edit().putBoolean("scheduled_blocking_active", isActive)
        if (activeSchedule != null) {
            editor.putString("active_schedule_id", activeSchedule.id)
            editor.putString("active_schedule_title", activeSchedule.title)
        } else {
            editor.remove("active_schedule_id")
            editor.remove("active_schedule_title")
        }
        editor.apply()
        return isActive
    }

    fun parseDays(daysJson: String): Set<Int> {
        val set = mutableSetOf<Int>()
        try {
            val arr = JSONArray(daysJson)
            for (i in 0 until arr.length()) {
                set.add(arr.getInt(i))
            }
        } catch (_: Exception) {
            // JVM unit test fallback when android.jar org.json is a stub
            try {
                val cleaned = daysJson.trim().removePrefix("[").removeSuffix("]").trim()
                if (cleaned.isNotEmpty()) {
                    cleaned.split(',').mapNotNull { it.trim().toIntOrNull() }.forEach { set.add(it) }
                }
            } catch (_: Exception) {}
        }
        return set
    }

    fun getActiveDays(schedule: FocusScheduleEntity): Set<Int> {
        return when (schedule.routineType) {
            "weekday" -> setOf(1, 2, 3, 4, 5)
            "weekend" -> setOf(0, 6)
            else -> parseDays(schedule.daysJson)
        }
    }

    fun parseSkipDates(skipDatesJson: String): Set<String> {
        val set = mutableSetOf<String>()
        try {
            val arr = JSONArray(skipDatesJson)
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
        } catch (_: Exception) {
            // JVM unit test fallback when android.jar org.json is a stub
            try {
                val cleaned = skipDatesJson.trim().removePrefix("[").removeSuffix("]").trim()
                if (cleaned.isNotEmpty()) {
                    cleaned.split(',').map { it.trim().removeSurrounding("\"").removeSurrounding("'") }.filter { it.isNotEmpty() }.forEach { set.add(it) }
                }
            } catch (_: Exception) {}
        }
        return set
    }

    fun formatDaysSummary(days: Set<Int>, routineType: String): String {
        return when (routineType) {
            "weekday" -> "Weekdays"
            "weekend" -> "Weekends"
            else -> {
                if (days.size == 7) "Every day"
                else if (days.size == 5 && days.containsAll(listOf(1, 2, 3, 4, 5))) "Weekdays"
                else if (days.size == 2 && days.contains(0) && days.contains(6)) "Weekends"
                else if (days.isEmpty()) "No days selected"
                else days.sorted().joinToString(", ") { WEEKDAY_LABELS[it] }
            }
        }
    }

    fun format12HourTime(time24: String): String {
        val parts = time24.split(":")
        if (parts.size != 2) return time24
        val h = parts[0].toIntOrNull() ?: return time24
        val m = parts[1].toIntOrNull() ?: return time24
        val period = if (h >= 12) "PM" else "AM"
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return String.format(Locale.US, "%d:%02d %s", hour12, m, period)
    }

    fun calculateEndTime12Hour(startTime24: String, durationMinutes: Int): String {
        val parts = startTime24.split(":")
        if (parts.size != 2) return ""
        val h = parts[0].toIntOrNull() ?: return ""
        val m = parts[1].toIntOrNull() ?: return ""
        val totalMinutes = (h * 60 + m + durationMinutes) % (24 * 60)
        val endH = totalMinutes / 60
        val endM = totalMinutes % 60
        return format12HourTime(String.format(Locale.US, "%02d:%02d", endH, endM))
    }
}
