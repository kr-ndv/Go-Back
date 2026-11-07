import { NativeModules, Platform } from 'react-native';

export type FocusNativeSnapshot = {
  active: boolean;
  mode: 'timer' | 'pomodoro' | 'stopwatch';
  endAt?: number | null;
  elapsedSeconds?: number;
  stopwatchRunStartedAt?: number | null;
  durationSeconds?: number;
  remainingSeconds?: number;
  paused?: boolean;
  sessionType?: 'focus' | 'short-break' | 'long-break';
  title?: string;
};

type BlockingBridge = {
  setFocusActive?: (active: boolean) => Promise<boolean>;
  setFocusState?: (json: string) => Promise<boolean>;
  setSessionBlockedPackages?: (json: string) => Promise<boolean>;
};

type UsageBridge = {
  hasUsageAccess?: () => Promise<boolean>;
  getUnlockCount?: (days: number) => Promise<number>;
};

type SystemBridge = {
  requestFocusTile?: () => Promise<number | boolean>;
  scheduleMaintenance?: () => Promise<boolean>;
  runMaintenanceNow?: () => Promise<boolean>;
  setUpdateChecksEnabled?: (enabled: boolean) => Promise<boolean>;
  updateWidgetData?: (json: string) => Promise<boolean>;
};

export type WidgetSnapshot = {
  focusMinutesToday: number;
  dailyGoalMinutes: number;
  streakDays: number;
  nextExamTitle: string;
  nextExamDate: string;
  nextExamDays: number;
  totalNotes: number;
  syllabusProgress: string;
};

export async function syncWidgetSnapshot(snapshot: WidgetSnapshot) {
  if (Platform.OS !== 'android') return;
  const bridge = NativeModules.GoBackSystem as SystemBridge | undefined;
  try {
    if (bridge?.updateWidgetData) {
      await bridge.updateWidgetData(JSON.stringify(snapshot));
    }
  } catch {
    // Non-fatal if native bridge is not ready
  }
}

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

export async function getNativeUnlockCount(days = 1): Promise<number> {
  if (Platform.OS !== 'android') return 0;
  const bridge = NativeModules.GoBackUsage as UsageBridge | undefined;
  try {
    if (bridge?.getUnlockCount) return await bridge.getUnlockCount(days);
    return 0;
  } catch {
    return 0;
  }
}

export const getGoBackSystemBridge = () => NativeModules.GoBackSystem as SystemBridge | undefined;

export const hasNativeFocusNotificationBridge = () => Platform.OS === 'android' && typeof (NativeModules.GoBackBlocking as BlockingBridge | undefined)?.setFocusState === 'function';
