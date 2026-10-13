import { router } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';

export default function FocusTab() {
  const theme = useAppTheme();
  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>DO NOT DISTURB YOUR DREAMS</Text>
    <Text style={[styles.title, { color: theme.text }]}>Focus mode</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>One thing at a time is enough.</Text>
    <View style={[styles.panel, { backgroundColor: theme.surface, borderColor: theme.border }]}>
      <View style={[styles.ring, { borderColor: theme.primary }]}><Ionicons name="hourglass-outline" size={38} color={theme.primary} /><Text style={[styles.time, { color: theme.text }]}>25:00</Text><Text style={[styles.label, { color: theme.muted }]}>READY WHEN YOU ARE</Text></View>
      <Pressable onPress={() => router.push('/focus')} style={[styles.button, { backgroundColor: theme.primary }]}><Ionicons name="play" size={18} color={theme.background} /><Text style={[styles.buttonText, { color: theme.background }]}>Start focus session</Text></Pressable>
    </View>
    <Text style={[styles.smallTitle, { color: theme.text }]}>A little structure goes a long way</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>Choose a session, put distractions aside, and take breaks when you need them. The full timer is being built next.</Text>
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 1.5, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 13, lineHeight: 20 },
  panel: { borderWidth: 1, borderRadius: 28, padding: 24, alignItems: 'center', gap: 25 },
  ring: { width: 220, height: 220, borderRadius: 110, borderWidth: 5, alignItems: 'center', justifyContent: 'center', gap: 7 },
  time: { fontSize: 40, fontWeight: '800', letterSpacing: -1.5 },
  label: { fontSize: 9, fontWeight: '800', letterSpacing: 1.5 },
  button: { borderRadius: 16, paddingVertical: 15, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, width: '100%' },
  buttonText: { fontWeight: '800', fontSize: 14 },
  smallTitle: { fontSize: 17, fontWeight: '800', marginTop: 5 },
});