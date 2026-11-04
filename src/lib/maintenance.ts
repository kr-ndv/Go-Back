import { NativeModules, Platform } from 'react-native';
import { checkForStableUpdate } from '@/lib/releaseChecker';
import { evaluateAndNotifyUsageLimits } from '@/lib/usageLimits';

type SystemBridge = {
  scheduleMaintenance?: () => Promise<boolean>;
  runMaintenanceNow?: () => Promise<boolean>;
  setUpdateChecksEnabled?: (enabled: boolean) => Promise<boolean>;
};

export async function runForegroundMaintenance() {
  if (Platform.OS === 'android') {
    const bridge = NativeModules.GoBackSystem as SystemBridge | undefined;
    if (bridge?.runMaintenanceNow) {
      try {
        await bridge.scheduleMaintenance?.();
        await bridge.runMaintenanceNow();
        return { source: 'native' as const };
      } catch {
        // Fall through to the JS path when the native module is unavailable or fails.
      }
    }
  }

  const [update, limits] = await Promise.allSettled([
    checkForStableUpdate({ notify: true }),
    evaluateAndNotifyUsageLimits(),
  ]);
  return { source: 'js' as const, update, limits };
}

export async function syncNativeUpdatePreference(enabled: boolean) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackSystem as SystemBridge | undefined;
  try {
    await bridge?.setUpdateChecksEnabled?.(enabled);
  } catch {
    // Foreground/manual update checks still work if the optional native bridge fails.
  }
}
