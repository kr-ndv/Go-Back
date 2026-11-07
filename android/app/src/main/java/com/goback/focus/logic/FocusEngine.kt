package com.goback.focus.logic

import kotlin.math.ceil
import kotlin.math.max

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
    val PRESET_MINUTES = listOf(15, 25, 45, 60)

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

    fun shouldTriggerGoalCelebration(priorSecondsToday: Int, newSecondsToday: Int, goalSeconds: Int): Boolean {
        return priorSecondsToday < goalSeconds && newSecondsToday >= goalSeconds
    }
}
