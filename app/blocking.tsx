import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, NativeModules, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';
import { DEFAULT_BLOCKING_CONFIG, normalizeBlockingConfig, type BlockingConfig } from '@/lib/blockingConfig';

type LaunchableApp = { packageName: string; appName: string };
type BlockingDiagnostics = { reason?: string; at?: number };
type BlockingBridge = {
  isAccessibilityEnabled: () => Promise<boolean>;
  openAccessibilitySettings: () => Promise<boolean>;
  getConfig: () => Promise<BlockingConfig>;
  getLaunchableApps?: () => Promise<LaunchableApp[]>;
  getDiagnostics?: () => Promise<BlockingDiagnostics>;
  setConfig: (json: string) => Promise<boolean>;
};
const bridge = NativeModules.GoBackBlocking as BlockingBridge | undefined;

export default function BlockingScreen() {
  const theme = useAppTheme();
  const [config, setConfig] = useState<BlockingConfig>(DEFAULT_BLOCKING_CONFIG);
  const [savedConfig, setSavedConfig] = useState<BlockingConfig>(DEFAULT_BLOCKING_CONFIG);
  const [channelText, setChannelText] = useState('');
  const [appSearch, setAppSearch] = useState('');
  const [installedApps, setInstalledApps] = useState<LaunchableApp[]>([]);
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [diagnostic, setDiagnostic] = useState('');

  const refresh = useCallback(async () => {
    if (!bridge) { setLoading(false); return; }
    try {
      const [saved, accessibilityEnabled, appRows, nativeDiagnostics] = await Promise.all([
        bridge.getConfig(),
        bridge.isAccessibilityEnabled(),
        bridge.getLaunchableApps ? bridge.getLaunchableApps().catch(() => []) : Promise.resolve([] as LaunchableApp[]),
        bridge.getDiagnostics ? bridge.getDiagnostics().catch(() => ({} as BlockingDiagnostics)) : Promise.resolve({} as BlockingDiagnostics),
      ]);
      const normalized = normalizeBlockingConfig(saved);
      setConfig(normalized);
      setSavedConfig(normalized);
      setInstalledApps(Array.isArray(appRows) ? appRows.filter(row => row && typeof row.packageName === 'string' && typeof row.appName === 'string') : []);
      setChannelText('');
      setEnabled(accessibilityEnabled);
      setDiagnostic(nativeDiagnostics.reason ? `Last action: ${nativeDiagnostics.reason}` : '');
      setError('');
    } catch {
      setError('Could not read the Android blocking settings. Reopen the screen and try again.');
    } finally { setLoading(false); }
  }, []);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') void refresh(); });
    return () => subscription.remove();
  }, [refresh]);

  const hasUnsavedChanges = JSON.stringify(config) !== JSON.stringify(savedConfig);
  const update = (key: 'blockYoutube' | 'blockShorts' | 'blockReels' | 'blockOnlyDuringFocus', value: boolean) => setConfig(current => ({ ...current, [key]: value }));
  const addBlockedApp = (app: LaunchableApp) => {
    if (config.blockedPackages.includes(app.packageName)) return;
    if (config.blockedPackages.length >= 100) {
      showThemedDialog('App list is full', 'You can select up to 100 apps for whole-app blocking.');
      return;
    }
    setConfig(current => ({ ...current, blockedPackages: [...current.blockedPackages, app.packageName] }));
  };
  const removeBlockedApp = (packageName: string) => setConfig(current => ({ ...current, blockedPackages: current.blockedPackages.filter(item => item !== packageName) }));
  const matchingApps = installedApps.filter(app => {
    const query = appSearch.trim().toLocaleLowerCase();
    return !query || app.appName.toLocaleLowerCase().includes(query) || app.packageName.toLocaleLowerCase().includes(query);
  });
  const addChannel = () => {
    const clean = channelText.trim().replace(/\s+/g, ' ');
    if (!clean) { showThemedDialog('Add a YouTube channel', 'Enter a channel name or @handle, then tap Add channel.'); return; }
    if (clean.length > 80) { showThemedDialog('Channel name is too long', 'Use the channel name or @handle shown by YouTube.'); return; }
    if (config.allowedChannels.length >= 50) { showThemedDialog('Channel list is full', 'You can save up to 50 allowed channels.'); return; }
    if (config.allowedChannels.some(channel => channel.toLocaleLowerCase() === clean.toLocaleLowerCase())) { setChannelText(''); return; }
    setConfig(current => ({ ...current, allowedChannels: [...current.allowedChannels, clean] }));
    setChannelText('');
  };
  const removeChannel = (channel: string) => setConfig(current => ({ ...current, allowedChannels: current.allowedChannels.filter(item => item !== channel) }));

  const save = async () => {
    if (!bridge) { setError('Install a Go Back native Android build to configure blocking.'); return; }
    setSaving(true); setError('');
    try {
      const next = normalizeBlockingConfig(config);
      const saved = await bridge.setConfig(JSON.stringify(next));
      if (!saved) throw new Error('Native blocking service did not confirm the save');
      setConfig(next);
      setSavedConfig(next);
      showThemedDialog(
        'Blocking rules saved',
        enabled
          ? next.blockYoutube
            ? 'YouTube allowed-channel limiting is active. Disallowed videos are closed, including any mini-player that remains after leaving the watch page.'
            : 'Your saved rules can now be applied while the Accessibility service is active.'
          : 'Your rules are saved, but blocking will not take effect until you enable Go Back in Android Accessibility settings.'
      );
    } catch { setError('Could not confirm that the rules were saved. Your changes may still be pending; retry before relying on blocking.'); }
    finally { setSaving(false); }
  };

  const openSettings = async () => {
    if (!bridge) { setError('Accessibility blocking requires a native Android build, not Expo Go.'); return; }
    try { await bridge.openAccessibilitySettings(); }
    catch { setError('Could not open Accessibility settings. Find Go Back under Android Settings → Accessibility.'); }
  };

  const Toggle = ({ title, detail, value, onValueChange }: { title: string; detail: string; value: boolean; onValueChange: (next: boolean) => void }) => (
    <View style={[styles.toggleRow, { borderColor: theme.border }]}>
      <View style={{ flex: 1, gap: 4 }}><Text style={[styles.toggleTitle, { color: theme.text }]}>{title}</Text><Text style={[styles.detail, { color: theme.muted }]}>{detail}</Text></View>
      <Switch accessibilityLabel={title} accessibilityState={{ checked: value }} value={value} onValueChange={onValueChange} trackColor={{ false: theme.border, true: theme.primary }} thumbColor={value ? theme.onPrimary : '#E8EAE5'} />
    </View>
  );

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel="Go back" onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable>
      <Text style={[styles.headerTitle, { color: theme.text }]}>App blocking</Text>
      <Pressable accessibilityRole="button" accessibilityLabel="Refresh blocking status" onPress={() => void refresh()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="refresh" size={18} color={theme.text} /></Pressable>
    </View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>PROTECT YOUR ATTENTION</Text>
      <Text style={[styles.title, { color: theme.text }]}>Choose what to block.</Text>
      <Text style={[styles.subtitle, { color: theme.muted }]}>Rules stay on this phone. Go Back uses Android Accessibility to notice the active app and apply your choices.</Text>

      {!bridge && <View style={[styles.notice, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="construct-outline" size={22} color={theme.primary} /><View style={{ flex: 1, gap: 5 }}><Text style={[styles.noticeTitle, { color: theme.text }]}>Native Android build required</Text><Text style={[styles.detail, { color: theme.muted }]}>These controls need the Go Back debug APK or another native build. They do not work in Expo Go.</Text></View></View>}

      {bridge && <View style={[styles.status, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.statusDot, { backgroundColor: enabled ? theme.primary : '#E5A34D' }]} /><View style={{ flex: 1, gap: 3 }}><Text style={[styles.statusTitle, { color: theme.text }]}>{enabled ? 'Accessibility service enabled' : 'Accessibility service not enabled'}</Text><Text style={[styles.detail, { color: theme.muted }]}>{hasUnsavedChanges ? 'You have unsaved changes. Save them before relying on these rules.' : enabled ? 'Saved rules can be applied while the service is active.' : 'Rules are saved, but blocking is off until you enable the Accessibility service.'}</Text>{!!diagnostic && <Text style={[styles.diagnostic, { color: theme.muted }]}>{diagnostic}</Text>}</View></View>}

      <Text style={[styles.section, { color: theme.text }]}>Whole-app blocking</Text>
      <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <Toggle title="Limit YouTube to allowed channels" detail="When a video is not from an allowed channel, Go Back closes the player and dismisses the mini-player instead of leaving playback minimized." value={config.blockYoutube} onValueChange={v => update('blockYoutube', v)} />
        <Toggle title="Only block during focus" detail="Apply all saved app and video rules only while a focus timer or stopwatch is actively running." value={config.blockOnlyDuringFocus} onValueChange={v => update('blockOnlyDuringFocus', v)} />
      </View>

      <Text style={[styles.section, { color: theme.text }]}>Choose other apps</Text>
      <View style={[styles.card, styles.appPickerCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <Text style={[styles.detail, { color: theme.muted }]}>Every launchable app on this phone is listed below. Turn on a switch to block it. Changes take effect after saving and enabling Go Back's Accessibility service.</Text>
        <TextInput accessibilityLabel="Search installed apps" value={appSearch} onChangeText={setAppSearch} placeholder="Search all installed apps…" placeholderTextColor={theme.muted} autoCapitalize="none" style={[styles.appSearch, { color: theme.text, backgroundColor: theme.surfaceAlt, borderColor: theme.border }]} />
        <Text style={[styles.appSectionLabel, { color: theme.text }]}>{matchingApps.length} apps · {config.blockedPackages.length} selected to block</Text>
        {matchingApps.length > 0 ? <ScrollView style={styles.appResultsScroll} nestedScrollEnabled keyboardShouldPersistTaps="handled">{matchingApps.map(app => {
          const blocked = config.blockedPackages.includes(app.packageName);
          return <View key={app.packageName} style={[styles.appResultRow, { borderColor: theme.border }]}>
            <View style={[styles.appResultGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="phone-portrait-outline" size={18} color={blocked ? theme.primary : theme.muted} /></View>
            <View style={{ flex: 1, gap: 2 }}><Text numberOfLines={1} style={[styles.appResultName, { color: theme.text }]}>{app.appName}</Text><Text numberOfLines={1} style={[styles.appPackage, { color: theme.muted }]}>{app.packageName}</Text></View>
            <Switch accessibilityLabel={'Block ' + app.appName} accessibilityState={{ checked: blocked }} value={blocked} onValueChange={value => value ? addBlockedApp(app) : removeBlockedApp(app.packageName)} trackColor={{ false: theme.border, true: theme.primary }} thumbColor={blocked ? theme.onPrimary : '#E8EAE5'} />
          </View>;
        })}</ScrollView> : <Text style={[styles.appEmpty, { color: theme.muted }]}>{bridge?.getLaunchableApps ? 'No matching apps. Clear the search to see all launchable apps.' : 'Install the latest Go Back APK to load the apps on this phone.'}</Text>}
        {!bridge?.getLaunchableApps && <Text style={[styles.appEmpty, { color: theme.muted }]}>The installed-app list requires a native Android build, not Expo Go.</Text>}
      </View>

      <Text style={[styles.section, { color: theme.text }]}>Short-form video</Text>
      <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <Toggle title="Block YouTube Shorts" detail="Go Back tries to leave Shorts when its selected tab is detected." value={config.blockShorts} onValueChange={v => update('blockShorts', v)} />
        <Toggle title="Block Instagram Reels" detail="Go Back tries to leave Reels when its selected tab is detected." value={config.blockReels} onValueChange={v => update('blockReels', v)} />
      </View>

      <Text style={[styles.section, { color: theme.text }]}>YouTube channel allowlist</Text>
      <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, gap: 12, paddingVertical: 14 }]}>
        <View style={styles.allowlistIntro}>
          <View style={[styles.youtubeGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="logo-youtube" size={21} color={theme.primary} /></View>
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={[styles.toggleTitle, { color: theme.text }]}>Allowed channels</Text>
            <Text style={[styles.detail, { color: theme.muted }]}>Add the channel names or @handles you want to watch. When the YouTube limit is enabled, videos from other channels are closed.</Text>
          </View>
        </View>
        <View style={styles.channelEntryRow}>
          <TextInput value={channelText} onChangeText={setChannelText} onSubmitEditing={addChannel} returnKeyType="done" placeholder="@channelhandle or channel name" placeholderTextColor={theme.muted} autoCapitalize="none" style={[styles.channelInput, { color: theme.text, backgroundColor: theme.surfaceAlt, borderColor: theme.border }]} />
          <Pressable accessibilityRole="button" accessibilityLabel="Add allowed channel" onPress={addChannel} style={[styles.channelAddButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={22} color={theme.onPrimary} /></Pressable>
        </View>
        {config.allowedChannels.length > 0 ? <View style={styles.channelChips}>{config.allowedChannels.map(channel => <View key={channel} style={[styles.channelChip, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}><Text numberOfLines={1} style={[styles.channelChipText, { color: theme.text }]}>{channel}</Text><Pressable onPress={() => removeChannel(channel)} accessibilityLabel={'Remove ' + channel} hitSlop={6}><Ionicons name="close-circle" size={17} color={theme.muted} /></Pressable></View>)}</View> : <View style={[styles.emptyChannels, { borderColor: theme.border }]}><Ionicons name="people-outline" size={20} color={theme.muted} /><Text style={[styles.detail, { color: theme.muted, flex: 1 }]}>No allowed channels yet. If the YouTube limit is enabled, every video you start is treated as disallowed until you add at least one channel.</Text></View>}
        <Text style={[styles.detail, { color: theme.muted }]}>{config.allowedChannels.length} of 50 channels selected</Text>
      </View>

      <Pressable onPress={() => void save()} disabled={saving || loading || !bridge} style={[styles.primaryButton, { backgroundColor: theme.primary, opacity: saving || loading || !bridge ? 0.55 : 1 }]}>{saving ? <ActivityIndicator color={theme.onPrimary} /> : <Ionicons name="shield-checkmark-outline" size={18} color={theme.onPrimary} />}<Text style={[styles.primaryText, { color: theme.onPrimary }]}>{saving ? 'Saving…' : hasUnsavedChanges ? 'Save blocking rules · Changes pending' : 'Blocking rules saved'}</Text></Pressable>
      <Pressable onPress={() => void openSettings()} disabled={!bridge} style={[styles.secondaryButton, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, opacity: bridge ? 1 : 0.55 }]}><Ionicons name="settings-outline" size={18} color={theme.primary} /><Text style={[styles.secondaryText, { color: theme.text }]}>Open Android Accessibility settings</Text></Pressable>

      <View style={[styles.caution, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="information-circle-outline" size={19} color={theme.primary} /><Text style={[styles.detail, { color: theme.muted, flex: 1 }]}>Important: Android requires you to enable this service yourself. YouTube and Instagram can change their accessibility labels in future app versions, so test the rules on your phone after updates. Go Back does not upload visible screen content.</Text></View>
      {!!error && <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 48, height: 48, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 16, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 40, gap: 13 },
  eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1.1 },
  subtitle: { fontSize: 12, lineHeight: 18, marginTop: -8 },
  section: { fontSize: 18, fontWeight: '800', marginTop: 7 },
  card: { borderWidth: 1, borderRadius: 26, paddingHorizontal: 15, paddingVertical: 3 },
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 14, borderBottomWidth: StyleSheet.hairlineWidth },
  toggleTitle: { fontSize: 14, fontWeight: '800' },
  detail: { fontSize: 13, lineHeight: 19 },
  diagnostic: { fontSize: 10, lineHeight: 15 },
  status: { flexDirection: 'row', alignItems: 'center', gap: 11, padding: 15, borderWidth: 1, borderRadius: 22 },
  statusDot: { width: 10, height: 10, borderRadius: 5 },
  statusTitle: { fontSize: 13, fontWeight: '800' },
  notice: { flexDirection: 'row', gap: 12, padding: 16, borderWidth: 1, borderRadius: 18 },
  noticeTitle: { fontSize: 14, fontWeight: '800' },
  appPickerCard: { paddingVertical: 14, gap: 12 },
  appSearch: { minHeight: 46, borderWidth: 1, borderRadius: 15, paddingHorizontal: 13, fontSize: 13 },
  appSectionLabel: { fontSize: 12, fontWeight: '900' },
  appEmpty: { fontSize: 11, lineHeight: 16 },
  appResultsScroll: { maxHeight: 360, flexGrow: 0 },
  appResultRow: { flexDirection: 'row', alignItems: 'center', gap: 10, minHeight: 59, borderBottomWidth: StyleSheet.hairlineWidth, paddingVertical: 8 },
  appResultGlyph: { width: 38, height: 38, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  appResultName: { fontSize: 12, fontWeight: '800' },
  appPackage: { fontSize: 9, lineHeight: 13 },
  allowlistIntro: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  youtubeGlyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  channelEntryRow: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  channelInput: { minHeight: 44, flex: 1, borderRadius: 14, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 12 },
  channelAddButton: { width: 48, height: 48, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  channelChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  channelChip: { maxWidth: '100%', minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 7, borderWidth: 1, borderRadius: 13, paddingLeft: 11, paddingRight: 8, paddingVertical: 8 },
  channelChipText: { maxWidth: 220, fontSize: 11, fontWeight: '700' },
  emptyChannels: { flexDirection: 'row', alignItems: 'center', gap: 9, padding: 11, borderWidth: 1, borderStyle: 'dashed', borderRadius: 14 },
  primaryButton: { minHeight: 50, borderRadius: 16, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9 },
  primaryText: { fontSize: 14, fontWeight: '900' },
  secondaryButton: { minHeight: 50, borderRadius: 16, borderWidth: 1, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9 },
  secondaryText: { fontSize: 13, fontWeight: '800' },
  caution: { borderRadius: 16, padding: 14, flexDirection: 'row', gap: 9, alignItems: 'flex-start' },
  error: { fontSize: 12, lineHeight: 18 },
});
