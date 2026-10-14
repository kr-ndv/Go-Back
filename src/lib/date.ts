/** Strict YYYY-MM-DD validation that rejects impossible calendar dates. */
export function isValidISODate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(value + 'T12:00:00.000Z');
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

/** Calendar-day distance, independent of the device's time of day. */
export function daysUntilISODate(value: string, now = new Date()): number {
  if (!isValidISODate(value)) throw new Error('Expected a valid YYYY-MM-DD date.');
  const target = Date.parse(value + 'T00:00:00.000Z');
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((target - today) / 86_400_000);
}
