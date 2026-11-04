import { NativeModules, Platform } from 'react-native';
import type { UsageLimit } from '@/lib/usageLimits';

type UsageBridge = { setUsageLimits?: (json: string) => Promise<boolean> };

export async function syncUsageLimitsToNative(limits: UsageLimit[]) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackUsage as UsageBridge | undefined;
  if (!bridge?.setUsageLimits) return;
  const safe = limits.filter(limit => limit.enabled === 1).map(limit => ({
    packageName: limit.package_name,
    appName: limit.app_name,
    limitMinutes: limit.limit_minutes,
    warningPercent: limit.warning_percent,
  }));
  try { await bridge.setUsageLimits(JSON.stringify(safe)); } catch { /* Foreground limit checks still work. */ }
}
