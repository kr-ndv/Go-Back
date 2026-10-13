import { useEffect, useRef, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { useAppTheme } from '@/state/theme';

const PRESETS = [15, 25, 45, 60];
export default function FocusScreen() {
  const theme = useAppTheme();
  const [minutes, setMinutes] = useState(25);
  const [remaining, setRemaining] = useState(25 * 60);
  const [running, setRunning] = useState(false);
  const startedAt = useRef<string | null>(null);
  const remainingRef = useRef(25 * 60);
  const minutesRef = useRef(25);
  useEffect(() => { remainingRef.current = remaining; }, [remaining]);
  useEffect(() => { minutesRef.current = minutes; }, [minutes]);

  useEffect(() => {
    if (!running) return;
    const interval = setInterval(() => setRemaining(current => {
      if (current <= 1) { clearInterval(interval); remainingRef.current = 0; setRunning(false); void finish(true); return 0; }
      remainingRef.current = current - 1;
      return current - 1;
    }), 1000);
    return () => clearInterval(interval);
  }, [running]);

  const finish = async (completed: boolean) => {
    const db = await getDatabase();
    await db.runAsync('INSERT INTO focus_sessions (id, started_at, duration_seconds, completed) VALUES (?, ?, ?, ?)', createLocalId('focus'), startedAt.current ?? new Date().toISOString(), initialSeconds - remaining, completed ? 1 : 0);
    if (completed) Alert.alert('Session complete', 'You showed up for yourself. Take a short break.');
  };
  const choose = (value: number) => { setMinutes(value); setRemaining(value * 60); remainingRef.current = value * 60; setRunning(false); };
  const close = () => { if (running) { Alert.alert('End focus session?', 'Your current session will stop.', [{ text: 'Keep focusing', style: 'cancel' }, { text: 'End session', style: 'destructive', onPress: () => { setRunning(false); void finish(false).then(() => router.back()); } }]); } else router.back(); };
  const start = () => { if (!running) { if (remaining === 0) { setRemaining(minutes * 60); remainingRef.current = minutes * 60; } startedAt.current = new Date().toISOString(); setRunning(true); } };
  const format = (s: number) => `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`;

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.top}><Pressable onPress={close} style={[styles.close, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="close" size={22} color={theme.text} /></Pressable><Text style={[styles.topLabel, { color: theme.muted }]}>FOCUS SESSION</Text><View style={{ width: 42 }} /></View>
    <View style={styles.main}><View style={[styles.ring, { borderColor: theme.primary }]}><Text style={[styles.status, { color: theme.muted }]}>{running ? 'STAY IN THE MOMENT' : 'ONE THING AT A TIME'}</Text><Text style={[styles.time, { color: theme.text }]}>{format(remaining)}</Text><Text style={[styles.mins, { color: theme.muted }]}>{running ? 'YOU’RE DOING GREAT' : 'MINUTES OF INTENTION'}</Text></View>
      {!running ? <View style={styles.presets}>{PRESETS.map(p => <Pressable key={p} onPress={() => choose(p)} style={[styles.preset, { backgroundColor: minutes === p ? theme.primary : theme.surface, borderColor: minutes === p ? theme.primary : theme.border }]}><Text style={{ color: minutes === p ? theme.background : theme.text, fontWeight: '800' }}>{p}m</Text></Pressable>)}</View> : <Text style={[styles.runningNote, { color: theme.muted }]}>Keep this screen open to keep the timer active. Background-safe timing and persistent notifications are planned for the next stage.</Text>}
      <Pressable onPress={() => running ? setRunning(false) : start()} style={[styles.start, { backgroundColor: theme.primary }]}><Ionicons name={running ? 'pause' : 'play'} size={19} color={theme.background} /><Text style={[styles.startText, { color: theme.background }]}>{running ? 'Pause session' : 'Begin focusing'}</Text></Pressable>
      {running && <Pressable onPress={() => { setRunning(false); void finish(false); setRemaining(minutes * 60); remainingRef.current = minutes * 60; } }><Text style={[styles.endText, { color: theme.muted }]}>End session</Text></Pressable>}
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