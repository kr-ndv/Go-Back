import { router, useFocusEffect } from 'expo-router';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { MotionReveal } from '@/components/MotionReveal';
import { useCallback, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';

type DashboardStats = {
  tasksDone: number; tasksTotal: number; notes: number; nextExam: string;
  chaptersDone: number; chaptersTotal: number; habitsDone: number; habitsTotal: number;
  goalsDone: number; goalsTotal: number; focusMinutes: number;
};
const emptyStats: DashboardStats = {
  tasksDone: 0, tasksTotal: 0, notes: 0, nextExam: 'No exams yet',
  chaptersDone: 0, chaptersTotal: 0, habitsDone: 0, habitsTotal: 0,
  goalsDone: 0, goalsTotal: 0, focusMinutes: 0,
};
const localDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

export default function TodayScreen() {
  const theme = useAppTheme();
  const [stats, setStats] = useState<DashboardStats>(emptyStats);
  const [profileName, setProfileName] = useState('');

  useFocusEffect(useCallback(() => {
    let active = true;
    const load = async () => {
      try {
        const db = await getDatabase();
        const today = localDate(new Date());
        const [tasks, notes, exams, subjects, habits, habitLogs, goals, focus, name] = await Promise.all([
          db.getFirstAsync<{ total: number; done: number }>('SELECT COUNT(*) as total, COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0) as done FROM tasks'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM notes'),
          db.getFirstAsync<{ title: string; exam_date: string }>('SELECT title, exam_date FROM exams WHERE exam_date >= ? ORDER BY exam_date ASC LIMIT 1', today),
          db.getAllAsync<{ chapters_json: string }>('SELECT chapters_json FROM subjects'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM habits WHERE archived = 0'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM habit_logs l INNER JOIN habits h ON h.id = l.habit_id WHERE h.archived = 0 AND l.log_date = ? AND l.completed = 1', today),
          db.getFirstAsync<{ total: number; done: number }>('SELECT COUNT(*) as total, COALESCE(SUM(CASE WHEN progress = 100 THEN 1 ELSE 0 END), 0) as done FROM goals'),
          db.getFirstAsync<{ seconds: number }>('SELECT COALESCE(SUM(duration_seconds), 0) as seconds FROM focus_sessions WHERE completed = 1 AND substr(started_at, 1, 10) = ?', today),
          getSetting<string>('profile.name', ''),
        ]);
        let chaptersTotal = 0, chaptersDone = 0;
        for (const subject of subjects) {
          try {
            const chapters = JSON.parse(subject.chapters_json) as { done?: boolean }[];
            if (Array.isArray(chapters)) {
              chaptersTotal += chapters.length;
              chaptersDone += chapters.filter(chapter => chapter.done).length;
            }
          } catch { /* Ignore malformed legacy chapter data in the overview. */ }
        }
        if (active) {
          setStats({
            tasksDone: tasks?.done ?? 0, tasksTotal: tasks?.total ?? 0, notes: notes?.total ?? 0,
            nextExam: exams ? exams.title : 'No upcoming exam',
            chaptersDone, chaptersTotal,
            habitsDone: habitLogs?.total ?? 0, habitsTotal: habits?.total ?? 0,
            goalsDone: goals?.done ?? 0, goalsTotal: goals?.total ?? 0,
            focusMinutes: Math.floor((focus?.seconds ?? 0) / 60),
          });
          setProfileName(name);
        }
      } catch (error) {
        console.warn('Could not refresh the Go Back dashboard', error);
      }
    };
    void load();
    return () => { active = false; };
  }, []));

  const actions: { icon: keyof typeof Ionicons.glyphMap; label: string; metric: string; detail: string; route: string; tone?: 'secondary' }[] = [
    { icon: 'checkbox-outline', label: 'Tasks', metric: `${stats.tasksDone}/${stats.tasksTotal}`, detail: 'completed', route: '/(tabs)/tasks' },
    { icon: 'document-text-outline', label: 'Notes', metric: String(stats.notes), detail: stats.notes === 1 ? 'saved note' : 'saved notes', route: '/(tabs)/notes', tone: 'secondary' },
    { icon: 'calendar-outline', label: 'Exams', metric: stats.nextExam, detail: 'next on your timetable', route: '/exams' },
    { icon: 'book-outline', label: 'Syllabus', metric: `${stats.chaptersDone}/${stats.chaptersTotal}`, detail: 'chapters complete', route: '/syllabus', tone: 'secondary' },
    { icon: 'leaf-outline', label: 'Habits', metric: `${stats.habitsDone}/${stats.habitsTotal}`, detail: 'done today', route: '/habits' },
    { icon: 'flag-outline', label: 'Goals', metric: `${stats.goalsDone}/${stats.goalsTotal}`, detail: 'goals completed', route: '/goals', tone: 'secondary' },
    { icon: 'hourglass-outline', label: 'Focus', metric: `${stats.focusMinutes} min`, detail: 'focused today', route: '/focus' },
    { icon: 'analytics-outline', label: 'Insights', metric: 'View trends', detail: 'screen-time & usage', route: '/usage', tone: 'secondary' },
    { icon: 'shield-checkmark-outline', label: 'Blocking', metric: 'Your rules', detail: 'apps & short videos', route: '/blocking' },
  ];

  return (
    <Screen>
      <View style={styles.topRow}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.muted }]}>YOUR SPACE</Text>
          <Text style={[styles.title, { color: theme.text }]}>Go Back<Text style={{ color: theme.primary }}>.</Text></Text>
        </View>
        <Pressable accessibilityLabel="Open local profile settings" onPress={() => router.push('/(tabs)/settings')} style={[styles.avatar, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}>{profileName.trim() ? <Text style={{ color: theme.primary, fontSize: 20, fontWeight: '900' }}>{profileName.trim().charAt(0).toLocaleUpperCase()}</Text> : <Ionicons name="person-outline" size={22} color={theme.primary} />}</Pressable>
      </View>
      <Text style={[styles.greeting, { color: theme.text }]}>{profileName.trim() ? `Welcome back, ${profileName.trim()}.` : 'Welcome back.'}</Text>
      <Text style={[styles.sub, { color: theme.muted }]}>Your day, at a glance. No digging through screens.</Text>

      <MotionReveal delay={0}>
        <Pressable onPress={() => router.push('/focus')} style={[styles.hero, { backgroundColor: theme.primary, borderRadius: theme.expressive ? 34 : 24 }]}>
          <View style={styles.heroTop}>
            <View style={[styles.liveDot, { backgroundColor: theme.onPrimary }]} />
            <Text style={[styles.heroLabel, { color: theme.onPrimary }]}>MAKE SPACE TO FOCUS</Text>
            <Ionicons name="arrow-forward-outline" size={20} color={theme.onPrimary} style={{ marginLeft: 'auto' }} />
          </View>
          <Text style={[styles.heroTitle, { color: theme.onPrimary }]}>Find your{'\n'}focus.</Text>
          <View style={styles.heroBottom}>
            <Text style={[styles.heroHint, { color: theme.onPrimary }]}>Start a focused session</Text>
            <View style={[styles.play, { backgroundColor: theme.onPrimary }]}><Ionicons name="play" size={17} color={theme.primary} /></View>
          </View>
        </Pressable>
      </MotionReveal>

      <View style={styles.sectionHead}>
        <View><Text style={[styles.sectionTitle, { color: theme.text }]}>At a glance</Text><Text style={[styles.sectionSubtitle, { color: theme.muted }]}>Live summaries from all your tools</Text></View>
        <View style={[styles.sectionBadge, { backgroundColor: theme.primaryContainer }]}><Ionicons name="grid-outline" size={16} color={theme.primary} /></View>
      </View>
      <View style={styles.dashboard}>
        {actions.map((action, index) => <MotionReveal key={action.label} delay={Math.min(index * 20, 120)} style={styles.dashboardReveal}>
          <Pressable accessibilityRole="button" accessibilityLabel={`Open ${action.label}: ${action.metric}`} onPress={() => router.push(action.route as never)} style={[styles.actionCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, borderRadius: theme.expressive ? (index % 3 === 0 ? 28 : 22) : 18 }]}>
            <View style={[styles.actionIcon, { backgroundColor: action.tone === 'secondary' ? theme.secondaryContainer : theme.primaryContainer }]}><Ionicons name={action.icon} size={20} color={theme.primary} /></View>
            <Text style={[styles.actionLabel, { color: theme.text }]}>{action.label}</Text>
            <Text numberOfLines={1} style={[styles.actionMetric, { color: theme.primary }]}>{action.metric}</Text>
            <Text numberOfLines={2} style={[styles.actionDetail, { color: theme.muted }]}>{action.detail}</Text>
            {action.label === 'Tasks' && <View style={[styles.miniTrack, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.miniFill, { backgroundColor: theme.primary, width: `${stats.tasksTotal ? Math.min(100, stats.tasksDone / stats.tasksTotal * 100) : 0}%` as `${number}%` }]} /></View>}
            {action.label === 'Syllabus' && <View style={[styles.miniTrack, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.miniFill, { backgroundColor: theme.primary, width: `${stats.chaptersTotal ? stats.chaptersDone / stats.chaptersTotal * 100 : 0}%` as `${number}%` }]} /></View>}
            <Ionicons name="arrow-up-forward-outline" size={15} color={theme.muted} style={styles.actionArrow} />
          </Pressable>
        </MotionReveal>)}
      </View>
      <Text style={[styles.footer, { color: theme.muted }]}>YOUR TIME. YOUR TERMS.</Text>
    </Screen>
  );
}

const styles = StyleSheet.create({
  topRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  eyebrow: { fontSize: 10, letterSpacing: 2.2, fontWeight: '800', marginBottom: 4 },
  title: { fontSize: 32, fontWeight: '900', letterSpacing: -1.3 },
  avatar: { width: 44, height: 44, borderRadius: 18, alignItems: 'center', justifyContent: 'center', overflow: 'hidden', borderWidth: 1 },
  greeting: { fontSize: 26, fontWeight: '800', letterSpacing: -0.8, marginTop: 8 },
  sub: { fontSize: 13, lineHeight: 19, marginTop: -12 },
  hero: { padding: 24, minHeight: 214, justifyContent: 'space-between', overflow: 'hidden' },
  heroTop: { flexDirection: 'row', alignItems: 'center', gap: 9 },
  liveDot: { width: 7, height: 7, borderRadius: 4 },
  heroLabel: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6, opacity: 0.9 },
  heroTitle: { fontSize: 39, lineHeight: 42, fontWeight: '900', letterSpacing: -1.6, marginVertical: 13 },
  heroBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  heroHint: { fontSize: 13, fontWeight: '700', opacity: 0.92 },
  play: { width: 42, height: 42, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 3 },
  sectionTitle: { fontSize: 22, fontWeight: '900', letterSpacing: -0.6 },
  sectionSubtitle: { fontSize: 11, marginTop: 3 },
  sectionBadge: { width: 38, height: 38, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  dashboard: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  dashboardReveal: { width: '48%', flexGrow: 1, flexBasis: '45%' },
  actionCard: { minHeight: 164, borderWidth: 1, padding: 14, overflow: 'hidden' },
  actionIcon: { width: 38, height: 38, borderRadius: 14, alignItems: 'center', justifyContent: 'center', marginBottom: 11 },
  actionLabel: { fontSize: 13, fontWeight: '800' },
  actionMetric: { fontSize: 19, fontWeight: '900', letterSpacing: -0.4, marginTop: 6 },
  actionDetail: { fontSize: 10, lineHeight: 14, marginTop: 3, paddingRight: 4 },
  actionArrow: { position: 'absolute', right: 13, top: 15 },
  miniTrack: { height: 4, borderRadius: 3, overflow: 'hidden', marginTop: 10 },
  miniFill: { height: '100%', borderRadius: 3 },
  footer: { textAlign: 'center', fontSize: 9, letterSpacing: 2, fontWeight: '800', marginTop: 5 },
});
