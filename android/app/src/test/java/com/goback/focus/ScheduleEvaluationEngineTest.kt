package com.goback.focus

import com.goback.focus.data.entity.FocusScheduleEntity
import com.goback.focus.logic.ScheduleEvaluationEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleEvaluationEngineTest {

    @Test
    fun parseDays_parsesValidJson() {
        val days = ScheduleEvaluationEngine.parseDays("[1, 2, 3, 4, 5]")
        assertEquals(setOf(1, 2, 3, 4, 5), days)

        val empty = ScheduleEvaluationEngine.parseDays("[]")
        assertTrue(empty.isEmpty())

        val invalid = ScheduleEvaluationEngine.parseDays("not json")
        assertTrue(invalid.isEmpty())
    }

    @Test
    fun getActiveDays_respectsRoutineTypes() {
        val weekdaySchedule = FocusScheduleEntity(
            id = "s1",
            title = "Study",
            startTime = "09:00",
            routineType = "weekday",
            createdAt = "2026-01-01"
        )
        assertEquals(setOf(1, 2, 3, 4, 5), ScheduleEvaluationEngine.getActiveDays(weekdaySchedule))

        val weekendSchedule = FocusScheduleEntity(
            id = "s2",
            title = "Weekend Read",
            startTime = "10:00",
            routineType = "weekend",
            createdAt = "2026-01-01"
        )
        assertEquals(setOf(0, 6), ScheduleEvaluationEngine.getActiveDays(weekendSchedule))

        val customSchedule = FocusScheduleEntity(
            id = "s3",
            title = "Custom",
            startTime = "11:00",
            daysJson = "[0, 2, 4]",
            routineType = "custom",
            createdAt = "2026-01-01"
        )
        assertEquals(setOf(0, 2, 4), ScheduleEvaluationEngine.getActiveDays(customSchedule))
    }

    @Test
    fun format12HourTime_formatsAccurately() {
        assertEquals("9:00 AM", ScheduleEvaluationEngine.format12HourTime("09:00"))
        assertEquals("12:00 PM", ScheduleEvaluationEngine.format12HourTime("12:00"))
        assertEquals("12:00 AM", ScheduleEvaluationEngine.format12HourTime("00:00"))
        assertEquals("1:30 PM", ScheduleEvaluationEngine.format12HourTime("13:30"))
        assertEquals("11:59 PM", ScheduleEvaluationEngine.format12HourTime("23:59"))
    }

    @Test
    fun calculateEndTime12Hour_computesEndAndMidnightRollover() {
        // 9:00 AM + 45 min -> 9:45 AM
        assertEquals("9:45 AM", ScheduleEvaluationEngine.calculateEndTime12Hour("09:00", 45))

        // 11:30 PM + 60 min -> 12:30 AM
        assertEquals("12:30 AM", ScheduleEvaluationEngine.calculateEndTime12Hour("23:30", 60))
    }

    @Test
    fun isScheduleActive_evaluatesCorrectlyWithinTimeWindow() {
        val schedule = FocusScheduleEntity(
            id = "s1",
            title = "Morning Block",
            startTime = "10:00",
            durationMinutes = 60,
            enabled = 1,
            routineType = "weekday",
            createdAt = "2026-01-01"
        )

        // Monday at 10:30 AM -> Active
        val activeCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 4, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertTrue(ScheduleEvaluationEngine.isScheduleActive(schedule, activeCal))

        // Monday at 11:30 AM -> Inactive (ended at 11:00)
        val afterCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 4, 11, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertFalse(ScheduleEvaluationEngine.isScheduleActive(schedule, afterCal))

        // Sunday at 10:30 AM -> Inactive (Sunday is not weekday)
        val weekendCal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 3, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertFalse(ScheduleEvaluationEngine.isScheduleActive(schedule, weekendCal))

        // Disabled schedule -> Inactive
        val disabledSchedule = schedule.copy(enabled = 0)
        assertFalse(ScheduleEvaluationEngine.isScheduleActive(disabledSchedule, activeCal))
    }
}
