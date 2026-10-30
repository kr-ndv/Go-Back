export type UsageLimitRule = {
  id: string;
  package_name: string;
  app_name: string;
  limit_minutes: number;
  warning_percent: number;
  enabled: number;
};

export type UsageLimitStat = { packageName: string; appName: string; foregroundTimeMs: number };
export type UsageLimitEvent = { packageName: string; appName: string; kind: 'warning' | 'exceeded'; usedMinutes: number; limitMinutes: number };
export type SentLimitState = Record<string, ('warning' | 'exceeded')[]>;

export function evaluateUsageLimits(
  stats: UsageLimitStat[],
  limits: UsageLimitRule[],
  alreadySent: SentLimitState,
): { events: UsageLimitEvent[]; nextSent: SentLimitState } {
  const byPackage = new Map(stats.map(item => [item.packageName, item]));
  const nextSent: SentLimitState = {};
  for (const [key, value] of Object.entries(alreadySent)) nextSent[key] = [...new Set(value)];
  const events: UsageLimitEvent[] = [];
  for (const limit of limits) {
    if (limit.enabled !== 1 || limit.limit_minutes < 1) continue;
    const stat = byPackage.get(limit.package_name);
    const usedMinutes = Math.max(0, Math.floor((stat?.foregroundTimeMs ?? 0) / 60000));
    const warningAt = Math.max(1, Math.floor(limit.limit_minutes * Math.max(1, Math.min(99, limit.warning_percent)) / 100));
    const sent = nextSent[limit.package_name] ?? [];
    if (usedMinutes >= limit.limit_minutes && !sent.includes('exceeded')) {
      events.push({ packageName: limit.package_name, appName: limit.app_name || stat?.appName || limit.package_name, kind: 'exceeded', usedMinutes, limitMinutes: limit.limit_minutes });
      nextSent[limit.package_name] = [...new Set<'warning' | 'exceeded'>([...sent, 'warning', 'exceeded'])];
    } else if (usedMinutes >= warningAt && !sent.includes('warning')) {
      events.push({ packageName: limit.package_name, appName: limit.app_name || stat?.appName || limit.package_name, kind: 'warning', usedMinutes, limitMinutes: limit.limit_minutes });
      nextSent[limit.package_name] = [...new Set<'warning' | 'exceeded'>([...sent, 'warning'])];
    }
  }
  return { events, nextSent };
}
