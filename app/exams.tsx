import { useCallback, useEffect, useState } from 'react';
import { router } from 'expo-router';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { daysUntilISODate, isValidISODate } from '@/lib/date';
import { useAppTheme } from '@/state/theme';
import { DateField } from '@/components/DateField';

type Exam = { id: string; title: string; exam_date: string; details: string };
const dateLabel = (date: string) => new Date(date + 'T12:00:00').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
const daysUntil = (date: string) => daysUntilISODate(date);

export default function ExamsScreen() {
  const theme = useAppTheme();
  const [exams, setExams] = useState<Exam[]>([]);
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const [details, setDetails] = useState('');
  const load = useCallback(async () => { const db = await getDatabase(); setExams(await db.getAllAsync<Exam>('SELECT * FROM exams ORDER BY exam_date ASC')); }, []);
  useEffect(() => { void load(); }, [load]);
  const add = async () => {
    const cleanTitle = title.trim();
    const validDate = isValidISODate(date);
    if (!cleanTitle || !validDate) { Alert.alert('Check your exam details', 'Add a subject or exam name and a date in YYYY-MM-DD format.'); return; }
    const db = await getDatabase();
    await db.runAsync('INSERT INTO exams (id, title, exam_date, details) VALUES (?, ?, ?, ?)', createLocalId('exam'), cleanTitle, date, details.trim());
    setTitle(''); setDate(''); setDetails(''); await load();
  };
  const remove = (exam: Exam) => Alert.alert('Remove exam?', exam.title, [{ text: 'Cancel', style: 'cancel' }, { text: 'Remove', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM exams WHERE id = ?', exam.id); await load(); } }]);
  const upcoming = exams.filter(e => daysUntil(e.exam_date) >= 0);
  const next = upcoming[0];
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Exam planner</Text><View style={{ width: 40 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>KEEP THE DATE IN SIGHT</Text>
      {next ? <View style={[styles.hero, { backgroundColor: theme.primary }]}><Text style={[styles.heroLabel, { color: theme.background }]}>UP NEXT</Text><Text style={[styles.heroTitle, { color: theme.background }]}>{next.title}</Text><View style={styles.countRow}><Text style={[styles.count, { color: theme.background }]}>{daysUntil(next.exam_date)}</Text><Text style={[styles.days, { color: theme.background }]}>DAYS TO GO</Text></View><Text style={[styles.heroDate, { color: theme.background }]}>{dateLabel(next.exam_date)}</Text></View> : <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="calendar-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Your next milestone starts here.</Text><Text style={[styles.hint, { color: theme.muted }]}>Add your exams to see countdowns and keep the timetable in one place.</Text></View>}
      <Text style={[styles.section, { color: theme.text }]}>Add an exam</Text>
      <View style={[styles.form, { backgroundColor: theme.surface, borderColor: theme.border }]}>
        <TextInput value={title} onChangeText={setTitle} placeholder="Subject or exam name" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text }]} />
        <DateField value={date} onChange={setDate} placeholder="Choose exam date" />
        <TextInput value={details} onChangeText={setDetails} placeholder="Time / room / notes (optional)" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text }]} />
        <Pressable onPress={() => void add()} style={[styles.add, { backgroundColor: theme.primary }]}><Ionicons name="add" size={19} color={theme.background} /><Text style={[styles.addText, { color: theme.background }]}>Add to timetable</Text></Pressable>
      </View>
      <Text style={[styles.section, { color: theme.text }]}>Your timetable</Text>
      {exams.length === 0 ? <Text style={[styles.hint, { color: theme.muted }]}>No exams added yet.</Text> : exams.map(exam => <Pressable key={exam.id} onLongPress={() => remove(exam)} style={[styles.exam, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.dateBox, { backgroundColor: theme.surfaceAlt }]}><Text style={[styles.dateDay, { color: theme.primary }]}>{new Date(exam.exam_date + 'T12:00:00').getDate()}</Text><Text style={[styles.dateMonth, { color: theme.muted }]}>{new Date(exam.exam_date + 'T12:00:00').toLocaleDateString(undefined, { month: 'short' }).toUpperCase()}</Text></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.examTitle, { color: theme.text }]}>{exam.title}</Text><Text style={[styles.hint, { color: theme.muted }]}>{dateLabel(exam.exam_date)}{exam.details ? ' · ' + exam.details : ''}</Text></View><View style={{ alignItems: 'flex-end' }}><Text style={[styles.daysLeft, { color: theme.primary }]}>{daysUntil(exam.exam_date) < 0 ? 'Done' : daysUntil(exam.exam_date) + 'd'}</Text><Text style={[styles.hint, { color: theme.muted }]}>hold to remove</Text></View></Pressable>)}
    </ScrollView>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 17, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 36, gap: 13 }, eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.7 },
  hero: { padding: 22, borderRadius: 25, gap: 8 }, heroLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6, opacity: 0.8 },
  heroTitle: { fontSize: 23, fontWeight: '800' }, countRow: { flexDirection: 'row', alignItems: 'baseline', gap: 10 },
  count: { fontSize: 52, fontWeight: '900', letterSpacing: -2 }, days: { fontSize: 10, fontWeight: '800', letterSpacing: 1.4, opacity: 0.8 },
  heroDate: { fontSize: 12, opacity: 0.8 }, section: { fontSize: 17, fontWeight: '800', marginTop: 7 },
  form: { borderWidth: 1, borderRadius: 20, padding: 12, gap: 7 }, input: { paddingHorizontal: 12, paddingVertical: 11, fontSize: 13 },
  add: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 14, paddingVertical: 13 }, addText: { fontSize: 13, fontWeight: '800' },
  empty: { borderWidth: 1, borderRadius: 22, padding: 24, alignItems: 'center', gap: 10 }, emptyTitle: { fontSize: 16, fontWeight: '800' },
  hint: { fontSize: 11, lineHeight: 17 }, exam: { borderWidth: 1, borderRadius: 18, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  dateBox: { width: 48, height: 54, borderRadius: 13, alignItems: 'center', justifyContent: 'center' }, dateDay: { fontSize: 21, fontWeight: '900' }, dateMonth: { fontSize: 9, fontWeight: '800' },
  examTitle: { fontSize: 13, fontWeight: '800' }, daysLeft: { fontSize: 13, fontWeight: '900' }
});