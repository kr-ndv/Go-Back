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
  const [editingChapterId, setEditingChapterId] = useState<string | null>(null);
  const [chapterEditDraft, setChapterEditDraft] = useState('');
  const [editingSubjectId, setEditingSubjectId] = useState<string | null>(null);
  const [subjectEditDraft, setSubjectEditDraft] = useState('');
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
  const beginEditChapter = (chapter: Chapter) => {
    setEditingChapterId(chapter.id);
    setChapterEditDraft(chapter.title);
  };
  const commitEditChapter = async (subject: Subject, chapterId: string) => {
    const title = chapterEditDraft.trim();
    if (!title) { showThemedDialog('Chapter name required', 'Enter a name, or cancel to keep the existing chapter.'); return; }
    const saved = await saveChapters(subject, parseChapters(subject.chapters_json).map(chapter => chapter.id === chapterId ? { ...chapter, title } : chapter));
    if (saved) { setEditingChapterId(null); setChapterEditDraft(''); }
  };
  const beginEditSubject = (subject: Subject) => { setEditingSubjectId(subject.id); setSubjectEditDraft(subject.name); };
  const commitEditSubject = async (subject: Subject) => {
    const clean = subjectEditDraft.trim();
    if (!clean) { showThemedDialog('Subject name required', 'Enter a name, or cancel to keep the existing subject.'); return; }
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE subjects SET name = ?, updated_at = ? WHERE id = ?', clean, new Date().toISOString(), subject.id);
      setEditingSubjectId(null); setSubjectEditDraft(''); await load();
    } catch (error) {
      showThemedDialog('Subject not updated', error instanceof Error ? error.message : 'Go Back could not rename this subject.');
    }
  };
  const removeSubject = (subject: Subject) => showThemedDialog('Delete subject?', `This removes ${subject.name} and its chapter checklist.`, [{ text: 'Cancel', style: 'cancel' }, { text: 'Delete', style: 'destructive', onPress: async () => { try { const db = await getDatabase(); await db.runAsync('DELETE FROM subjects WHERE id = ?', subject.id); await load(); } catch (error) { showThemedDialog('Subject not deleted', error instanceof Error ? error.message : 'Go Back could not delete this subject.'); } } }]);
  const showSubjectOptions = (subject: Subject) => showThemedDialog(subject.name, 'Manage this subject and its chapters.', [
    { text: 'Rename subject', onPress: () => beginEditSubject(subject) },
    { text: 'Delete subject', style: 'destructive', onPress: () => removeSubject(subject) },
    { text: 'Cancel', style: 'cancel' },
  ]);
  const removeChapter = (subject: Subject, chapter: Chapter) => showThemedDialog('Delete chapter?', `Remove “${chapter.title}” from ${subject.name}?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: () => { void saveChapters(subject, parseChapters(subject.chapters_json).filter(item => item.id !== chapter.id)); } },
  ]);
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
        return <View key={subject.id} style={[styles.subject, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={styles.subjectHead}><View style={{ flex: 1, gap: 4 }}>{editingSubjectId === subject.id ? <View style={styles.subjectEditRow}><TextInput autoFocus value={subjectEditDraft} onChangeText={setSubjectEditDraft} onSubmitEditing={() => void commitEditSubject(subject)} returnKeyType="done" accessibilityLabel="Subject name" placeholder="Subject name" placeholderTextColor={theme.muted} style={[styles.chapterInput, styles.editChapterInput, { color: theme.text, borderColor: theme.primary }]} /><Pressable accessibilityRole="button" accessibilityLabel="Save subject name" onPress={() => void commitEditSubject(subject)} hitSlop={8}><Ionicons name="checkmark-circle" size={23} color={theme.primary} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel="Cancel subject rename" onPress={() => { setEditingSubjectId(null); setSubjectEditDraft(''); }} hitSlop={8}><Ionicons name="close-circle-outline" size={22} color={theme.muted} /></Pressable></View> : <Text style={[styles.subjectName, { color: theme.text }]}>{subject.name}</Text>}<Text style={[styles.hint, { color: theme.muted }]}>{completed}/{chapters.length} chapters · {progress}%</Text></View><Pressable accessibilityRole="button" accessibilityLabel={`Options for ${subject.name}`} onPress={() => showSubjectOptions(subject)} style={styles.subjectOptions}><Ionicons name="ellipsis-horizontal" size={20} color={theme.muted} /></Pressable></View><View style={[styles.track, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.fill, { width: `${progress}%` as `${number}%`, backgroundColor: theme.primary }]} /></View>{chapters.map(chapter => <View key={chapter.id} style={styles.chapter}>{editingChapterId === chapter.id ? <><TextInput autoFocus value={chapterEditDraft} onChangeText={setChapterEditDraft} onSubmitEditing={() => void commitEditChapter(subject, chapter.id)} returnKeyType="done" placeholder="Chapter name" placeholderTextColor={theme.muted} style={[styles.chapterInput, styles.editChapterInput, { color: theme.text, borderColor: theme.primary }]} /><Pressable accessibilityRole="button" accessibilityLabel="Save chapter name" onPress={() => void commitEditChapter(subject, chapter.id)} hitSlop={8}><Ionicons name="checkmark-circle" size={23} color={theme.primary} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel="Cancel chapter edit" onPress={() => { setEditingChapterId(null); setChapterEditDraft(''); }} hitSlop={8}><Ionicons name="close-circle-outline" size={22} color={theme.muted} /></Pressable></> : <><Pressable accessibilityRole="checkbox" accessibilityState={{ checked: chapter.done }} onPress={() => void toggleChapter(subject, chapter.id)} style={[styles.check, { backgroundColor: chapter.done ? theme.primary : 'transparent', borderColor: chapter.done ? theme.primary : theme.muted }]}>{chapter.done && <Ionicons name="checkmark" size={13} color={theme.onPrimary} />}</Pressable><Pressable onPress={() => void toggleChapter(subject, chapter.id)} style={{ flex: 1, paddingVertical: 5 }}><Text style={[styles.chapterText, { color: chapter.done ? theme.muted : theme.text, textDecorationLine: chapter.done ? 'line-through' : 'none' }]}>{chapter.title}</Text></Pressable><Pressable accessibilityRole="button" accessibilityLabel={`Edit chapter ${chapter.title}`} onPress={() => beginEditChapter(chapter)} hitSlop={8} style={styles.editChapterButton}><Ionicons name="create-outline" size={18} color={theme.primary} /></Pressable><Pressable accessibilityRole="button" accessibilityLabel={`Delete chapter ${chapter.title}`} onPress={() => removeChapter(subject, chapter)} hitSlop={8} style={styles.editChapterButton}><Ionicons name="trash-outline" size={17} color={theme.danger} /></Pressable></>}</View>)}<View style={styles.chapterAdd}><TextInput value={chapterDrafts[subject.id] ?? ''} onChangeText={value => setChapterDrafts(current => ({ ...current, [subject.id]: value }))} placeholder="Add chapter..." placeholderTextColor={theme.muted} onSubmitEditing={() => void addChapter(subject)} style={[styles.chapterInput, { color: theme.text }]} /><Pressable onPress={() => void addChapter(subject)}><Ionicons name="add-circle" size={23} color={theme.primary} /></Pressable></View></View>;
      })}
      <Text style={[styles.hint, { color: theme.muted }]}>Tip: use the subject menu to rename or delete a subject. Edit a chapter with the pencil, or remove it with the delete icon. Everything stays on this device.</Text>
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
  subject: { borderWidth: 1, borderRadius: 24, padding: 15, gap: 12 }, subjectHead: { flexDirection: 'row', alignItems: 'center', gap: 8 }, subjectName: { fontSize: 15, fontWeight: '800' }, subjectEditRow: { flexDirection: 'row', alignItems: 'center', gap: 6 }, subjectOptions: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  chapter: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 5 }, check: { width: 21, height: 21, borderWidth: 1.5, borderRadius: 7, alignItems: 'center', justifyContent: 'center' },
  chapterText: { fontSize: 12, flex: 1 }, chapterAdd: { flexDirection: 'row', alignItems: 'center', borderTopWidth: 1, borderTopColor: 'transparent', paddingTop: 8, gap: 10 },
  chapterInput: { flex: 1, fontSize: 12, paddingVertical: 7 }, editChapterInput: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 10 }, editChapterButton: { width: 30, height: 30, borderRadius: 10, alignItems: 'center', justifyContent: 'center' }
});