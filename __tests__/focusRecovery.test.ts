import { normalizeRecoverableFocusSession, recoveredElapsedSeconds } from '@/lib/focusRecovery';

describe('focus recovery', () => {
  test('marks an expired countdown without negative remaining time', () => {
    const now = new Date('2026-10-29T10:10:00Z').getTime();
    const session = normalizeRecoverableFocusSession({
      sessionId: 'focus-1', minutes: 5, remaining: 100, endAt: now - 1000,
      startedAt: '2026-10-29T10:00:00Z', mode: 'timer', sessionType: 'focus',
    }, now);
    expect(session?.expired).toBe(true);
    expect(session?.remaining).toBe(0);
    expect(session && recoveredElapsedSeconds(session, now)).toBe(300);
  });

  test('restores stopwatch accumulation once', () => {
    const now = 10_000;
    const session = normalizeRecoverableFocusSession({
      sessionId: 'focus-2', minutes: 25, remaining: 0, endAt: null,
      startedAt: '2026-10-29T00:00:00Z', mode: 'stopwatch', elapsedSeconds: 3, stopwatchRunStartedAt: 6_000,
    }, now);
    expect(session?.running).toBe(true);
    expect(session && recoveredElapsedSeconds(session, now)).toBe(7);
  });

  test('sanitizes preset package overrides', () => {
    const session = normalizeRecoverableFocusSession({
      minutes: 25, remaining: 1500, endAt: null, startedAt: '2026-10-29T00:00:00Z',
      blockedPackages: ['com.example.one', 'bad package', 'com.example.one'], presetName: 'Study',
    });
    expect(session?.blockedPackages).toEqual(['com.example.one']);
    expect(session?.presetName).toBe('Study');
  });
});
