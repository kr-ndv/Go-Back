import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getDatabase } from '@/lib/database';

export const EXAM_CHANNEL_ID = 'exam-reminders-v1';

type ExamRow = {
  id: string;
  title: string;
  exam_date: string;
  details?: string;
};

export async function cancelManagedExamNotifications() {
  try {
    const scheduled = await Notifications.getAllScheduledNotificationsAsync();
    const managed = scheduled.filter(item => item.content.data?.goBackKind === 'exam-reminder');
    await Promise.all(
      managed.map(item => Notifications.cancelScheduledNotificationAsync(item.identifier).catch(() => undefined))
    );
  } catch {
    // Non-fatal if notifications unavailable
  }
}

export async function rescheduleExamNotifications(options: { askPermission?: boolean } = {}) {
  await cancelManagedExamNotifications();
  let permission = await Notifications.getPermissionsAsync();
  if (!permission.granted && options.askPermission) {
    permission = await Notifications.requestPermissionsAsync();
  }
  if (!permission.granted) return { scheduled: 0, denied: true };

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(EXAM_CHANNEL_ID, {
      name: 'Exam timetable reminders',
      description: 'Audible reminders for upcoming exams 7 days, 1 day, and the morning of the exam',
      importance: Notifications.AndroidImportance.HIGH,
      sound: 'default',
      enableVibrate: true,
      vibrationPattern: [0, 250, 150, 250],
      lightColor: '#76D7C4',
      audioAttributes: {
        usage: Notifications.AndroidAudioUsage.NOTIFICATION_EVENT,
        contentType: Notifications.AndroidAudioContentType.SONIFICATION,
      },
    });
  }

  const now = new Date();
  const todayDateString = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

  let scheduled = 0;
  try {
    const db = await getDatabase();
    const exams = await db.getAllAsync<ExamRow>(
      'SELECT id, title, exam_date, details FROM exams WHERE exam_date >= ? ORDER BY exam_date ASC',
      todayDateString
    );

    for (const exam of exams) {
      const examDate = new Date(`${exam.exam_date}T09:00:00`);
      if (isNaN(examDate.getTime())) continue;

      // 1. 7 days before at 9:00 AM
      const sevenDaysBefore = new Date(examDate);
      sevenDaysBefore.setDate(sevenDaysBefore.getDate() - 7);
      sevenDaysBefore.setHours(9, 0, 0, 0);

      // 2. 1 day before at 9:00 AM
      const oneDayBefore = new Date(examDate);
      oneDayBefore.setDate(oneDayBefore.getDate() - 1);
      oneDayBefore.setHours(9, 0, 0, 0);

      // 3. Morning of exam at 7:00 AM
      const morningOfExam = new Date(examDate);
      morningOfExam.setHours(7, 0, 0, 0);

      const reminders = [
        {
          triggerDate: sevenDaysBefore,
          title: `📅 Exam in 7 days: ${exam.title}`,
          body: `${exam.title} is coming up in one week (${exam.exam_date}). Review syllabus topics early!`,
          subKind: '7d',
        },
        {
          triggerDate: oneDayBefore,
          title: `⚠️ Exam tomorrow: ${exam.title}`,
          body: `${exam.title} is tomorrow. Take time to review key summary notes and rest well tonight.`,
          subKind: '1d',
        },
        {
          triggerDate: morningOfExam,
          title: `🎯 Exam day: ${exam.title}`,
          body: `Good luck with your ${exam.title} exam today! Stay calm, focused, and confident.`,
          subKind: 'morning',
        },
      ];

      for (const item of reminders) {
        if (item.triggerDate.getTime() > now.getTime()) {
          try {
            const trigger = Platform.OS === 'android'
              ? ({ type: Notifications.SchedulableTriggerInputTypes.DATE, date: item.triggerDate, channelId: EXAM_CHANNEL_ID } as Notifications.NotificationTriggerInput)
              : ({ type: Notifications.SchedulableTriggerInputTypes.DATE, date: item.triggerDate } as Notifications.NotificationTriggerInput);

            await Notifications.scheduleNotificationAsync({
              content: {
                title: item.title,
                body: item.body,
                sound: 'default',
                priority: Notifications.AndroidNotificationPriority.HIGH,
                data: {
                  goBackKind: 'exam-reminder',
                  examId: exam.id,
                  subKind: item.subKind,
                  examDate: exam.exam_date,
                },
              },
              trigger,
            });
            scheduled += 1;
          } catch {
            // Ignore individual scheduling errors
          }
        }
      }
    }
  } catch {
    // Database or notification failure
  }

  return { scheduled, denied: false };
}
