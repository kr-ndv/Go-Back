import { NativeModules, Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getDatabase, getSetting, setSetting } from '@/lib/database';
import { evaluateUsageLimits, type SentLimitState, type UsageLimitEvent, type UsageLimitRule, type UsageLimitStat } from '@/lib/usageLimitLogic';

export type UsageLimit = UsageLimitRule;
export type LimitEvent = UsageLimitEvent;
export { evaluateUsageLimits } from '@/lib/usageLimitLogic';

type UsageBridge = { hasUsageAccess: () => Promise<boolean>; getUsageStats: (days: number) => Promise<UsageLimitStat[]> };
const usageBridge = NativeModules.GoBackUsage as UsageBridge | undefined;
const CHANNEL_ID = 'screen-time-limits-v1';

const dateKey = (date = new Date()) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

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
    const sent = await getSetting<SentLimitState>(key, {});
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
