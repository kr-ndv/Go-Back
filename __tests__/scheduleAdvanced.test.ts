import { routineDays, sanitizeSkipDates, upcomingScheduleOccurrences } from '@/lib/scheduleAdvanced';

describe('advanced scheduling', () => {
  test('builds weekday and weekend presets', () => {
    expect(routineDays('weekday')).toEqual([1, 2, 3, 4, 5]);
    expect(routineDays('weekend')).toEqual([0, 6]);
  });

  test('deduplicates real skip dates and rejects impossible dates', () => {
    expect(sanitizeSkipDates('["2026-10-30","bad","2026-02-31","2026-10-30"]')).toEqual(['2026-10-30']);
  });

  test('skips excluded dates and applies pre-reminder', () => {
    const rows = upcomingScheduleOccurrences({
      id: 's1', title: 'Study', days_json: '[5]', start_time: '18:00', duration_minutes: 25,
      enabled: 1, reminder_minutes: 10, skip_dates_json: '["2026-10-30"]', routine_type: 'custom',
    }, new Date('2026-10-29T12:00:00'), 10);
    expect(rows.every(row => row.start.toISOString().slice(0, 10) !== '2026-10-30')).toBe(true);
  });
});
