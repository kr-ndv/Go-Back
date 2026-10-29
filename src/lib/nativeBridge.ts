import { NativeModules, Platform } from 'react-native';

export type FocusNativeSnapshot = {
  active: boolean;
  mode: 'timer' | 'pomodoro' | 'stopwatch';
  endAt?: number | null;
  elapsedSeconds?: number;
  stopwatchRunStartedAt?: number | null;
  title?: string;
};

type BlockingBridge = {
  setFocusActive?: (active: boolean) => Promise<boolean>;
  setFocusState?: (json: string) => Promise<boolean>;
  setSessionBlockedPackages?: (json: string) => Promise<boolean>;
};

type SystemBridge = {
  requestFocusTile?: () => Promise<number | boolean>;
  scheduleMaintenance?: () => Promise<boolean>;
  runMaintenanceNow?: () => Promise<boolean>;
  setUpdateChecksEnabled?: (enabled: boolean) => Promise<boolean>;
};

export async function syncNativeFocusState(snapshot: FocusNativeSnapshot) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackBlocking as BlockingBridge | undefined;
  try {
    if (bridge?.setFocusState) await bridge.setFocusState(JSON.stringify(snapshot));
    else await bridge?.setFocusActive?.(snapshot.active);
  } catch { /* JS focus state remains the source of truth for history. */ }
}

export async function setNativeSessionBlockedPackages(packages: string[]) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackBlocking as BlockingBridge | undefined;
  try { await bridge?.setSessionBlockedPackages?.(JSON.stringify(packages)); } catch { /* Blocking is optional. */ }
}

export const getGoBackSystemBridge = () => NativeModules.GoBackSystem as SystemBridge | undefined;
