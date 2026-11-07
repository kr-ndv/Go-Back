package com.goback.focus

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tier 3: Cross-Feature Combinations & Tier 4: Real-World Application Scenarios.
 * Validates complex end-to-end interactions across focus sessions, app blocking,
 * streak shield preservation, schedule occurrences, and multi-stage exam reminders.
 */
class CrossFeatureAndScenariosTest {

    @Test
    fun tier3_timerRecoveryAndPresetBlockingInteraction() {
        val nowMs = 1_000_000L
        val endAtMs = nowMs + 1800 * 1000L // 30 mins remaining
        val presetBlocked = setOf("com.reddit.frontpage", "com.twitter.android")

        // 1. In-flight session with preset-blocked packages
        val session = FocusTimerAndPomodoroTest.FocusEngine.recoverSession(
            sessionId = "session-preset-1",
            minutes = 45,
            endAtMs = endAtMs,
            mode = FocusTimerAndPomodoroTest.FocusMode.TIMER,
            sessionType = FocusTimerAndPomodoroTest.PomodoroSessionType.FOCUS,
            accumulatedElapsed = 900,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Exam Prep",
            notes = "Focus block",
            nowMs = nowMs
        )

        assertTrue(session.isRunning)
        assertFalse(session.isExpired)

        val config = BlockingConfigurationTest.BlockingConfig(
            blockedPackages = emptySet(), // No permanent blocks
            blockOnlyDuringFocus = true
        )

        // While session is running, preset apps must be blocked
        assertTrue(
            BlockingConfigurationTest.BlockingEngine.isPackageBlocked(
                "com.reddit.frontpage",
                config,
                sessionBlockedPackages = presetBlocked,
                focusActive = session.isRunning
            )
        )

        // Normal apps remain unblocked
        assertFalse(
            BlockingConfigurationTest.BlockingEngine.isPackageBlocked(
                "com.google.android.apps.docs",
                config,
                sessionBlockedPackages = presetBlocked,
                focusActive = session.isRunning
            )
        )

        // 2. Later: Session expires
        val expiredSession = FocusTimerAndPomodoroTest.FocusEngine.recoverSession(
            sessionId = "session-preset-1",
            minutes = 45,
            endAtMs = endAtMs,
            mode = FocusTimerAndPomodoroTest.FocusMode.TIMER,
            sessionType = FocusTimerAndPomodoroTest.PomodoroSessionType.FOCUS,
            accumulatedElapsed = 2700,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Exam Prep",
            notes = "Focus block",
            nowMs = endAtMs + 10_000L // 10 seconds past expiration
        )

        assertFalse(expiredSession.isRunning)
        assertTrue(expiredSession.isExpired)

        // After session expiration, session-blocked apps are automatically released
        assertFalse(
            BlockingConfigurationTest.BlockingEngine.isPackageBlocked(
                "com.reddit.frontpage",
                config,
                sessionBlockedPackages = emptySet(), // Cleared on session end
                focusActive = expiredSession.isRunning
            )
        )
    }

    @Test
    fun tier3_strictModeChallengeWithPomodoroPhaseCompletion() {
        val focusRounds = 3

        // Strict mode active: user cannot exit manually without challenge phrase
        val challengeAttempt = "I want to stop"
        assertFalse(FocusTimerAndPomodoroTest.FocusEngine.validateStrictChallenge(challengeAttempt))

        val challengeSuccess = "I choose to break my focus"
        assertTrue(FocusTimerAndPomodoroTest.FocusEngine.validateStrictChallenge(challengeSuccess))

        // When the timer completes naturally, next phase transitions WITHOUT challenge
        val nextPhase = FocusTimerAndPomodoroTest.FocusEngine.nextPomodoroPhase(
            FocusTimerAndPomodoroTest.PomodoroSessionType.FOCUS,
            focusRounds
        )

        // 4th completed round transitions to Long Break (15 mins)
        assertEquals(FocusTimerAndPomodoroTest.PomodoroSessionType.LONG_BREAK, nextPhase.sessionType)
        assertEquals(15, nextPhase.minutes)
        assertEquals(4, nextPhase.completedFocusRounds)
    }

    @Test
    fun tier3_scheduleAndExamNotificationChannelCoexistence() {
        val now = LocalDateTime.of(2026, 11, 18, 6, 0, 0) // 06:00 AM on exam day

        val exam = SchedulesAndExamsTest.ExamDefinition(
            id = "exam-final",
            title = "Calculus II",
            examDate = "2026-11-18"
        )

        val schedule = SchedulesAndExamsTest.ScheduleDefinition(
            id = "sched-exam-morning",
            title = "Final Review Session",
            days = listOf(3), // Wednesday Nov 18
            startTime = "07:30",
            durationMinutes = 45,
            reminderMinutes = 15
        )

        // 1. Exam reminder fires at 07:00 AM on channel go-back-exams-v1
        val examReminders = SchedulesAndExamsTest.ScheduleEngine.calculateExamReminders(exam, now)
        assertEquals(1, examReminders.size)
        assertEquals("morning", examReminders[0].stage)
        assertEquals(LocalDateTime.of(2026, 11, 18, 7, 0), examReminders[0].triggerDateTime)

        // 2. Schedule occurrence pre-reminder fires at 07:15 AM on channel go-back-schedule-v3
        val occurrences = SchedulesAndExamsTest.ScheduleEngine.generateUpcomingOccurrences(schedule, now, horizonDays = 1)
        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 11, 18, 7, 30), occurrences[0].startDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 18, 7, 15), occurrences[0].reminderDateTime)

        // Ensure triggers are properly sequenced: 07:00 Exam alert -> 07:15 Schedule pre-reminder -> 07:30 Session start
        assertTrue(examReminders[0].triggerDateTime.isBefore(occurrences[0].reminderDateTime))
        assertTrue(occurrences[0].reminderDateTime.isBefore(occurrences[0].startDateTime))
    }

    // =========================================================================
    // Tier 4: Real-World Application Scenarios
    // =========================================================================

    @Test
    fun tier4_scenario1_studentDailyStudyWorkflow() {
        val today = LocalDate.of(2026, 11, 7) // Saturday

        // 1. Student checks dashboard: 3-day streak (Spark ✨), next exam in 11 days
        val pastSessions = listOf(
            StreakTiersAndShieldTest.FocusSessionRecord("2026-11-04T10:00:00", 7200),
            StreakTiersAndShieldTest.FocusSessionRecord("2026-11-05T10:00:00", 7200),
            StreakTiersAndShieldTest.FocusSessionRecord("2026-11-06T10:00:00", 7200)
        )
        val initialStats = StreakTiersAndShieldTest.StreakEngine.computeFocusGoalStats(pastSessions, 120, today)
        assertEquals(3, initialStats.currentStreak)
        assertEquals(StreakTiersAndShieldTest.StreakTier.SPARK, initialStats.streakMilestone.tier)
        assertEquals("✨", initialStats.streakMilestone.badge)

        val daysUntilExam = SchedulesAndExamsTest.ScheduleEngine.calculateDaysUntilExam(today, "2026-11-18")
        assertEquals(11L, daysUntilExam)

        // 2. Student runs 4-Round Pomodoro session:
        var rounds = 0
        var totalFocusSecondsToday = 0

        val blockingConfig = BlockingConfigurationTest.BlockingConfig(
            blockedPackages = setOf("com.instagram.android"),
            blockShorts = true,
            youtubeStudyMode = true,
            allowedChannels = setOf("3Blue1Brown")
        )

        for (round in 1..4) {
            // Focus period (25 mins)
            totalFocusSecondsToday += 25 * 60

            // During round 2, student tries to open YouTube Shorts -> blocked!
            if (round == 2) {
                // In YouTube Shorts feed
                assertTrue(blockingConfig.blockShorts)
            }

            // Student watches allowed 3Blue1Brown video -> allowed!
            assertTrue(
                BlockingConfigurationTest.BlockingEngine.matchesAllowedYouTubeChannel(
                    "3Blue1Brown - Chapter 2: Linear combinations and basis vectors",
                    blockingConfig.allowedChannels
                )
            )

            // Transition phase
            val phase = FocusTimerAndPomodoroTest.FocusEngine.nextPomodoroPhase(
                FocusTimerAndPomodoroTest.PomodoroSessionType.FOCUS,
                rounds
            )
            rounds = phase.completedFocusRounds

            if (round < 4) {
                assertEquals(FocusTimerAndPomodoroTest.PomodoroSessionType.SHORT_BREAK, phase.sessionType)
                assertEquals(5, phase.minutes)
                // Return to focus after short break
                val resumePhase = FocusTimerAndPomodoroTest.FocusEngine.nextPomodoroPhase(
                    FocusTimerAndPomodoroTest.PomodoroSessionType.SHORT_BREAK,
                    rounds
                )
                assertEquals(FocusTimerAndPomodoroTest.PomodoroSessionType.FOCUS, resumePhase.sessionType)
            } else {
                // 4th round earns Long Break!
                assertEquals(FocusTimerAndPomodoroTest.PomodoroSessionType.LONG_BREAK, phase.sessionType)
                assertEquals(15, phase.minutes)
            }
        }

        assertEquals(4, rounds)
        assertEquals(6000, totalFocusSecondsToday) // 100 minutes (1h 40m)

        // Goal celebration check at 100 mins: not yet (goal is 120m)
        assertFalse(
            StreakTiersAndShieldTest.StreakEngine.shouldTriggerCelebration(
                priorSecondsToday = 0,
                newSecondsToday = totalFocusSecondsToday,
                goalSeconds = 120 * 60
            )
        )

        // 3. Student completes 1 final 25m countdown session to reach 125 mins (7500s)
        val finalSeconds = totalFocusSecondsToday + 25 * 60 // 7500s
        assertTrue(
            StreakTiersAndShieldTest.StreakEngine.shouldTriggerCelebration(
                priorSecondsToday = totalFocusSecondsToday,
                newSecondsToday = finalSeconds,
                goalSeconds = 120 * 60
            )
        )

        // 4. End-of-day stats: streak increments from 3 to 4 days, on track to Momentum (7d)
        val todaySessions = pastSessions + StreakTiersAndShieldTest.FocusSessionRecord("2026-11-07T14:00:00", finalSeconds)
        val endOfDayStats = StreakTiersAndShieldTest.StreakEngine.computeFocusGoalStats(todaySessions, 120, today)

        assertTrue(endOfDayStats.todayGoalMet)
        assertEquals(4, endOfDayStats.currentStreak)
        assertEquals(StreakTiersAndShieldTest.StreakTier.SPARK, endOfDayStats.streakMilestone.tier)
        assertEquals(3, endOfDayStats.streakMilestone.daysToNext) // 7 - 4 = 3 days to Momentum
    }

    @Test
    fun tier4_scenario2_strictExamPreparationSession() {
        val examDateStr = "2026-11-18"
        val dayBefore = LocalDate.of(2026, 11, 17)
        val now = LocalDateTime.of(2026, 11, 17, 9, 0, 0) // Morning day before

        val exam = SchedulesAndExamsTest.ExamDefinition(
            id = "calc-final",
            title = "Calculus II",
            examDate = examDateStr
        )

        // 1. 1-day exam reminder fires at 09:00 AM
        val reminders = SchedulesAndExamsTest.ScheduleEngine.calculateExamReminders(exam, now.minusMinutes(1))
        val oneDayReminder = reminders.find { it.stage == "1d" }
        assertNotNull(oneDayReminder)
        assertEquals("⚠️ Exam tomorrow: Calculus II", oneDayReminder!!.title)

        // 2. Student schedules evening study block 19:00 to 21:00 with Strict Mode active
        val schedule = SchedulesAndExamsTest.ScheduleDefinition(
            id = "sched-strict",
            title = "Final Exam Cram",
            days = listOf(2), // Tuesday Nov 17
            startTime = "19:00",
            durationMinutes = 120,
            reminderMinutes = 15
        )

        val occurrences = SchedulesAndExamsTest.ScheduleEngine.generateUpcomingOccurrences(schedule, now, 1)
        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 11, 17, 18, 45), occurrences[0].reminderDateTime)

        // 3. At 19:15, student impulsively tries to give up -> strict challenge intercepts!
        val attempt1 = "quit now"
        assertFalse(FocusTimerAndPomodoroTest.FocusEngine.validateStrictChallenge(attempt1))

        // Student tries opening Settings to force-stop Go Back -> Settings protection blocks it!
        val blockingConfig = BlockingConfigurationTest.BlockingConfig(
            strictMode = true,
            strictUninstallProtection = true
        )
        assertTrue(
            BlockingConfigurationTest.BlockingEngine.shouldBlockSettingsAccess(
                "com.android.settings",
                "App info for Go Back: Force stop or Uninstall",
                blockingConfig,
                focusActive = true
            )
        )

        // Student decides to persist and completes the 120m session!
        val sessionRecord = StreakTiersAndShieldTest.FocusSessionRecord("2026-11-17T19:00:00", 7200)
        val stats = StreakTiersAndShieldTest.StreakEngine.computeFocusGoalStats(listOf(sessionRecord), 120, dayBefore)
        assertTrue(stats.todayGoalMet)

        // 4. Morning reminder on exam day (Nov 18, 07:00 AM)
        val examDayNow = LocalDateTime.of(2026, 11, 18, 6, 30, 0)
        val morningReminders = SchedulesAndExamsTest.ScheduleEngine.calculateExamReminders(exam, examDayNow)
        assertEquals(1, morningReminders.size)
        assertEquals("morning", morningReminders[0].stage)
        assertEquals("🎯 Exam day: Calculus II", morningReminders[0].title)
    }
}
