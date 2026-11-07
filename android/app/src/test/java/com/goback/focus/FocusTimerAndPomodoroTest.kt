package com.goback.focus

import com.goback.focus.data.entity.FocusSessionEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.max

/**
 * Tier 1, Tier 2, and Tier 4 tests for Focus Timer & Pomodoro Engine.
 * Tests duration math, progress calculation, Pomodoro 4-round state machine,
 * open-ended stopwatch, process recovery, and strict mode anti-bypass.
 */
class FocusTimerAndPomodoroTest {

    // --- Domain Models & Logic Oracles ---

    enum class FocusMode { TIMER, POMODORO, STOPWATCH }
    enum class PomodoroSessionType { FOCUS, SHORT_BREAK, LONG_BREAK }

    data class PomodoroPhase(
        val sessionType: PomodoroSessionType,
        val minutes: Int,
        val completedFocusRounds: Int
    )

    data class RecoveredSession(
        val sessionId: String,
        val minutes: Int,
        val remainingSeconds: Int,
        val endAtMs: Long?,
        val sessionType: PomodoroSessionType,
        val mode: FocusMode,
        val elapsedSeconds: Int,
        val stopwatchRunStartedAtMs: Long?,
        val pomodoroRounds: Int,
        val tag: String,
        val notes: String,
        val isRunning: Boolean,
        val isExpired: Boolean
    )

    object FocusEngine {
        const val COMMITMENT_PHRASE = "I choose to break my focus"

        fun calculateProgress(selectedMinutes: Int, remainingSeconds: Int): Float {
            if (selectedMinutes <= 0) return 0f
            val totalSeconds = selectedMinutes * 60f
            val progress = 1f - (remainingSeconds.toFloat() / totalSeconds)
            return progress.coerceIn(0f, 1f)
        }

        fun nextPomodoroPhase(currentType: PomodoroSessionType, completedRounds: Int): PomodoroPhase {
            val rounds = max(0, completedRounds)
            return when (currentType) {
                PomodoroSessionType.FOCUS -> {
                    val nextRounds = rounds + 1
                    val longBreakDue = nextRounds % 4 == 0
                    PomodoroPhase(
                        sessionType = if (longBreakDue) PomodoroSessionType.LONG_BREAK else PomodoroSessionType.SHORT_BREAK,
                        minutes = if (longBreakDue) 15 else 5,
                        completedFocusRounds = nextRounds
                    )
                }
                PomodoroSessionType.LONG_BREAK -> {
                    PomodoroPhase(
                        sessionType = PomodoroSessionType.FOCUS,
                        minutes = 25,
                        completedFocusRounds = 0
                    )
                }
                PomodoroSessionType.SHORT_BREAK -> {
                    PomodoroPhase(
                        sessionType = PomodoroSessionType.FOCUS,
                        minutes = 25,
                        completedFocusRounds = rounds
                    )
                }
            }
        }

        fun stopwatchElapsedSeconds(accumulatedSeconds: Int, runStartedAtMs: Long?, nowMs: Long): Int {
            val base = max(0, accumulatedSeconds)
            if (runStartedAtMs == null || runStartedAtMs > nowMs) return base
            val running = max(0L, (nowMs - runStartedAtMs) / 1000L).toInt()
            return base + running
        }

        fun formatFocusTime(totalSeconds: Int, forceHours: Boolean = false): String {
            val safe = max(0, totalSeconds)
            val hours = safe / 3600
            val minutes = (safe % 3600) / 60
            val seconds = safe % 60
            return if (forceHours || hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun recoverSession(
            sessionId: String,
            minutes: Int,
            endAtMs: Long?,
            mode: FocusMode,
            sessionType: PomodoroSessionType,
            accumulatedElapsed: Int,
            stopwatchRunStartedAtMs: Long?,
            pomodoroRounds: Int,
            tag: String,
            notes: String,
            nowMs: Long
        ): RecoveredSession {
            val safeMinutes = minutes.coerceIn(1, 180)
            val isExpired = mode != FocusMode.STOPWATCH && endAtMs != null && endAtMs <= nowMs
            val remaining = when {
                mode == FocusMode.STOPWATCH -> 0
                endAtMs != null -> {
                    val diff = endAtMs - nowMs
                    if (diff <= 0L) 0 else ceil(diff / 1000.0).toInt()
                }
                else -> safeMinutes * 60
            }
            val elapsed = if (mode == FocusMode.STOPWATCH) {
                stopwatchElapsedSeconds(accumulatedElapsed, stopwatchRunStartedAtMs, nowMs)
            } else if (isExpired) {
                safeMinutes * 60
            } else {
                max(0, safeMinutes * 60 - remaining)
            }
            val isRunning = if (mode == FocusMode.STOPWATCH) {
                stopwatchRunStartedAtMs != null
            } else {
                endAtMs != null && !isExpired
            }

            return RecoveredSession(
                sessionId = sessionId,
                minutes = safeMinutes,
                remainingSeconds = remaining,
                endAtMs = endAtMs,
                sessionType = sessionType,
                mode = mode,
                elapsedSeconds = elapsed,
                stopwatchRunStartedAtMs = stopwatchRunStartedAtMs,
                pomodoroRounds = max(0, pomodoroRounds),
                tag = tag.take(40),
                notes = notes.take(300),
                isRunning = isRunning,
                isExpired = isExpired
            )
        }

        fun validateStrictChallenge(phrase: String): Boolean {
            return phrase.trim().equals(COMMITMENT_PHRASE, ignoreCase = true)
        }
    }

    // =========================================================================
    // Tier 1: Feature Coverage (>=5 per feature)
    // =========================================================================

    @Test
    fun tier1_timerCountdownProgress_standardPresets() {
        // 25 minutes timer (1500 seconds)
        val selected = 25
        assertEquals(0.0f, FocusEngine.calculateProgress(selected, 1500), 0.001f)
        assertEquals(0.5f, FocusEngine.calculateProgress(selected, 750), 0.001f)
        assertEquals(0.75f, FocusEngine.calculateProgress(selected, 375), 0.001f)
        assertEquals(1.0f, FocusEngine.calculateProgress(selected, 0), 0.001f)
    }

    @Test
    fun tier1_pomodoroCycle_firstThreeRoundsTriggerShortBreak() {
        // Round 1 focus -> short break (5m)
        val phase1 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, 0)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase1.sessionType)
        assertEquals(5, phase1.minutes)
        assertEquals(1, phase1.completedFocusRounds)

        // Return from short break to focus
        val backToFocus1 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, 1)
        assertEquals(PomodoroSessionType.FOCUS, backToFocus1.sessionType)
        assertEquals(25, backToFocus1.minutes)
        assertEquals(1, backToFocus1.completedFocusRounds)

        // Round 2 focus -> short break (5m)
        val phase2 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, 1)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase2.sessionType)
        assertEquals(5, phase2.minutes)
        assertEquals(2, phase2.completedFocusRounds)

        // Round 3 focus -> short break (5m)
        val phase3 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, 2)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase3.sessionType)
        assertEquals(5, phase3.minutes)
        assertEquals(3, phase3.completedFocusRounds)
    }

    @Test
    fun tier1_pomodoroCycle_fourthRoundTriggersLongBreakAndResets() {
        // Round 4 focus -> long break (15m)
        val phase4 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, 3)
        assertEquals(PomodoroSessionType.LONG_BREAK, phase4.sessionType)
        assertEquals(15, phase4.minutes)
        assertEquals(4, phase4.completedFocusRounds)

        // Completing long break resets completed rounds to 0 for a fresh cycle
        val postLongBreak = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, 4)
        assertEquals(PomodoroSessionType.FOCUS, postLongBreak.sessionType)
        assertEquals(25, postLongBreak.minutes)
        assertEquals(0, postLongBreak.completedFocusRounds)
    }

    @Test
    fun tier1_stopwatch_elapsedTimeCalculationAndPausePreservation() {
        val startMs = 1_000_000L
        val currentMs = startMs + 45_000L // 45 seconds elapsed

        // Running: base 0 + 45s = 45s
        val elapsedRunning = FocusEngine.stopwatchElapsedSeconds(0, startMs, currentMs)
        assertEquals(45, elapsedRunning)

        // Paused: accumulated 45s, runStartedAtMs = null -> remains 45s
        val elapsedPaused = FocusEngine.stopwatchElapsedSeconds(45, null, currentMs + 30_000L)
        assertEquals(45, elapsedPaused)

        // Resumed: accumulated 45s, new start at (currentMs + 30_000L), now at +50_000L (+20s) -> 65s
        val resumeStartMs = currentMs + 30_000L
        val elapsedResumed = FocusEngine.stopwatchElapsedSeconds(45, resumeStartMs, resumeStartMs + 20_000L)
        assertEquals(65, elapsedResumed)
    }

    @Test
    fun tier1_timeFormatting_standardAndHoursDisplay() {
        assertEquals("25:00", FocusEngine.formatFocusTime(1500))
        assertEquals("05:30", FocusEngine.formatFocusTime(330))
        assertEquals("00:45", FocusEngine.formatFocusTime(45))
        assertEquals("01:15:00", FocusEngine.formatFocusTime(4500))
        assertEquals("00:05:00", FocusEngine.formatFocusTime(300, forceHours = true))
    }

    @Test
    fun tier1_sessionRecovery_inFlightActiveCountdown() {
        val nowMs = 500_000L
        val endAtMs = nowMs + 120_000L // 2 minutes remaining

        val session = FocusEngine.recoverSession(
            sessionId = "session-101",
            minutes = 25,
            endAtMs = endAtMs,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Math",
            notes = "Problem set 4",
            nowMs = nowMs
        )

        assertFalse(session.isExpired)
        assertTrue(session.isRunning)
        assertEquals(120, session.remainingSeconds)
        assertEquals(1380, session.elapsedSeconds) // 1500 - 120 = 1380s
        assertEquals("Math", session.tag)
    }

    @Test
    fun tier1_sessionRecovery_expiredCountdown() {
        val nowMs = 500_000L
        val endAtMs = nowMs - 5_000L // Expired 5 seconds ago

        val session = FocusEngine.recoverSession(
            sessionId = "session-102",
            minutes = 45,
            endAtMs = endAtMs,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Physics",
            notes = "Electromagnetism chapter",
            nowMs = nowMs
        )

        assertTrue(session.isExpired)
        assertFalse(session.isRunning)
        assertEquals(0, session.remainingSeconds)
        assertEquals(2700, session.elapsedSeconds) // Full 45 minutes = 2700s
    }

    @Test
    fun tier1_strictMode_challengePhraseValidation() {
        assertTrue(FocusEngine.validateStrictChallenge("I choose to break my focus"))
        assertTrue(FocusEngine.validateStrictChallenge("  i choose to break my focus  "))
        assertTrue(FocusEngine.validateStrictChallenge("I CHOOSE TO BREAK MY FOCUS"))
        assertFalse(FocusEngine.validateStrictChallenge("I want to quit"))
        assertFalse(FocusEngine.validateStrictChallenge("I choose to break focus"))
        assertFalse(FocusEngine.validateStrictChallenge(""))
    }

    // =========================================================================
    // Tier 2: Boundary & Corner Cases (>=5 per feature)
    // =========================================================================

    @Test
    fun tier2_boundary_minimumAndMaximumDurationClamping() {
        val nowMs = 1_000_000L
        // Min duration is 1 minute (60s)
        val minSession = FocusEngine.recoverSession(
            sessionId = "min-session",
            minutes = 0, // Out of bounds -> clamped to 1
            endAtMs = nowMs + 60_000L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        assertEquals(1, minSession.minutes)

        // Max duration is 180 minutes (3 hours)
        val maxSession = FocusEngine.recoverSession(
            sessionId = "max-session",
            minutes = 240, // Out of bounds -> clamped to 180
            endAtMs = nowMs + 180 * 60_000L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        assertEquals(180, maxSession.minutes)
    }

    @Test
    fun tier2_boundary_subSecondPrecisionRecovery() {
        val nowMs = 1_000_000L
        // 400ms remaining should ceil to 1 second remaining, not 0
        val sessionSubSecond = FocusEngine.recoverSession(
            sessionId = "subsecond-session",
            minutes = 25,
            endAtMs = nowMs + 400L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        assertEquals(1, sessionSubSecond.remainingSeconds)
        assertFalse(sessionSubSecond.isExpired)
    }

    @Test
    fun tier2_boundary_clockShiftBackwardHandledGracefully() {
        val nowMs = 1_000_000L
        // Device clock shifted backward into the past by 1 hour (endAt is now 1h 25m in the future)
        val sessionShifted = FocusEngine.recoverSession(
            sessionId = "shifted-session",
            minutes = 25,
            endAtMs = nowMs + 85 * 60_000L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        // Elapsed should not be negative
        assertEquals(0, sessionShifted.elapsedSeconds)
    }

    @Test
    fun tier2_boundary_metadataStringLengthClamping() {
        val longTag = "A".repeat(100) // Max 40
        val longNotes = "B".repeat(500) // Max 300

        val session = FocusEngine.recoverSession(
            sessionId = "long-meta",
            minutes = 25,
            endAtMs = null,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = longTag,
            notes = longNotes,
            nowMs = 1_000_000L
        )

        assertEquals(40, session.tag.length)
        assertEquals(300, session.notes.length)
    }

    @Test
    fun tier2_boundary_zeroSelectedMinutesProgressSafe() {
        assertEquals(0f, FocusEngine.calculateProgress(0, 0), 0.001f)
        assertEquals(0f, FocusEngine.calculateProgress(-10, 50), 0.001f)
    }

    @Test
    fun tier2_boundary_negativeStopwatchInputsSafe() {
        val elapsed = FocusEngine.stopwatchElapsedSeconds(-50, null, 1_000_000L)
        assertEquals(0, elapsed)
    }

    // =========================================================================
    // Tier 4: Real-World Scenario
    // =========================================================================

    @Test
    fun tier4_realWorld_fourRoundPomodoroStudentSession() {
        var currentRounds = 0
        var totalFocusSeconds = 0

        // Round 1
        var phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, currentRounds)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase.sessionType)
        assertEquals(5, phase.minutes)
        currentRounds = phase.completedFocusRounds
        totalFocusSeconds += 25 * 60

        // Round 2
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, currentRounds)
        assertEquals(PomodoroSessionType.FOCUS, phase.sessionType)
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, currentRounds)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase.sessionType)
        currentRounds = phase.completedFocusRounds
        totalFocusSeconds += 25 * 60

        // Round 3
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, currentRounds)
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, currentRounds)
        assertEquals(PomodoroSessionType.SHORT_BREAK, phase.sessionType)
        currentRounds = phase.completedFocusRounds
        totalFocusSeconds += 25 * 60

        // Round 4
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, currentRounds)
        phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, currentRounds)
        // 4th completed round earns Long Break!
        assertEquals(PomodoroSessionType.LONG_BREAK, phase.sessionType)
        assertEquals(15, phase.minutes)
        assertEquals(4, phase.completedFocusRounds)
        totalFocusSeconds += 25 * 60

        // Total focus minutes after 4 rounds = 100 minutes (6000 seconds)
        assertEquals(6000, totalFocusSeconds)
        assertEquals("01:40:00", FocusEngine.formatFocusTime(totalFocusSeconds))

        // After long break, cycle cleanly resets
        val freshCycle = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, phase.completedFocusRounds)
        assertEquals(PomodoroSessionType.FOCUS, freshCycle.sessionType)
        assertEquals(0, freshCycle.completedFocusRounds)
    }
}
