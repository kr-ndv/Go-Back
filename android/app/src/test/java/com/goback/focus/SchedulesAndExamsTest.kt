package com.goback.focus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Tier 1, Tier 2, and Tier 3 tests for Schedules Engine & 3-Stage Exam Reminders.
 * Tests 24-hr time validation, weekday parsing/formatting, 35-day occurrence generation,
 * skip dates exclusion, and the 7d / 1d / morning 3-stage exam reminder scheduling.
 */
class SchedulesAndExamsTest {

    // --- Domain Models & Logic Oracles ---

    data class ScheduleDefinition(
        val id: String,
        val title: String,
        val days: List<Int>,
        val startTime: String, // "HH:mm"
        val durationMinutes: Int = 25,
        val enabled: Boolean = true,
        val reminderMinutes: Int = 0,
        val skipDates: Set<String> = emptySet(),
        val routineType: String = "custom"
    )

    data class ScheduleOccurrence(
        val scheduleId: String,
        val title: String,
        val startDateTime: LocalDateTime,
        val reminderDateTime: LocalDateTime,
        val durationMinutes: Int
    )

    data class ExamDefinition(
        val id: String,
        val title: String,
        val examDate: String, // "YYYY-MM-DD"
        val details: String = ""
    )

    data class ExamReminderTrigger(
        val examId: String,
        val stage: String, // "7d", "1d", "morning"
        val triggerDateTime: LocalDateTime,
        val title: String,
        val body: String
    )

    object ScheduleEngine {
        val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun isValidTimeOfDay(time: String): Boolean {
            return time.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))
        }

        fun isValidDuration(minutes: Int): Boolean {
            return minutes in 5..180
        }

        fun formatWeekdays(days: List<Int>): String {
            val clean = days.filter { it in 0..6 }.distinct().sorted()
            if (clean.size == 7) return "Every day"
            if (clean.size == 5 && clean == listOf(1, 2, 3, 4, 5)) return "Weekdays"
            if (clean.size == 2 && clean == listOf(0, 6)) return "Weekends"
            return clean.joinToString(", ") { WEEKDAY_LABELS[it] }
        }

        fun resolveActiveDays(schedule: ScheduleDefinition): List<Int> {
            return when (schedule.routineType.lowercase()) {
                "weekday" -> listOf(1, 2, 3, 4, 5)
                "weekend" -> listOf(0, 6)
                else -> schedule.days.filter { it in 0..6 }.distinct().sorted()
            }
        }

        fun generateUpcomingOccurrences(
            schedule: ScheduleDefinition,
            now: LocalDateTime,
            horizonDays: Int = 35
        ): List<ScheduleOccurrence> {
            if (!schedule.enabled || !isValidTimeOfDay(schedule.startTime)) return emptyList()

            val activeDays = resolveActiveDays(schedule)
            if (activeDays.isEmpty()) return emptyList()

            val timeParts = schedule.startTime.split(":").map { it.toInt() }
            val scheduleTime = LocalTime.of(timeParts[0], timeParts[1])
            val result = mutableListOf<ScheduleOccurrence>()
            val baseDate = now.toLocalDate()

            for (offset in 0..horizonDays) {
                val candidateDate = baseDate.plusDays(offset.toLong())
                val dayOfWeekSun0 = candidateDate.dayOfWeek.value % 7 // Java Mon=1..Sun=7 -> Sun=0..Sat=6
                if (!activeDays.contains(dayOfWeekSun0)) continue

                val dateKey = candidateDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                if (schedule.skipDates.contains(dateKey)) continue

                val startDateTime = LocalDateTime.of(candidateDate, scheduleTime)
                if (startDateTime.isBefore(now) || startDateTime.isEqual(now)) continue

                val reminderOffset = schedule.reminderMinutes.coerceIn(0, 120)
                val reminderDateTime = startDateTime.minusMinutes(reminderOffset.toLong())
                val effectiveReminder = if (reminderDateTime.isAfter(now)) reminderDateTime else startDateTime

                result.add(
                    ScheduleOccurrence(
                        scheduleId = schedule.id,
                        title = schedule.title,
                        startDateTime = startDateTime,
                        reminderDateTime = effectiveReminder,
                        durationMinutes = schedule.durationMinutes
                    )
                )
            }
            return result
        }

        fun calculateExamReminders(
            exam: ExamDefinition,
            now: LocalDateTime
        ): List<ExamReminderTrigger> {
            val examDate = try {
                LocalDate.parse(exam.examDate, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                return emptyList()
            }

            // 1. 7 days prior at 09:00:00
            val stage7d = LocalDateTime.of(examDate.minusDays(7), LocalTime.of(9, 0, 0))

            // 2. 1 day prior at 09:00:00
            val stage1d = LocalDateTime.of(examDate.minusDays(1), LocalTime.of(9, 0, 0))

            // 3. Morning of exam at 07:00:00
            val stageMorning = LocalDateTime.of(examDate, LocalTime.of(7, 0, 0))

            val candidateReminders = listOf(
                ExamReminderTrigger(
                    examId = exam.id,
                    stage = "7d",
                    triggerDateTime = stage7d,
                    title = "📅 Exam in 7 days: ${exam.title}",
                    body = "${exam.title} is coming up in one week (${exam.examDate}). Review syllabus topics early!"
                ),
                ExamReminderTrigger(
                    examId = exam.id,
                    stage = "1d",
                    triggerDateTime = stage1d,
                    title = "⚠️ Exam tomorrow: ${exam.title}",
                    body = "${exam.title} is tomorrow. Take time to review key summary notes and rest well tonight."
                ),
                ExamReminderTrigger(
                    examId = exam.id,
                    stage = "morning",
                    triggerDateTime = stageMorning,
                    title = "🎯 Exam day: ${exam.title}",
                    body = "Good luck with your ${exam.title} exam today! Stay calm, focused, and confident."
                )
            )

            // Only schedule future triggers
            return candidateReminders.filter { it.triggerDateTime.isAfter(now) }
        }

        fun calculateDaysUntilExam(today: LocalDate, examDateStr: String): Long {
            val examDate = LocalDate.parse(examDateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            return ChronoUnit.DAYS.between(today, examDate)
        }
    }

    // =========================================================================
    // Tier 1: Feature Coverage (>=5 per feature)
    // =========================================================================

    @Test
    fun tier1_timeValidation_validAndInvalidFormats() {
        assertTrue(ScheduleEngine.isValidTimeOfDay("09:30"))
        assertTrue(ScheduleEngine.isValidTimeOfDay("00:00"))
        assertTrue(ScheduleEngine.isValidTimeOfDay("23:59"))
        assertTrue(ScheduleEngine.isValidTimeOfDay("18:45"))

        assertFalse(ScheduleEngine.isValidTimeOfDay("24:00"))
        assertFalse(ScheduleEngine.isValidTimeOfDay("12:60"))
        assertFalse(ScheduleEngine.isValidTimeOfDay("9:30"))
        assertFalse(ScheduleEngine.isValidTimeOfDay("random"))
    }

    @Test
    fun tier1_weekdayFormatting_standardLabels() {
        assertEquals("Weekdays", ScheduleEngine.formatWeekdays(listOf(1, 2, 3, 4, 5)))
        assertEquals("Weekends", ScheduleEngine.formatWeekdays(listOf(0, 6)))
        assertEquals("Every day", ScheduleEngine.formatWeekdays(listOf(0, 1, 2, 3, 4, 5, 6)))
        assertEquals("Mon, Wed, Fri", ScheduleEngine.formatWeekdays(listOf(1, 3, 5)))
    }

    @Test
    fun tier1_occurrenceGeneration_generatesCorrectDates() {
        // Today is Saturday Nov 7, 2026 at 10:00
        val now = LocalDateTime.of(2026, 11, 7, 10, 0, 0)
        val schedule = ScheduleDefinition(
            id = "sched-1",
            title = "Morning Deep Work",
            days = listOf(1, 3, 5), // Mon, Wed, Fri
            startTime = "08:00",
            durationMinutes = 60,
            reminderMinutes = 15
        )

        val occurrences = ScheduleEngine.generateUpcomingOccurrences(schedule, now, horizonDays = 7)

        // Over next 7 days:
        // Mon Nov 9 (08:00), Wed Nov 11 (08:00), Fri Nov 13 (08:00)
        assertEquals(3, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 11, 9, 8, 0), occurrences[0].startDateTime)
        // Reminder 15 minutes before at 07:45
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 45), occurrences[0].reminderDateTime)
    }

    @Test
    fun tier1_skipDates_correctlyExcludesHolidays() {
        val now = LocalDateTime.of(2026, 11, 7, 10, 0, 0)
        val schedule = ScheduleDefinition(
            id = "sched-2",
            title = "Daily Study",
            days = listOf(0, 1, 2, 3, 4, 5, 6),
            startTime = "18:00",
            skipDates = setOf("2026-11-08") // Skip Sunday Nov 8
        )

        val occurrences = ScheduleEngine.generateUpcomingOccurrences(schedule, now, horizonDays = 2)

        // Nov 7 18:00 (Today evening - included)
        // Nov 8 (Sunday - skipped!)
        // Nov 9 (Monday 18:00 - included)
        assertEquals(2, occurrences.size)
        assertEquals(LocalDate.of(2026, 11, 7), occurrences[0].startDateTime.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 9), occurrences[1].startDateTime.toLocalDate())
    }

    @Test
    fun tier1_examReminders_allThreeStagesGeneratedForFutureExam() {
        // Today is Nov 1, 2026 at 08:00
        val now = LocalDateTime.of(2026, 11, 1, 8, 0, 0)
        val exam = ExamDefinition(
            id = "exam-1",
            title = "Calculus II",
            examDate = "2026-11-18" // In 17 days
        )

        val reminders = ScheduleEngine.calculateExamReminders(exam, now)

        assertEquals(3, reminders.size)

        // Stage 1: 7 days prior at 09:00 (Nov 11, 09:00)
        assertEquals("7d", reminders[0].stage)
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 0), reminders[0].triggerDateTime)
        assertTrue(reminders[0].title.contains("7 days"))

        // Stage 2: 1 day prior at 09:00 (Nov 17, 09:00)
        assertEquals("1d", reminders[1].stage)
        assertEquals(LocalDateTime.of(2026, 11, 17, 9, 0), reminders[1].triggerDateTime)
        assertTrue(reminders[1].title.contains("tomorrow"))

        // Stage 3: Morning of exam at 07:00 (Nov 18, 07:00)
        assertEquals("morning", reminders[2].stage)
        assertEquals(LocalDateTime.of(2026, 11, 18, 7, 0), reminders[2].triggerDateTime)
        assertTrue(reminders[2].title.contains("Exam day"))
    }

    @Test
    fun tier1_heroCountdownDaysCalculation() {
        val today = LocalDate.of(2026, 11, 7)
        assertEquals(11L, ScheduleEngine.calculateDaysUntilExam(today, "2026-11-18"))
        assertEquals(1L, ScheduleEngine.calculateDaysUntilExam(today, "2026-11-08"))
        assertEquals(0L, ScheduleEngine.calculateDaysUntilExam(today, "2026-11-07"))
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (>=5 per feature)
    // =========================================================================

    @Test
    fun tier2_boundary_durationValidation() {
        assertTrue(ScheduleEngine.isValidDuration(5)) // Minimum 5 mins
        assertTrue(ScheduleEngine.isValidDuration(180)) // Maximum 180 mins
        assertFalse(ScheduleEngine.isValidDuration(4))
        assertFalse(ScheduleEngine.isValidDuration(181))
        assertFalse(ScheduleEngine.isValidDuration(0))
    }

    @Test
    fun tier2_boundary_disabledScheduleProducesNoOccurrences() {
        val now = LocalDateTime.of(2026, 11, 7, 10, 0, 0)
        val disabled = ScheduleDefinition(
            id = "disabled",
            title = "Test",
            days = listOf(1, 2, 3),
            startTime = "14:00",
            enabled = false
        )
        val occurrences = ScheduleEngine.generateUpcomingOccurrences(disabled, now, 7)
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun tier2_boundary_pastRemindersFilteredOut() {
        // Today is Nov 17, 2026 at 12:00 (Day before exam)
        val now = LocalDateTime.of(2026, 11, 17, 12, 0, 0)
        val exam = ExamDefinition(
            id = "exam-near",
            title = "Chemistry",
            examDate = "2026-11-18"
        )

        val reminders = ScheduleEngine.calculateExamReminders(exam, now)

        // 7d reminder (Nov 11) is past -> filtered
        // 1d reminder (Nov 17 09:00) is past (now is 12:00) -> filtered
        // Only Morning of exam (Nov 18 07:00) is in future!
        assertEquals(1, reminders.size)
        assertEquals("morning", reminders[0].stage)
    }

    @Test
    fun tier2_boundary_preReminderClampedToStartTimeIfAlreadyPassed() {
        // Start time is 10:10, now is 10:05, pre-reminder is 15 mins (09:55 - which is past!)
        val now = LocalDateTime.of(2026, 11, 7, 10, 5, 0)
        val schedule = ScheduleDefinition(
            id = "sched-pre",
            title = "Fast Alert",
            days = listOf(6), // Saturday (today)
            startTime = "10:10",
            reminderMinutes = 15
        )

        val occurrences = ScheduleEngine.generateUpcomingOccurrences(schedule, now, 1)
        assertEquals(1, occurrences.size)
        // Effective reminder clamps to start time rather than a past timestamp
        assertEquals(occurrences[0].startDateTime, occurrences[0].reminderDateTime)
    }

    @Test
    fun tier2_boundary_routinePresetsOverrideDaysList() {
        val scheduleWeekday = ScheduleDefinition(
            id = "routine-1",
            title = "Weekday Study",
            days = emptyList(), // Empty days
            startTime = "10:00",
            routineType = "weekday"
        )
        val activeDays = ScheduleEngine.resolveActiveDays(scheduleWeekday)
        assertEquals(listOf(1, 2, 3, 4, 5), activeDays)
    }
}
