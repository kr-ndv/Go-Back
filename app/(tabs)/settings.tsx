import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme, useThemeStore } from '@/state/theme';
import type { AccentColor, ThemeMode } from '@/theme/palette';

export default function SettingsScreen() {
  const theme = useAppTheme();
  const mode = useThemeStore(s => s.mode);
  const setMode = useThemeStore(s => s.setMode);
  const accent = useThemeStore(s => s.accent);
  const setAccent = useThemeStore(s => s.setAccent);
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
    <Text style={[styles.section, { color: theme.text }]}>Privacy & data</Text>
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