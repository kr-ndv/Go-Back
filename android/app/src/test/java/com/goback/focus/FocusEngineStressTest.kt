package com.goback.focus

import com.goback.focus.logic.FocusEngine
import com.goback.focus.logic.FocusMode
import com.goback.focus.logic.PomodoroPhase
import com.goback.focus.logic.PomodoroSessionType
import com.goback.focus.logic.RecoveredSession
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Empirical Adversarial Challenger Stress Test Suite for FocusEngine.
 *
 * Authored by Challenger M2-1.
 * Tests genuine production code in `com.goback.focus.logic.FocusEngine` without mocking or shadowing.
 *
 * Dimensions:
 * 1. Pomodoro Phase Transitions (rounds 1-4, short break, long break, reset after 4th, arbitrary round numbers).
 * 2. Stopwatch Elapsed Accumulation (pause/resume, process restarts, clock shifts, negative skews, long runs).
 * 3. Strict Mode Challenge Validation (exact, whitespace, case, newlines, near-misses, injection/bypass).
 * 4. Session Recovery Logic (expired in background, sub-second active, zero/negative durations, up to 180 min).
 * 5. Progress Calculation Boundaries (0s remaining, negative remaining, 100% elapsed, zero/negative minutes).
 * 6. Time Formatting & Daily Goal Celebration Thresholds.
 */
class FocusEngineStressTest {

    // =========================================================================
    // 1. Pomodoro Phase Transitions & State Machine
    // =========================================================================

    @Test
    fun pomodoro_firstFourRounds_cycleCleanlyThroughShortBreaksAndLongBreak() {
        // Round 0 (Start): Focus round 1 -> Short break (5 min, completed = 1)
        val p1 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, 0)
        assertEquals(PomodoroSessionType.SHORT_BREAK, p1.sessionType)
        assertEquals(5, p1.minutes)
        assertEquals(1, p1.completedFocusRounds)

        // Short break 1 completes -> Focus round 2 (25 min, completed = 1 preserved)
        val b1 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, p1.completedFocusRounds)
        assertEquals(PomodoroSessionType.FOCUS, b1.sessionType)
        assertEquals(25, b1.minutes)
        assertEquals(1, b1.completedFocusRounds)

        // Focus round 2 completes -> Short break 2 (5 min, completed = 2)
        val p2 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, b1.completedFocusRounds)
        assertEquals(PomodoroSessionType.SHORT_BREAK, p2.sessionType)
        assertEquals(5, p2.minutes)
        assertEquals(2, p2.completedFocusRounds)

        // Short break 2 completes -> Focus round 3 (25 min, completed = 2)
        val b2 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, p2.completedFocusRounds)
        assertEquals(PomodoroSessionType.FOCUS, b2.sessionType)
        assertEquals(25, b2.minutes)
        assertEquals(2, b2.completedFocusRounds)

        // Focus round 3 completes -> Short break 3 (5 min, completed = 3)
        val p3 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, b2.completedFocusRounds)
        assertEquals(PomodoroSessionType.SHORT_BREAK, p3.sessionType)
        assertEquals(5, p3.minutes)
        assertEquals(3, p3.completedFocusRounds)

        // Short break 3 completes -> Focus round 4 (25 min, completed = 3)
        val b3 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, p3.completedFocusRounds)
        assertEquals(PomodoroSessionType.FOCUS, b3.sessionType)
        assertEquals(25, b3.minutes)
        assertEquals(3, b3.completedFocusRounds)

        // Focus round 4 completes -> Long break (15 min, completed = 4)
        val p4 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, b3.completedFocusRounds)
        assertEquals(PomodoroSessionType.LONG_BREAK, p4.sessionType)
        assertEquals(15, p4.minutes)
        assertEquals(4, p4.completedFocusRounds)

        // Long break completes -> Resets cycle to Focus round 1 with 0 completed rounds!
        val resetPhase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, p4.completedFocusRounds)
        assertEquals(PomodoroSessionType.FOCUS, resetPhase.sessionType)
        assertEquals(25, resetPhase.minutes)
        assertEquals(0, resetPhase.completedFocusRounds)
    }

    @Test
    fun pomodoro_arbitraryRoundNumbers_moduloFourRuleStrictlyMaintained() {
        // Multiples of 4 completed must trigger LONG_BREAK (15 min)
        val longBreakTriggers = listOf(3, 7, 11, 15, 19, 23, 99, 399, 999)
        for (completed in longBreakTriggers) {
            val phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, completed)
            val expectedNextRounds = completed + 1
            assertEquals("Completed $completed should earn LONG_BREAK", PomodoroSessionType.LONG_BREAK, phase.sessionType)
            assertEquals(15, phase.minutes)
            assertEquals(expectedNextRounds, phase.completedFocusRounds)
        }

        // Non-multiples of 4 completed must trigger SHORT_BREAK (5 min)
        val shortBreakTriggers = listOf(0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 100, 101, 102)
        for (completed in shortBreakTriggers) {
            val phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, completed)
            val expectedNextRounds = completed + 1
            assertEquals("Completed $completed should trigger SHORT_BREAK", PomodoroSessionType.SHORT_BREAK, phase.sessionType)
            assertEquals(5, phase.minutes)
            assertEquals(expectedNextRounds, phase.completedFocusRounds)
        }
    }

    @Test
    fun pomodoro_negativeCompletedRounds_clampedToZeroDefensively() {
        // Negative inputs should not crash and should clamp to 0
        val p1 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, -1)
        assertEquals(PomodoroSessionType.SHORT_BREAK, p1.sessionType)
        assertEquals(5, p1.minutes)
        assertEquals(1, p1.completedFocusRounds)

        val p2 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, -100)
        assertEquals(PomodoroSessionType.SHORT_BREAK, p2.sessionType)
        assertEquals(5, p2.minutes)
        assertEquals(1, p2.completedFocusRounds)

        val p3 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, -5)
        assertEquals(PomodoroSessionType.FOCUS, p3.sessionType)
        assertEquals(25, p3.minutes)
        assertEquals(0, p3.completedFocusRounds)

        val p4 = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, -4)
        assertEquals(PomodoroSessionType.FOCUS, p4.sessionType)
        assertEquals(25, p4.minutes)
        assertEquals(0, p4.completedFocusRounds)
    }

    @Test
    fun pomodoro_longBreakResetsCompletedRoundsToZero_regardlessOfInitialCount() {
        val testCounts = listOf(4, 8, 12, 16, 20, 100, 500)
        for (count in testCounts) {
            val phase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, count)
            assertEquals(PomodoroSessionType.FOCUS, phase.sessionType)
            assertEquals(25, phase.minutes)
            assertEquals("Long break completion must reset rounds to 0", 0, phase.completedFocusRounds)
        }
    }

    @Test
    fun pomodoro_stressSimulation_oneThousandContinuousCycles() {
        var completedRounds = 0
        var totalWorkMinutes = 0
        var totalBreakMinutes = 0

        for (cycle in 1..1000) {
            for (round in 1..4) {
                // Focus work
                totalWorkMinutes += 25
                val breakPhase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.FOCUS, completedRounds)
                completedRounds = breakPhase.completedFocusRounds

                if (round < 4) {
                    assertEquals(PomodoroSessionType.SHORT_BREAK, breakPhase.sessionType)
                    assertEquals(5, breakPhase.minutes)
                    totalBreakMinutes += 5

                    // Return to work
                    val workPhase = FocusEngine.nextPomodoroPhase(PomodoroSessionType.SHORT_BREAK, completedRounds)
                    assertEquals(PomodoroSessionType.FOCUS, workPhase.sessionType)
                    assertEquals(25, workPhase.minutes)
                    assertEquals(completedRounds, workPhase.completedFocusRounds)
                } else {
                    assertEquals(PomodoroSessionType.LONG_BREAK, breakPhase.sessionType)
                    assertEquals(15, breakPhase.minutes)
                    totalBreakMinutes += 15

                    // Return to work resetting cycle
                    val freshWork = FocusEngine.nextPomodoroPhase(PomodoroSessionType.LONG_BREAK, completedRounds)
                    assertEquals(PomodoroSessionType.FOCUS, freshWork.sessionType)
                    assertEquals(25, freshWork.minutes)
                    assertEquals(0, freshWork.completedFocusRounds)
                    completedRounds = freshWork.completedFocusRounds
                }
            }
        }

        assertEquals(1000 * 4 * 25, totalWorkMinutes) // 100,000 minutes of focus
        assertEquals(1000 * (3 * 5 + 15), totalBreakMinutes) // 30,000 minutes of break
        assertEquals(0, completedRounds)
    }

    // =========================================================================
    // 2. Stopwatch Elapsed Time Accumulation Across Pause/Resume & Process Restarts
    // =========================================================================

    @Test
    fun stopwatch_elapsedCalculation_activeRunningState() {
        val startMs = 1_000_000L
        val nowMs = startMs + 45_678L // 45.678 seconds elapsed
        val elapsed = FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds = 120, runStartedAtMs = startMs, nowMs = nowMs)
        assertEquals(120 + 45, elapsed) // 165 seconds
    }

    @Test
    fun stopwatch_elapsedCalculation_pausedStateFrozen() {
        val nowMs = 5_000_000L
        // When paused, runStartedAtMs is null -> elapsed equals accumulatedSeconds exactly
        val elapsed = FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds = 345, runStartedAtMs = null, nowMs = nowMs)
        assertEquals(345, elapsed)
    }

    @Test
    fun stopwatch_negativeAccumulated_clampedToZero() {
        val nowMs = 1_000_000L
        val elapsedPaused = FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds = -50, runStartedAtMs = null, nowMs = nowMs)
        assertEquals(0, elapsedPaused)

        val elapsedRunning = FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds = -50, runStartedAtMs = nowMs - 10_000L, nowMs = nowMs)
        assertEquals(10, elapsedRunning) // base clamped to 0 + 10s running
    }

    @Test
    fun stopwatch_clockSkew_runStartedInFuture_returnsAccumulatedBaseWithoutNegative() {
        val nowMs = 1_000_000L
        val futureStartMs = nowMs + 30_000L // Clock jumped backward by 30 seconds
        val elapsed = FocusEngine.stopwatchElapsedSeconds(accumulatedSeconds = 75, runStartedAtMs = futureStartMs, nowMs = nowMs)
        assertEquals("When clock jumps backward, accumulated base must be preserved without subtraction", 75, elapsed)
    }

    @Test
    fun stopwatch_multiPauseResumeSequence_strictlyMonotonicNonDecreasing() {
        var accumulated = 0
        var virtualNowMs = 10_000_000L
        val rng = Random(42)

        for (i in 1..200) {
            val runDurationSec = rng.nextInt(1, 120)
            val runStartMs = virtualNowMs

            // 1. Simulating active running interval
            virtualNowMs += runDurationSec * 1000L
            val runningElapsed = FocusEngine.stopwatchElapsedSeconds(accumulated, runStartMs, virtualNowMs)
            assertEquals(accumulated + runDurationSec, runningElapsed)

            // 2. Simulating pause event
            accumulated = runningElapsed
            val pauseDurationMs = rng.nextLong(500L, 60_000L)
            virtualNowMs += pauseDurationMs

            val pausedElapsed = FocusEngine.stopwatchElapsedSeconds(accumulated, null, virtualNowMs)
            assertEquals("Elapsed time must freeze during pause", accumulated, pausedElapsed)
        }

        assertTrue(accumulated > 0)
    }

    @Test
    fun stopwatch_extremeDuration_twentyFourHoursAndThirtyDaysSafe() {
        val oneDaySeconds = 86_400
        val thirtyDaysSeconds = 30 * oneDaySeconds
        val startMs = 1_000_000L
        val now30DaysMs = startMs + (thirtyDaysSeconds.toLong() * 1000L)

        val elapsed = FocusEngine.stopwatchElapsedSeconds(0, startMs, now30DaysMs)
        assertEquals(thirtyDaysSeconds, elapsed)
        assertEquals("720:00:00", FocusEngine.formatFocusTime(thirtyDaysSeconds))
    }

    @Test
    fun stopwatch_subSecondTruncation_integerFloorDivision() {
        val startMs = 100_000L
        assertEquals(0, FocusEngine.stopwatchElapsedSeconds(0, startMs, startMs + 999L))
        assertEquals(1, FocusEngine.stopwatchElapsedSeconds(0, startMs, startMs + 1000L))
        assertEquals(1, FocusEngine.stopwatchElapsedSeconds(0, startMs, startMs + 1999L))
        assertEquals(2, FocusEngine.stopwatchElapsedSeconds(0, startMs, startMs + 2000L))
    }

    // =========================================================================
    // 3. Strict Mode Challenge Phrase Validation
    // =========================================================================

    @Test
    fun strictChallenge_exactPhraseAccepted() {
        assertTrue(FocusEngine.validateStrictChallenge("I choose to break my focus"))
    }

    @Test
    fun strictChallenge_caseInsensitivityAccepted() {
        val variations = listOf(
            "i choose to break my focus",
            "I CHOOSE TO BREAK MY FOCUS",
            "i ChOoSe To BrEaK mY fOcUs",
            "I Choose To Break My Focus"
        )
        for (phrase in variations) {
            assertTrue("Expected '$phrase' to be accepted", FocusEngine.validateStrictChallenge(phrase))
        }
    }

    @Test
    fun strictChallenge_leadingAndTrailingWhitespaceAccepted() {
        val variations = listOf(
            "   I choose to break my focus",
            "I choose to break my focus   ",
            "  I choose to break my focus  ",
            "\tI choose to break my focus\t",
            "\nI choose to break my focus\n",
            "\r\n  I choose to break my focus  \r\n",
            "   \t\n  i choose to break my focus  \n\t  "
        )
        for (phrase in variations) {
            assertTrue("Expected whitespace padded phrase to be accepted", FocusEngine.validateStrictChallenge(phrase))
        }
    }

    @Test
    fun strictChallenge_internalWhitespaceVariationRejected() {
        // Internal double-spaces, tabs, or newlines must NOT match because trim() only affects ends
        val badInternalSpaces = listOf(
            "I  choose to break my focus",
            "I choose  to break my focus",
            "I choose to  break my focus",
            "I choose to break  my focus",
            "I choose to break my  focus",
            "I\tchoose to break my focus",
            "I choose\nto break my focus",
            "I   choose   to   break   my   focus"
        )
        for (bad in badInternalSpaces) {
            assertFalse("Internal space variation '$bad' must be rejected", FocusEngine.validateStrictChallenge(bad))
        }
    }

    @Test
    fun strictChallenge_nearMissTyposRejected() {
        val typos = listOf(
            "I chose to break my focus",       // chose vs choose
            "I chose to broke my focus",
            "I choos to break my focus",
            "I choose to brake my focus",      // brake vs break
            "I choose to break my focuses",
            "I choose to break my focu",
            "I choose to brek my focus",
            "I chooze to break my focus",
            "I choose too break my focus",
            "1 choose to break my focus",
            "I choose 2 break my focus",
            "I choose to break me focus"
        )
        for (typo in typos) {
            assertFalse("Typo '$typo' must be rejected", FocusEngine.validateStrictChallenge(typo))
        }
    }

    @Test
    fun strictChallenge_missingAndExtraWordsRejected() {
        val wordErrors = listOf(
            "",
            "   ",
            "I choose",
            "break my focus",
            "I choose to break",
            "I choose to focus",
            "I choose to break focus",
            "choose to break my focus",
            "I choose to break my focus now",
            "Please I choose to break my focus",
            "Yes, I choose to break my focus",
            "I choose to break my focus please"
        )
        for (we in wordErrors) {
            assertFalse("Word error '$we' must be rejected", FocusEngine.validateStrictChallenge(we))
        }
    }

    @Test
    fun strictChallenge_punctuationVariationsRejected() {
        val punctuations = listOf(
            "I choose to break my focus.",
            "I choose to break my focus!",
            "I choose to break my focus?",
            "\"I choose to break my focus\"",
            "'I choose to break my focus'",
            "(I choose to break my focus)",
            "I, choose to break my focus"
        )
        for (p in punctuations) {
            assertFalse("Punctuation '$p' must be rejected", FocusEngine.validateStrictChallenge(p))
        }
    }

    @Test
    fun strictChallenge_adversarialBypassStringsRejected() {
        val attacks = listOf(
            "admin",
            "sudo",
            "quit",
            "cancel",
            "stop",
            "exit",
            "bypass",
            "override",
            "true",
            "false",
            "null",
            "' OR '1'='1",
            "<script>alert(1)</script>",
            "I choose to break my focus\u0000",
            "I choose to break my fоcus" // Cyrillic 'о' U+043E homoglyph
        )
        for (atk in attacks) {
            assertFalse("Attack string '$atk' must be rejected", FocusEngine.validateStrictChallenge(atk))
        }
    }

    // =========================================================================
    // 4. Session Recovery Logic
    // =========================================================================

    @Test
    fun sessionRecovery_activeCountdown_preciseCeilSeconds() {
        val nowMs = 1_000_000L

        // 500 ms remaining -> ceil gives 1s
        val s1 = FocusEngine.recoverSession(
            sessionId = "s1",
            minutes = 25,
            endAtMs = nowMs + 500L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Tag",
            notes = "Notes",
            nowMs = nowMs
        )
        assertEquals(1, s1.remainingSeconds)
        assertFalse(s1.isExpired)
        assertTrue(s1.isRunning)
        assertEquals(1500 - 1, s1.elapsedSeconds)

        // 1 ms remaining -> ceil gives 1s
        val s2 = FocusEngine.recoverSession(
            sessionId = "s2",
            minutes = 25,
            endAtMs = nowMs + 1L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Tag",
            notes = "Notes",
            nowMs = nowMs
        )
        assertEquals(1, s2.remainingSeconds)
        assertFalse(s2.isExpired)
        assertTrue(s2.isRunning)

        // 1001 ms remaining -> ceil gives 2s
        val s3 = FocusEngine.recoverSession(
            sessionId = "s3",
            minutes = 25,
            endAtMs = nowMs + 1001L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Tag",
            notes = "Notes",
            nowMs = nowMs
        )
        assertEquals(2, s3.remainingSeconds)
        assertFalse(s3.isExpired)
        assertTrue(s3.isRunning)
    }

    @Test
    fun sessionRecovery_exactExpiryBoundary() {
        val nowMs = 1_000_000L

        // endAtMs == nowMs exactly
        val sessionExact = FocusEngine.recoverSession(
            sessionId = "exact",
            minutes = 25,
            endAtMs = nowMs,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        assertTrue(sessionExact.isExpired)
        assertFalse(sessionExact.isRunning)
        assertEquals(0, sessionExact.remainingSeconds)
        assertEquals(1500, sessionExact.elapsedSeconds)

        // endAtMs == nowMs - 1ms
        val sessionOneMsPast = FocusEngine.recoverSession(
            sessionId = "past1ms",
            minutes = 25,
            endAtMs = nowMs - 1L,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )
        assertTrue(sessionOneMsPast.isExpired)
        assertFalse(sessionOneMsPast.isRunning)
        assertEquals(0, sessionOneMsPast.remainingSeconds)
        assertEquals(1500, sessionOneMsPast.elapsedSeconds)
    }

    @Test
    fun sessionRecovery_longExpired_safeElapsedCappedToDuration() {
        val nowMs = 500_000_000L
        val threeDaysAgoMs = nowMs - (3 * 86_400_000L)

        val session = FocusEngine.recoverSession(
            sessionId = "long-expired",
            minutes = 45,
            endAtMs = threeDaysAgoMs,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "Math",
            notes = "Calculus",
            nowMs = nowMs
        )

        assertTrue(session.isExpired)
        assertFalse(session.isRunning)
        assertEquals(0, session.remainingSeconds)
        // Elapsed must cap to safeMinutes * 60 (2700s), NOT expand to 3 days!
        assertEquals(45 * 60, session.elapsedSeconds)
    }

    @Test
    fun sessionRecovery_unstartedOrPausedCountdown() {
        val session = FocusEngine.recoverSession(
            sessionId = "paused",
            minutes = 60,
            endAtMs = null,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = 1_000_000L
        )

        assertFalse(session.isExpired)
        assertFalse(session.isRunning)
        assertEquals(3600, session.remainingSeconds)
        assertEquals(0, session.elapsedSeconds)
    }

    @Test
    fun sessionRecovery_stopwatchMode_neverExpires() {
        val nowMs = 1_000_000L

        // Stopwatch with past endAtMs (should be ignored for stopwatch)
        val sessionRunning = FocusEngine.recoverSession(
            sessionId = "sw-running",
            minutes = 25,
            endAtMs = nowMs - 10_000L,
            mode = FocusMode.STOPWATCH,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 100,
            stopwatchRunStartedAtMs = nowMs - 50_000L,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )

        assertFalse("Stopwatch must NEVER be marked as expired", sessionRunning.isExpired)
        assertTrue(sessionRunning.isRunning)
        assertEquals(0, sessionRunning.remainingSeconds)
        assertEquals(100 + 50, sessionRunning.elapsedSeconds)

        // Paused stopwatch
        val sessionPaused = FocusEngine.recoverSession(
            sessionId = "sw-paused",
            minutes = 25,
            endAtMs = null,
            mode = FocusMode.STOPWATCH,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 240,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )

        assertFalse(sessionPaused.isExpired)
        assertFalse(sessionPaused.isRunning)
        assertEquals(0, sessionPaused.remainingSeconds)
        assertEquals(240, sessionPaused.elapsedSeconds)
    }

    @Test
    fun sessionRecovery_durationClamping_boundaryValues() {
        val nowMs = 1_000_000L

        // Zero clamped to 1
        val sZero = FocusEngine.recoverSession("sZero", 0, null, FocusMode.TIMER, PomodoroSessionType.FOCUS, 0, null, 0, "", "", nowMs)
        assertEquals(1, sZero.minutes)
        assertEquals(60, sZero.remainingSeconds)

        // Negative clamped to 1
        val sNeg = FocusEngine.recoverSession("sNeg", -15, null, FocusMode.TIMER, PomodoroSessionType.FOCUS, 0, null, 0, "", "", nowMs)
        assertEquals(1, sNeg.minutes)
        assertEquals(60, sNeg.remainingSeconds)

        // 1 min lower bound preserved
        val s1 = FocusEngine.recoverSession("s1", 1, null, FocusMode.TIMER, PomodoroSessionType.FOCUS, 0, null, 0, "", "", nowMs)
        assertEquals(1, s1.minutes)
        assertEquals(60, s1.remainingSeconds)

        // 180 min upper bound preserved
        val s180 = FocusEngine.recoverSession("s180", 180, null, FocusMode.TIMER, PomodoroSessionType.FOCUS, 0, null, 0, "", "", nowMs)
        assertEquals(180, s180.minutes)
        assertEquals(180 * 60, s180.remainingSeconds)

        // Over 180 clamped to 180
        val sOver = FocusEngine.recoverSession("sOver", 300, null, FocusMode.TIMER, PomodoroSessionType.FOCUS, 0, null, 0, "", "", nowMs)
        assertEquals(180, sOver.minutes)
        assertEquals(180 * 60, sOver.remainingSeconds)
    }

    @Test
    fun sessionRecovery_clockSkewBackward_elapsedClampedToZero() {
        val nowMs = 1_000_000L
        // endAt is 50 minutes in the future for a 25-minute session due to clock rollback
        val endAtMs = nowMs + 50 * 60_000L

        val session = FocusEngine.recoverSession(
            sessionId = "skew",
            minutes = 25,
            endAtMs = endAtMs,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 0,
            tag = "",
            notes = "",
            nowMs = nowMs
        )

        assertEquals(3000, session.remainingSeconds)
        // safeMinutes * 60 - remaining = 1500 - 3000 = -1500 -> clamped to 0!
        assertEquals("Elapsed seconds must never be negative", 0, session.elapsedSeconds)
        assertTrue(session.isRunning)
        assertFalse(session.isExpired)
    }

    @Test
    fun sessionRecovery_metadataBoundary_extremeLengthsAndUnicode() {
        val longTag = "T".repeat(500)
        val longNotes = "N".repeat(2000)
        val unicodeTag = "🚀 Study Math 🎯"

        val s = FocusEngine.recoverSession(
            sessionId = "meta",
            minutes = 25,
            endAtMs = null,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = -5, // Negative rounds
            tag = longTag,
            notes = longNotes,
            nowMs = 1_000_000L
        )

        assertEquals(40, s.tag.length)
        assertEquals(300, s.notes.length)
        assertEquals(0, s.pomodoroRounds) // Clamped to 0

        val sUnicode = FocusEngine.recoverSession(
            sessionId = "unicode",
            minutes = 25,
            endAtMs = null,
            mode = FocusMode.TIMER,
            sessionType = PomodoroSessionType.FOCUS,
            accumulatedElapsed = 0,
            stopwatchRunStartedAtMs = null,
            pomodoroRounds = 2,
            tag = unicodeTag,
            notes = "Valid notes",
            nowMs = 1_000_000L
        )
        assertEquals(unicodeTag, sUnicode.tag)
    }

    // =========================================================================
    // 5. Progress Calculation Boundaries
    // =========================================================================

    @Test
    fun progress_zeroAndNegativeSelectedMinutes_safeZero() {
        assertEquals(0f, FocusEngine.calculateProgress(0, 0), 0.0001f)
        assertEquals(0f, FocusEngine.calculateProgress(0, 50), 0.0001f)
        assertEquals(0f, FocusEngine.calculateProgress(-5, 50), 0.0001f)
        assertEquals(0f, FocusEngine.calculateProgress(-100, -100), 0.0001f)
    }

    @Test
    fun progress_boundaryValues_coercedBetweenZeroAndOne() {
        val selected = 25 // 1500s

        // Exact start
        assertEquals(0.0f, FocusEngine.calculateProgress(selected, 1500), 0.0001f)

        // Over-full (remaining > total) -> coerced to 0f
        assertEquals(0.0f, FocusEngine.calculateProgress(selected, 1600), 0.0001f)
        assertEquals(0.0f, FocusEngine.calculateProgress(selected, 99999), 0.0001f)

        // Exact midpoint
        assertEquals(0.5f, FocusEngine.calculateProgress(selected, 750), 0.0001f)

        // Exact completion
        assertEquals(1.0f, FocusEngine.calculateProgress(selected, 0), 0.0001f)

        // Negative remaining -> coerced to 1f
        assertEquals(1.0f, FocusEngine.calculateProgress(selected, -1), 0.0001f)
        assertEquals(1.0f, FocusEngine.calculateProgress(selected, -500), 0.0001f)
    }

    @Test
    fun progress_strictlyMonotonicNonDecreasingSweep() {
        val selected = 25 // 1500 seconds
        var previousProgress = -1.0f

        for (remaining in 1500 downTo 0) {
            val progress = FocusEngine.calculateProgress(selected, remaining)
            assertTrue("Progress $progress must be >= 0.0f", progress >= 0.0f)
            assertTrue("Progress $progress must be <= 1.0f", progress <= 1.0f)
            assertTrue("Progress must be monotonically non-decreasing", progress >= previousProgress)
            previousProgress = progress
        }

        assertEquals(0.0f, FocusEngine.calculateProgress(selected, 1500), 0.0001f)
        assertEquals(1.0f, FocusEngine.calculateProgress(selected, 0), 0.0001f)
    }

    @Test
    fun progress_extremeDurations_oneMinuteAndOneHundredEightyMinutes() {
        // 1 minute (60s)
        assertEquals(0.0f, FocusEngine.calculateProgress(1, 60), 0.0001f)
        assertEquals(0.5f, FocusEngine.calculateProgress(1, 30), 0.0001f)
        assertEquals(1.0f, FocusEngine.calculateProgress(1, 0), 0.0001f)

        // 180 minutes (10,800s)
        assertEquals(0.0f, FocusEngine.calculateProgress(180, 10800), 0.0001f)
        assertEquals(0.5f, FocusEngine.calculateProgress(180, 5400), 0.0001f)
        assertEquals(1.0f, FocusEngine.calculateProgress(180, 0), 0.0001f)
        // 1 second remaining
        val almostDone = FocusEngine.calculateProgress(180, 1)
        assertTrue(almostDone > 0.999f && almostDone < 1.0f)
    }

    // =========================================================================
    // 6. Time Formatting & Goal Celebration Thresholds
    // =========================================================================

    @Test
    fun timeFormatting_variousDurationsAndForceHours() {
        assertEquals("00:00", FocusEngine.formatFocusTime(0))
        assertEquals("00:01", FocusEngine.formatFocusTime(1))
        assertEquals("00:59", FocusEngine.formatFocusTime(59))
        assertEquals("01:00", FocusEngine.formatFocusTime(60))
        assertEquals("59:59", FocusEngine.formatFocusTime(3599))
        assertEquals("01:00:00", FocusEngine.formatFocusTime(3600))
        assertEquals("01:01:01", FocusEngine.formatFocusTime(3661))
        assertEquals("23:59:59", FocusEngine.formatFocusTime(86399))
        assertEquals("24:00:00", FocusEngine.formatFocusTime(86400))
        assertEquals("100:00:00", FocusEngine.formatFocusTime(360000))

        // Negative seconds clamped to 0
        assertEquals("00:00", FocusEngine.formatFocusTime(-50))

        // forceHours flag
        assertEquals("00:00:00", FocusEngine.formatFocusTime(0, forceHours = true))
        assertEquals("00:05:00", FocusEngine.formatFocusTime(300, forceHours = true))
        assertEquals("00:59:59", FocusEngine.formatFocusTime(3599, forceHours = true))
    }

    @Test
    fun goalCelebration_thresholdCrossingTrigger() {
        val goalSeconds = 7200 // 120 minutes

        // 1. Crosses exactly at boundary
        assertTrue(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 7199, newSecondsToday = 7200, goalSeconds = goalSeconds))

        // 2. Crosses above boundary
        assertTrue(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 7000, newSecondsToday = 7500, goalSeconds = goalSeconds))

        // 3. First single long session reaches goal directly
        assertTrue(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 0, newSecondsToday = 7200, goalSeconds = goalSeconds))

        // 4. Has NOT reached goal yet
        assertFalse(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 0, newSecondsToday = 7199, goalSeconds = goalSeconds))
        assertFalse(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 5000, newSecondsToday = 7199, goalSeconds = goalSeconds))

        // 5. Already reached goal earlier -> must NOT trigger celebration again!
        assertFalse(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 7200, newSecondsToday = 7500, goalSeconds = goalSeconds))
        assertFalse(FocusEngine.shouldTriggerGoalCelebration(priorSecondsToday = 8000, newSecondsToday = 9000, goalSeconds = goalSeconds))
    }

    @Test
    fun presetMinutes_valuesMatchContract() {
        assertEquals(listOf(15, 25, 45, 60), FocusEngine.PRESET_MINUTES)
        assertEquals("I choose to break my focus", FocusEngine.COMMITMENT_PHRASE)
    }
}
