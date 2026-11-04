import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getSetting } from './database';
import {
  FOCUS_ALARM_SETTING_KEY,
  focusAlarmChannelId,
  focusAlarmLabel,
  normalizeFocusAlarmSound,
  type FocusAlarmSound,
} from './focusAlarm';

export const FOCUS_STATUS_CHANNEL_ID = 'focus-status-v1';

export async function getSelectedFocusAlarm(): Promise<FocusAlarmSound> {
  const saved = await getSetting<unknown>(FOCUS_ALARM_SETTING_KEY, 'default');
  return normalizeFocusAlarmSound(saved);
}

export async function ensureFocusStatusChannel(): Promise<string | null> {
  if (Platform.OS !== 'android') return null;
  await Notifications.setNotificationChannelAsync(FOCUS_STATUS_CHANNEL_ID, {
    name: 'Focus timer status',
    description: 'Silent ongoing timer status while a focus session is active.',
    importance: Notifications.AndroidImportance.LOW,
    vibrationPattern: [],
    lightColor: '#C7F485',
    sound: null,
  });
  return FOCUS_STATUS_CHANNEL_ID;
}

export async function ensureFocusAlertChannel(sound: FocusAlarmSound): Promise<string | null> {
  if (Platform.OS !== 'android') return null;
  const channelId = focusAlarmChannelId(sound);
  await Notifications.setNotificationChannelAsync(channelId, {
    name: `Focus timer alerts · ${focusAlarmLabel(sound)}`,
    description: 'High-priority alert when a focus timer or break finishes.',
    importance: Notifications.AndroidImportance.MAX,
    vibrationPattern: [0, 250, 180, 250, 180, 500],
    lightColor: '#C7F485',
    sound,
  });
  return channelId;
}
