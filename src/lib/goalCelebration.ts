import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getDatabase, getSetting } from '@/lib/database';

export const GOAL_COMPLETE_CHANNEL_ID = 'focus-goal-complete-v1';

const localDateKey = (date = new Date()) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export async function checkAndCelebrateDailyGoal(addedSeconds: number): Promise<{ achieved: boolean; goalMinutes: number }> {
  if (addedSeconds <= 0) return { achieved: false, goalMinutes: 0 };

  try {
    const db = await getDatabase();
    const savedGoal = await getSetting<number>('focus.dailyGoalMinutes', 120);
    const goalMinutes = Number.isInteger(savedGoal) && savedGoal >= 15 && savedGoal <= 480 ? savedGoal : 120;
    const goalSeconds = goalMinutes * 60;

    const today = localDateKey();
    const rows = await db.getAllAsync<{ duration_seconds: number }>(
      'SELECT duration_seconds FROM focus_sessions WHERE session_type = \'focus\' AND date(started_at) = ?',
      today
    );

    const totalSecondsToday = rows.reduce((acc, row) => acc + (Number(row.duration_seconds) || 0), 0);
    const priorSecondsToday = totalSecondsToday - addedSeconds;

    // Trigger celebration only on the exact session that crossed the threshold
    if (priorSecondsToday < goalSeconds && totalSecondsToday >= goalSeconds) {
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync(GOAL_COMPLETE_CHANNEL_ID, {
          name: 'Daily goal celebrations',
          description: 'Celebratory notifications when achieving your daily focus goal',
          importance: Notifications.AndroidImportance.HIGH,
          sound: 'default',
          enableVibrate: true,
          vibrationPattern: [0, 200, 100, 200, 100, 400],
          lightColor: '#F59E0B',
          audioAttributes: {
            usage: Notifications.AndroidAudioUsage.NOTIFICATION_EVENT,
            contentType: Notifications.AndroidAudioContentType.SONIFICATION,
          },
        });
      }

      const trigger = Platform.OS === 'android'
        ? ({ channelId: GOAL_COMPLETE_CHANNEL_ID } as Notifications.NotificationTriggerInput)
        : null;

      await Notifications.scheduleNotificationAsync({
        content: {
          title: '🎉 Daily Focus Goal Completed!',
          body: `You conquered your ${goalMinutes}-minute focus goal today. Outstanding work!`,
          sound: 'default',
          priority: Notifications.AndroidNotificationPriority.HIGH,
          data: { goBackKind: 'goal-achieved', date: today, goalMinutes },
        },
        trigger,
      });

      return { achieved: true, goalMinutes };
    }
  } catch {
    // Non-fatal
  }

  return { achieved: false, goalMinutes: 0 };
}
