import { dailyFocusTotals, type FocusHistoryPoint } from '@/lib/focusInsights';

export type DailyUsagePoint = {
  date: string;
  packageName: string;
  appName: string;
  foregroundTimeMs: number;
};
export type UsageCategory = 'productive' | 'distracting' | 'neutral';

export type PeriodInsight = {
  start: string;
  end: string;
  focusSeconds: number;
  usageMs: number;
  productiveMs: number;
  distractingMs: number;
  neutralMs: number;
  bestFocusDay: { date: string; seconds: number } | null;
  topDistractingApps: { packageName: string; appName: string; foregroundTimeMs: number }[];
};

const dateKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function dateRangeEndingToday(days: number, now = new Date()) {
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const start = new Date(end);
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { start: dateKey(start), end: dateKey(end) };
}

export function previousRange(range: { start: string; end: string }) {
  const start = new Date(`${range.start}T12:00:00`);
  const end = new Date(`${range.end}T12:00:00`);
  const days = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86400000) + 1);
  const previousEnd = new Date(start);
  previousEnd.setDate(previousEnd.getDate() - 1);
  const previousStart = new Date(previousEnd);
  previousStart.setDate(previousStart.getDate() - (days - 1));
  return { start: dateKey(previousStart), end: dateKey(previousEnd) };
}

export function aggregatePeriod(
  sessions: FocusHistoryPoint[],
  usage: DailyUsagePoint[],
  categories: Record<string, UsageCategory>,
  range: { start: string; end: string },
): PeriodInsight {
  const focusByDay = dailyFocusTotals(sessions).filter(item => item.date >= range.start && item.date <= range.end);
  const focusSeconds = focusByDay.reduce((sum, item) => sum + item.seconds, 0);
  const bestFocusDay = focusByDay.reduce<{ date: string; seconds: number } | null>((best, item) => !best || item.seconds > best.seconds ? item : best, null);

  let usageMs = 0;
  let productiveMs = 0;
  let distractingMs = 0;
  let neutralMs = 0;
  const distractingApps = new Map<string, { packageName: string; appName: string; foregroundTimeMs: number }>();
  for (const item of usage) {
    if (item.date < range.start || item.date > range.end) continue;
    const value = Number.isFinite(item.foregroundTimeMs) ? Math.max(0, item.foregroundTimeMs) : 0;
    usageMs += value;
    const category = categories[item.packageName] ?? 'neutral';
    if (category === 'productive') productiveMs += value;
    else if (category === 'distracting') {
      distractingMs += value;
      const existing = distractingApps.get(item.packageName);
      distractingApps.set(item.packageName, { packageName: item.packageName, appName: item.appName, foregroundTimeMs: (existing?.foregroundTimeMs ?? 0) + value });
    } else neutralMs += value;
  }
  return {
    start: range.start,
    end: range.end,
    focusSeconds,
    usageMs,
    productiveMs,
    distractingMs,
    neutralMs,
    bestFocusDay,
    topDistractingApps: [...distractingApps.values()].sort((a, b) => b.foregroundTimeMs - a.foregroundTimeMs).slice(0, 5),
  };
}

export function percentChange(current: number, previous: number): number | null {
  if (!Number.isFinite(current) || !Number.isFinite(previous)) return null;
  if (previous <= 0) return current > 0 ? null : 0;
  return Math.round(((current - previous) / previous) * 100);
}
