import { showThemedDialog } from '@/state/dialog';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, NativeModules, Pressable, ScrollView, StyleSheet, Switch, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';

type BlockingConfig = {
  blockYoutube: boolean;
  blockInstagram: boolean;
  blockShorts: boolean;
  blockReels: boolean;
  allowedChannels: string[];
};
type BlockingBridge = {
  isAccessibilityEnabled: () => Promise<boolean>;
  openAccessibilitySettings: () => Promise<boolean>;
  getConfig: () => Promise<BlockingConfig>;
  setConfig: (json: string) => Promise<boolean>;
};
const bridge = NativeModules.GoBackBlocking as BlockingBridge | undefined;
const DEFAULT_CONFIG: BlockingConfig = { blockYoutube: false, blockInstagram: false, blockShorts: false, blockReels: false, allowedChannels: [] };

export default function BlockingScreen() {
  const theme = useAppTheme();
  const [config, setConfig] = useState<BlockingConfig>(DEFAULT_CONFIG);
  const [channelText, setChannelText] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    if (!bridge) { setLoading(false); return; }
    try {
      const [saved, accessibilityEnabled] = await Promise.all([bridge.getConfig(), bridge.isAccessibilityEnabled()]);
      setConfig(saved);
      setChannelText('');
      setEnabled(accessibilityEnabled);
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

  const update = (key: keyof Omit<BlockingConfig, 'allowedChannels'>, value: boolean) => setConfig(current => ({ ...current, [key]: value }));
  const addChannel = () => {
    const clean = channelText.trim().replace(/\s+/g, ' ');
    if (!clean) { showThemedDialog('Add a YouTube channel', 'Enter a channel name or @handle, then tap Add channel.'); return; }
    if (config.allowedChannels.length >= 50) { showThemedDialog('Channel list is full', 'You can save up to 50 allowed channels.'); return; }
    if (config.allowedChannels.some(channel => channel.toLocaleLowerCase() === clean.toLocaleLowerCase())) {
      setChannelText('');
      return;
    }
    setConfig(current => ({ ...current, allowedChannels: [...current.allowedChannels, clean] }));
    setChannelText('');
  };
  const removeChannel = (channel: string) => setConfig(current => ({ ...current, allowedChannels: current.allowedChannels.filter(item => item !== channel) }));
  const save = async () => {
    if (!bridge) { setError('Install a Go Back native Android build to configure blocking.'); return; }
    setSaving(true); setError('');
    try {
      const next = { ...config, allowedChannels: config.allowedChannels.slice(0, 50) };
      await bridge.setConfig(JSON.stringify(next));
      setConfig(next);
      showThemedDialog('Blocking rules saved', 'Only channels in your allowlist are exempt from YouTube video blocking. Enable Go Back in Android Accessibility settings for the rules to take effect.');
    } catch { setError('Could not save blocking rules. Please try again.'); }
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
      <Switch value={value} onValueChange={onValueChange} trackColor={{ false: theme.border, true: theme.primary }} thumbColor={value ? theme.background : '#E8EAE5'} />
    </View>
  );

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}>
      <Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable>
      <Text style={[styles.headerTitle, { color: theme.text }]}>App blocking</Text>
      <Pressable onPress={() => void refresh()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="refresh" size={18} color={theme.text} /></Pressable>
    </View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow, { color: theme.muted }]}>PROTECT YOUR ATTENTION</Text>
      <Text style={[styles.title, { color: theme.text }]}>Choose what to block.</Text>
      <Text style={[styles.subtitle, { color: theme.muted }]}>Rules stay on this phone. Go Back uses Android Accessibility to notice the active app and apply your choices.</Text>

      {!bridge && <View style={[styles.notice, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name="construct-outline" size={22} color={theme.primary} /><View style={{ flex: 1, gap: 5 }}><Text style={[styles.noticeTitle, { color: theme.text }]}>Native Android build required</Text><Text style={[styles.detail, { color: theme.muted }]}>These controls need the Go Back debug APK or another native build. They do not work in Expo Go.</Text></View></View>}

      {bridge && <View style={[styles.status, { backgroundColor: theme.surface, borderColor: theme.border }]}><View style={[styles.statusDot, { backgroundColor: enabled ? theme.primary : '#E5A34D' }]} /><View style={{ flex: 1, gap: 3 }}><Text style={[styles.statusTitle, { color: theme.text }]}>{enabled ? 'Accessibility service enabled' : 'Accessibility service not enabled'}</Text><Text style={[styles.detail, { color: theme.muted }]}>{enabled ? 'Saved rules can be applied while the service is active.' : 'Save your rules, then enable Go Back in Android Accessibility settings.'}</Text></View></View>}

      <Text style={[styles.section, { color: theme.text }]}>Whole-app blocking</Text>
      <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }]}>
        <Toggle title="Limit YouTube to allowed channels" detail="Opening YouTube and browsing its feed stays allowed. Only active playback from a channel outside this list is stopped." value={config.blockYoutube} onValueChange={v => update('blockYoutube', v)} />
        <Toggle title="Block Instagram" detail="Return to the home screen when Instagram opens." value={config.blockInstagram} onValueChange={v => update('blockInstagram', v)} />
      </View>

      <Text style={[styles.section, { color: theme.text }]}>Short-form video</Text>
      <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }]}>
        <Toggle title="Block YouTube Shorts" detail="Go Back tries to leave Shorts when its selected tab is detected." value={config.blockShorts} onValueChange={v => update('blockShorts', v)} />
        <Toggle title="Block Instagram Reels" detail="Go Back tries to leave Reels when its selected tab is detected." value={config.blockReels} onValueChange={v => update('blockReels', v)} />
      </View>

      <Text style={[styles.section, { color: theme.text }]}>YouTube channel allowlist</Text>
      <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border, gap: 12, paddingVertical: 14 }]}>
        <View style={styles.allowlistIntro}>
          <View style={[styles.youtubeGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="logo-youtube" size={21} color={theme.primary} /></View>
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={[styles.toggleTitle, { color: theme.text }]}>Allowed channels</Text>
            <Text style={[styles.detail, { color: theme.muted }]}>Add only the channel names or @handles you want to watch. Other video screens are blocked when the YouTube limit is on.</Text>
          </View>
        </View>
        <View style={styles.channelEntryRow}>
          <TextInput value={channelText} onChangeText={setChannelText} onSubmitEditing={addChannel} returnKeyType="done" placeholder="@channelhandle or channel name" placeholderTextColor={theme.muted} autoCapitalize="none" style={[styles.channelInput, { color: theme.text, backgroundColor: theme.surfaceAlt, borderColor: theme.border }]} />
          <Pressable accessibilityLabel="Add allowed channel" onPress={addChannel} style={[styles.channelAddButton, { backgroundColor: theme.primary }]}><Ionicons name="add" size={22} color={theme.background} /></Pressable>
        </View>
        {config.allowedChannels.length > 0 ? <View style={styles.channelChips}>{config.allowedChannels.map(channel => <View key={channel} style={[styles.channelChip, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}><Text numberOfLines={1} style={[styles.channelChipText, { color: theme.text }]}>{channel}</Text><Pressable onPress={() => removeChannel(channel)} accessibilityLabel={'Remove ' + channel} hitSlop={6}><Ionicons name="close-circle" size={17} color={theme.muted} /></Pressable></View>)}</View> : <View style={[styles.emptyChannels, { borderColor: theme.border }]}><Ionicons name="people-outline" size={20} color={theme.muted} /><Text style={[styles.detail, { color: theme.muted, flex: 1 }]}>No allowed channels yet. YouTube and its feed stay available; any video you start playing is treated as not allowed.</Text></View>}
        <Text style={[styles.detail, { color: theme.muted }]}>{config.allowedChannels.length} of 50 channels selected</Text>
      </View>

      <Pressable onPress={() => void save()} disabled={saving || loading || !bridge} style={[styles.primaryButton, { backgroundColor: theme.primary, opacity: saving || loading || !bridge ? 0.55 : 1 }]}>{saving ? <ActivityIndicator color={theme.background} /> : <Ionicons name="shield-checkmark-outline" size={18} color={theme.background} />}<Text style={[styles.primaryText, { color: theme.background }]}>{saving ? 'Saving…' : 'Save blocking rules'}</Text></Pressable>
      <Pressable onPress={() => void openSettings()} disabled={!bridge} style={[styles.secondaryButton, { backgroundColor: theme.surface, borderColor: theme.border, opacity: bridge ? 1 : 0.55 }]}><Ionicons name="settings-outline" size={18} color={theme.primary} /><Text style={[styles.secondaryText, { color: theme.text }]}>Open Android Accessibility settings</Text></Pressable>

      <View style={[styles.caution, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="information-circle-outline" size={19} color={theme.primary} /><Text style={[styles.detail, { color: theme.muted, flex: 1 }]}>Important: Android requires you to enable this service yourself. Detection depends on what YouTube and Instagram expose to accessibility; app updates, language, ads, or recommendations can affect Shorts/Reels and channel matching. Test each rule before relying on it. Go Back does not read passwords or send screen content to a server.</Text></View>
      {!!error && <Text style={styles.error}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 16, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 40, gap: 13 },
  eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1.1 },
  subtitle: { fontSize: 12, lineHeight: 18, marginTop: -8 },
  section: { fontSize: 18, fontWeight: '800', marginTop: 7 },
  card: { borderWidth: 1, borderRadius: 22, paddingHorizontal: 15, paddingVertical: 3 },
  toggleRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 14, borderBottomWidth: StyleSheet.hairlineWidth },
  toggleTitle: { fontSize: 14, fontWeight: '800' },
  detail: { fontSize: 12, lineHeight: 18 },
  status: { flexDirection: 'row', alignItems: 'center', gap: 11, padding: 15, borderWidth: 1, borderRadius: 18 },
  statusDot: { width: 10, height: 10, borderRadius: 5 },
  statusTitle: { fontSize: 13, fontWeight: '800' },
  notice: { flexDirection: 'row', gap: 12, padding: 16, borderWidth: 1, borderRadius: 18 },
  noticeTitle: { fontSize: 14, fontWeight: '800' },
  allowlistIntro: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  youtubeGlyph: { width: 42, height: 42, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  channelEntryRow: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  channelInput: { minHeight: 44, flex: 1, borderRadius: 14, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 12 },
  channelAddButton: { width: 44, height: 44, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  channelChips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7 },
  channelChip: { maxWidth: '100%', flexDirection: 'row', alignItems: 'center', gap: 7, borderWidth: 1, borderRadius: 13, paddingLeft: 11, paddingRight: 8, paddingVertical: 8 },
  channelChipText: { maxWidth: 220, fontSize: 11, fontWeight: '700' },
  emptyChannels: { flexDirection: 'row', alignItems: 'center', gap: 9, padding: 11, borderWidth: 1, borderStyle: 'dashed', borderRadius: 14 },
  primaryButton: { minHeight: 50, borderRadius: 16, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9 },
  primaryText: { fontSize: 14, fontWeight: '900' },
  secondaryButton: { minHeight: 50, borderRadius: 16, borderWidth: 1, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 9 },
  secondaryText: { fontSize: 13, fontWeight: '800' },
  caution: { borderRadius: 16, padding: 14, flexDirection: 'row', gap: 9, alignItems: 'flex-start' },
  error: { fontSize: 12, color: '#D85D52', lineHeight: 18 },
});
