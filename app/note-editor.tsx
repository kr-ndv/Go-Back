import { useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase } from '@/lib/database';
import { useAppTheme } from '@/state/theme';

export default function NoteEditorScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useAppTheme();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let active = true;
    void getDatabase().then(async db => {
      const note = await db.getFirstAsync<{ title: string; body: string }>('SELECT title, body FROM notes WHERE id = ?', id);
      if (active && note) { setTitle(note.title); setBody(note.body); setReady(true); }
    });
    return () => { active = false; };
  }, [id]);
  const save = async () => {
    const db = await getDatabase();
    await db.runAsync('UPDATE notes SET title = ?, body = ?, updated_at = ? WHERE id = ?', title.trim() || 'Untitled note', body, new Date().toISOString(), id);
  };
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.toolbar}><Pressable onPress={() => { void save().then(() => router.back()); }} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.saved, { color: theme.muted }]}>{ready ? 'Saved locally' : 'Loading note…'}</Text><Pressable onPress={() => void save()}><Text style={[styles.save, { color: theme.primary }]}>Save</Text></Pressable></View>
    <View style={styles.content}><TextInput value={title} onChangeText={setTitle} placeholder="Note title" placeholderTextColor={theme.muted} style={[styles.title, { color: theme.text }]} /><TextInput value={body} onChangeText={setBody} placeholder="Start writing…" placeholderTextColor={theme.muted} multiline textAlignVertical="top" style={[styles.body, { color: theme.text }]} /></View>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1 },
  toolbar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 12, gap: 14 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  saved: { fontSize: 12, flex: 1 },
  save: { fontWeight: '800', fontSize: 14 },
  content: { flex: 1, paddingHorizontal: 24, paddingTop: 24, gap: 18 },
  title: { fontSize: 27, fontWeight: '800', letterSpacing: -0.8 },
  body: { flex: 1, fontSize: 16, lineHeight: 25 },
});