export type FocusMode = 'timer' | 'pomodoro' | 'stopwatch';
export type FocusSessionType = 'focus' | 'short-break' | 'long-break';

export type PomodoroPhase = {
  sessionType: FocusSessionType;
  minutes: number;
  completedFocusRounds: number;
};

/**
 * Resolve the next Pomodoro phase after a phase completes. Four completed
 * focus rounds earn a long break; the caller controls when the next phase starts.
 */
export function nextPomodoroPhase(
  currentType: FocusSessionType,
  completedFocusRounds: number,
): PomodoroPhase {
  const rounds = Math.max(0, Math.floor(completedFocusRounds));

  if (currentType === 'focus') {
    const nextRounds = rounds + 1;
    const longBreakDue = nextRounds % 4 === 0;
    return {
      sessionType: longBreakDue ? 'long-break' : 'short-break',
      minutes: longBreakDue ? 15 : 5,
      completedFocusRounds: nextRounds,
    };
  }

  if (currentType === 'long-break') {
    return { sessionType: 'focus', minutes: 25, completedFocusRounds: 0 };
  }

  return { sessionType: 'focus', minutes: 25, completedFocusRounds: rounds };
}

/** Calculate a stopwatch display value while preserving paused elapsed time. */
export function stopwatchElapsedSeconds(
  accumulatedSeconds: number,
  runStartedAt: number | null,
  now: number = Date.now(),
): number {
  const accumulated = Number.isFinite(accumulatedSeconds)
    ? Math.max(0, Math.floor(accumulatedSeconds))
    : 0;
  if (runStartedAt === null || !Number.isFinite(runStartedAt)) return accumulated;
  return accumulated + Math.max(0, Math.floor((now - runStartedAt) / 1000));
}

/** Format countdowns as MM:SS and stopwatches as MM:SS or HH:MM:SS. */
export function formatFocusTime(totalSeconds: number, showHours = false): string {
  const safeSeconds = Number.isFinite(totalSeconds) ? Math.max(0, Math.floor(totalSeconds)) : 0;
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const seconds = safeSeconds % 60;
  if (showHours || hours > 0) {
    return [hours, minutes, seconds].map(value => String(value).padStart(2, '0')).join(':');
  }
  return [Math.floor(safeSeconds / 60), seconds].map(value => String(value).padStart(2, '0')).join(':');
}
