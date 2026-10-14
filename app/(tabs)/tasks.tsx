import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { DateField } from '@/components/DateField';
import { useAppTheme } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { isValidISODate } from '@/lib/date';

type Priority = 'low' | 'normal' | 'high';
type Task = { id: string; title: string; completed: number; created_at: string; priority: Priority; due_date: string | null };

export default function TasksScreen() {
  const theme = useAppTheme();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [priority, setPriority] = useState<Priority>('normal');
  const load = useCallback(async () => {
    const db = await getDatabase();
    setTasks(await db.getAllAsync<Task>('SELECT * FROM tasks ORDER BY completed ASC, CASE priority WHEN \'high\' THEN 0 WHEN \'normal\' THEN 1 ELSE 2 END, CASE WHEN due_date IS NULL THEN 1 ELSE 0 END, due_date ASC, created_at DESC'));
  }, []);
  useEffect(() => { void load(); }, [load]);

  const add = async () => {
    const clean = title.trim();
    if (!clean) { showThemedDialog('Add a task', 'Type a task name first, then tap the plus button.'); return; }
    if (dueDate && !isValidISODate(dueDate)) {
      showThemedDialog('Check the due date', 'Choose a valid calendar date, or clear the optional due date.');
      return;
    }
    try {
      const db = await getDatabase();
      await db.runAsync('INSERT INTO tasks (id, title, completed, created_at, priority, due_date) VALUES (?, ?, 0, ?, ?, ?)', createLocalId('task'), clean, new Date().toISOString(), priority, dueDate.trim() || null);
      setTitle(''); setDueDate(''); setPriority('normal');
      await load();
    } catch (error) {
      showThemedDialog('Task not saved', error instanceof Error ? error.message : 'Go Back could not save this task. Please try again.');
    }
  };
  const toggle = async (task: Task) => {
    const db = await getDatabase();
    await db.runAsync('UPDATE tasks SET completed = ? WHERE id = ?', task.completed ? 0 : 1, task.id);
    await load();
  };
  const remove = (task: Task) => showThemedDialog('Delete task?', task.title, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM tasks WHERE id = ?', task.id); await load(); } },
  ]);
  const priorityColor = (value: Priority) => value === 'high' ? '#D85D52' : value === 'low' ? '#64A9FF' : theme.muted;

  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>STAY ON TRACK</Text>
    <Text style={[styles.title, { color: theme.text }]}>Your tasks</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>{tasks.filter(t => t.completed).length} of {tasks.length} completed · saved on this device</Text>
    <View style={[styles.form, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <View style={styles.addRow}>
        <TextInput value={title} onChangeText={setTitle} placeholder="What needs to get done?" placeholderTextColor={theme.muted} onSubmitEditing={() => void add()} returnKeyType="done" style={[styles.input, { color: theme.text }]} />
        <Pressable onPress={() => void add()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={23} color={theme.background} /></Pressable>
      </View>
      <DateField value={dueDate} onChange={setDueDate} placeholder="Due date (optional)" optional />
      <View style={styles.priorities}><Text style={[styles.priorityLabel, { color: theme.muted }]}>PRIORITY</Text>{(['low', 'normal', 'high'] as Priority[]).map(value => <Pressable key={value} onPress={() => setPriority(value)} style={[styles.priorityOption, { borderColor: priority === value ? priorityColor(value) : theme.border, backgroundColor: priority === value ? theme.surfaceAlt : 'transparent' }]}><View style={[styles.priorityDot, { backgroundColor: priorityColor(value) }]} /><Text style={[styles.priorityText, { color: theme.text }]}>{value[0].toUpperCase() + value.slice(1)}</Text></Pressable>)}</View>
    </View>
    {tasks.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="leaf-outline" size={30} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>A clear mind starts here.</Text><Text style={[styles.sub, { color: theme.muted, textAlign: 'center' }]}>Add one small task and build momentum.</Text></View> :
      <View style={{ gap: 9 }}>{tasks.map(task => <View key={task.id} style={[styles.task, { backgroundColor: theme.surface, borderColor: theme.border }]}><Pressable onPress={() => void toggle(task)} style={[styles.check, { borderColor: task.completed ? theme.primary : theme.muted, backgroundColor: task.completed ? theme.primary : 'transparent' }]}>{task.completed ? <Ionicons name="checkmark" size={16} color={theme.background} /> : null}</Pressable><Pressable onPress={() => void toggle(task)} style={{ flex: 1, gap: 5 }}><Text style={[styles.taskText, { color: task.completed ? theme.muted : theme.text, textDecorationLine: task.completed ? 'line-through' : 'none' }]}>{task.title}</Text><View style={styles.meta}><Text style={[styles.metaText, { color: priorityColor(task.priority) }]}>{task.priority.toUpperCase()} PRIORITY</Text>{task.due_date && <Text style={[styles.metaText, { color: task.due_date < new Date().toISOString().slice(0, 10) && !task.completed ? '#D85D52' : theme.muted }]}>DUE {task.due_date}</Text>}</View></Pressable><Pressable onPress={() => remove(task)} hitSlop={10}><Ionicons name="ellipsis-horizontal" size={19} color={theme.muted} /></Pressable></View>)}</View>}
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 2, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 12, marginTop: -12 },
  form: { borderWidth: 1, borderRadius: 18, padding: 9, gap: 7 },
  addRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 12, fontSize: 14 },
  dueInput: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, fontSize: 12 },
  addButton: { width: 43, height: 43, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  priorities: { flexDirection: 'row', alignItems: 'center', gap: 7, flexWrap: 'wrap', paddingHorizontal: 3, paddingVertical: 3 },
  priorityLabel: { fontSize: 9, letterSpacing: 1, fontWeight: '800', marginRight: 2 },
  priorityOption: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 9, paddingVertical: 7, flexDirection: 'row', alignItems: 'center', gap: 5 },
  priorityDot: { width: 6, height: 6, borderRadius: 3 }, priorityText: { fontSize: 11, fontWeight: '700' },
  empty: { alignItems: 'center', padding: 28, borderRadius: 22, borderWidth: 1, gap: 12, marginTop: 12 },
  emptyTitle: { fontSize: 17, fontWeight: '800' },
  task: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 15, borderRadius: 17, borderWidth: 1 },
  check: { width: 24, height: 24, borderWidth: 1.5, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  taskText: { fontSize: 14, fontWeight: '600' }, meta: { flexDirection: 'row', gap: 10, flexWrap: 'wrap' }, metaText: { fontSize: 9, letterSpacing: 0.5, fontWeight: '800' },
});