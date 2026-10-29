import { NativeModules, Platform } from 'react-native';
import { checkForStableUpdate } from '@/lib/releaseChecker';
import { evaluateAndNotifyUsageLimits } from '@/lib/usageLimits';

type SystemBridge = {
  scheduleMaintenance?: () => Promise<boolean>;
  runMaintenanceNow?: () => Promise<boolean>;
  setUpdateChecksEnabled?: (enabled: boolean) => Promise<boolean>;
};

export async function runForegroundMaintenance() {
  const [update, limits] = await Promise.allSettled([
    checkForStableUpdate({ notify: true }),
    evaluateAndNotifyUsageLimits(),
  ]);
  if (Platform.OS === 'android') {
    const bridge = NativeModules.GoBackSystem as SystemBridge | undefined;
    try { await bridge?.scheduleMaintenance?.(); } catch { /* Background delivery is best-effort. */ }
  }
  return { update, limits };
}

export async function syncNativeUpdatePreference(enabled: boolean) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackSystem as SystemBridge | undefined;
  try { await bridge?.setUpdateChecksEnabled?.(enabled); } catch { /* Foreground checks still work. */ }
}
