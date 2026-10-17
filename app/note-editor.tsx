import { useCallback, useEffect, useState } from 'react';
import { showThemedDialog } from '@/state/dialog';
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
    void (async () => {
      try {
        const db = await getDatabase();
        const note = await db.getFirstAsync<{ title: string; body: string }>('SELECT title, body FROM notes WHERE id = ?', id);
        if (!active) return;
        if (!note) {
          setSaveStatus('Note not found');
          showThemedDialog('Note not found', 'This note may have been deleted. Return to your notes and open another one.');
          return;
        }
        setTitle(note.title); setBody(note.body); setReady(true); setSaveStatus('Saved locally');
      } catch (error) {
        if (!active) return;
        setSaveStatus('Could not load note');
        showThemedDialog('Note could not load', error instanceof Error ? error.message : 'Go Back could not open this note.');
      }
    })();
    return () => { active = false; };
  }, [id]);
  const save = useCallback(async (showError = false): Promise<boolean> => {
    if (!ready) return false;
    setSaveStatus('Saving…');
    try {
      const db = await getDatabase();
      await db.runAsync('UPDATE notes SET title = ?, body = ?, updated_at = ? WHERE id = ?', title.trim() || 'Untitled note', body, new Date().toISOString(), id);
      setSaveStatus('Saved locally');
      return true;
    } catch (error) {
      setSaveStatus('Save failed');
      if (showError) showThemedDialog('Note not saved', error instanceof Error ? error.message : 'Your changes could not be saved. Keep this screen open and try again.');
      return false;
    }
  }, [body, id, ready, title]);
  useEffect(() => {
    if (!ready) return;
    const timer = setTimeout(() => { void save(); }, 500);
    return () => clearTimeout(timer);
  }, [ready, save]);
  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.toolbar}><Pressable accessibilityRole="button" accessibilityLabel="Save and go back to notes" disabled={!ready} onPress={() => { void save(true).then(saved => { if (saved) router.back(); }); }} style={[styles.back, { backgroundColor: theme.surfaceAlt, opacity: ready ? 1 : 0.5 }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text accessibilityLiveRegion="polite" style={[styles.saved, { color: saveStatus === 'Save failed' || saveStatus === 'Could not load note' ? theme.danger : theme.muted }]}>{saveStatus}</Text><Pressable accessibilityRole="button" accessibilityLabel="Save note" disabled={!ready} onPress={() => void save(true)} style={{ minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center', opacity: ready ? 1 : 0.5 }}><Text style={[styles.save, { color: theme.primary }]}>Save</Text></Pressable></View>
    <View style={styles.content}><TextInput accessibilityLabel="Note title" value={title} onChangeText={setTitle} placeholder="Note title" placeholderTextColor={theme.muted} editable={ready} style={[styles.title, { color: theme.text }]} /><TextInput accessibilityLabel="Note body" value={body} onChangeText={setBody} placeholder="Start writing…" placeholderTextColor={theme.muted} multiline textAlignVertical="top" editable={ready} style={[styles.body, { color: theme.text }]} /></View>
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