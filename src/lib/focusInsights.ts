export type FocusHistoryPoint = {
  started_at: string;
  duration_seconds: number;
  session_type?: string;
};

export type DailyFocusTotal = { date: string; seconds: number };

export type StreakMilestoneTier = 'none' | 'spark' | 'momentum' | 'commitment' | 'habit' | 'mastery' | 'centurion';

export type StreakMilestone = {
  tier: StreakMilestoneTier;
  label: string;
  badge: string;
  days: number;
  nextMilestoneDays: number;
  daysToNext: number;
  progressPercent: number;
};

export type FocusGoalStatsResult = {
  currentStreak: number;
  bestStreak: number;
  goalDaysThisWeek: number;
  goalDaysThisMonth: number;
  todaySeconds: number;
  goalSeconds: number;
  todayGoalMet: boolean;
  streakMilestone: StreakMilestone;
  streakShieldActive: boolean;
};

const STREAK_MILESTONES = [
  { days: 3, tier: 'spark' as const, label: 'Spark', badge: '✨' },
  { days: 7, tier: 'momentum' as const, label: 'Momentum', badge: '🔥' },
  { days: 14, tier: 'commitment' as const, label: 'Commitment', badge: '⚡' },
  { days: 30, tier: 'habit' as const, label: 'Habit Builder', badge: '🛡️' },
  { days: 50, tier: 'mastery' as const, label: 'Focus Mastery', badge: '💎' },
  { days: 100, tier: 'centurion' as const, label: 'Centurion', badge: '👑' },
];

export function calculateStreakMilestone(currentStreak: number): StreakMilestone {
  if (currentStreak < 3) {
    return {
      tier: 'none',
      label: 'Getting Started',
      badge: '🌱',
      days: currentStreak,
      nextMilestoneDays: 3,
      daysToNext: Math.max(1, 3 - currentStreak),
      progressPercent: Math.round((currentStreak / 3) * 100),
    };
  }

  let currentTier = STREAK_MILESTONES[0];
  let nextTier = STREAK_MILESTONES[1];

  for (let i = 0; i < STREAK_MILESTONES.length; i++) {
    if (currentStreak >= STREAK_MILESTONES[i].days) {
      currentTier = STREAK_MILESTONES[i];
      nextTier = STREAK_MILESTONES[i + 1] ?? {
        days: currentTier.days + 50,
        tier: 'centurion' as const,
        label: 'Grandmaster',
        badge: '👑',
      };
    }
  }

  const range = nextTier.days - currentTier.days;
  const progressIntoTier = currentStreak - currentTier.days;
  const progressPercent = Math.min(100, Math.round((progressIntoTier / range) * 100));

  return {
    tier: currentTier.tier,
    label: currentTier.label,
    badge: currentTier.badge,
    days: currentStreak,
    nextMilestoneDays: nextTier.days,
    daysToNext: Math.max(0, nextTier.days - currentStreak),
    progressPercent,
  };
}

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

export function focusGoalStats(rows: FocusHistoryPoint[], dailyGoalMinutes: number, today = new Date()): FocusGoalStatsResult {
  const goalSeconds = Math.max(1, Math.floor(dailyGoalMinutes * 60));
  const totals = new Map(dailyFocusTotals(rows).map(item => [item.date, item.seconds]));
  const todayKey = localDateKey(today);
  const todaySeconds = totals.get(todayKey) ?? 0;
  const todayGoalMet = todaySeconds >= goalSeconds;

  let currentStreak = 0;
  let cursor = todayKey;
  let streakShieldActive = false;

  // If today is not complete yet, check from yesterday
  if (!todayGoalMet) {
    cursor = previousDateKey(cursor, 1);
  }

  // Count consecutive days meeting the goal
  for (let guard = 0; guard < 3660; guard += 1) {
    const daySeconds = totals.get(cursor) ?? 0;
    if (daySeconds >= goalSeconds) {
      currentStreak += 1;
      cursor = previousDateKey(cursor, 1);
    } else if (!streakShieldActive && guard > 0) {
      // 1-day streak shield: if user missed 1 single day within a running streak, check the day before
      const dayBefore = previousDateKey(cursor, 1);
      if ((totals.get(dayBefore) ?? 0) >= goalSeconds) {
        streakShieldActive = true;
        cursor = dayBefore;
      } else {
        break;
      }
    } else {
      break;
    }
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
  bestStreak = Math.max(bestStreak, currentStreak);

  const weekStart = new Date(today);
  const mondayOffset = (weekStart.getDay() + 6) % 7;
  weekStart.setDate(weekStart.getDate() - mondayOffset);
  weekStart.setHours(0, 0, 0, 0);
  const monthStart = new Date(today.getFullYear(), today.getMonth(), 1);
  const weekKey = localDateKey(weekStart);
  const monthKey = localDateKey(monthStart);
  const goalDaysThisWeek = successful.filter(date => date >= weekKey && date <= todayKey).length + (todayGoalMet && !successful.includes(todayKey) ? 1 : 0);
  const goalDaysThisMonth = successful.filter(date => date >= monthKey && date <= todayKey).length + (todayGoalMet && !successful.includes(todayKey) ? 1 : 0);

  const streakMilestone = calculateStreakMilestone(currentStreak);

  return {
    currentStreak,
    bestStreak,
    goalDaysThisWeek,
    goalDaysThisMonth,
    todaySeconds,
    goalSeconds,
    todayGoalMet,
    streakMilestone,
    streakShieldActive,
  };
}
