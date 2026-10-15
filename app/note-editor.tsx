import { useCallback, useEffect, useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';
import { StyleSheet, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
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
  const [saveStatus, setSaveStatus] = useState('Loading note…');
  useEffect(() => {
    let active = true;
    void getDatabase().then(async db => {
      const note = await db.getFirstAsync<{ title: string; body: string }>('SELECT title, body FROM notes WHERE id = ?', id);
      if (active && note) { setTitle(note.title); setBody(note.body); setReady(true); setSaveStatus('Saved locally'); }
    });
    return () => { active = false; };
  }, [id]);
  const save = useCallback(async () => {
    if (!ready) return;
    setSaveStatus('Saving…');
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE notes SET title = ?, body = ?, updated_at = ? WHERE id = ?', title.trim() || 'Untitled note', body, new Date().toISOString(), id);
      setSaveStatus('Saved locally');
    } catch {
      setSaveStatus('Save failed');
    }
  }, [body, id, ready, title]);
  useEffect(() => {
    if (!ready) return;
    const timer = setTimeout(() => { void save(); }, 500);
    return () => clearTimeout(timer);
  }, [ready, save]);
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.toolbar}><Pressable onPress={() => { void save().then(() => router.back()); }} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.saved, { color: theme.muted }]}>{saveStatus}</Text><Pressable onPress={() => void save()}><Text style={[styles.save, { color: theme.primary }]}>Save</Text></Pressable></View>
    <View style={styles.content}><TextInput value={title} onChangeText={setTitle} placeholder="Note title" placeholderTextColor={theme.muted} editable={ready} style={[styles.title, { color: theme.text }]} /><TextInput value={body} onChangeText={setBody} placeholder="Start writing…" placeholderTextColor={theme.muted} multiline textAlignVertical="top" editable={ready} style={[styles.body, { color: theme.text }]} /></View>
  </SafeAreaView>;
}
const styles = StyleSheet.create({
  safe: { flex: 1 },
  toolbar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 12, gap: 14 },
  back: { width: 40, height: 40, borderRadius: 17, alignItems: 'center', justifyContent: 'center' },
  saved: { fontSize: 12, flex: 1 },
  save: { fontWeight: '800', fontSize: 14 },
  content: { flex: 1, paddingHorizontal: 24, paddingTop: 28, gap: 22 },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -0.9 },
  body: { flex: 1, fontSize: 17, lineHeight: 27 },
});