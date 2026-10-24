import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, Platform, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import * as Notifications from 'expo-notifications';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { useAppTheme } from '@/state/theme';
import { formatWeekdays, isValidFocusDuration, isValidTimeOfDay, parseWeekdays, WEEKDAY_LABELS } from '@/lib/scheduleLogic';

type FocusSchedule = {
  id: string;
  title: string;
  days_json: string;
  start_time: string;
  duration_minutes: number;
  enabled: number;
  created_at: string;
};

const INITIAL_DAYS = [1, 2, 3, 4, 5];
const VALID_DURATIONS = [15, 25, 45, 60];

async function cancelScheduleNotifications() {
  const scheduled = await Notifications.getAllScheduledNotificationsAsync();
  const managed = scheduled.filter(item => item.content.data?.goBackKind === 'focus-schedule');
  await Promise.all(managed.map(item => Notifications.cancelScheduledNotificationAsync(item.identifier)));
}

async function scheduleReminders(schedules: FocusSchedule[], askPermission: boolean): Promise<{ scheduled: number; denied: boolean; errors: number }> {
  // Only touch notifications owned by Go Back's focus scheduler.
  try {
    await cancelScheduleNotifications();
  } catch {
    return { scheduled: 0, denied: false, errors: 1 };
  }

  try {
    let permission = await Notifications.getPermissionsAsync();
    if (!permission.granted && askPermission) permission = await Notifications.requestPermissionsAsync();
    if (!permission.granted) return { scheduled: 0, denied: true, errors: 0 };

    if (Platform.OS === 'android') {
      await Notifications.setNotificationChannelAsync('focus-schedules-v1', {
        name: 'Focus schedule reminders',
        importance: Notifications.AndroidImportance.DEFAULT,
        vibrationPattern: [0, 180, 120, 180],
        lightColor: '#C7F485',
      });
    }

    let scheduledCount = 0;
    let errors = 0;
    for (const schedule of schedules) {
      if (schedule.enabled !== 1 || !isValidTimeOfDay(schedule.start_time) || !isValidFocusDuration(schedule.duration_minutes)) continue;
      const days = parseWeekdays(schedule.days_json);
      const [hour, minute] = schedule.start_time.split(':').map(Number);
      for (const day of days) {
        try {
          await Notifications.scheduleNotificationAsync({
            content: {
              title: 'Your focus time is coming up',
              body: schedule.title + ' · ' + schedule.duration_minutes + ' minutes. Open Go Back when you are ready to begin.',
              data: { goBackKind: 'focus-schedule', scheduleId: schedule.id },
              ...(Platform.OS === 'android' ? { channelId: 'focus-schedules-v1' } : {}),
            },
            trigger: {
              type: Notifications.SchedulableTriggerInputTypes.CALENDAR,
              weekday: day + 1,
              hour,
              minute,
              second: 0,
              repeats: true,
            },
          });
          scheduledCount += 1;
        } catch {
          errors += 1;
        }
      }
    }
    return { scheduled: scheduledCount, denied: false, errors };
  } catch {
    return { scheduled: 0, denied: false, errors: 1 };
  }
}

function formatTimeLabel(value: string) {
  if (!isValidTimeOfDay(value)) return value;
  const [hour, minute] = value.split(':').map(Number);
  const suffix = hour >= 12 ? 'PM' : 'AM';
  const formattedHour = hour % 12 || 12;
  return formattedHour + ':' + String(minute).padStart(2, '0') + ' ' + suffix;
}

export default function FocusScheduleScreen() {
  const theme = useAppTheme();
  const [schedules, setSchedules] = useState<FocusSchedule[]>([]);
  const [title, setTitle] = useState('');
  const [startTime, setStartTime] = useState('18:00');
  const [duration, setDuration] = useState('25');
  const [selectedDays, setSelectedDays] = useState<number[]>(INITIAL_DAYS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [reminderStatus, setReminderStatus] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const db = await getDatabase();
      const rows = await db.getAllAsync<FocusSchedule>('SELECT * FROM focus_schedules ORDER BY enabled DESC, start_time ASC');
      const sanitized = rows.map(row => ({
        ...row,
        enabled: row.enabled === 1 ? 1 : 0,
        title: typeof row.title === 'string' ? row.title : 'Focus session',
        days_json: typeof row.days_json === 'string' ? row.days_json : '[]',
      }));
      setSchedules(sanitized);
      const reminderResult = await scheduleReminders(sanitized, false);
      if (reminderResult.denied && sanitized.some(row => row.enabled === 1)) {
        setReminderStatus('Allow notifications to receive your weekly reminders. Your schedules are still saved.');
      } else if (reminderResult.errors > 0) {
        setReminderStatus('Some reminders could not be scheduled. Check notification permission and try refreshing.');
      } else {
        setReminderStatus(sanitized.some(row => row.enabled === 1) ? reminderResult.scheduled + ' weekly reminder(s) scheduled on this device.' : 'No reminders are active.');
      }
    } catch {
      setError('Your schedules could not be loaded. Your existing data has not been changed.');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    void load();
  }, [load]));

  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') void load();
    });
    return () => subscription.remove();
  }, [load]);

  const toggleDay = (day: number) => {
    setSelectedDays(current => current.includes(day) ? current.filter(value => value !== day) : [...current, day].sort((a, b) => a - b));
  };

  const saveSchedule = async () => {
    const cleanTitle = title.trim();
    const minutes = Number(duration);
    if (!cleanTitle) {
      showThemedDialog('Name your focus block', 'Add a short label such as Study, Reading or Revision.');
      return;
    }
    if (!isValidTimeOfDay(startTime)) {
      showThemedDialog('Check the start time', 'Use 24-hour time in HH:MM format, for example 18:30.');
      return;
    }
    if (!isValidFocusDuration(minutes)) {
      showThemedDialog('Check the duration', 'Choose a whole number between 5 and 180 minutes.');
      return;
    }
    if (selectedDays.length === 0) {
      showThemedDialog('Choose at least one day', 'Select the days when you want a focus reminder.');
      return;
    }

    setSaving(true);
    try {
      const db = await getDatabase();
      const id = createLocalId('schedule');
      await db.runAsync(
        'INSERT INTO focus_schedules (id, title, days_json, start_time, duration_minutes, enabled, created_at) VALUES (?, ?, ?, ?, ?, 1, ?)',
        id, cleanTitle, JSON.stringify([...selectedDays].sort((a, b) => a - b)), startTime, minutes, new Date().toISOString()
      );
      setTitle('');
      setReminderStatus('');
      const rows = await db.getAllAsync<FocusSchedule>('SELECT * FROM focus_schedules ORDER BY enabled DESC, start_time ASC');
      setSchedules(rows);
      const reminderResult = await scheduleReminders(rows, true);
      if (reminderResult.denied) {
        setReminderStatus('Schedule saved, but Android notifications are not allowed. Enable notifications to receive reminders.');
      } else if (reminderResult.errors > 0) {
        setReminderStatus('Schedule saved, but one or more reminders could not be scheduled.');
      } else {
        setReminderStatus(reminderResult.scheduled + ' weekly reminder(s) scheduled on this device.');
      }
    } catch {
      showThemedDialog('Schedule not saved', 'Go Back could not save the focus schedule. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  const toggleSchedule = async (schedule: FocusSchedule, enabled: boolean) => {
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE focus_schedules SET enabled = ? WHERE id = ?', enabled ? 1 : 0, schedule.id);
      const rows = await db.getAllAsync<FocusSchedule>('SELECT * FROM focus_schedules ORDER BY enabled DESC, start_time ASC');
      setSchedules(rows);
      const result = await scheduleReminders(rows, enabled);
      if (result.denied && enabled) {
        setReminderStatus('Schedule enabled, but notifications are not allowed. Enable them in Android settings.');
      } else if (result.errors > 0) {
        setReminderStatus('Schedule saved, but some reminders could not be updated.');
      } else {
        setReminderStatus(result.scheduled + ' weekly reminder(s) scheduled on this device.');
      }
    } catch {
      showThemedDialog('Schedule not updated', 'Your schedule setting could not be saved. Please try again.');
    }
  };

  const removeSchedule = (schedule: FocusSchedule) => {
    showThemedDialog('Delete focus schedule?', 'Delete “' + schedule.title + '” and its reminders?', [
      { text: 'Keep schedule', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: async () => {
        try {
          const db = await getDatabase();
          await db.runAsync('DELETE FROM focus_schedules WHERE id = ?', schedule.id);
          const rows = await db.getAllAsync<FocusSchedule>('SELECT * FROM focus_schedules ORDER BY enabled DESC, start_time ASC');
          setSchedules(rows);
          const result = await scheduleReminders(rows, false);
          setReminderStatus(result.denied && rows.some(row => row.enabled === 1) ? 'Enable notifications to receive your reminders.' : result.errors ? 'Some remaining reminders could not be updated.' : result.scheduled + ' weekly reminder(s) scheduled on this device.');
        } catch {
          showThemedDialog('Schedule not deleted', 'Go Back could not delete this schedule. Please try again.');
        }
      } },
    ]);
  };

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel="Go back" onPress={() => router.back()} style={[styles.backButton, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[styles.headerTitle, { color: theme.text }]}>Focus schedule</Text>
        <Text style={[styles.headerSubtitle, { color: theme.muted }]}>A gentle reminder for your plan</Text>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Refresh focus schedules" onPress={() => void load()} style={[styles.backButton, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="refresh" size={18} color={theme.text} /></Pressable>
    </View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <View style={[styles.hero, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <View style={[styles.heroIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="calendar-outline" size={25} color={theme.primary} /></View>
        <Text style={[styles.heroTitle, { color: theme.text }]}>Make time for what matters.</Text>
        <Text style={[styles.heroBody, { color: theme.muted }]}>Create a repeating weekly reminder to begin a focus session. Go Back will notify you at the chosen time; it will not start the timer or block other apps automatically.</Text>
      </View>

      <View style={[styles.formCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Create a weekly reminder</Text>
        <Text style={[styles.fieldLabel, { color: theme.muted }]}>What are you planning?</Text>
        <TextInput accessibilityLabel="Focus schedule name" value={title} onChangeText={setTitle} maxLength={60} placeholder="e.g. Evening revision" placeholderTextColor={theme.muted} returnKeyType="next" style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: theme.border }]} />

        <View style={styles.fieldsRow}>
          <View style={{ flex: 1, gap: 7 }}>
            <Text style={[styles.fieldLabel, { color: theme.muted }]}>Start time (24-hour)</Text>
            <TextInput accessibilityLabel="Focus reminder start time in HH:MM format" value={startTime} onChangeText={setStartTime} maxLength={5} placeholder="18:00" placeholderTextColor={theme.muted} keyboardType="numbers-and-punctuation" autoCapitalize="none" style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: isValidTimeOfDay(startTime) ? theme.border : theme.danger }]} />
            {isValidTimeOfDay(startTime) && <Text style={[styles.timePreview, { color: theme.primary }]}>{formatTimeLabel(startTime)}</Text>}
          </View>
          <View style={{ flex: 1, gap: 7 }}>
            <Text style={[styles.fieldLabel, { color: theme.muted }]}>Session length</Text>
            <TextInput accessibilityLabel="Focus session duration in minutes" value={duration} onChangeText={setDuration} maxLength={3} placeholder="25" placeholderTextColor={theme.muted} keyboardType="number-pad" style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: isValidFocusDuration(Number(duration)) ? theme.border : theme.danger }]} />
            <View style={styles.durationPresets}>{VALID_DURATIONS.map(value =>
              <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: duration === String(value) }} onPress={() => setDuration(String(value))} style={[styles.durationChip, { backgroundColor: duration === String(value) ? theme.primary : theme.surface, borderColor: duration === String(value) ? theme.primary : theme.border }]}><Text style={{ color: duration === String(value) ? theme.onPrimary : theme.text, fontSize: 9, fontWeight: '800' }}>{value}m</Text></Pressable>
            )}</View>
          </View>
        </View>

        <Text style={[styles.fieldLabel, { color: theme.muted }]}>Repeat on</Text>
        <View style={styles.weekdays}>{WEEKDAY_LABELS.map((day, index) => {
          const selected = selectedDays.includes(index);
          return <Pressable key={day} accessibilityRole="button" accessibilityLabel={day + (selected ? ', selected' : ', not selected')} accessibilityState={{ selected }} onPress={() => toggleDay(index)} style={[styles.dayChip, { backgroundColor: selected ? theme.primary : theme.surface, borderColor: selected ? theme.primary : theme.border }]}><Text style={{ color: selected ? theme.onPrimary : theme.text, fontSize: 11, fontWeight: '800' }}>{day}</Text></Pressable>;
        })}</View>
        <Text style={[styles.daysPreview, { color: theme.muted }]}>{formatWeekdays(selectedDays) || 'Choose one or more days'}</Text>

        <Pressable accessibilityRole="button" accessibilityLabel="Save weekly focus schedule" disabled={saving} onPress={() => void saveSchedule()} style={[styles.saveButton, { backgroundColor: theme.primary, opacity: saving ? 0.65 : 1 }]}>
          {saving ? <ActivityIndicator size="small" color={theme.onPrimary} /> : <Ionicons name="calendar" size={18} color={theme.onPrimary} />}
          <Text style={{ color: theme.onPrimary, fontWeight: '900', fontSize: 14 }}>{saving ? 'Saving schedule…' : 'Save weekly reminder'}</Text>
        </Pressable>
      </View>

      <View style={styles.schedulesHeading}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Your schedules</Text>
        <Text style={[styles.count, { color: theme.muted }]}>{schedules.length}</Text>
      </View>

      {loading ? <View style={[styles.emptyCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><ActivityIndicator color={theme.primary} /><Text style={[styles.emptyText, { color: theme.muted }]}>Loading your schedules…</Text></View>
      : error ? <View style={[styles.emptyCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="alert-circle-outline" size={24} color={theme.danger} /><Text style={[styles.emptyText, { color: theme.muted }]}>{error}</Text><Pressable onPress={() => void load()} style={[styles.retryButton, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontWeight: '800' }}>Try again</Text></Pressable></View>
      : schedules.length === 0 ? <View style={[styles.emptyCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="calendar-clear-outline" size={27} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Your week, your rhythm.</Text><Text style={[styles.emptyText, { color: theme.muted }]}>Your weekly reminders will appear here. Start with one study block and adjust it as your routine changes.</Text></View>
      : schedules.map(schedule => {
        const days = parseWeekdays(schedule.days_json);
        const valid = isValidTimeOfDay(schedule.start_time) && isValidFocusDuration(schedule.duration_minutes);
        return <View key={schedule.id} style={[styles.scheduleCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, opacity: schedule.enabled ? 1 : 0.72 }]}>
          <View style={[styles.scheduleIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="calendar" size={21} color={theme.primary} /></View>
          <View style={{ flex: 1, gap: 5 }}>
            <Text numberOfLines={2} style={[styles.scheduleTitle, { color: theme.text }]}>{schedule.title}</Text>
            <Text style={[styles.scheduleTime, { color: theme.primary }]}>{formatTimeLabel(schedule.start_time)} · {schedule.duration_minutes} min</Text>
            <Text style={[styles.scheduleDays, { color: theme.muted }]}>{formatWeekdays(days)}</Text>
            {!valid && <Text style={[styles.scheduleDays, { color: theme.danger }]}>This schedule needs to be corrected.</Text>}
            <View style={styles.scheduleActions}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 7 }}>
                <Switch value={schedule.enabled === 1} onValueChange={value => void toggleSchedule(schedule, value)} trackColor={{ false: theme.border, true: theme.primary }} thumbColor={theme.surface} accessibilityLabel={'Enable ' + schedule.title} />
                <Text style={[styles.enabledLabel, { color: theme.muted }]}>{schedule.enabled ? 'Reminders on' : 'Paused'}</Text>
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={'Delete ' + schedule.title} onPress={() => removeSchedule(schedule)} style={[styles.deleteButton, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="trash-outline" size={16} color={theme.danger} /></Pressable>
            </View>
          </View>
        </View>;
      })}

      {!!reminderStatus && <View style={[styles.statusCard, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="notifications-outline" size={17} color={theme.primary} /><Text style={[styles.statusText, { color: theme.muted }]}>{reminderStatus}</Text></View>}
      <Text style={[styles.footnote, { color: theme.muted }]}>Reminders use your device's local time and Android notification settings. Battery restrictions, OS updates or notification permission changes may affect delivery. Schedules and reminder configuration stay on this device.</Text>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { paddingHorizontal: 16, paddingVertical: 7, flexDirection: 'row', alignItems: 'center', gap: 12 },
  backButton: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 21, fontWeight: '900', letterSpacing: -0.5 },
  headerSubtitle: { fontSize: 11 },
  content: { padding: 18, paddingBottom: 36, gap: 14 },
  hero: { borderWidth: 1, borderRadius: 26, padding: 18, alignItems: 'center', gap: 9 },
  heroIcon: { width: 52, height: 52, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  heroTitle: { fontSize: 19, fontWeight: '900', textAlign: 'center', letterSpacing: -0.4 },
  heroBody: { fontSize: 12, lineHeight: 18, textAlign: 'center' },
  formCard: { borderWidth: 1, borderRadius: 26, padding: 16, gap: 11 },
  sectionTitle: { fontSize: 18, fontWeight: '900', letterSpacing: -0.35 },
  fieldLabel: { fontSize: 11, fontWeight: '800' },
  input: { minHeight: 47, borderWidth: 1, borderRadius: 15, paddingHorizontal: 13, paddingVertical: 10, fontSize: 13 },
  fieldsRow: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  timePreview: { fontSize: 10, fontWeight: '800', marginTop: -3 },
  durationPresets: { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  durationChip: { borderWidth: 1, minHeight: 27, paddingHorizontal: 7, borderRadius: 9, alignItems: 'center', justifyContent: 'center' },
  weekdays: { flexDirection: 'row', justifyContent: 'space-between', gap: 5 },
  dayChip: { flex: 1, minHeight: 42, borderWidth: 1, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  daysPreview: { fontSize: 11, marginTop: -5 },
  saveButton: { minHeight: 51, borderRadius: 17, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 9, marginTop: 5 },
  schedulesHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 },
  count: { fontSize: 11, fontWeight: '800' },
  emptyCard: { borderWidth: 1, borderRadius: 23, padding: 22, alignItems: 'center', gap: 10 },
  emptyTitle: { fontSize: 15, fontWeight: '900' },
  emptyText: { textAlign: 'center', fontSize: 12, lineHeight: 18 },
  retryButton: { minHeight: 42, borderRadius: 13, justifyContent: 'center', paddingHorizontal: 18 },
  scheduleCard: { borderWidth: 1, borderRadius: 23, padding: 14, flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  scheduleIcon: { width: 43, height: 43, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  scheduleTitle: { fontSize: 14, fontWeight: '900' },
  scheduleTime: { fontSize: 12, fontWeight: '900' },
  scheduleDays: { fontSize: 10, lineHeight: 15 },
  scheduleActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4, gap: 8 },
  enabledLabel: { fontSize: 10, fontWeight: '700' },
  deleteButton: { width: 36, height: 36, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  statusCard: { borderRadius: 17, padding: 12, flexDirection: 'row', alignItems: 'flex-start', gap: 9 },
  statusText: { flex: 1, fontSize: 11, lineHeight: 16 },
  footnote: { fontSize: 10, lineHeight: 16, textAlign: 'center', marginTop: 2 },
});
