export type FocusHistoryPoint = {
  started_at: string;
  duration_seconds: number;
  session_type?: string;
};

export type DailyFocusTotal = { date: string; seconds: number };

const localDateKey = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export function dailyFocusTotals(rows: FocusHistoryPoint[]): DailyFocusTotal[] {
  const totals = new Map<string, number>();
  for (const row of rows) {
    if (row.session_type && row.session_type !== 'focus') continue;
    const date = new Date(row.started_at);
    if (!Number.isFinite(date.getTime())) continue;
    const seconds = Number.isFinite(row.duration_seconds) ? Math.max(0, Math.floor(row.duration_seconds)) : 0;
    const key = localDateKey(date);
    totals.set(key, (totals.get(key) ?? 0) + seconds);
  }
  return [...totals.entries()].map(([date, seconds]) => ({ date, seconds })).sort((a, b) => a.date.localeCompare(b.date));
}

function previousDateKey(dateKey: string, days: number) {
  const date = new Date(`${dateKey}T12:00:00`);
  date.setDate(date.getDate() - days);
  return localDateKey(date);
}

export function focusGoalStats(rows: FocusHistoryPoint[], dailyGoalMinutes: number, today = new Date()) {
  const goalSeconds = Math.max(1, Math.floor(dailyGoalMinutes * 60));
  const totals = new Map(dailyFocusTotals(rows).map(item => [item.date, item.seconds]));
  const todayKey = localDateKey(today);

  let currentStreak = 0;
  let cursor = todayKey;
  // If today is not complete yet, allow the streak to continue from yesterday rather than treating today as a miss.
  if ((totals.get(cursor) ?? 0) < goalSeconds) cursor = previousDateKey(cursor, 1);
  for (let guard = 0; guard < 3660 && (totals.get(cursor) ?? 0) >= goalSeconds; guard += 1) {
    currentStreak += 1;
    cursor = previousDateKey(cursor, 1);
  }

  const successful = [...totals.entries()].filter(([, seconds]) => seconds >= goalSeconds).map(([date]) => date).sort();
  let bestStreak = 0;
  let running = 0;
  let previous = '';
  for (const date of successful) {
    running = previous && previousDateKey(date, 1) === previous ? running + 1 : 1;
    bestStreak = Math.max(bestStreak, running);
    previous = date;
  }

  const weekStart = new Date(today);
  const mondayOffset = (weekStart.getDay() + 6) % 7;
  weekStart.setDate(weekStart.getDate() - mondayOffset);
  weekStart.setHours(0, 0, 0, 0);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const weekKey = localDateKey(weekStart);
  const monthKey = localDateKey(monthStart);
  const goalDaysThisWeek = successful.filter(date => date >= weekKey && date <= todayKey).length;
  const goalDaysThisMonth = successful.filter(date => date >= monthKey && date <= todayKey).length;

  return {
    currentStreak,
    bestStreak,
    goalDaysThisWeek,
    goalDaysThisMonth,
    todaySeconds: totals.get(todayKey) ?? 0,
    goalSeconds,
  };
}
