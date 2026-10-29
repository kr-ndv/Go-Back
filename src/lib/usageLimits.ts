import { NativeModules, Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getDatabase, getSetting, setSetting } from '@/lib/database';

export type UsageLimit = {
  id: string;
  package_name: string;
  app_name: string;
  limit_minutes: number;
  warning_percent: number;
  enabled: number;
};

type UsageStat = { packageName: string; appName: string; foregroundTimeMs: number };
type UsageBridge = { hasUsageAccess: () => Promise<boolean>; getUsageStats: (days: number) => Promise<UsageStat[]> };
const usageBridge = NativeModules.GoBackUsage as UsageBridge | undefined;
const CHANNEL_ID = 'screen-time-limits-v1';

type AlertKind = 'warning' | 'exceeded';
export type LimitEvent = { packageName: string; appName: string; kind: AlertKind; usedMinutes: number; limitMinutes: number };

const dateKey = (date = new Date()) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const dedupeKinds = (values: readonly AlertKind[]): AlertKind[] => [...new Set<AlertKind>(values)];

export function evaluateUsageLimits(
  stats: UsageStat[],
  limits: UsageLimit[],
  alreadySent: Record<string, AlertKind[]>,
): { events: LimitEvent[]; nextSent: Record<string, AlertKind[]> } {
  const byPackage = new Map(stats.map(item => [item.packageName, item]));
  const nextSent: Record<string, AlertKind[]> = {};
  for (const [key, value] of Object.entries(alreadySent)) nextSent[key] = dedupeKinds(value);
  const events: LimitEvent[] = [];
  for (const limit of limits) {
    if (limit.enabled !== 1 || limit.limit_minutes < 1) continue;
    const stat = byPackage.get(limit.package_name);
    const usedMinutes = Math.max(0, Math.floor((stat?.foregroundTimeMs ?? 0) / 60000));
    const warningAt = Math.max(1, Math.floor(limit.limit_minutes * Math.max(1, Math.min(99, limit.warning_percent)) / 100));
    const sent = nextSent[limit.package_name] ?? [];
    if (usedMinutes >= limit.limit_minutes && !sent.includes('exceeded')) {
      events.push({ packageName: limit.package_name, appName: limit.app_name || stat?.appName || limit.package_name, kind: 'exceeded', usedMinutes, limitMinutes: limit.limit_minutes });
      nextSent[limit.package_name] = dedupeKinds([...sent, 'warning', 'exceeded']);
    } else if (usedMinutes >= warningAt && !sent.includes('warning')) {
      events.push({ packageName: limit.package_name, appName: limit.app_name || stat?.appName || limit.package_name, kind: 'warning', usedMinutes, limitMinutes: limit.limit_minutes });
      nextSent[limit.package_name] = dedupeKinds([...sent, 'warning']);
    }
  }
  return { events, nextSent };
}

async function notify(event: LimitEvent) {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Screen-time limit alerts',
      importance: Notifications.AndroidImportance.DEFAULT,
      vibrationPattern: [0, 180, 100, 180],
    });
  }
  const trigger = Platform.OS === 'android' ? ({ channelId: CHANNEL_ID } as Notifications.NotificationTriggerInput) : null;
  await Notifications.scheduleNotificationAsync({
    content: {
      title: event.kind === 'exceeded' ? `${event.appName} reached its daily limit` : `${event.appName} is nearing its daily limit`,
      body: event.kind === 'exceeded'
        ? `${event.usedMinutes} minutes used today · ${event.limitMinutes} minute limit.`
        : `${event.usedMinutes} of ${event.limitMinutes} minutes used today.`,
      data: { goBackKind: 'usage-limit', packageName: event.packageName },
    },
    trigger,
  });
}

export async function evaluateAndNotifyUsageLimits(): Promise<{ events: LimitEvent[]; unavailable?: string }> {
  if (!usageBridge) return { events: [], unavailable: 'Native Android build required.' };
  try {
    if (!(await usageBridge.hasUsageAccess())) return { events: [], unavailable: 'Usage Access is not enabled.' };
    const db = await getDatabase();
    const limits = await db.getAllAsync<UsageLimit>('SELECT id, package_name, app_name, limit_minutes, warning_percent, enabled FROM usage_limits WHERE enabled = 1');
    if (!limits.length) return { events: [] };
    const stats = await usageBridge.getUsageStats(1);
    const key = `usage.limitAlerts.${dateKey()}`;
    const sent = await getSetting<Record<string, AlertKind[]>>(key, {});
    const result = evaluateUsageLimits(stats, limits, sent);
    if (result.events.length) {
      let permission = await Notifications.getPermissionsAsync();
      if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
      if (permission.granted) for (const event of result.events) await notify(event);
      await setSetting(key, result.nextSent);
    }
    return { events: result.events };
  } catch (error) {
    return { events: [], unavailable: error instanceof Error ? error.message : 'Could not evaluate screen-time limits.' };
  }
}
