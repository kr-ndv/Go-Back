import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';

function Card({ children, style }: { children: React.ReactNode; style?: object }) {
  const theme = useAppTheme();
  return <View style={[styles.card, { backgroundColor: theme.surface, borderColor: theme.border }, style]}>{children}</View>;
}

export default function TodayScreen() {
  const theme = useAppTheme();
  const [taskCount, setTaskCount] = useState(0);
  const [doneCount, setDoneCount] = useState(0);
  const [profileName, setProfileName] = useState('');

  useFocusEffect(useCallback(() => {
    let active = true;
    const load = async () => {
      const db = await getDatabase();
      const [counts, name] = await Promise.all([
        db.getFirstAsync<{ total: number; done: number }>('SELECT COUNT(*) as total, SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END) as done FROM tasks'),
        getSetting<string>('profile.name', ''),
      ]);
      if (active) { setTaskCount(counts?.total ?? 0); setDoneCount(counts?.done ?? 0); setProfileName(name); }
    };
    void load();
    return () => { active = false; };
  }, []));

  return (
    <Screen>
      <View style={styles.topRow}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.muted }]}>YOUR SPACE</Text>
          <Text style={[styles.title, { color: theme.text }]}>Go Back<Text style={{ color: theme.primary }}>.</Text></Text>
        </View>
        <View style={[styles.avatar, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="person-outline" size={22} color={theme.primary} /></View>
      </View>
      <Text style={[styles.greeting, { color: theme.text }]}>{profileName.trim() ? `Make today count, ${profileName.trim()}.` : 'Make today count.'}</Text>
      <Text style={[styles.sub, { color: theme.muted }]}>Small steps. Real progress. Less distraction.</Text>

      <Pressable onPress={() => router.push('/focus')} style={styles.hero}>
        <View style={styles.heroTop}>
          <View style={styles.liveDot} />
          <Text style={styles.heroLabel}>TIME FOR YOURSELF</Text>
          <Ionicons name="arrow-up-right" size={20} color="#D8F9B3" style={{ marginLeft: 'auto' }} />
        </View>
        <Text style={styles.heroTitle}>Find your{ '\n' }focus.</Text>
        <View style={styles.heroBottom}>
          <Text style={styles.heroHint}>Start a focused session</Text>
          <View style={styles.play}><Ionicons name="play" size={17} color="#14251B" /></View>
        </View>
      </Pressable>

      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Your day</Text>
        <Text style={[styles.sectionMeta, { color: theme.muted }]}>AT A GLANCE</Text>
      </View>
      <View style={styles.grid}>
        <Card style={styles.statCard}>
          <View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="checkmark-done-outline" size={19} color={theme.primary} /></View>
          <Text style={[styles.statValue, { color: theme.text }]}>{doneCount}/{taskCount}</Text>
          <Text style={[styles.statLabel, { color: theme.muted }]}>Tasks completed</Text>
        </Card>
        <Card style={styles.statCard}>
          <View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="hourglass-outline" size={19} color={theme.primary} /></View>
          <Text style={[styles.statValue, { color: theme.text }]}>25 min</Text>
          <Text style={[styles.statLabel, { color: theme.muted }]}>Next focus goal</Text>
        </Card>
      </View>

      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Quick actions</Text>
      </View>
      <View style={styles.actions}>
        <Action icon="add-circle-outline" label="Add a task" onPress={() => router.push('/(tabs)/tasks')} theme={theme} />
        <Action icon="document-text-outline" label="Write a note" onPress={() => router.push('/(tabs)/notes')} theme={theme} />
        <Action icon="calendar-outline" label="Exam countdown & timetable" onPress={() => router.push('/exams')} theme={theme} />
        <Action icon="book-outline" label="Syllabus tracker" onPress={() => router.push('/syllabus')} theme={theme} />
        <Action icon="leaf-outline" label="Daily habits & streaks" onPress={() => router.push('/habits')} theme={theme} />
        <Action icon="flag-outline" label="Goals & progress" onPress={() => router.push('/goals')} theme={theme} />
      </View>
      <Text style={[styles.footer, { color: theme.muted }]}>YOUR TIME. YOUR TERMS.</Text>
    </Screen>
  );
}

function Action({ icon, label, onPress, theme }: { icon: keyof typeof Ionicons.glyphMap; label: string; onPress: () => void; theme: ReturnType<typeof useAppTheme> }) {
  return <Pressable onPress={onPress} style={[styles.action, { backgroundColor: theme.surface, borderColor: theme.border }]}><Ionicons name={icon} size={21} color={theme.primary} /><Text style={[styles.actionText, { color: theme.text }]}>{label}</Text><Ionicons name="chevron-forward" size={16} color={theme.muted} style={{ marginLeft: 'auto' }} /></Pressable>;
}

const styles = StyleSheet.create({
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  eyebrow: { fontSize: 10, letterSpacing: 2.2, fontWeight: '800', marginBottom: 4 },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1 },
  avatar: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  greeting: { fontSize: 25, fontWeight: '800', letterSpacing: -0.7, marginTop: 8 },
  sub: { fontSize: 13, marginTop: -12 },
  hero: { backgroundColor: '#17352A', borderRadius: 28, padding: 22, minHeight: 222, justifyContent: 'space-between', overflow: 'hidden' },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  liveDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#B8F36B' },
  heroLabel: { color: '#B9D2C1', fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  heroTitle: { color: '#F5FAF2', fontSize: 37, lineHeight: 41, fontWeight: '800', letterSpacing: -1.4, marginVertical: 15 },
  heroBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heroHint: { color: '#D0DFD3', fontSize: 13, fontWeight: '500' },
  play: { width: 42, height: 42, borderRadius: 15, backgroundColor: '#B8F36B', alignItems: 'center', justifyContent: 'center' },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 3 },
  sectionTitle: { fontSize: 18, fontWeight: '800', letterSpacing: -0.4 },
  sectionMeta: { fontSize: 9, letterSpacing: 1.4, fontWeight: '800' },
  grid: { flexDirection: 'row', gap: 12 },
  card: { borderWidth: 1, borderRadius: 22, padding: 16 },
  statCard: { flex: 1, minHeight: 132 },
  iconBubble: { width: 35, height: 35, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginBottom: 13 },
  statValue: { fontSize: 23, fontWeight: '800', letterSpacing: -0.7 },
  statLabel: { fontSize: 11, marginTop: 3 },
  actions: { gap: 9 },
  action: { borderWidth: 1, borderRadius: 17, paddingHorizontal: 14, paddingVertical: 15, flexDirection: 'row', alignItems: 'center', gap: 12 },
  actionText: { fontSize: 13, fontWeight: '600' },
  footer: { textAlign: 'center', fontSize: 9, letterSpacing: 2, fontWeight: '800', marginTop: 5 },
});