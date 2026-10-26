import { formatFocusTime, nextPomodoroPhase, stopwatchElapsedSeconds } from '../src/lib/focusLogic';

describe('stopwatchElapsedSeconds', () => {
  it('preserves paused elapsed time', () => {
    expect(stopwatchElapsedSeconds(73, null, 100_000)).toBe(73);
  });

  it('adds whole seconds since the current run started', () => {
    expect(stopwatchElapsedSeconds(73, 10_000, 15_999)).toBe(78);
  });

  it('does not count negative elapsed time or invalid input', () => {
    expect(stopwatchElapsedSeconds(12, 20_000, 10_000)).toBe(12);
    expect(stopwatchElapsedSeconds(Number.NaN, null)).toBe(0);
  });
});

describe('formatFocusTime', () => {
  it('formats short durations as MM:SS', () => {
    expect(formatFocusTime(1505)).toBe('25:05');
  });

  it('shows hours for long stopwatch sessions', () => {
    expect(formatFocusTime(3661, true)).toBe('01:01:01');
    expect(formatFocusTime(3661)).toBe('01:01:01');
  });
});

describe('nextPomodoroPhase', () => {
  it('alternates focus and short breaks before the fourth-round long break', () => {
    expect(nextPomodoroPhase('focus', 0)).toEqual({ sessionType: 'short-break', minutes: 5, completedFocusRounds: 1 });
    expect(nextPomodoroPhase('focus', 2)).toEqual({ sessionType: 'short-break', minutes: 5, completedFocusRounds: 3 });
    expect(nextPomodoroPhase('focus', 3)).toEqual({ sessionType: 'long-break', minutes: 15, completedFocusRounds: 4 });
  });

  it('resets the round counter after the long break', () => {
    expect(nextPomodoroPhase('long-break', 4)).toEqual({ sessionType: 'focus', minutes: 25, completedFocusRounds: 0 });
  });

  it('does not increment focus rounds when a break completes', () => {
    expect(nextPomodoroPhase('short-break', 2)).toEqual({ sessionType: 'focus', minutes: 25, completedFocusRounds: 2 });
  });
});
