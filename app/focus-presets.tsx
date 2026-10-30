import { useCallback, useState } from 'react';
import { NativeModules, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getDatabase, setSetting } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { parsePresetPackages, type FocusPreset } from '@/lib/focusPresets';
import type { FocusMode } from '@/lib/focusLogic';
import { showThemedDialog } from '@/state/dialog';

type LaunchableApp = { packageName: string; appName: string };
type BlockingBridge = { getLaunchableApps?: () => Promise<LaunchableApp[]> };
const blocking = NativeModules.GoBackBlocking as BlockingBridge | undefined;

const MODES: { value: FocusMode; label: string }[] = [
  { value: 'timer', label: 'Timer' }, { value: 'pomodoro', label: 'Pomodoro' }, { value: 'stopwatch', label: 'Stopwatch' },
];

export default function FocusPresetsScreen() {
  const theme = useAppTheme();
  const [presets, setPresets] = useState<FocusPreset[]>([]);
  const [apps, setApps] = useState<LaunchableApp[]>([]);
  const [name, setName] = useState('');
  const [mode, setMode] = useState<FocusMode>('timer');
  const [duration, setDuration] = useState('25');
  const [search, setSearch] = useState('');
  const [selectedPackages, setSelectedPackages] = useState<string[]>([]);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const db = await getDatabase();
      const [rows, appRows] = await Promise.all([
        db.getAllAsync<FocusPreset>('SELECT * FROM focus_presets ORDER BY created_at DESC'),
        blocking?.getLaunchableApps ? blocking.getLaunchableApps().catch(() => []) : Promise.resolve([]),
      ]);
      setPresets(rows);
      setApps(Array.isArray(appRows) ? appRows : []);
      setError('');
    } catch { setError('Focus presets could not be loaded.'); }
  }, []);

  useFocusEffect(useCallback(() => { void load(); }, [load]));

  const togglePackage = (packageName: string) => setSelectedPackages(current => current.includes(packageName) ? current.filter(item => item !== packageName) : current.length < 100 ? [...current, packageName] : current);
  const visibleApps = apps.filter(app => !search.trim() || app.appName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()) || app.packageName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));

  const save = async () => {
    const cleanName = name.trim();
    const minutes = Number(duration);
    if (!cleanName) { showThemedDialog('Name this preset', 'Try Study, Deep Work, Reading or Sleep.'); return; }
    if (mode !== 'stopwatch' && (!Number.isInteger(minutes) || minutes < 1 || minutes > 180)) { showThemedDialog('Check the duration', 'Choose a whole number from 1 to 180 minutes.'); return; }
    try {
      const db = await getDatabase();
      await db.runAsync(
        'INSERT INTO focus_presets (id, name, mode, duration_minutes, blocked_packages_json, created_at) VALUES (?, ?, ?, ?, ?, ?)',
        createLocalId('preset'), cleanName.slice(0, 60), mode, mode === 'stopwatch' ? 25 : minutes, JSON.stringify(selectedPackages), new Date().toISOString()
      );
      setName(''); setMode('timer'); setDuration('25'); setSelectedPackages([]); setSearch(''); await load();
    } catch { showThemedDialog('Preset not saved', 'Go Back could not save this preset.'); }
  };

  const apply = async (preset: FocusPreset) => {
    await setSetting('focus.pendingPreset', {
      id: preset.id, name: preset.name, mode: preset.mode, durationMinutes: preset.duration_minutes, blockedPackages: parsePresetPackages(preset.blocked_packages_json),
    });
    router.replace('/focus');
  };

  const remove = (preset: FocusPreset) => showThemedDialog('Delete preset?', `Delete “${preset.name}”?`, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Delete', style: 'destructive', onPress: async () => { const db = await getDatabase(); await db.runAsync('DELETE FROM focus_presets WHERE id = ?', preset.id); await load(); } },
  ]);

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Focus presets</Text><View style={{ width: 44 }} /></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>REUSE WHAT WORKS</Text>
      <Text style={[styles.title, { color: theme.text }]}>Your focus modes.</Text>
      <Text style={[styles.subtitle, { color: theme.muted }]}>Save a timer style and optional apps to block only for that session. Your normal blocking rules stay unchanged.</Text>

      <View style={[styles.form, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <TextInput value={name} onChangeText={setName} maxLength={60} placeholder="Preset name · e.g. Deep Work" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: theme.border }]} />
        <View style={styles.modes}>{MODES.map(item => <Pressable key={item.value} onPress={() => setMode(item.value)} style={[styles.mode, { backgroundColor: mode === item.value ? theme.primary : theme.surface, borderColor: mode === item.value ? theme.primary : theme.border }]}><Text style={{ color: mode === item.value ? theme.onPrimary : theme.text, fontWeight: '800', fontSize: 11 }}>{item.label}</Text></Pressable>)}</View>
        {mode !== 'stopwatch' && <TextInput value={duration} onChangeText={setDuration} keyboardType="number-pad" maxLength={3} placeholder="Duration in minutes" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: theme.border }]} />}
        <Text style={[styles.label, { color: theme.text }]}>Block during this preset · optional</Text>
        <TextInput value={search} onChangeText={setSearch} placeholder="Search installed apps…" placeholderTextColor={theme.muted} style={[styles.input, { color: theme.text, backgroundColor: theme.surface, borderColor: theme.border }]} />
        {apps.length ? <ScrollView nestedScrollEnabled style={styles.appScroll}>{visibleApps.map(app => { const selected = selectedPackages.includes(app.packageName); return <Pressable key={app.packageName} onPress={() => togglePackage(app.packageName)} style={[styles.appRow, { borderBottomColor: theme.border }]}><View style={[styles.appIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="apps-outline" size={17} color={selected ? theme.primary : theme.muted} /></View><Text numberOfLines={1} style={[styles.appName, { color: theme.text }]}>{app.appName}</Text><Ionicons name={selected ? 'checkbox' : 'square-outline'} size={20} color={selected ? theme.primary : theme.muted} /></Pressable>; })}</ScrollView> : <Text style={[styles.hint, { color: theme.muted }]}>A native Android build is required to select installed apps. You can still save a timer-only preset.</Text>}
        <Pressable onPress={() => void save()} style={[styles.primary, { backgroundColor: theme.primary }]}><Ionicons name="add" size={18} color={theme.onPrimary} /><Text style={{ color: theme.onPrimary, fontWeight: '900' }}>Save preset</Text></Pressable>
      </View>

      <Text style={[styles.section, { color: theme.text }]}>Saved presets</Text>
      {presets.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="options-outline" size={25} color={theme.primary} /><Text style={[styles.hint, { color: theme.muted }]}>No presets yet. Your current timer defaults are unchanged.</Text></View> : presets.map(preset => <View key={preset.id} style={[styles.presetCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={{ flex: 1, gap: 4 }}><Text style={[styles.presetTitle, { color: theme.text }]}>{preset.name}</Text><Text style={[styles.hint, { color: theme.muted }]}>{preset.mode === 'stopwatch' ? 'Stopwatch' : `${preset.mode === 'pomodoro' ? 'Pomodoro' : 'Timer'} · ${preset.duration_minutes} min`} · {parsePresetPackages(preset.blocked_packages_json).length} session app block(s)</Text></View><Pressable onPress={() => void apply(preset)} style={[styles.apply, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontSize: 11, fontWeight: '900' }}>Use</Text></Pressable><Pressable accessibilityLabel={`Delete ${preset.name}`} onPress={() => remove(preset)} style={styles.iconButton}><Ionicons name="trash-outline" size={18} color={theme.muted} /></Pressable></View>)}
      {!!error && <Text style={{ color: theme.danger, fontSize: 12 }}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe:{flex:1}, header:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingHorizontal:20,paddingVertical:10}, back:{width:44,height:44,borderRadius:16,alignItems:'center',justifyContent:'center'}, headerTitle:{fontSize:16,fontWeight:'800'}, content:{padding:20,paddingBottom:40,gap:13}, eyebrow:{fontSize:10,fontWeight:'800',letterSpacing:1.6}, title:{fontSize:28,fontWeight:'900',letterSpacing:-1}, subtitle:{fontSize:12,lineHeight:18,marginTop:-7}, form:{borderWidth:1,borderRadius:24,padding:14,gap:10}, input:{borderWidth:1,borderRadius:14,minHeight:46,paddingHorizontal:12,fontSize:12}, modes:{flexDirection:'row',gap:7}, mode:{flex:1,minHeight:42,borderWidth:1,borderRadius:13,alignItems:'center',justifyContent:'center'}, label:{fontSize:12,fontWeight:'800'}, hint:{fontSize:11,lineHeight:17}, appScroll:{maxHeight:260}, appRow:{minHeight:52,flexDirection:'row',alignItems:'center',gap:9,borderBottomWidth:StyleSheet.hairlineWidth}, appIcon:{width:36,height:36,borderRadius:12,alignItems:'center',justifyContent:'center'}, appName:{flex:1,fontSize:12,fontWeight:'700'}, primary:{minHeight:48,borderRadius:15,flexDirection:'row',alignItems:'center',justifyContent:'center',gap:8}, section:{fontSize:18,fontWeight:'800',marginTop:4}, empty:{borderWidth:1,borderRadius:22,padding:20,alignItems:'center',gap:9}, presetCard:{borderWidth:1,borderRadius:18,padding:13,flexDirection:'row',alignItems:'center',gap:9}, presetTitle:{fontSize:14,fontWeight:'800'}, apply:{minWidth:52,minHeight:38,borderRadius:12,alignItems:'center',justifyContent:'center'}, iconButton:{width:42,height:42,alignItems:'center',justifyContent:'center'}
});
