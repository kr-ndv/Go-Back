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

type Chapter = { id: string; title: string; done: boolean };
type Subject = { id: string; name: string; chapters_json: string; updated_at: string };
const parseChapters = (value: string): Chapter[] => { try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed : []; } catch { return []; } };

export default function SyllabusScreen() {
  const theme = useAppTheme();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [name, setName] = useState('');
  const [chapterDrafts, setChapterDrafts] = useState<Record<string, string>>({});
  const load = useCallback(async () => {
    try {
      const db = await getDatabase();
      setSubjects(await db.getAllAsync<Subject>('SELECT * FROM subjects ORDER BY name COLLATE NOCASE'));
    } catch (error) {
      showThemedDialog('Syllabus could not load', error instanceof Error ? error.message : 'Could not read the saved syllabus.');
    }
  }, []);
  useEffect(() => { void load(); }, [load]);
  const addSubject = async () => {
    const clean = name.trim();
    if (!clean) { showThemedDialog('Add a subject name', 'For example, History or Political Science.'); return; }
    try {
      const db = await getDatabase();
      await db.runAsync('INSERT INTO subjects (id, name, chapters_json, updated_at) VALUES (?, ?, ?, ?)', createLocalId('subject'), clean, '[]', new Date().toISOString());
      setName('');
      await load();
    } catch (error) {
      showThemedDialog('Subject not saved', error instanceof Error ? error.message : 'Go Back could not save this subject.');
    }
  };
  const saveChapters = async (subject: Subject, chapters: Chapter[]) => {
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE subjects SET chapters_json = ?, updated_at = ? WHERE id = ?', JSON.stringify(chapters), new Date().toISOString(), subject.id);
      await load();
      return true;
    } catch (error) {
      showThemedDialog('Chapter changes not saved', error instanceof Error ? error.message : 'Go Back could not update this subject.');
      return false;
    }
  };
  const addChapter = async (subject: Subject) => {
    const title = (chapterDrafts[subject.id] ?? '').trim();
    if (!title) { showThemedDialog('Add a chapter name', 'Type the chapter name in the field at the bottom of the subject card.'); return; }
    const saved = await saveChapters(subject, [...parseChapters(subject.chapters_json), { id: createLocalId('chapter'), title, done: false }]);
    if (saved) setChapterDrafts(current => ({ ...current, [subject.id]: '' }));
  };
  const toggleChapter = async (subject: Subject, chapterId: string) => {
    await saveChapters(subject, parseChapters(subject.chapters_json).map(c => c.id === chapterId ? { ...c, done: !c.done } : c));
  };
  const removeSubject = (subject: Subject) => showThemedDialog('Delete subject?', 'This removes the subject and its chapter checklist.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Delete', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM subjects WHERE id = ?', subject.id); await load(); } }]);
  const total = subjects.reduce((sum, s) => sum + parseChapters(s.chapters_json).length, 0);
  const done = subjects.reduce((sum, s) => sum + parseChapters(s.chapters_json).filter(c => c.done).length, 0);
  const percent = total ? Math.round(done / total * 100) : 0;
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Syllabus tracker</Text><View style={{ width: 40 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>EVERY CHAPTER COUNTS</Text>
      <View style={[styles.progressCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={styles.progressTop}><View><Text style={[styles.progressTitle, { color: theme.text }]}>Overall progress</Text><Text style={[styles.hint, { color: theme.muted }]}>{done} of {total} chapters completed</Text></View><Text style={[styles.percent, { color: theme.primary }]}>{percent}%</Text></View><View style={[styles.track, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.fill, { width: `${percent}%` as `${number}%`, backgroundColor: theme.primary }]} /></View></View>
      <View style={[styles.addRow, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><TextInput value={name} onChangeText={setName} placeholder="Add a subject..." placeholderTextColor={theme.muted} onSubmitEditing={() => void addSubject()} style={[styles.input, { color: theme.text }]} /><Pressable onPress={() => void addSubject()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={22} color={theme.background} /></Pressable></View>
      {subjects.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="book-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Build your syllabus checklist.</Text><Text style={[styles.hint, { color: theme.muted, textAlign: 'center' }]}>Add a subject, then add chapters. Your percentage updates automatically as you tick them off.</Text></View> : subjects.map(subject => {
        const chapters = parseChapters(subject.chapters_json);
        const completed = chapters.filter(c => c.done).length;
        const progress = chapters.length ? Math.round(completed / chapters.length * 100) : 0;
        return <View key={subject.id} style={[styles.subject, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={styles.subjectHead}><View style={{ flex: 1, gap: 4 }}><Text style={[styles.subjectName, { color: theme.text }]}>{subject.name}</Text><Text style={[styles.hint, { color: theme.muted }]}>{completed}/{chapters.length} chapters · {progress}%</Text></View><Pressable onLongPress={() => removeSubject(subject)} hitSlop={8}><Ionicons name="ellipsis-horizontal" size={20} color={theme.muted} /></Pressable></View><View style={[styles.track, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.fill, { width: `${progress}%` as `${number}%`, backgroundColor: theme.primary }]} /></View>{chapters.map(chapter => <Pressable key={chapter.id} onPress={() => void toggleChapter(subject, chapter.id)} style={styles.chapter}><View style={[styles.check, { backgroundColor: chapter.done ? theme.primary : 'transparent', borderColor: chapter.done ? theme.primary : theme.muted }]}>{chapter.done && <Ionicons name="checkmark" size={13} color={theme.background} />}</View><Text style={[styles.chapterText, { color: chapter.done ? theme.muted : theme.text, textDecorationLine: chapter.done ? 'line-through' : 'none' }]}>{chapter.title}</Text></Pressable>)}<View style={styles.chapterAdd}><TextInput value={chapterDrafts[subject.id] ?? ''} onChangeText={value => setChapterDrafts(current => ({ ...current, [subject.id]: value }))} placeholder="Add chapter..." placeholderTextColor={theme.muted} onSubmitEditing={() => void addChapter(subject)} style={[styles.chapterInput, { color: theme.text }]} /><Pressable onPress={() => void addChapter(subject)}><Ionicons name="add-circle" size={23} color={theme.primary} /></Pressable></View></View>;
      })}
      <Text style={[styles.hint, { color: theme.muted }]}>Tip: long-press a subject to delete it. Everything is stored locally on this device.</Text>
    </ScrollView>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 17, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 36, gap: 13 }, eyebrow: { fontSize: 10, letterSpacing: 1.7, fontWeight: '800' },
  progressCard: { borderWidth: 1, borderRadius: 20, padding: 17, gap: 14 }, progressTop: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  progressTitle: { fontSize: 14, fontWeight: '800' }, hint: { fontSize: 11, lineHeight: 17 }, percent: { fontSize: 28, fontWeight: '900' },
  track: { height: 6, borderRadius: 6, overflow: 'hidden' }, fill: { height: 6, borderRadius: 6 }, addRow: { borderWidth: 1, borderRadius: 17, padding: 6, flexDirection: 'row', alignItems: 'center' },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 11, fontSize: 13 }, addButton: { width: 42, height: 42, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  empty: { borderWidth: 1, borderRadius: 26, padding: 25, alignItems: 'center', gap: 10 }, emptyTitle: { fontSize: 16, fontWeight: '800' },
  subject: { borderWidth: 1, borderRadius: 24, padding: 15, gap: 12 }, subjectHead: { flexDirection: 'row', alignItems: 'center' }, subjectName: { fontSize: 15, fontWeight: '800' },
  chapter: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 5 }, check: { width: 21, height: 21, borderWidth: 1.5, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  chapterText: { fontSize: 12, flex: 1 }, chapterAdd: { flexDirection: 'row', alignItems: 'center', borderTopWidth: 1, borderTopColor: '#294034', paddingTop: 8, gap: 10 },
  chapterInput: { flex: 1, fontSize: 12, paddingVertical: 7 }
});