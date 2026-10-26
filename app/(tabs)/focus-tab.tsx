import { router } from 'expo-router';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';

export default function FocusTab() {
  const theme = useAppTheme();
  return <Screen>
    <Text style={[styles.eyebrow, { color: theme.muted }]}>MAKE SPACE FOR WHAT MATTERS</Text>
    <Text style={[styles.title, { color: theme.text }]}>Focus mode</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>One thing at a time is enough.</Text>
    <View style={[styles.panel, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
      <View style={[styles.ring, { borderColor: theme.primary }]}>
        <Ionicons name="hourglass-outline" size={38} color={theme.primary} />
        <Text style={[styles.time, { color: theme.text }]}>25:00</Text>
        <Text style={[styles.label, { color: theme.muted }]}>READY WHEN YOU ARE</Text>
      </View>
      <Pressable accessibilityRole="button" onPress={() => router.push('/focus')} style={[styles.button, { backgroundColor: theme.primary }]}>
        <Ionicons name="play" size={18} color={theme.onPrimary} />
        <Text style={[styles.buttonText, { color: theme.onPrimary }]}>Open focus timer</Text>
      </Pressable>
    </View>
    <Text style={[styles.smallTitle, { color: theme.text }]}>Choose how you focus</Text>
    <Text style={[styles.sub, { color: theme.muted }]}>Use a flexible countdown, a guided Pomodoro cycle, or a stopwatch to track open-ended study sessions.</Text>
    <Pressable accessibilityRole="button" accessibilityLabel="Open focus session history" onPress={() => router.push('/sessions')} style={[styles.historyCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
      <View style={[styles.historyIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="time-outline" size={22} color={theme.primary} /></View>
      <View style={{ flex: 1, gap: 3 }}>
        <Text style={[styles.historyTitle, { color: theme.text }]}>Session history</Text>
        <Text style={[styles.historyBody, { color: theme.muted }]}>Review saved sessions, tags, notes and weekly focus time.</Text>
      </View>
      <Ionicons name="chevron-forward" size={20} color={theme.muted} />
    </Pressable>
    <Pressable accessibilityRole="button" accessibilityLabel="Open focus schedule" onPress={() => router.push('/schedule')} style={[styles.historyCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
      <View style={[styles.historyIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="calendar-outline" size={22} color={theme.primary} /></View>
      <View style={{ flex: 1, gap: 3 }}>
        <Text style={[styles.historyTitle, { color: theme.text }]}>Focus schedule</Text>
        <Text style={[styles.historyBody, { color: theme.muted }]}>Plan repeating reminders for your weekly study routine.</Text>
      </View>
      <Ionicons name="chevron-forward" size={20} color={theme.muted} />
    </Pressable>
  </Screen>;
}
const styles = StyleSheet.create({
  eyebrow: { fontSize: 10, letterSpacing: 1.5, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1.2, marginTop: -12 },
  sub: { fontSize: 13, lineHeight: 20 },
  panel: { borderWidth: 1, borderRadius: 32, padding: 24, alignItems: 'center', gap: 25 },
  ring: { width: 220, height: 220, borderRadius: 114, borderWidth: 5, alignItems: 'center', justifyContent: 'center', gap: 7 },
  time: { fontSize: 40, fontWeight: '800', letterSpacing: -1.5, fontVariant: ['tabular-nums'] },
  label: { fontSize: 9, fontWeight: '800', letterSpacing: 1.5 },
  button: { borderRadius: 16, paddingVertical: 15, paddingHorizontal: 20, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10, width: '100%', minHeight: 50 },
  buttonText: { fontWeight: '800', fontSize: 14 },
  smallTitle: { fontSize: 17, fontWeight: '800', marginTop: 5 },
  historyCard: { borderWidth: 1, borderRadius: 22, padding: 14, flexDirection: 'row', alignItems: 'center', gap: 12, marginTop: 2 },
  historyIcon: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  historyTitle: { fontSize: 14, fontWeight: '900' },
  historyBody: { fontSize: 11, lineHeight: 16 },
});
