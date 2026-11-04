export const WEEKDAY_LABELS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'] as const;

export function isValidTimeOfDay(value: string): boolean {
  if (!/^([01]\d|2[0-3]):([0-5]\d)$/.test(value)) return false;
  const [hours, minutes] = value.split(':').map(Number);
  return Number.isInteger(hours) && Number.isInteger(minutes);
}

export function parseWeekdays(value: string): number[] {
  try {
    const parsed: unknown = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((day): day is number => Number.isInteger(day) && day >= 0 && day <= 6))].sort((a, b) => a - b);
  } catch {
    return [];
  }
}

export function formatWeekdays(days: number[]): string {
  const clean = [...new Set(days.filter(day => Number.isInteger(day) && day >= 0 && day <= 6))].sort((a, b) => a - b);
  if (clean.length === 7) return 'Every day';
  if (clean.length === 5 && [1, 2, 3, 4, 5].every(day => clean.includes(day))) return 'Weekdays';
  if (clean.length === 2 && clean.includes(0) && clean.includes(6)) return 'Weekends';
  return clean.map(day => WEEKDAY_LABELS[day]).join(', ');
}

export function isValidFocusDuration(value: number): boolean {
  return Number.isInteger(value) && value >= 5 && value <= 180;
}
