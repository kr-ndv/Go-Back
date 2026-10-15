import { router, useFocusEffect } from 'expo-router';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { MotionReveal } from '@/components/MotionReveal';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';

function Card({ children, style }: { children: React.ReactNode; style?: object }) {
  const theme = useAppTheme();
  return <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }, style]}>{children}</View>;
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
        <Pressable accessibilityLabel="Open local profile settings" onPress={() => router.push("/(tabs)/settings")} style={[styles.avatar, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}>{profileName.trim() ? <Text style={{ color: theme.primary, fontSize: 20, fontWeight: '900' }}>{profileName.trim().charAt(0).toLocaleUpperCase()}</Text> : <Ionicons name="person-outline" size={22} color={theme.primary} />}</Pressable>
      </View>
      <Text style={[styles.greeting, { color: theme.text }]}>{profileName.trim() ? `Welcome back, ${profileName.trim()}.` : 'Welcome back.'}</Text>
      <Text style={[styles.sub, { color: theme.muted }]}>Make room for what matters today.</Text>

      <MotionReveal delay={70}>
      <Pressable onPress={() => router.push('/focus')} style={[styles.hero, { backgroundColor: theme.primary, borderRadius: theme.expressive ? 32 : 24 }]}>
        <View style={styles.heroTop}>
          <View style={styles.liveDot} />
          <Text style={[styles.heroLabel, { color: theme.background }]}>TIME FOR YOURSELF</Text>
          <Ionicons name="arrow-forward-outline" size={20} color={theme.background} style={{ marginLeft: 'auto' }} />
        </View>
        <Text style={[styles.heroTitle, { color: theme.background }]}>Find your{ '\n' }focus.</Text>
        <View style={styles.heroBottom}>
          <Text style={[styles.heroHint, { color: theme.background }]}>Start a focused session</Text>
          <View style={[styles.play, { backgroundColor: theme.background }]}><Ionicons name="play" size={17} color={theme.primary} /></View>
        </View>
      </Pressable>
      </MotionReveal>

      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Your overview</Text>
        <Text style={[styles.sectionMeta, { color: theme.muted }]}>AT A GLANCE</Text>
      </View>
      <MotionReveal delay={130}>
      <View style={styles.grid}>
        <Card style={styles.statCard}>
          <View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="checkmark-done-outline" size={19} color={theme.primary} /></View>
          <Text style={[styles.statValue, { color: theme.text }]}>{doneCount}/{taskCount}</Text>
          <Text style={[styles.statLabel, { color: theme.muted }]}>Tasks completed</Text><View style={[styles.progressTrack, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.progressFill, { backgroundColor: theme.primary, width: ((taskCount ? Math.max(0, Math.min(100, doneCount / taskCount * 100)) : 0) + "%") as `${number}%` }]} /></View>
        </Card>
        <Card style={styles.statCard}>
          <View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="hourglass-outline" size={19} color={theme.primary} /></View>
          <Text style={[styles.statValue, { color: theme.text }]}>25 min</Text>
          <Text style={[styles.statLabel, { color: theme.muted }]}>Next focus goal</Text>
        </Card>
      </View>
      </MotionReveal>

      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Quick actions</Text>
      </View>
      <MotionReveal delay={190}>
      <View style={styles.actions}>
        <Action icon="add-circle-outline" label="Add a task" onPress={() => router.push('/(tabs)/tasks')} theme={theme} />
        <Action icon="document-text-outline" label="Write a note" onPress={() => router.push('/(tabs)/notes')} theme={theme} />
        <Action icon="calendar-outline" label="Exam countdown & timetable" onPress={() => router.push('/exams')} theme={theme} />
        <Action icon="book-outline" label="Syllabus tracker" onPress={() => router.push('/syllabus')} theme={theme} />
        <Action icon="leaf-outline" label="Daily habits & streaks" onPress={() => router.push('/habits')} theme={theme} />
        <Action icon="flag-outline" label="Goals & progress" onPress={() => router.push('/goals')} theme={theme} />
        <Action icon="analytics-outline" label="Screen-time insights" onPress={() => router.push('/usage')} theme={theme} />
        <Action icon="shield-checkmark-outline" label="App & Shorts blocking" onPress={() => router.push('/blocking')} theme={theme} />
      </View>
      </MotionReveal>
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
  title: { fontSize: 32, fontWeight: '900', letterSpacing: -1.3 },
  avatar: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center', overflow: 'hidden', borderWidth: 1 },
  greeting: { fontSize: 27, fontWeight: '800', letterSpacing: -0.8, marginTop: 8 },
  sub: { fontSize: 13, lineHeight: 19, marginTop: -12 },
  hero: { borderRadius: 32, padding: 24, minHeight: 232, justifyContent: 'space-between', overflow: 'hidden' },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  liveDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#B8F36B' },
  heroLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6, opacity: 0.78 },
  heroTitle: { fontSize: 40, lineHeight: 43, fontWeight: '900', letterSpacing: -1.6, marginVertical: 15 },
  heroBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heroHint: { fontSize: 13, fontWeight: '600', opacity: 0.82 },
  play: { width: 42, height: 42, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 3 },
  sectionTitle: { fontSize: 18, fontWeight: '800', letterSpacing: -0.4 },
  sectionMeta: { fontSize: 9, letterSpacing: 1.4, fontWeight: '800' },
  grid: { flexDirection: 'row', gap: 12 },
  card: { borderWidth: 1, borderRadius: 26, padding: 17 },
  statCard: { flex: 1, minHeight: 140 },
  iconBubble: { width: 35, height: 35, borderRadius: 12, alignItems: 'center', justifyContent: 'center', marginBottom: 13 },
  statValue: { fontSize: 23, fontWeight: '800', letterSpacing: -0.7 },
  statLabel: { fontSize: 11, marginTop: 3 },
  progressTrack: { height: 5, borderRadius: 3, overflow: 'hidden', marginTop: 12 },
  progressFill: { height: '100%', borderRadius: 3 },
  actions: { gap: 9 },
  action: { borderWidth: 1, borderRadius: 20, paddingHorizontal: 15, paddingVertical: 16, flexDirection: 'row', alignItems: 'center', gap: 12 },
  actionText: { fontSize: 13, fontWeight: '600' },
  footer: { textAlign: 'center', fontSize: 9, letterSpacing: 2, fontWeight: '800', marginTop: 5 },
});