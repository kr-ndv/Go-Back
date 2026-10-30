import { useCallback, useState } from 'react';
import { Platform, StyleSheet, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Notifications from 'expo-notifications';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getSetting, setSetting } from '@/lib/database';
import { FOCUS_ALARM_OPTIONS, FOCUS_ALARM_SETTING_KEY, normalizeFocusAlarmSound, type FocusAlarmSound } from '@/lib/focusAlarm';
import { ensureFocusAlertChannel } from '@/lib/focusNotifications';

export default function AlarmSoundScreen() {
  const theme = useAppTheme();
  const [selected, setSelected] = useState<FocusAlarmSound>('default');
  const [previewing, setPreviewing] = useState(false);
  const [status, setStatus] = useState('');

  useFocusEffect(useCallback(() => {
    let active = true;
    void (async () => {
      const saved = normalizeFocusAlarmSound(await getSetting<unknown>(FOCUS_ALARM_SETTING_KEY, 'default'));
      if (active) setSelected(saved);
    })();
    return () => { active = false; };
  }, []));

  const choose = async (value: FocusAlarmSound) => {
    setSelected(value);
    setStatus('');
    try { await setSetting(FOCUS_ALARM_SETTING_KEY, value); }
    catch { setStatus('The alarm choice could not be saved. Please try again.'); }
  };

  const preview = async () => {
    if (previewing) return;
    setPreviewing(true); setStatus('');
    try {
      let permission = await Notifications.getPermissionsAsync();
      if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
      if (!permission.granted) { setStatus('Allow notifications to preview timer alarms.'); return; }
      const channelId = await ensureFocusAlertChannel(selected);
      await Notifications.scheduleNotificationAsync({
        content: { title: 'Focus alarm preview', body: 'This is how your selected timer alarm will sound.', sound: selected, priority: Notifications.AndroidNotificationPriority.MAX },
        trigger: Platform.OS === 'android' && channelId ? ({ channelId } as Notifications.NotificationTriggerInput) : null,
      });
      setStatus('Preview sent. Android notification-channel or Do Not Disturb settings can still silence it.');
    } catch { setStatus('Could not play the preview on this device.'); }
    finally { setPreviewing(false); }
  };

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel="Go back" onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Timer alarm</Text><View style={{ width: 44 }} /></View>
    <View style={styles.content}>
      <Text style={[styles.eyebrow, { color: theme.muted }]}>FOCUS NOTIFICATIONS</Text>
      <Text style={[styles.title, { color: theme.text }]}>Choose your finish sound.</Text>
      <Text style={[styles.subtitle, { color: theme.muted }]}>This sound is used when a timer or Pomodoro phase finishes. The live timer notification stays silent.</Text>
      <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        {FOCUS_ALARM_OPTIONS.map((option, index) => {
          const active = selected === option.value;
          return <Pressable key={option.value} accessibilityRole="radio" accessibilityState={{ selected: active }} onPress={() => void choose(option.value)} style={[styles.row, { borderBottomWidth: index === FOCUS_ALARM_OPTIONS.length - 1 ? 0 : StyleSheet.hairlineWidth, borderBottomColor: theme.border }]}>
            <View style={[styles.icon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name={active ? 'notifications' : 'notifications-outline'} size={19} color={theme.primary} /></View>
            <View style={{ flex: 1, gap: 3 }}><Text style={[styles.rowTitle, { color: theme.text }]}>{option.label}</Text><Text style={[styles.hint, { color: theme.muted }]}>{option.description}</Text></View>
            <Ionicons name={active ? 'radio-button-on' : 'radio-button-off'} size={21} color={active ? theme.primary : theme.muted} />
          </Pressable>;
        })}
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Preview selected focus alarm" disabled={previewing} onPress={() => void preview()} style={[styles.preview, { backgroundColor: theme.primary, opacity: previewing ? 0.65 : 1 }]}><Ionicons name="volume-high-outline" size={18} color={theme.onPrimary} /><Text style={{ color: theme.onPrimary, fontWeight: '900' }}>{previewing ? 'Sending preview…' : 'Preview alarm'}</Text></Pressable>
      {!!status && <Text style={[styles.status, { color: status.startsWith('Could') || status.startsWith('The alarm') ? theme.danger : theme.muted }]}>{status}</Text>}
      <Text style={[styles.footnote, { color: theme.muted }]}>Custom sounds are bundled in the native app, so install a fresh Android build after this change. Android may preserve per-channel sound choices you change in system settings.</Text>
    </View>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 }, back: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 16, fontWeight: '800' },
  content: { padding: 20, gap: 13 }, eyebrow: { fontSize: 10, letterSpacing: 1.7, fontWeight: '800' }, title: { fontSize: 28, fontWeight: '900', letterSpacing: -1 }, subtitle: { fontSize: 12, lineHeight: 18, marginTop: -7 }, card: { borderWidth: 1, borderRadius: 24, paddingHorizontal: 14 }, row: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 11, paddingVertical: 12 }, icon: { width: 40, height: 40, borderRadius: 13, alignItems: 'center', justifyContent: 'center' }, rowTitle: { fontSize: 13, fontWeight: '800' }, hint: { fontSize: 11, lineHeight: 16 }, preview: { minHeight: 50, borderRadius: 16, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8 }, status: { fontSize: 11, lineHeight: 17 }, footnote: { fontSize: 10, lineHeight: 16 },
});
