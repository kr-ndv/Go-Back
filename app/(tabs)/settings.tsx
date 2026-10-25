import { showThemedDialog } from '@/state/dialog';
import { useEffect, useState } from 'react';
import { Image, Linking, Share, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import * as ImagePicker from 'expo-image-picker';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme, useThemeStore } from '@/state/theme';
import { accentColors, paletteNames, type AccentColor, type ThemeMode } from '@/theme/palette';
import { getSetting, setSetting } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { createBackupJson, restoreBackupJson } from '@/lib/backup';

export default function SettingsScreen() {
  const theme = useAppTheme();
  const mode = useThemeStore(s => s.mode);
  const setMode = useThemeStore(s => s.setMode);
  const accent = useThemeStore(s => s.accent);
  const setAccent = useThemeStore(s => s.setAccent);
  const expressive = useThemeStore(s => s.expressive);
  const setExpressive = useThemeStore(s => s.setExpressive);
  const [profileName, setProfileName] = useState('');
  const [profileId, setProfileId] = useState('');
  const [profileImageUri, setProfileImageUri] = useState('');
  const [choosingProfileImage, setChoosingProfileImage] = useState(false);
  useEffect(() => {
    void (async () => {
      const savedName = await getSetting<string>('profile.name', '');
      const savedImageUri = await getSetting<string>('profile.imageUri', '');
      let savedId = await getSetting<string>('profile.id', '');
      if (!savedId) {
        savedId = createLocalId('GB').toUpperCase();
        await setSetting('profile.id', savedId);
      }
      setProfileName(savedName);
      setProfileImageUri(savedImageUri);
      setProfileId(savedId);
    })().catch(() => showThemedDialog('Profile unavailable', 'Could not load your local profile.'));
  }, []);
  const saveProfile = async () => {
    try {
      await setSetting('profile.name', profileName.trim());
      showThemedDialog('Profile saved', 'Your display name is saved on this device.');
    } catch (error) {
      showThemedDialog('Could not save profile', error instanceof Error ? error.message : 'Please try again.');
    }
  };

  const chooseProfileImage = async () => {
    if (choosingProfileImage) return;
    setChoosingProfileImage(true);
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.85,
      });
      if (result.canceled || !result.assets[0]) return;
      const asset = result.assets[0];
      const directory = FileSystem.documentDirectory;
      if (!directory) throw new Error('Local app storage is unavailable.');
      const fileName = asset.fileName ?? asset.uri;
      const extensionMatch = fileName.match(/\.([a-zA-Z0-9]{2,5})(?:[?#]|$)/);
      const extension = extensionMatch?.[1]?.toLowerCase() ?? 'jpg';
      const destination = directory + 'profile-picture-' + Date.now() + '.' + extension;
      await FileSystem.copyAsync({ from: asset.uri, to: destination });
      await setSetting('profile.imageUri', destination);
      setProfileImageUri(destination);
      showThemedDialog('Profile photo updated', 'Your photo is stored locally on this device.');
    } catch (error) {
      showThemedDialog('Could not choose photo', error instanceof Error ? error.message : 'The photo picker is unavailable. You can still use your display-name initial.');
    } finally {
      setChoosingProfileImage(false);
    }
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
      showThemedDialog('Backup export failed', error instanceof Error ? error.message : 'Please try again.');
    }
  };
  const importBackup = async () => {
    try {
      const picked = await DocumentPicker.getDocumentAsync({ type: 'application/json', copyToCacheDirectory: true });
      if (picked.canceled || !picked.assets[0]) return;
      const json = await FileSystem.readAsStringAsync(picked.assets[0].uri);
      const asset = picked.assets[0];
      const sizeLabel = typeof asset.size === 'number' ? (asset.size < 1024 * 1024 ? Math.max(1, Math.round(asset.size / 1024)) + ' KB' : (asset.size / (1024 * 1024)).toFixed(1) + ' MB') : 'size unavailable';
      showThemedDialog('Replace current local data?', 'Selected file: ' + asset.name + ' (' + sizeLabel + '). Restoring replaces tasks, notes, exams, syllabus, goals, habits, focus history and settings on this device. Export a backup first if you may need the current data.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Replace current data', style: 'destructive', onPress: () => { void (async () => {
          try {
            await restoreBackupJson(json);
            await useThemeStore.getState().hydrate();
            showThemedDialog('Backup restored', 'Your Go Back data has been restored.');
          } catch (error) {
            showThemedDialog('Restore failed', error instanceof Error ? error.message : 'The backup could not be restored.');
          }
        })(); } },
      ]);
    } catch (error) {
      showThemedDialog('Could not open backup', error instanceof Error ? error.message : 'Please choose a valid JSON backup.');
    }
  };
  const choices: { value: ThemeMode; label: string; hint: string; icon: keyof typeof Ionicons.glyphMap }[] = [
    { value: 'system', label: 'System default', hint: 'Follow your device appearance', icon: 'phone-portrait-outline' },
    { value: 'light', label: 'Light theme', hint: 'Bright, calm and clear', icon: 'sunny-outline' },
    { value: 'dark', label: 'Dark theme', hint: 'Deep forest, easier on the eyes', icon: 'moon-outline' },
  ];
  const accents: { value: AccentColor; label: string; color: string }[] = [
    { value: 'forest', label: paletteNames.forest, color: accentColors.forest[theme.mode] },
    { value: 'blue', label: paletteNames.blue, color: accentColors.blue[theme.mode] },
    { value: 'violet', label: paletteNames.violet, color: accentColors.violet[theme.mode] },
    { value: 'amber', label: paletteNames.amber, color: accentColors.amber[theme.mode] },
    { value: 'rose', label: paletteNames.rose, color: accentColors.rose[theme.mode] },
    { value: 'teal', label: paletteNames.teal, color: accentColors.teal[theme.mode] },
    { value: 'berry', label: paletteNames.berry, color: accentColors.berry[theme.mode] },
    { value: 'sakura', label: paletteNames.sakura, color: accentColors.sakura[theme.mode] },
    { value: 'graphite', label: paletteNames.graphite, color: accentColors.graphite[theme.mode] },
    { value: 'coral', label: paletteNames.coral, color: accentColors.coral[theme.mode] },
    { value: 'lavender', label: paletteNames.lavender, color: accentColors.lavender[theme.mode] },
    { value: 'crimson', label: paletteNames.crimson, color: accentColors.crimson[theme.mode] },
  ];
  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>MAKE IT YOURS</Text>
    <Text style={[styles.title, { color: theme.text }]}>Settings</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>A calmer space, tuned to you.</Text>
    <Text style={[styles.section, { color: theme.text }]}>Appearance</Text>
    <View style={{ gap: 9 }}>{choices.map(choice => <Pressable key={choice.value} onPress={() => void setMode(choice.value)} style={[styles.choice, { backgroundColor: theme.surface, borderColor: mode === choice.value ? theme.primary : theme.border }]}><View style={[styles.glyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name={choice.icon} size={20} color={theme.primary} /></View><View style={{ flex: 1, gap: 3 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>{choice.label}</Text><Text style={[styles.hint, { color: theme.muted }]}>{choice.hint}</Text></View><Ionicons name={mode === choice.value ? 'checkmark-circle' : 'ellipse-outline'} size={21} color={mode === choice.value ? theme.primary : theme.muted} /></Pressable>)}</View>
    <Text style={[styles.section, { color: theme.text }]}>Theme Studio</Text>
    <Text style={[styles.hint, { color: theme.muted, lineHeight: 18 }]}>Choose a complete colour personality. Your selection applies across Go Back and stays on this device.</Text>
    <View style={styles.accents}>{accents.map(choice => <Pressable key={choice.value} accessibilityRole="radio" accessibilityState={{ selected: accent === choice.value }} onPress={() => void setAccent(choice.value)} style={[styles.accentChoice, { backgroundColor: theme.surface, borderColor: accent === choice.value ? theme.primary : theme.border, borderWidth: accent === choice.value ? 2 : 1 }]}><View style={[styles.swatch, { backgroundColor: choice.color }]} /><Text style={[styles.accentLabel, { color: theme.text }]}>{choice.label}</Text>{accent === choice.value && <Ionicons name="checkmark-circle" size={17} color={theme.primary} />}</Pressable>)}</View>
    <View style={[styles.themePreview, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={{ flex: 1, gap: 5 }}><Text style={[styles.previewEyebrow, { color: theme.primary }]}>LIVE PREVIEW</Text><Text style={[styles.previewTitle, { color: theme.text }]}>Your space, your palette</Text><Text style={[styles.hint, { color: theme.muted }]}>Backgrounds, cards, borders, icons and buttons update together.</Text></View><View style={[styles.previewMiniCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.previewLine, { backgroundColor: theme.primary }]} /><View style={[styles.previewLineShort, { backgroundColor: theme.muted }]} /><View style={styles.previewSwatches}><View style={[styles.previewSwatch, { backgroundColor: theme.primary }]} /><View style={[styles.previewSwatch, { backgroundColor: theme.secondary }]} /><View style={[styles.previewSwatch, { backgroundColor: theme.surfaceAlt, borderColor: theme.border, borderWidth: 1 }]} /></View></View></View>
    <View style={[styles.expressiveRow, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.glyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="sparkles-outline" size={20} color={theme.primary} /></View><View style={{ flex: 1, gap: 4 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Expressive styling</Text><Text style={[styles.hint, { color: theme.muted, lineHeight: 17 }]}>Use bolder shapes, stronger hierarchy and springier touch feedback.</Text></View><Switch accessibilityLabel="Expressive styling" value={expressive} onValueChange={value => void setExpressive(value)} trackColor={{ false: theme.border, true: theme.primary }} thumbColor={expressive ? theme.background : theme.surfaceAlt} />
    </View>
    <Text style={[styles.section, { color: theme.text }]}>Local profile</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border, alignItems: 'flex-start' }]}>
      <View style={{ flex: 1, gap: 9 }}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <View style={[styles.profileAvatar, { backgroundColor: theme.surfaceAlt, alignItems: 'center', justifyContent: 'center' }]}>{profileImageUri ? <Image source={{ uri: profileImageUri }} style={StyleSheet.absoluteFillObject} resizeMode="cover" accessibilityLabel="Your local profile photo" /> : profileName.trim() ? <Text style={{ color: theme.primary, fontSize: 28, fontWeight: '900' }}>{profileName.trim().charAt(0).toLocaleUpperCase()}</Text> : <Ionicons name="person-outline" size={26} color={theme.primary} />}</View>
          <View style={{ flex: 1, gap: 7 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Profile</Text><Text style={[styles.hint, { color: theme.muted }]}>{profileName.trim() ? 'Hello, ' + profileName.trim() : 'Add a display name below'}</Text><Pressable accessibilityRole="button" accessibilityLabel="Choose a local profile photo" disabled={choosingProfileImage} onPress={() => void chooseProfileImage()} style={[styles.photoButton, { backgroundColor: theme.primaryContainer, opacity: choosingProfileImage ? 0.6 : 1 }]}><Ionicons name="image-outline" size={16} color={theme.primary} /><Text style={{ color: theme.primary, fontWeight: '800', fontSize: 12 }}>{choosingProfileImage ? 'Opening photos…' : profileImageUri ? 'Change photo' : 'Add profile photo'}</Text></Pressable></View>
        </View>
        <Text style={[styles.choiceTitle, { color: theme.text }]}>Display name</Text>
        <TextInput value={profileName} onChangeText={setProfileName} placeholder="Your name (optional)" placeholderTextColor={theme.muted} style={[styles.profileInput, { color: theme.text, borderColor: theme.border }]} />
        <Pressable onPress={() => void saveProfile()} style={[styles.profileButton, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontWeight: '800', fontSize: 12 }}>Save profile</Text></Pressable>
        <Text style={[styles.hint, { color: theme.muted }]}>Local Go Back ID · not connected to social features yet</Text>
        <Pressable onPress={() => void Share.share({ message: profileId })}><Text style={[styles.profileId, { color: theme.primary }]}>{profileId || 'Creating your ID…'}  ·  Share</Text></Pressable>
      </View>
    </View>
    <Text style={[styles.section, { color: theme.text }]}>Backup & restore</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <Ionicons name="shield-checkmark-outline" size={23} color={theme.primary} />
      <View style={{ flex: 1, gap: 5 }}>
        <Text style={[styles.choiceTitle, { color: theme.text }]}>Your data stays on your device</Text>
        <Text style={[styles.hint, { color: theme.muted, lineHeight: 19, fontSize: 12 }]}>Export a portable JSON backup or restore one. Restoring replaces the current local dataset.</Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 9, borderRadius: 14, padding: 11, backgroundColor: theme.surfaceAlt }}>
          <Ionicons name="warning-outline" size={18} color={theme.warning} />
          <Text style={{ color: theme.text, fontSize: 12, lineHeight: 18, flex: 1 }}>Backup files are not encrypted. Anyone with the file may be able to read its contents. Keep it private and store it somewhere you trust.</Text>
        </View>
        <View style={styles.backupActions}>
          <Pressable accessibilityRole="button" accessibilityLabel="Export Go Back backup" onPress={() => void exportBackup()} style={[styles.backupButton, { backgroundColor: theme.primary }]}><Ionicons name="download-outline" size={17} color={theme.onPrimary} /><Text style={{ color: theme.onPrimary, fontWeight: '800', fontSize: 12 }}>Export</Text></Pressable>
          <Pressable accessibilityRole="button" accessibilityLabel="Import Go Back backup" onPress={() => void importBackup()} style={[styles.backupButton, { borderColor: theme.border, borderWidth: 1 }]}><Ionicons name="cloud-upload-outline" size={17} color={theme.primary} /><Text style={{ color: theme.text, fontWeight: '800', fontSize: 12 }}>Restore</Text></Pressable>
        </View>
      </View>
    </View>
    <Text style={[styles.section, { color: theme.text }]}>Privacy</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="shield-checkmark-outline" size={23} color={theme.primary} /><View style={{ flex: 1, gap: 5 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Your data stays on your device</Text><Text style={[styles.hint, { color: theme.muted, lineHeight: 18 }]}>This starter uses local SQLite storage. No AI, ads, analytics, or cloud account is configured.</Text></View></View>
    <Text style={[styles.section, { color: theme.text }]}>About</Text>
    <View style={[styles.info, { backgroundColor: theme.surface, borderColor: theme.border }]}><Image source={require('../../assets/icon.png')} style={styles.logo} resizeMode="cover" accessibilityLabel="Go Back app icon" /><View style={{ flex: 1 }}><Text style={[styles.choiceTitle, { color: theme.text }]}>Go Back</Text><Text style={[styles.hint, { color: theme.muted }]}>Version 1.0.0 · Local-first focus companion</Text></View></View>
    <Pressable accessibilityRole="link" accessibilityLabel="Open KriSh's GitHub profile" onPress={() => void Linking.openURL('https://github.com/kr-ndv')} style={[styles.credit, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.creditIcon,{ backgroundColor:theme.surfaceAlt }]}><Ionicons name="heart" size={18} color={theme.primary} /></View><View style={{ flex: 1, gap: 3 }}><Text style={[styles.choiceTitle,{ color:theme.text }]}>Made with Love By – KriSh</Text><Text style={[styles.hint,{ color:theme.muted }]}>Open GitHub profile</Text></View><Ionicons name="open-outline" size={18} color={theme.primary} /></Pressable>
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 2, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 13 },
  section: { fontSize: 17, fontWeight: '800', marginTop: 8 },
  accents: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  themePreview: { borderWidth: 1, borderRadius: 26, padding: 15, flexDirection: 'row', alignItems: 'center', gap: 12 },
  previewEyebrow: { fontSize: 9, letterSpacing: 1.5, fontWeight: '900' },
  previewTitle: { fontSize: 15, fontWeight: '900', letterSpacing: -0.3 },
  previewMiniCard: { width: 96, height: 100, borderWidth: 1, borderRadius: 20, padding: 11, justifyContent: 'center', gap: 8 },
  previewLine: { height: 7, width: '78%', borderRadius: 5 },
  previewLineShort: { height: 5, width: '55%', borderRadius: 5 },
  previewSwatches: { flexDirection: 'row', gap: 5, marginTop: 5 },
  previewSwatch: { width: 17, height: 17, borderRadius: 7 },
  expressiveRow: { borderWidth: 1, borderRadius: 20, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 10 },
  profileAvatar: { width: 66, height: 66, borderRadius: 22, overflow: 'hidden' },
  photoButton: { minHeight: 48, alignSelf: 'flex-start', borderRadius: 14, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center', gap: 7 },
  profileInput: { borderWidth: 1, borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, fontSize: 13 },
  profileButton: { borderRadius: 12, paddingVertical: 11, alignItems: 'center' },
  profileId: { fontSize: 12, fontWeight: '800' },
  backupActions: { flexDirection: 'row', gap: 8, marginTop: 7 },
  backupButton: { borderRadius: 12, paddingHorizontal: 13, paddingVertical: 11, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
  accentChoice: { width: '48%', minHeight: 58, borderWidth: 1, borderRadius: 18, paddingHorizontal: 10, paddingVertical: 9, flexDirection: 'row', alignItems: 'center', gap: 8 },
  swatch: { width: 22, height: 22, borderRadius: 8 },
  accentLabel: { flex: 1, fontSize: 11, fontWeight: '800', lineHeight: 14 },
  choice: { borderWidth: 1, borderRadius: 18, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12 },
  glyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  choiceTitle: { fontSize: 13, fontWeight: '800' },
  hint: { fontSize: 11 },
  info: { borderWidth: 1, borderRadius: 19, padding: 16, flexDirection: 'row', alignItems: 'center', gap: 13 },
  logo: { width: 42, height: 42, borderRadius: 14, overflow: 'hidden' },
  credit: { borderWidth: 1, borderRadius: 19, padding: 13, flexDirection: 'row', alignItems: 'center', gap: 12 },
  creditIcon: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
});