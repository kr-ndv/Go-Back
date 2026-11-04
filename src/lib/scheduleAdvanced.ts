import { isValidISODate } from '@/lib/date';

export type AdvancedSchedule = {
  id: string;
  title: string;
  days_json: string;
  start_time: string;
  duration_minutes: number;
  enabled: number;
  reminder_minutes?: number;
  skip_dates_json?: string;
  routine_type?: string;
  timezone?: string;
};

export type ScheduleOccurrence = {
  scheduleId: string;
  title: string;
  start: Date;
  reminder: Date;
  durationMinutes: number;
};

export const detectedTimeZone = () => {
  try { return Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch { return ''; }
};

export function sanitizeSkipDates(value: string | undefined): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter(item => typeof item === 'string' && isValidISODate(item)))].slice(0, 120).sort();
  } catch { return []; }
}

export function routineDays(type: string): number[] | null {
  if (type === 'weekday') return [1, 2, 3, 4, 5];
  if (type === 'weekend') return [0, 6];
  return null;
}

function parsedDays(schedule: AdvancedSchedule): number[] {
  const preset = routineDays(schedule.routine_type ?? 'custom');
  if (preset) return preset;
  try {
    const parsed = JSON.parse(schedule.days_json) as unknown;
    return Array.isArray(parsed) ? [...new Set(parsed.filter(value => Number.isInteger(value) && Number(value) >= 0 && Number(value) <= 6).map(Number))].sort() : [];
  } catch { return []; }
}

const dateKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function upcomingScheduleOccurrences(schedule: AdvancedSchedule, now = new Date(), horizonDays = 35): ScheduleOccurrence[] {
  if (schedule.enabled !== 1 || !/^([01]\d|2[0-3]):[0-5]\d$/.test(schedule.start_time)) return [];
  const [hour, minute] = schedule.start_time.split(':').map(Number);
  const days = parsedDays(schedule);
  if (!days.length) return [];
  const skips = new Set(sanitizeSkipDates(schedule.skip_dates_json));
  const reminderMinutes = Number.isInteger(schedule.reminder_minutes) ? Math.max(0, Math.min(120, Number(schedule.reminder_minutes))) : 0;
  const result: ScheduleOccurrence[] = [];
  const base = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  for (let offset = 0; offset <= Math.max(1, horizonDays); offset += 1) {
    const date = new Date(base);
    date.setDate(base.getDate() + offset);
    if (!days.includes(date.getDay()) || skips.has(dateKey(date))) continue;
    const start = new Date(date.getFullYear(), date.getMonth(), date.getDate(), hour, minute, 0, 0);
    if (start.getTime() <= now.getTime()) continue;
    const reminder = new Date(start.getTime() - reminderMinutes * 60000);
    result.push({ scheduleId: schedule.id, title: schedule.title, start, reminder: reminder.getTime() > now.getTime() ? reminder : start, durationMinutes: schedule.duration_minutes });
  }
  return result;
}
