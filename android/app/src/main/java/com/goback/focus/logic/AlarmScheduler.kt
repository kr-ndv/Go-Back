package com.goback.focus.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.goback.focus.GoBackApplication
import com.goback.focus.data.db.GoBackDatabase
import com.goback.focus.data.entity.ExamEntity
import com.goback.focus.data.entity.FocusScheduleEntity
import com.goback.focus.receiver.FocusAlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {

    /**
     * Reschedules the next upcoming occurrence for a specific schedule.
     */
    fun scheduleNextOccurrence(context: Context, schedule: FocusScheduleEntity) {
        if (schedule.enabled != 1) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = Calendar.getInstance()

        val parts = schedule.startTime.split(":")
        if (parts.size != 2) return
        val startHour = parts[0].toIntOrNull() ?: return
        val startMinute = parts[1].toIntOrNull() ?: return
        val days = parseActiveDays(schedule.daysJson)
        val skipDates = parseSkipDates(schedule.skipDatesJson)

        // Find the next occurrence in the next 14 days
        for (offset in 0..14) {
            val target = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, startHour)
                set(Calendar.MINUTE, startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (schedule.reminderMinutes > 0) {
                    add(Calendar.MINUTE, -schedule.reminderMinutes)
                }
            }

            val targetDayIndex = target.get(Calendar.DAY_OF_WEEK) - 1
            if (!days.contains(targetDayIndex)) continue

            val dateIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(target.time)
            if (skipDates.contains(dateIso)) continue

            if (target.timeInMillis <= now.timeInMillis) continue

            val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
                putExtra("kind", "schedule")
                putExtra("schedule_id", schedule.id)
                putExtra("title", if (schedule.reminderMinutes > 0) "Focus time is coming up" else "Your focus block has started")
                putExtra("body", "${schedule.title} · ${schedule.durationMinutes} min. Open Go Back to focus.")
            }

            val requestCode = 9200 + (schedule.id.hashCode() and 0x7FFF)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } catch (_: Exception) {}
            break
        }
    }

    /**
     * Reschedules all enabled schedules from the database.
     */
    suspend fun rescheduleAllSchedules(context: Context) = withContext(Dispatchers.IO) {
        val db = GoBackDatabase.getDatabase(context)
        val schedules = db.focusScheduleDao().getAll().first()
        schedules.forEach { schedule ->
            scheduleNextOccurrence(context, schedule)
        }
    }

    /**
     * Reschedules all upcoming exam reminders (7d, 1d, exam morning) from the database.
     */
    suspend fun rescheduleAllExams(context: Context) = withContext(Dispatchers.IO) {
        val db = GoBackDatabase.getDatabase(context)
        val exams = db.examDao().getAll().first()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@withContext
        val nowMs = System.currentTimeMillis()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        exams.forEach { exam ->
            val examDateObj = try {
                format.parse(exam.examDate.trim())
            } catch (_: Exception) { null } ?: return@forEach

            // Stage 1: 7 days before at 9:00 AM
            val stage1Cal = Calendar.getInstance().apply {
                time = examDateObj
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Stage 2: 1 day before at 9:00 AM
            val stage2Cal = Calendar.getInstance().apply {
                time = examDateObj
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Stage 3: Morning of exam at 7:00 AM
            val stage3Cal = Calendar.getInstance().apply {
                time = examDateObj
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val stages = listOf(
                Triple(1, stage1Cal.timeInMillis, "📅 Exam in 7 days: ${exam.title}" to "${exam.title} is coming up in one week (${exam.examDate}). Review syllabus chapters early!"),
                Triple(2, stage2Cal.timeInMillis, "⚠️ Exam tomorrow: ${exam.title}" to "${exam.title} is tomorrow. Take time to review key summary notes and rest well tonight."),
                Triple(3, stage3Cal.timeInMillis, "🎯 Exam day: ${exam.title}" to "Good luck with your ${exam.title} exam today! Stay calm, focused, and confident.")
            )

            stages.forEach { (stage, triggerTimeMs, alert) ->
                if (triggerTimeMs > nowMs) {
                    val intent = Intent(context, FocusAlarmReceiver::class.java).apply {
                        putExtra("kind", "exam")
                        putExtra("exam_id", exam.id)
                        putExtra("title", alert.first)
                        putExtra("body", alert.second)
                    }

                    val requestCode = 8200 + ((exam.id.hashCode() and 0x7FF) * 10) + stage
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun cancelSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        val requestCode = 9200 + (scheduleId.hashCode() and 0x7FFF)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    fun cancelExam(context: Context, examId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusAlarmReceiver::class.java)
        for (stage in 1..3) {
            val requestCode = 8200 + ((examId.hashCode() and 0x7FF) * 10) + stage
            val pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
        }
    }

    private fun parseActiveDays(daysJson: String): List<Int> {
        return try {
            val array = JSONArray(daysJson)
            val list = mutableListOf<Int>()
            for (i in 0 until array.length()) {
                list.add(array.getInt(i))
            }
            if (list.isEmpty()) listOf(1, 2, 3, 4, 5) else list
        } catch (_: Exception) {
            listOf(1, 2, 3, 4, 5)
        }
    }

    private fun parseSkipDates(skipDatesJson: String): Set<String> {
        return try {
            val array = JSONArray(skipDatesJson)
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                set.add(array.getString(i))
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }
}
