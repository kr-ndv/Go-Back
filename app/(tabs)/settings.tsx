import { useEffect, useState } from 'react';
import { Alert, Pressable, Share, StyleSheet, Text, TextInput, View } from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme, useThemeStore } from '@/state/theme';
import type { AccentColor, ThemeMode } from '@/theme/palette';
import { getSetting, setSetting } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { createBackupJson, restoreBackupJson } from '@/lib/backup';

export default function SettingsScreen() {
  const theme = useAppTheme();
  const mode = useThemeStore(s => s.mode);
  const setMode = useThemeStore(s => s.setMode);
  const accent = useThemeStore(s => s.accent);
  const setAccent = useThemeStore(s => s.setAccent);
  const [profileName, setProfileName] = useState('');
  const [profileId, setProfileId] = useState('');
  useEffect(() => {
    void (async () => {
      const savedName = await getSetting<string>('profile.name', '');
      let savedId = await getSetting<string>('profile.id', '');
      if (!savedId) {
        savedId = createLocalId('GB').toUpperCase();
        await setSetting('profile.id', savedId);
      }
      setProfileName(savedName);
      setProfileId(savedId);
    })().catch(() => Alert.alert('Profile unavailable', 'Could not load your local profile.'));
  }, []);
  const saveProfile = async () => {
    await setSetting('profile.name', profileName.trim());
    Alert.alert('Profile saved', 'Your display name is saved on this device.');
  };
  const exportBackup = async () => {
    try {
      const directory = FileSystem.cacheDirectory;
      if (!directory) throw new Error('No temporary file directory is available.');
      const json = await createBackupJson();
      const uri = directory + 'go-back-backup-' + new Date().toISOString().slice(0, 10) + '.json';
      await FileSystem.writeAsStringAsync(uri, json, { encoding: FileSystem.EncodingType.UTF8 });
      await Sharing.shareAsync(uri, { mimeType: 'application/json', dialogTitle: 'Export Go Back backup' });
    } catch (error) {
      Alert.alert('Backup export failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };
  const importBackup = async () => {
    try {
      const picked = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (picked.canceled || !picked.assets[0]) return;
      const json = await FileSystem.readAsStringAsync(picked.assets[0].uri);
      Alert.alert('Replace local data?', 'Restoring this backup replaces the tasks, notes, exams, syllabus, goals, habits, focus history and settings currently stored on this device.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Restore backup', style: 'destructive', onPress: () => { void (async () => {
          try {
            await restoreBackupJson(json);
            await useThemeStore.getState().hydrate();
            Alert.alert('Backup restored', 'Your Go Back data has been restored.');
          } catch (error) {
            Alert.alert('Restore failed', error instanceof Error ? error.message : 'The backup could not be restored.');
          }
        })(); } },
      ]);
    } catch (error) {
      Alert.alert('Could not open backup', error instanceof Error ? error.message : 'Please choose a valid JSON backup.');
    }
  };
  const choices: { value: ThemeMode; label: string; hint: string; icon: keyof typeof Ionicons.glyphMap }[] = [
    { value: 'system', label: 'System default', hint: 'Follow your device appearance', icon: 'phone-portrait-outline' },
    { value: 'light', label: 'Light theme', hint: 'Bright, calm and clear', icon: 'sunny-outline' },
    { value: 'dark', label: 'Dark theme', hint: 'Deep forest, easier on the eyes', icon: 'moon-outline' },
  ];
  const accents: { value: AccentColor; label: string; color: string }[] = [
    { value: 'forest', label: 'Forest', color: '#B8F36B' },
    { value: 'blue', label: 'Blue', color: '#64A9FF' },
    { value: 'violet', label: 'Violet', color: '#B49AFF' },
    { value: 'amber', label: 'Amber', color: '#F2B75E' },
  ];
  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>MAKE IT YOURS</Text>
    <Text style={[styles.title, { color: theme.text }]}>Settings</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>A calmer space, tuned to you.</Text>
    <Text style={[styles.section, { color: theme.text }]}>Appearance</Text>
    <View style={{ gap: 9 }}>{choices.map(choice => <Pressable key={choice.value} onPress={() => void setMode(choice.value)} style={[styles.choice, { backgroundColor: theme.surface, borderColor: mode === choice.value ? theme.primary : theme.border }]}><View style={[styles.glyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name={choice.icon} size={20} color={theme.primary} /></View><View style={{ flex: 1, gap: 3 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>{choice.label}</Text><Text style={[styles.hint, { color: theme.muted }]}>{choice.hint}</Text></View><Ionicons name={mode === choice.value ? 'checkmark-circle' : 'ellipse-outline'} size={21} color={mode === choice.value ? theme.primary : theme.muted} /></Pressable>)}</View>
    <Text style={[styles.section, { color: theme.text }]}>Accent colour</Text>
    <View style={styles.accents}>{accents.map(choice => <Pressable key={choice.value} onPress={() => void setAccent(choice.value)} style={[styles.accentChoice, { backgroundColor: theme.surface, borderColor: accent === choice.value ? theme.primary : theme.border }]}><View style={[styles.swatch, { backgroundColor: choice.color }]} /><Text style={[styles.accentLabel, { color: theme.text }]}>{choice.label}</Text>{accent === choice.value && <Ionicons name="checkmark-circle" size={17} color={theme.primary} />}</Pressable>)}</View>
    <Text style={[styles.section, { color: theme.text }]}>Local profile</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border, alignItems: 'flex-start' }]}>
      <View style={{ flex: 1, gap: 9 }}>
        <Text style={[styles.choiceTitle, { color: theme.text }]}>Display name</Text>
        <TextInput value={profileName} onChangeText={setProfileName} placeholder="Your name (optional)" placeholderTextColor={theme.muted} style={[styles.profileInput, { color: theme.text, borderColor: theme.border }]} />
        <Pressable onPress={() => void saveProfile()} style={[styles.profileButton, { backgroundColor: theme.primary }]}><Text style={{ color: theme.background, fontWeight: '800', fontSize: 12 }}>Save profile</Text></Pressable>
        <Text style={[styles.hint, { color: theme.muted }]}>Local Go Back ID · not connected to social features yet</Text>
        <Pressable onPress={() => void Share.share({ message: profileId })}><Text style={[styles.profileId, { color: theme.primary }]}>{profileId || 'Creating your ID…'}  ·  Share</Text></Pressable>
      </View>
    </View>
    <Text style={[styles.section, { color: theme.text }]}>Backup & restore</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <Ionicons name="shield-checkmark-outline" size={23} color={theme.primary} />
      <View style={{ flex: 1, gap: 5 }}>
        <Text style={[styles.choiceTitle, { color: theme.text }]}>Your data stays on your device</Text>
        <Text style={[styles.hint, { color: theme.muted, lineHeight: 18 }]}>Export a portable JSON backup or restore one. Restoring replaces the current local dataset. Backups are not encrypted in this version, so keep the file private.</Text>
        <View style={styles.backupActions}>
          <Pressable onPress={() => void exportBackup()} style={[styles.backupButton, { backgroundColor: theme.primary }]}><Ionicons name="download-outline" size={17} color={theme.background} /><Text style={{ color: theme.background, fontWeight: '800', fontSize: 12 }}>Export</Text></Pressable>
          <Pressable onPress={() => void importBackup()} style={[styles.backupButton, { borderColor: theme.border, borderWidth: 1 }]}><Ionicons name="cloud-upload-outline" size={17} color={theme.primary} /><Text style={{ color: theme.text, fontWeight: '800', fontSize: 12 }}>Restore</Text></Pressable>
        </View>
      </View>
    </View>
    <Text style={[styles.section, { color: theme.text }]}>Privacy</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="shield-checkmark-outline" size={23} color={theme.primary} /><View style={{ flex: 1, gap: 5 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Your data stays on your device</Text><Text style={[styles.hint, { color: theme.muted, lineHeight: 18 }]}>This starter uses local SQLite storage. No AI, ads, analytics, or cloud account is configured.</Text></View></View>
    <Text style={[styles.section, { color: theme.text }]}>About</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.logo, { backgroundColor: theme.primary }]}><Text style={[styles.logoText, { color: theme.background }]}>G</Text></View><View style={{ flex: 1 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Go Back</Text><Text style={[styles.hint, { color: theme.muted }]}>Version 1.0.0 · Local-first focus companion</Text></View></View>
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 2, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 13 },
  section: { fontSize: 17, fontWeight: '800', marginTop: 8 },
  accents: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  profileInput: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, fontSize: 13 },
  profileButton: { borderRadius: 12, paddingVertical: 11, alignItems: 'center' },
  profileId: { fontSize: 12, fontWeight: '800' },
  backupActions: { flexDirection: 'row', gap: 8, marginTop: 7 },
  backupButton: { borderRadius: 12, paddingHorizontal: 13, paddingVertical: 11, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  accentChoice: { borderWidth: 1, borderRadius: 15, paddingHorizontal: 10, paddingVertical: 10, flexDirection: 'row', alignItems: 'center', gap: 7 },
  swatch: { width: 14, height: 14, borderRadius: 7 },
  accentLabel: { fontSize: 12, fontWeight: '700' },
  choice: { borderWidth: 1, borderRadius: 18, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12 },
  glyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  choiceTitle: { fontSize: 13, fontWeight: '800' },
  hint: { fontSize: 11 },
  info: { borderWidth: 1, borderRadius: 19, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 13 },
  logo: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  logoText: { fontSize: 22, fontWeight: '900' },
});