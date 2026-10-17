import { showThemedDialog } from '@/state/dialog';
import { useCallback, useState } from 'react';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { StyleSheet, Text, TextInput, View } from 'react-native';
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
  const [search, setSearch] = useState('');
  const [creating, setCreating] = useState(false);
  const load = useCallback(async () => {
    try {
      const db = await getDatabase();
      setNotes(await db.getAllAsync<Note>('SELECT * FROM notes ORDER BY updated_at DESC'));
    } catch (error) {
      showThemedDialog('Notes could not load', error instanceof Error ? error.message : 'Go Back could not read your saved notes.');
    }
  }, []);
  useFocusEffect(useCallback(() => { void load(); }, [load]));
  const create = async () => {
    const clean = title.trim();
    if (creating) return;
    if (!clean) { showThemedDialog('Give your note a title', 'Enter a title to create a note.'); return; }
    setCreating(true);
    try {
      const id = createLocalId('note');
      const db = await getDatabase();
      await db.runAsync('INSERT INTO notes (id, title, body, updated_at) VALUES (?, ?, ?, ?)', id, clean, '', new Date().toISOString());
      setTitle('');
      await load();
      router.push({ pathname: '/note-editor', params: { id } });
    } catch (error) {
      showThemedDialog('Note not created', error instanceof Error ? error.message : 'Go Back could not create this note. Please try again.');
    } finally {
      setCreating(false);
    }
  };
  const filteredNotes = notes.filter(note => (note.title + ' ' + note.body).toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));
  return <Screen>
    <View style={styles.header}><View><Text style={[styles.eyebrow, { color: theme.muted }]}>THINK IT THROUGH</Text><Text style={[styles.title, { color: theme.text }]}>Personal notes</Text></View><View style={[styles.noteIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="document-text-outline" size={22} color={theme.primary} /></View></View>
    <Text style={[styles.sub, { color: theme.muted }]}>Private by default. Stored on this device.</Text>
    <View style={[styles.addBox, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><TextInput accessibilityLabel="New note title" value={title} onChangeText={setTitle} placeholder="Give a new note a title..." placeholderTextColor={theme.muted} onSubmitEditing={() => void create()} returnKeyType="done" style={[styles.input, { color: theme.text }]} /><Pressable accessibilityRole="button" accessibilityLabel="Create note" disabled={creating} onPress={() => void create()} style={[styles.addButton, { backgroundColor: theme.primary, opacity: creating ? 0.6 : 1 }]}><Ionicons name={creating ? 'time-outline' : 'add'} size={20} color={theme.background} /></Pressable></View>
    {notes.length > 0 && <View style={[styles.searchBox, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="search-outline" size={18} color={theme.muted} /><TextInput accessibilityLabel="Search notes" value={search} onChangeText={setSearch} placeholder="Search titles and note contents" placeholderTextColor={theme.muted} style={[styles.searchInput, { color: theme.text }]} /></View>}
    {notes.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="book-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Your thoughts, in one place.</Text><Text style={[styles.sub, { color: theme.muted, textAlign: 'center' }]}>Create a note for ideas, class notes, or anything you want to remember.</Text></View> : filteredNotes.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="search-outline" size={26} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>No matching notes.</Text><Text style={[styles.sub, { color: theme.muted }]}>Try another search term.</Text></View> : <View style={styles.list}>{filteredNotes.map(note => <Pressable key={note.id} onPress={() => router.push({ pathname: '/note-editor', params: { id: note.id } })} accessibilityRole="button" accessibilityLabel={`Open note: ${note.title}`} style={[styles.note, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.noteGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="document-text-outline" size={18} color={theme.primary} /></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.noteTitle, { color: theme.text }]}>{note.title}</Text><Text numberOfLines={2} style={[styles.preview, { color: theme.muted }]}>{note.body || 'Tap to add your thoughts...'}</Text></View><Ionicons name="chevron-forward" size={17} color={theme.muted} /></Pressable>)}</View>}
  </Screen>;
}
const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  eyebrow: { fontSize: 10, letterSpacing: 1.8, fontWeight: '800' },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1, marginTop: 3 },
  noteIcon: { width: 44, height: 44, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  sub: { fontSize: 13, lineHeight: 20 },
  addBox: { flexDirection: 'row', alignItems: 'center', borderRadius: 22, borderWidth: 1, padding: 6, gap: 6 },
  searchBox: { flexDirection: 'row', alignItems: 'center', borderRadius: 15, borderWidth: 1, paddingHorizontal: 13, gap: 9 },
  searchInput: { flex: 1, paddingVertical: 12, fontSize: 12 },
  input: { flex: 1, paddingHorizontal: 12, paddingVertical: 12, fontSize: 13 },
  addButton: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  empty: { alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 28, padding: 28, marginTop: 8 },
  emptyTitle: { fontSize: 17, fontWeight: '800' },
  list: { gap: 10 },
  note: { borderWidth: 1, borderRadius: 23, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12 },
  noteGlyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  noteTitle: { fontSize: 14, fontWeight: '800' },
  preview: { fontSize: 12, lineHeight: 17 },
});