import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { isValidISODate } from '@/lib/date';
import { useAppTheme } from '@/state/theme';
import { DateField } from '@/components/DateField';

type Goal = { id: string; title: string; target_date: string | null; progress: number; created_at: string };

export default function GoalsScreen() {
  const theme = useAppTheme();
  const [goals, setGoals] = useState<Goal[]>([]);
  const [title, setTitle] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const load = useCallback(async () => {
    try { const db = await getDatabase(); setGoals(await db.getAllAsync<Goal>('SELECT * FROM goals ORDER BY progress ASC, created_at DESC')); }
    catch (error) { showThemedDialog('Goals could not load', error instanceof Error ? error.message : 'Go Back could not read your saved goals.'); }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const add = async () => {
    const clean = title.trim();
    if (!clean) { showThemedDialog('Name your goal', 'Add a short description of what you want to achieve.'); return; }
    if (targetDate && !isValidISODate(targetDate)) {
      showThemedDialog('Check the target date', 'Use a real date in YYYY-MM-DD format, or leave it blank.'); return;
    }
    try {
      const db = await getDatabase();
      await db.runAsync('INSERT INTO goals (id, title, target_date, progress, created_at) VALUES (?, ?, ?, 0, ?)', createLocalId('goal'), clean, targetDate.trim() || null, new Date().toISOString());
      setTitle(''); setTargetDate(''); await load();
    } catch (error) {
      showThemedDialog('Goal not saved', error instanceof Error ? error.message : 'Go Back could not save this goal.');
    }
  };
  const updateProgress = async (goal: Goal, delta: number) => {
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE goals SET progress = ? WHERE id = ?', Math.max(0, Math.min(100, goal.progress + delta)), goal.id);
      await load();
    } catch (error) {
      showThemedDialog('Progress not updated', error instanceof Error ? error.message : 'Go Back could not update this goal.');
    }
  };
  const remove = (goal: Goal) => showThemedDialog('Delete goal?', `Delete “${goal.title}”?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => {
      try { const db = await getDatabase(); await db.runAsync('DELETE FROM goals WHERE id = ?', goal.id); await load(); }
      catch (error) { showThemedDialog('Goal not deleted', error instanceof Error ? error.message : 'Go Back could not delete this goal.'); }
    } },
  ]);
  const showGoalOptions = (goal: Goal) => showThemedDialog(goal.title, 'Manage this goal.', [
    { text: 'Delete goal', style: 'destructive', onPress: () => remove(goal) },
    { text: 'Cancel', style: 'cancel' },
  ]);
  const completed = goals.filter(goal => goal.progress === 100).length;
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable accessibilityRole="button" accessibilityLabel="Go back" onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Goals</Text><View style={{ width: 40 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>AIM FOR WHAT MATTERS</Text>
      <View style={[styles.summary, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.glyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="flag-outline" size={22} color={theme.primary} /></View><View style={{ flex: 1, gap: 3 }}><Text style={[styles.summaryTitle, { color: theme.text }]}>Make progress visible.</Text><Text style={[styles.hint, { color: theme.muted }]}>{completed} of {goals.length} goals completed</Text></View><Text style={[styles.percent, { color: theme.primary }]}>{goals.length ? Math.round(goals.reduce((sum, goal) => sum + goal.progress, 0) / goals.length) : 0}%</Text></View>
      <Text style={[styles.section, { color: theme.text }]}>Create a goal</Text>
      <View style={[styles.form, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><TextInput value={title} onChangeText={setTitle} placeholder="What do you want to achieve?" placeholderTextColor={theme.muted} onSubmitEditing={() => void add()} style={[styles.input, { color: theme.text }]} /><DateField value={targetDate} onChange={setTargetDate} placeholder="Target date (optional)" optional /><Pressable accessibilityRole="button" accessibilityLabel="Add goal" onPress={() => void add()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={18} color={theme.onPrimary} /><Text style={[styles.addText, { color: theme.onPrimary }]}>Add goal</Text></Pressable></View>
      <Text style={[styles.section, { color: theme.text }]}>Your goals</Text>
      {goals.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="trending-up-outline" size={29} color={theme.primary} /><Text style={[styles.summaryTitle, { color: theme.text }]}>Progress, not perfection.</Text><Text style={[styles.hint, { color: theme.muted, textAlign: 'center' }]}>Set a meaningful goal, then update progress as you move forward.</Text></View> : goals.map(goal => <View key={goal.id} style={[styles.goal, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={styles.goalHead}><View style={{ flex: 1, gap: 4 }}><Text style={[styles.goalTitle, { color: theme.text }]}>{goal.title}</Text><Text style={[styles.hint, { color: theme.muted }]}>{goal.progress === 100 ? 'Completed' : goal.target_date ? 'Target · ' + goal.target_date : 'No target date'}</Text></View><Pressable accessibilityRole="button" accessibilityLabel={`Options for ${goal.title}`} onPress={() => showGoalOptions(goal)} hitSlop={8} style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}><Ionicons name="ellipsis-horizontal" size={19} color={theme.muted} /></Pressable></View><View style={[styles.track, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.fill, { width: `${goal.progress}%` as `${number}%`, backgroundColor: theme.primary }]} /></View><View style={styles.progressControls}><Text style={[styles.percentSmall, { color: theme.primary }]}>{goal.progress}%</Text><View style={styles.controlRow}><Pressable accessibilityRole="button" accessibilityLabel={`Decrease ${goal.title} progress`} disabled={goal.progress === 0} onPress={() => void updateProgress(goal, -10)} hitSlop={8} style={[styles.control, { borderColor: theme.border, opacity: goal.progress === 0 ? 0.35 : 1 }]}><Ionicons name="remove" size={16} color={theme.text} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel={`Increase ${goal.title} progress`} disabled={goal.progress === 100} onPress={() => void updateProgress(goal, 10)} hitSlop={8} style={[styles.control, { backgroundColor: theme.primary, borderColor: theme.primary, opacity: goal.progress === 100 ? 0.35 : 1 }]}><Ionicons name="add" size={16} color={theme.onPrimary} /></Pressable></View></View></View>)}
      <Text style={[styles.hint, { color: theme.muted }]}>Use the + and − controls to update progress in 10% steps. Open a goal’s options menu to remove it.</Text>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 17, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 36, gap: 13 }, eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.7 },
  summary: { borderWidth: 1, borderRadius: 26, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 12 }, glyph: { width: 43, height: 43, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  summaryTitle: { fontSize: 14, fontWeight: '800' }, hint: { fontSize: 11, lineHeight: 17 }, percent: { fontSize: 22, fontWeight: '900' },
  section: { fontSize: 17, fontWeight: '800', marginTop: 6 }, form: { borderWidth: 1, borderRadius: 19, padding: 12, gap: 7 },
  input: { borderWidth: 1, borderColor: 'transparent', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 11, fontSize: 13 }, addButton: { flexDirection: 'row', gap: 7, justifyContent: 'center', alignItems: 'center', borderRadius: 13, paddingVertical: 13 }, addText: { fontSize: 12, fontWeight: '800' },
  empty: { borderWidth: 1, borderRadius: 26, padding: 24, alignItems: 'center', gap: 10 }, goal: { borderWidth: 1, borderRadius: 19, padding: 15, gap: 12 },
  goalHead: { flexDirection: 'row', alignItems: 'center', gap: 10 }, goalTitle: { fontSize: 14, fontWeight: '800' }, track: { height: 7, borderRadius: 7, overflow: 'hidden' }, fill: { height: 7, borderRadius: 7 },
  progressControls: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, percentSmall: { fontSize: 13, fontWeight: '900' }, controlRow: { flexDirection: 'row', gap: 8 }, control: { width: 32, height: 32, borderWidth: 1, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
});