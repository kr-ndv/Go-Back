import { useEffect, useRef, useState } from 'react';
import { Alert, AppState, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Notifications from 'expo-notifications';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase, getSetting, setSetting } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { useAppTheme } from '@/state/theme';

const PRESETS = [15, 25, 45, 60];
type ActiveFocusSession = { minutes: number; remaining: number; endAt: number | null; startedAt: string };

export default function FocusScreen() {
  const theme = useAppTheme();
  const [minutes, setMinutes] = useState(25);
  const [remaining, setRemaining] = useState(25 * 60);
  const [running, setRunning] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const startedAt = useRef<string | null>(null);
  const remainingRef = useRef(25 * 60);
  const minutesRef = useRef(25);
  const endAtRef = useRef<number | null>(null);
  const notificationIdRef = useRef<string | null>(null);
  const finishingRef = useRef(false);

  useEffect(() => { remainingRef.current = remaining; }, [remaining]);
  useEffect(() => { minutesRef.current = minutes; }, [minutes]);

  const cancelNotification = async () => {
    if (!notificationIdRef.current) return;
    const id = notificationIdRef.current;
    notificationIdRef.current = null;
    try { await Notifications.cancelScheduledNotificationAsync(id); } catch { /* notification may already have fired */ }
  };

  const finish = async (completed: boolean) => {
    if (finishingRef.current) return;
    finishingRef.current = true;
    const sessionStart = startedAt.current ?? new Date().toISOString();
    const elapsed = completed ? minutesRef.current * 60 : Math.max(0, minutesRef.current * 60 - remainingRef.current);
    startedAt.current = null;
    endAtRef.current = null;
    setRunning(false);
    await cancelNotification();
    try {
      const db = await getDatabase();
      if (elapsed > 0) {
        await db.runAsync('INSERT INTO focus_sessions (id, started_at, duration_seconds, completed) VALUES (?, ?, ?, ?)', createLocalId('focus'), sessionStart, elapsed, completed ? 1 : 0);
      }
      await setSetting('focus.active', null);
      if (completed) Alert.alert('Session complete', 'You showed up for yourself. Take a short break.');
    } catch {
      Alert.alert('Could not save session', 'Your focus session ended, but its history could not be saved.');
    } finally {
      finishingRef.current = false;
    }
  };

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const session = await getSetting<ActiveFocusSession | null>('focus.active', null);
        if (!active) return;
        if (session && [15, 25, 45, 60].includes(session.minutes) && Number.isFinite(session.remaining) && typeof session.startedAt === 'string') {
          minutesRef.current = session.minutes;
          setMinutes(session.minutes);
          startedAt.current = session.startedAt;
          if (typeof session.endAt === 'number' && Number.isFinite(session.endAt)) {
            endAtRef.current = session.endAt;
            const secondsLeft = Math.max(0, Math.ceil((session.endAt - Date.now()) / 1000));
            remainingRef.current = secondsLeft;
            setRemaining(secondsLeft);
            setRunning(true);
          } else {
            remainingRef.current = Math.max(0, session.remaining);
            setRemaining(remainingRef.current);
          }
        }
      } catch {
        Alert.alert('Could not restore timer', 'A previous focus session could not be restored.');
      } finally {
        if (active) setRestoring(false);
      }
    })();
    return () => { active = false; };
  }, []);

  const tick = () => {
    if (!endAtRef.current) return;
    const secondsLeft = Math.max(0, Math.ceil((endAtRef.current - Date.now()) / 1000));
    remainingRef.current = secondsLeft;
    setRemaining(secondsLeft);
    if (secondsLeft === 0) void finish(true);
  };

  useEffect(() => {
    if (!running) return;
    const interval = setInterval(tick, 500);
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') tick();
    });
    return () => { clearInterval(interval); subscription.remove(); };
  }, [running]);

  const choose = (value: number) => {
    setMinutes(value);
    minutesRef.current = value;
    setRemaining(value * 60);
    remainingRef.current = value * 60;
    setRunning(false);
    endAtRef.current = null;
    startedAt.current = null;
    void cancelNotification();
    void setSetting('focus.active', null);
  };

  const start = async () => {
    if (running || restoring || finishingRef.current) return;
    const seconds = remainingRef.current > 0 ? remainingRef.current : minutesRef.current * 60;
    if (remainingRef.current <= 0) {
      remainingRef.current = seconds;
      setRemaining(seconds);
    }
    if (!startedAt.current) startedAt.current = new Date().toISOString();
    endAtRef.current = Date.now() + seconds * 1000;
    void setSetting('focus.active', { minutes: minutesRef.current, remaining: seconds, endAt: endAtRef.current, startedAt: startedAt.current });
    setRunning(true);
    try {
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('focus-sessions', {
          name: 'Focus sessions',
          importance: Notifications.AndroidImportance.HIGH,
          vibrationPattern: [0, 250, 250, 250],
          lightColor: '#B8F36B',
        });
      }
      let permission = await Notifications.getPermissionsAsync();
      if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
      if (permission.granted && endAtRef.current) {
        await cancelNotification();
        notificationIdRef.current = await Notifications.scheduleNotificationAsync({
          content: { title: 'Focus session complete', body: 'Nice work. Take a short break before your next session.', ...(Platform.OS === 'android' ? { channelId: 'focus-sessions' } : {}) },
          trigger: { type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL, seconds },
        });
      }
    } catch {
      // The timer remains usable when notifications are unavailable or permission is denied.
    }
  };

  const pause = () => {
    if (endAtRef.current) {
      const secondsLeft = Math.max(0, Math.ceil((endAtRef.current - Date.now()) / 1000));
      remainingRef.current = secondsLeft;
      setRemaining(secondsLeft);
      endAtRef.current = null;
    }
    setRunning(false);
    void cancelNotification();
    if (remainingRef.current === 0) void finish(true);
    else if (startedAt.current) void setSetting('focus.active', { minutes: minutesRef.current, remaining: remainingRef.current, endAt: null, startedAt: startedAt.current });
  };

  const endSession = async (goBack = false) => {
    if (endAtRef.current) {
      remainingRef.current = Math.max(0, Math.ceil((endAtRef.current - Date.now()) / 1000));
      setRemaining(remainingRef.current);
      endAtRef.current = null;
    }
    setRunning(false);
    await cancelNotification();
    await finish(false);
    if (goBack) router.back();
    else {
      const reset = minutesRef.current * 60;
      remainingRef.current = reset;
      setRemaining(reset);
    }
  };

  const close = () => {
    if (!running && !startedAt.current) { router.back(); return; }
    Alert.alert('End focus session?', 'Your current session will stop.', [
      { text: 'Keep focusing', style: 'cancel' },
      { text: 'End session', style: 'destructive', onPress: () => { void endSession(true); } },
    ]);
  };

  const format = (seconds: number) => `${Math.floor(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`;

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.top}><Pressable onPress={close} style={[styles.close, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="close" size={22} color={theme.text} /></Pressable><Text style={[styles.topLabel, { color: theme.muted }]}>FOCUS SESSION</Text><View style={{ width: 42 }} /></View>
    <View style={styles.main}>
      <View style={[styles.ring, { borderColor: theme.primary }]}><Text style={[styles.status, { color: theme.muted }]}>{running ? 'STAY IN THE MOMENT' : 'ONE THING AT A TIME'}</Text><Text style={[styles.time, { color: theme.text }]}>{format(remaining)}</Text><Text style={[styles.mins, { color: theme.muted }]}>{running ? 'YOU’RE DOING GREAT' : 'MINUTES OF INTENTION'}</Text></View>
      {!running ? <View style={styles.presets}>{PRESETS.map(p => <Pressable key={p} onPress={() => choose(p)} style={[styles.preset, { backgroundColor: minutes === p ? theme.primary : theme.surface, borderColor: minutes === p ? theme.primary : theme.border }]}><Text style={{ color: minutes === p ? theme.background : theme.text, fontWeight: '800' }}>{p}m</Text></Pressable>)}</View> : <Text style={[styles.runningNote, { color: theme.muted }]}>The countdown uses elapsed time, so it catches up when you return to the app. A completion notification is scheduled when permission is granted.</Text>}
      <Pressable disabled={restoring} onPress={() => running ? pause() : void start()} style={[styles.start, { backgroundColor: theme.primary }]}><Ionicons name={running ? 'pause' : 'play'} size={19} color={theme.background} /><Text style={[styles.startText, { color: theme.background }]}>{restoring ? 'Restoring session…' : running ? 'Pause session' : 'Begin focusing'}</Text></Pressable>
      {startedAt.current && <Pressable onPress={() => void endSession(false)}><Text style={[styles.endText, { color: theme.muted }]}>End session</Text></Pressable>}
    </View>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  top: { paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  close: { width: 42, height: 42, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  topLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 2 },
  main: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 26, gap: 30 },
  ring: { width: 270, height: 270, borderRadius: 135, borderWidth: 5, alignItems: 'center', justifyContent: 'center', gap: 13 },
  status: { fontSize: 9, fontWeight: '800', letterSpacing: 1.3 },
  time: { fontSize: 58, fontWeight: '800', letterSpacing: -2.5 },
  mins: { fontSize: 9, fontWeight: '700', letterSpacing: 1.2 },
  presets: { flexDirection: 'row', gap: 9 },
  preset: { paddingVertical: 12, paddingHorizontal: 17, borderRadius: 14, borderWidth: 1 },
  start: { width: '100%', paddingVertical: 17, borderRadius: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 },
  startText: { fontSize: 15, fontWeight: '800' },
  runningNote: { fontSize: 12, textAlign: 'center', lineHeight: 18 },
  endText: { fontSize: 13, fontWeight: '700', padding: 8 },
});