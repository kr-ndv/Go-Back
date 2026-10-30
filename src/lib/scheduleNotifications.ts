import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getDatabase } from '@/lib/database';
import { detectedTimeZone, upcomingScheduleOccurrences, type AdvancedSchedule } from '@/lib/scheduleAdvanced';

const CHANNEL_ID = 'focus-schedules-v2';

export async function cancelManagedFocusScheduleNotifications() {
  const scheduled = await Notifications.getAllScheduledNotificationsAsync();
  const managed = scheduled.filter(item => item.content.data?.goBackKind === 'focus-schedule');
  await Promise.all(managed.map(item => Notifications.cancelScheduledNotificationAsync(item.identifier).catch(() => undefined)));
}

export async function rescheduleAdvancedFocusNotifications(options: { askPermission?: boolean; horizonDays?: number } = {}) {
  await cancelManagedFocusScheduleNotifications();
  let permission = await Notifications.getPermissionsAsync();
  if (!permission.granted && options.askPermission) permission = await Notifications.requestPermissionsAsync();
  if (!permission.granted) return { scheduled: 0, denied: true, errors: 0 };

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(CHANNEL_ID, {
      name: 'Focus schedule reminders',
      importance: Notifications.AndroidImportance.DEFAULT,
      vibrationPattern: [0, 180, 120, 180],
      lightColor: '#C7F485',
    });
  }

  const db = await getDatabase();
  const schedules = await db.getAllAsync<AdvancedSchedule>('SELECT id, title, days_json, start_time, duration_minutes, enabled, reminder_minutes, skip_dates_json, routine_type, timezone FROM focus_schedules WHERE enabled = 1 ORDER BY start_time ASC');
  const now = new Date();
  let scheduled = 0;
  let errors = 0;
  const zone = detectedTimeZone();

  for (const schedule of schedules) {
    // Preserve the local wall-clock schedule if the device timezone changed.
    if ((schedule.timezone ?? '') !== zone) {
      await db.runAsync('UPDATE focus_schedules SET timezone = ? WHERE id = ?', zone, schedule.id).catch(() => undefined);
    }
    const occurrences = upcomingScheduleOccurrences({ ...schedule, timezone: zone }, now, options.horizonDays ?? 35);
    for (const occurrence of occurrences) {
      try {
        const reminderMinutes = Number(schedule.reminder_minutes ?? 0);
        const early = occurrence.reminder.getTime() < occurrence.start.getTime();
        const body = early
          ? `${occurrence.title} starts in ${reminderMinutes} minutes · ${occurrence.durationMinutes} minute session.`
          : `${occurrence.title} · ${occurrence.durationMinutes} minutes. Open Go Back when you are ready to begin.`;
        const trigger = Platform.OS === 'android'
          ? ({ type: Notifications.SchedulableTriggerInputTypes.DATE, date: occurrence.reminder, channelId: CHANNEL_ID } as Notifications.NotificationTriggerInput)
          : ({ type: Notifications.SchedulableTriggerInputTypes.DATE, date: occurrence.reminder } as Notifications.NotificationTriggerInput);
        await Notifications.scheduleNotificationAsync({
          content: {
            title: early ? 'Focus time is coming up' : 'Your focus time is here',
            body,
            data: { goBackKind: 'focus-schedule', scheduleId: schedule.id, occurrenceAt: occurrence.start.toISOString() },
          },
          trigger,
        });
        scheduled += 1;
      } catch { errors += 1; }
    }
  }
  return { scheduled, denied: false, errors };
}
