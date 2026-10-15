import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { useAppTheme } from '@/state/theme';

type Habit = { id: string; title: string; created_at: string };
type HabitRow = Habit & { done_today: number; streak: number };
const localDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;

export default function HabitsScreen() {
  const theme = useAppTheme();
  const [habits, setHabits] = useState<HabitRow[]>([]);
  const [title, setTitle] = useState('');
  const [today] = useState(() => localDate(new Date()));
  const load = useCallback(async () => {
    const db = await getDatabase();
    const rows = await db.getAllAsync<Habit>('SELECT id, title, created_at FROM habits WHERE archived = 0 ORDER BY created_at DESC');
    const result: HabitRow[] = await Promise.all(rows.map(async habit => {
      const logs = await db.getAllAsync<{ log_date: string; completed: number }>('SELECT log_date, completed FROM habit_logs WHERE habit_id = ? AND completed = 1 ORDER BY log_date DESC', habit.id);
      const doneToday = logs.some(log => log.log_date === today);
      let cursor = new Date(today + 'T12:00:00');
      if (!doneToday) cursor.setDate(cursor.getDate() - 1);
      let streak = 0;
      const dates = new Set(logs.map(log => log.log_date));
      while (dates.has(localDate(cursor))) { streak++; cursor.setDate(cursor.getDate() - 1); }
      return { ...habit, done_today: doneToday ? 1 : 0, streak };
    }));
    setHabits(result);
  }, [today]);
  useEffect(() => { void load(); }, [load]);
  const add = async () => {
    const clean = title.trim();
    if (!clean) return;
    const db = await getDatabase();
    await db.runAsync('INSERT INTO habits (id, title, created_at, archived) VALUES (?, ?, ?, 0)', createLocalId('habit'), clean, new Date().toISOString());
    setTitle(''); await load();
  };
  const toggle = async (habit: HabitRow) => {
    const db = await getDatabase();
    if (habit.done_today) await db.runAsync('DELETE FROM habit_logs WHERE habit_id = ? AND log_date = ?', habit.id, today);
    else await db.runAsync('INSERT INTO habit_logs (id, habit_id, log_date, completed) VALUES (?, ?, ?, 1) ON CONFLICT(habit_id, log_date) DO UPDATE SET completed = 1', createLocalId('habit-log'), habit.id, today);
    await load();
  };
  const remove = (habit: HabitRow) => showThemedDialog('Remove habit?', habit.title, [{ text: 'Cancel', style: 'cancel' }, { text: 'Remove', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM habits WHERE id = ?', habit.id); await load(); } }]);
  const completed = habits.filter(h => h.done_today).length;
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Daily habits</Text><View style={{ width: 40 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>CONSISTENCY OVER INTENSITY</Text>
      <View style={[styles.summary, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.summaryIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="leaf" size={24} color={theme.primary} /></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.summaryTitle, { color: theme.text }]}>Show up for yourself.</Text><Text style={[styles.hint, { color: theme.muted }]}>{completed} of {habits.length} habits completed today</Text></View><Text style={[styles.percent, { color: theme.primary }]}>{habits.length ? Math.round(completed / habits.length * 100) : 0}%</Text></View>
      <View style={[styles.addRow, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><TextInput value={title} onChangeText={setTitle} placeholder="A habit to build..." placeholderTextColor={theme.muted} onSubmitEditing={() => void add()} returnKeyType="done" style={[styles.input, { color: theme.text }]} /><Pressable onPress={() => void add()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={22} color={theme.background} /></Pressable></View>
      {habits.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="sunny-outline" size={30} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Start small. Keep showing up.</Text><Text style={[styles.hint, { color: theme.muted, textAlign: 'center' }]}>Add a daily habit like reading, walking, or revising a chapter.</Text></View> : habits.map(habit => <View key={habit.id} style={[styles.habit, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Pressable onPress={() => void toggle(habit)} style={[styles.check, { borderColor: habit.done_today ? theme.primary : theme.muted, backgroundColor: habit.done_today ? theme.primary : 'transparent' }]}>{habit.done_today ? <Ionicons name="checkmark" size={17} color={theme.background} /> : null}</Pressable><Pressable onPress={() => void toggle(habit)} style={{ flex: 1, gap: 4 }}><Text style={[styles.habitTitle, { color: habit.done_today ? theme.muted : theme.text, textDecorationLine: habit.done_today ? 'line-through' : 'none' }]}>{habit.title}</Text><Text style={[styles.hint, { color: theme.muted }]}>{habit.streak} day streak {habit.streak === 1 ? '🔥' : '✨'}</Text></Pressable><Pressable onPress={() => remove(habit)} hitSlop={10}><Ionicons name="ellipsis-horizontal" size={19} color={theme.muted} /></Pressable></View>)}
      <Text style={[styles.hint, { color: theme.muted }]}>Streaks count consecutive calendar days with a completed check-in. All data stays on your device.</Text>
    </ScrollView>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 17, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 36, gap: 13 }, eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  summary: { borderWidth: 1, borderRadius: 26, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  summaryIcon: { width: 44, height: 44, borderRadius: 15, alignItems: 'center', justifyContent: 'center' }, summaryTitle: { fontSize: 15, fontWeight: '800' }, hint: { fontSize: 11, lineHeight: 17 },
  percent: { fontSize: 22, fontWeight: '900' }, addRow: { borderWidth: 1, borderRadius: 17, padding: 6, flexDirection: 'row', alignItems: 'center' },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 11, fontSize: 13 }, addButton: { width: 42, height: 42, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  empty: { borderWidth: 1, borderRadius: 26, padding: 24, alignItems: 'center', gap: 10 }, emptyTitle: { fontSize: 16, fontWeight: '800' },
  habit: { borderWidth: 1, borderRadius: 22, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  check: { width: 27, height: 27, borderRadius: 9, borderWidth: 1.5, alignItems: 'center', justifyContent: 'center' }, habitTitle: { fontSize: 13, fontWeight: '800' }
});