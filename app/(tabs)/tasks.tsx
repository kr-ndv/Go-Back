import { useCallback, useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';

type Task = { id: string; title: string; completed: number; created_at: string };

export default function TasksScreen() {
  const theme = useAppTheme();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [title, setTitle] = useState('');
  const load = useCallback(async () => {
    const db = await getDatabase();
    setTasks(await db.getAllAsync<Task>('SELECT * FROM tasks ORDER BY completed ASC, created_at DESC'));
  }, []);
  useEffect(() => { void load(); }, [load]);

  const add = async () => {
    const clean = title.trim();
    if (!clean) return;
    const db = await getDatabase();
    await db.runAsync('INSERT INTO tasks (id, title, completed, created_at) VALUES (?, ?, 0, ?)', createLocalId('task'), clean, new Date().toISOString());
    setTitle('');
    await load();
  };
  const toggle = async (task: Task) => {
    const db = await getDatabase();
    await db.runAsync('UPDATE tasks SET completed = ? WHERE id = ?', task.completed ? 0 : 1, task.id);
    await load();
  };
  const remove = (task: Task) => Alert.alert('Delete task?', task.title, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM tasks WHERE id = ?', task.id); await load(); } },
  ]);

  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>STAY ON TRACK</Text>
    <Text style={[styles.title, { color: theme.text }]}>Your tasks</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>{tasks.filter(t => t.completed).length} of {tasks.length} completed · saved on this device</Text>
    <View style={[styles.addRow, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <TextInput value={title} onChangeText={setTitle} placeholder="What needs to get done?" placeholderTextColor={theme.muted} onSubmitEditing={() => void add()} returnKeyType="done" style={[styles.input, { color: theme.text }]} />
      <Pressable onPress={() => void add()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={23} color={theme.background} /></Pressable>
    </View>
    {tasks.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="leaf-outline" size={30} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>A clear mind starts here.</Text><Text style={[styles.sub, { color: theme.muted, textAlign: 'center' }]}>Add one small task and build momentum.</Text></View> :
      <View style={{ gap: 9 }}>{tasks.map(task => <View key={task.id} style={[styles.task, { backgroundColor: theme.surface, borderColor: theme.border }]}><Pressable onPress={() => void toggle(task)} style={[styles.check, { borderColor: task.completed ? theme.primary : theme.muted, backgroundColor: task.completed ? theme.primary : 'transparent' }]}>{task.completed ? <Ionicons name="checkmark" size={16} color={theme.background} /> : null}</Pressable><Pressable onPress={() => void toggle(task)} style={{ flex: 1 }}><Text style={[styles.taskText, { color: task.completed ? theme.muted : theme.text, textDecorationLine: task.completed ? 'line-through' : 'none' }]}>{task.title}</Text></Pressable><Pressable onPress={() => remove(task)} hitSlop={10}><Ionicons name="ellipsis-horizontal" size={19} color={theme.muted} /></Pressable></View>)}</View>}
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 2, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 12, marginTop: -12 },
  addRow: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: 18, padding: 6, gap: 8 },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 12, fontSize: 14 },
  addButton: { width: 43, height: 43, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', padding: 28, borderRadius: 22, borderWidth: 1, gap: 12, marginTop: 12 },
  emptyTitle: { fontSize: 17, fontWeight: '800' },
  task: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 15, borderRadius: 17, borderWidth: 1 },
  check: { width: 24, height: 24, borderWidth: 1.5, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  taskText: { fontSize: 14, fontWeight: '600' },
});