import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { router } from 'expo-router';
import { Modal, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
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
  const [syllabusSubjects, setSyllabusSubjects] = useState<string[]>([]);
  const [subjectPickerVisible, setSubjectPickerVisible] = useState(false);
  const [customSubject, setCustomSubject] = useState('');
  const subjectOptions = Array.from(new Set([...syllabusSubjects, ...exams.map(exam => exam.title)])).sort((a, b) => a.localeCompare(b));
  const load = useCallback(async () => {
    try {
      const db = await getDatabase();
      const [examRows, subjectRows] = await Promise.all([
        db.getAllAsync<Exam>('SELECT * FROM exams ORDER BY exam_date ASC'),
        db.getAllAsync<{ name: string }>('SELECT name FROM subjects ORDER BY name COLLATE NOCASE'),
      ]);
      setExams(examRows);
      setSyllabusSubjects(subjectRows.map(subject => subject.name));
    } catch (error) {
      showThemedDialog('Exam planner could not load', error instanceof Error ? error.message : 'Go Back could not read your timetable.');
    }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const add = async () => {
    const cleanTitle = title.trim();
    if (!cleanTitle) { showThemedDialog('Choose an exam subject', 'Type a subject name or select one from your syllabus list.'); return; }
    if (!isValidISODate(date)) { showThemedDialog('Choose the exam date', 'Select a date from the calendar before adding the exam.'); return; }
    try {
      const db = await getDatabase();
      await db.runAsync('INSERT INTO exams (id, title, exam_date, details) VALUES (?, ?, ?, ?)', createLocalId('exam'), cleanTitle, date, details.trim());
      setTitle(''); setDate(''); setDetails('');
      await load();
      showThemedDialog('Exam added', cleanTitle + ' is now in your timetable.');
    } catch (error) {
      showThemedDialog('Exam not saved', error instanceof Error ? error.message : 'Go Back could not save this exam. Please try again.');
    }
  };
  const applyCustomSubject = () => {
    const clean = customSubject.trim();
    if (!clean) return;
    setTitle(clean);
    setCustomSubject('');
    setSubjectPickerVisible(false);
  };
  const remove = (exam: Exam) => showThemedDialog('Remove exam?', `Remove ${exam.title} from your timetable?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Remove', style: 'destructive', onPress: async () => {
      try { const db = await getDatabase(); await db.runAsync('DELETE FROM exams WHERE id = ?', exam.id); await load(); }
      catch (error) { showThemedDialog('Exam not removed', error instanceof Error ? error.message : 'Go Back could not remove this exam.'); }
    } },
  ]);
  const showExamOptions = (exam: Exam) => showThemedDialog(exam.title, 'Manage this exam entry.', [
    { text: 'Remove exam', style: 'destructive', onPress: () => remove(exam) },
    { text: 'Cancel', style: 'cancel' },
  ]);
  const upcoming = exams.filter(e => daysUntil(e.exam_date) >= 0);
  const next = upcoming[0];
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Exam planner</Text><View style={{ width: 40 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>KEEP THE DATE IN SIGHT</Text>
      {next ? <View style={[styles.hero, { backgroundColor: theme.primary }]}><Text style={[styles.heroLabel, { color: theme.onPrimary }]}>UP NEXT</Text><Text style={[styles.heroTitle, { color: theme.onPrimary }]}>{next.title}</Text><View style={styles.countRow}><Text style={[styles.count, { color: theme.onPrimary }]}>{daysUntil(next.exam_date)}</Text><Text style={[styles.days, { color: theme.onPrimary }]}>DAYS TO GO</Text></View><Text style={[styles.heroDate, { color: theme.onPrimary }]}>{dateLabel(next.exam_date)}</Text></View> : <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="calendar-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Your next milestone starts here.</Text><Text style={[styles.hint, { color: theme.muted }]}>Add your exams to see countdowns and keep the timetable in one place.</Text></View>}
      <Text style={[styles.section, { color: theme.text }]}>Add an exam</Text>
      <View style={[styles.form, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <Pressable accessibilityRole="button" accessibilityLabel="Choose exam subject" onPress={() => { setCustomSubject(title); setSubjectPickerVisible(true); }} style={[styles.subjectField, { backgroundColor: theme.surfaceAlt, borderColor: title ? theme.primary : theme.border }]}>
          <Ionicons name="book-outline" size={18} color={theme.primary} />
          <Text numberOfLines={1} style={[styles.subjectFieldText, { color: title ? theme.text : theme.muted }]}>{title || 'Select subject'}</Text>
          <Ionicons name="chevron-down" size={18} color={theme.muted} />
        </Pressable>
        <DateField value={date} onChange={setDate} placeholder="Choose exam date" />
        <TextInput value={details} onChangeText={setDetails} placeholder="Time / room / notes (optional)" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text }]} />
        <Pressable onPress={() => void add()} style={[styles.add, { backgroundColor: theme.primary }]}><Ionicons name="add" size={19} color={theme.background} /><Text style={[styles.addText, { color: theme.onPrimary }]}>Add to timetable</Text></Pressable>
      </View>
      <Text style={[styles.section, { color: theme.text }]}>Your timetable</Text>
      {exams.length === 0 ? <Text style={[styles.hint, { color: theme.muted }]}>No exams added yet.</Text> : exams.map(exam => <View key={exam.id} style={[styles.exam, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.dateBox, { backgroundColor: theme.surfaceAlt }]}><Text style={[styles.dateDay, { color: theme.primary }]}>{new Date(exam.exam_date + 'T12:00:00').getDate()}</Text><Text style={[styles.dateMonth, { color: theme.muted }]}>{new Date(exam.exam_date + 'T12:00:00').toLocaleDateString(undefined, { month: 'short' }).toUpperCase()}</Text></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.examTitle, { color: theme.text }]}>{exam.title}</Text><Text style={[styles.hint, { color: theme.muted }]}>{dateLabel(exam.exam_date)}{exam.details ? ' · ' + exam.details : ''}</Text></View><View style={{ alignItems: 'flex-end', gap: 3 }}><Text style={[styles.daysLeft, { color: theme.primary }]}>{daysUntil(exam.exam_date) < 0 ? 'Done' : daysUntil(exam.exam_date) + 'd'}</Text><Pressable accessibilityRole="button" accessibilityLabel={`Options for ${exam.title}`} onPress={() => showExamOptions(exam)} hitSlop={8} style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}><Ionicons name="ellipsis-horizontal" size={19} color={theme.muted} /></Pressable></View></View>)}
    </ScrollView>
    <Modal transparent visible={subjectPickerVisible} animationType="fade" statusBarTranslucent onRequestClose={() => setSubjectPickerVisible(false)}>
      <View style={styles.subjectOverlay}>
        <Pressable accessibilityRole="button" accessibilityLabel="Dismiss subject picker" style={StyleSheet.absoluteFill} onPress={() => setSubjectPickerVisible(false)} />
        <View style={[styles.subjectModal, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.subjectModalHeader}>
            <View style={[styles.subjectModalGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="book-outline" size={22} color={theme.primary} /></View>
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={[styles.subjectModalTitle, { color: theme.text }]}>Choose a subject</Text>
              <Text style={[styles.hint, { color: theme.muted }]}>Select one or enter a new subject below.</Text>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel="Close subject picker" onPress={() => setSubjectPickerVisible(false)} hitSlop={8} style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' }}><Ionicons name="close" size={22} color={theme.muted} /></Pressable>
          </View>
          <ScrollView style={styles.subjectList} keyboardShouldPersistTaps="handled">
            {subjectOptions.map(subject => <Pressable key={subject} onPress={() => { setTitle(subject); setCustomSubject(''); setSubjectPickerVisible(false); }} style={[styles.subjectOption, { backgroundColor: title === subject ? theme.surfaceAlt : 'transparent', borderColor: title === subject ? theme.primary : theme.border }]}>
              <Ionicons name={title === subject ? 'radio-button-on' : 'radio-button-off'} size={19} color={title === subject ? theme.primary : theme.muted} />
              <Text style={[styles.subjectOptionText, { color: theme.text }]}>{subject}</Text>
              {title === subject && <Ionicons name="checkmark-circle" size={18} color={theme.primary} />}
            </Pressable>)}
            {subjectOptions.length === 0 && <Text style={[styles.hint, { color: theme.muted, paddingVertical: 8 }]}>No saved subjects yet. Enter any subject name below.</Text>}
          </ScrollView>
          <Text style={[styles.pickerLabel, { color: theme.muted }]}>OR ENTER A NEW SUBJECT</Text>
          <TextInput value={customSubject} onChangeText={setCustomSubject} placeholder="e.g. History, Geography, English" placeholderTextColor={theme.muted} returnKeyType="done" onSubmitEditing={applyCustomSubject} style={[styles.customSubjectInput, { backgroundColor: theme.surfaceAlt, borderColor: theme.border, color: theme.text }]} />
          <View style={styles.subjectModalActions}>
            <Pressable onPress={() => setSubjectPickerVisible(false)} style={[styles.subjectModalButton, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}><Text style={[styles.addText, { color: theme.text }]}>Cancel</Text></Pressable>
            <Pressable disabled={!customSubject.trim()} onPress={applyCustomSubject} style={[styles.subjectModalButton, { backgroundColor: theme.primary, borderColor: theme.primary, opacity: customSubject.trim() ? 1 : 0.45 }]}><Text style={[styles.addText, { color: theme.onPrimary }]}>Use subject</Text></Pressable>
          </View>
        </View>
      </View>
    </Modal>
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
  form: { borderWidth: 1, borderRadius: 20, padding: 12, gap: 9 }, input: { paddingHorizontal: 12, paddingVertical: 11, fontSize: 13 },
  subjectField: { minHeight: 46, borderWidth: 1, borderRadius: 13, flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 12 },
  subjectFieldText: { flex: 1, fontSize: 13, fontWeight: '700' },
  subjectOverlay: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 22, backgroundColor: 'rgba(0,0,0,0.62)' },
  subjectModal: { width: '100%', maxWidth: 390, maxHeight: '85%', borderWidth: 1, borderRadius: 29, padding: 18, gap: 11, elevation: 24, shadowColor: '#000', shadowOpacity: 0.25, shadowRadius: 22, shadowOffset: { width: 0, height: 10 } },
  subjectModalHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  subjectModalGlyph: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  subjectModalTitle: { fontSize: 18, fontWeight: '900' },
  subjectList: { flexGrow: 0, maxHeight: 230 },
  subjectOption: { minHeight: 45, borderWidth: 1, borderRadius: 13, paddingHorizontal: 11, marginBottom: 7, flexDirection: 'row', alignItems: 'center', gap: 9 },
  subjectOptionText: { flex: 1, fontSize: 12, fontWeight: '700' },
  pickerLabel: { fontSize: 9, fontWeight: '800', letterSpacing: 1.1 },
  customSubjectInput: { minHeight: 46, borderWidth: 1, borderRadius: 13, paddingHorizontal: 12, fontSize: 13 },
  subjectModalActions: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8 },
  subjectModalButton: { minHeight: 42, borderRadius: 13, borderWidth: 1, paddingHorizontal: 14, alignItems: 'center', justifyContent: 'center' },
  add: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 14, paddingVertical: 13 }, addText: { fontSize: 13, fontWeight: '800' },
  empty: { borderWidth: 1, borderRadius: 26, padding: 24, alignItems: 'center', gap: 10 }, emptyTitle: { fontSize: 16, fontWeight: '800' },
  hint: { fontSize: 11, lineHeight: 17 }, exam: { borderWidth: 1, borderRadius: 18, padding: 12, flexDirection: 'row', alignItems: 'center', gap: 12 },
  dateBox: { width: 48, height: 54, borderRadius: 13, alignItems: 'center', justifyContent: 'center' }, dateDay: { fontSize: 21, fontWeight: '900' }, dateMonth: { fontSize: 9, fontWeight: '800' },
  examTitle: { fontSize: 13, fontWeight: '800' }, daysLeft: { fontSize: 13, fontWeight: '900' }
});