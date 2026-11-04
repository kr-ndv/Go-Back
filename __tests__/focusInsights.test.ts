import { dailyFocusTotals, focusGoalStats } from '@/lib/focusInsights';

describe('focus insights', () => {
  test('aggregates focus sessions by local date', () => {
    const rows = [
      { started_at: '2026-10-27T10:00:00', duration_seconds: 1800, session_type: 'focus' },
      { started_at: '2026-10-27T14:00:00', duration_seconds: 1200, session_type: 'focus' },
      { started_at: '2026-10-27T15:00:00', duration_seconds: 600, session_type: 'short-break' },
    ];
    expect(dailyFocusTotals(rows)).toEqual([{ date: '2026-10-27', seconds: 3000 }]);
  });

  test('keeps a streak alive when today is not complete yet', () => {
    const rows = [
      { started_at: '2026-10-26T10:00:00', duration_seconds: 3600, session_type: 'focus' },
      { started_at: '2026-10-27T10:00:00', duration_seconds: 3600, session_type: 'focus' },
      { started_at: '2026-10-28T10:00:00', duration_seconds: 3600, session_type: 'focus' },
      { started_at: '2026-10-29T10:00:00', duration_seconds: 600, session_type: 'focus' },
    ];
    const stats = focusGoalStats(rows, 60, new Date('2026-10-29T15:00:00'));
    expect(stats.currentStreak).toBe(3);
    expect(stats.bestStreak).toBe(3);
  });
});
