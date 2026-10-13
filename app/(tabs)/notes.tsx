import { useCallback, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';

type Note = { id: string; title: string; body: string; updated_at: string };

export default function NotesScreen() {
  const theme = useAppTheme();
  const [notes, setNotes] = useState<Note[]>([]);
  const [title, setTitle] = useState('');
  const load = useCallback(async () => { const db = await getDatabase(); setNotes(await db.getAllAsync<Note>('SELECT * FROM notes ORDER BY updated_at DESC')); }, []);
  useFocusEffect(useCallback(() => { void load(); }, [load]));
  const create = async () => {
    const clean = title.trim();
    if (!clean) { Alert.alert('Give your note a title', 'Enter a title to create a note.'); return; }
    const id = createLocalId('note');
    const db = await getDatabase();
    await db.runAsync('INSERT INTO notes (id, title, body, updated_at) VALUES (?, ?, ?, ?)', id, clean, '', new Date().toISOString());
    setTitle('');
    await load();
    router.push({ pathname: '/note-editor', params: { id } });
  };
  return <Screen>
    <View style={styles.header}><View><Text style={[styles.eyebrow, { color: theme.muted }]}>THINK IT THROUGH</Text><Text style={[styles.title, { color: theme.text }]}>Personal notes</Text></View><View style={[styles.noteIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="document-text-outline" size={22} color={theme.primary} /></View></View>
    <Text style={[styles.sub, { color: theme.muted }]}>Private by default. Stored on this device.</Text>
    <View style={[styles.addBox, { backgroundColor: theme.surface, borderColor: theme.border }]}><TextInput value={title} onChangeText={setTitle} placeholder="Give a new note a title..." placeholderTextColor={theme.muted} onSubmitEditing={() => void create()} returnKeyType="done" style={[styles.input, { color: theme.text }]} /><Pressable onPress={() => void create()} style={[styles.addButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={20} color={theme.background} /></Pressable></View>
    {notes.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="book-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Your thoughts, in one place.</Text><Text style={[styles.sub, { color: theme.muted, textAlign: 'center' }]}>Create a note for ideas, class notes, or anything you want to remember.</Text></View> : <View style={styles.list}>{notes.map(note => <Pressable key={note.id} onPress={() => router.push({ pathname: '/note-editor', params: { id: note.id } })} style={[styles.note, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.noteGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="document-text-outline" size={18} color={theme.primary} /></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.noteTitle, { color: theme.text }]}>{note.title}</Text><Text numberOfLines={2} style={[styles.preview, { color: theme.muted }]}>{note.body || 'Tap to add your thoughts...'}</Text></View><Ionicons name="chevron-forward" size={17} color={theme.muted} /></Pressable>)}</View>}
  </Screen>;
}
const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  eyebrow: { fontSize: 10, letterSpacing: 1.8, fontWeight: '800' },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1, marginTop: 3 },
  noteIcon: { width: 44, height: 44, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  sub: { fontSize: 13, lineHeight: 20 },
  addBox: { flexDirection: 'row', alignItems: 'center', borderRadius: 18, borderWidth: 1, padding: 6, gap: 6 },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 12, fontSize: 13 },
  addButton: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 24, padding: 28, marginTop: 8 },
  emptyTitle: { fontSize: 17, fontWeight: '800' },
  list: { gap: 10 },
  note: { borderWidth: 1, borderRadius: 19, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  noteGlyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  noteTitle: { fontSize: 14, fontWeight: '800' },
  preview: { fontSize: 12, lineHeight: 17 },
});