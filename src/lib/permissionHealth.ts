import { NativeModules, Platform } from 'react-native';
import * as Notifications from 'expo-notifications';

type UsageBridge = { hasUsageAccess?: () => Promise<boolean> };
type BlockingBridge = { isAccessibilityEnabled?: () => Promise<boolean> };

export type PermissionHealth = {
  notifications: 'granted' | 'denied' | 'unknown';
  usageAccess: 'granted' | 'denied' | 'unavailable';
  accessibility: 'granted' | 'denied' | 'unavailable';
};

export async function readPermissionHealth(): Promise<PermissionHealth> {
  let notifications: PermissionHealth['notifications'] = 'unknown';
  try {
    const result = await Notifications.getPermissionsAsync();
    notifications = result.granted ? 'granted' : 'denied';
  } catch { notifications = 'unknown'; }

  if (Platform.OS !== 'android') {
    return { notifications, usageAccess: 'unavailable', accessibility: 'unavailable' };
  }

  const usage = NativeModules.GoBackUsage as UsageBridge | undefined;
  const blocking = NativeModules.GoBackBlocking as BlockingBridge | undefined;
  let usageAccess: PermissionHealth['usageAccess'] = usage?.hasUsageAccess ? 'denied' : 'unavailable';
  let accessibility: PermissionHealth['accessibility'] = blocking?.isAccessibilityEnabled ? 'denied' : 'unavailable';
  try { if (usage?.hasUsageAccess) usageAccess = await usage.hasUsageAccess() ? 'granted' : 'denied'; } catch { /* Leave denied. */ }
  try { if (blocking?.isAccessibilityEnabled) accessibility = await blocking.isAccessibilityEnabled() ? 'granted' : 'denied'; } catch { /* Leave denied. */ }
  return { notifications, usageAccess, accessibility };
}

export function permissionHealthSummary(value: PermissionHealth) {
  const missing: string[] = [];
  if (value.notifications === 'denied') missing.push('notifications');
  if (value.usageAccess === 'denied') missing.push('Usage Access');
  if (value.accessibility === 'denied') missing.push('Accessibility');
  if (!missing.length) return 'Required permissions look healthy.';
  return `Needs attention: ${missing.join(', ')}.`;
}
