import { router, useFocusEffect } from 'expo-router';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { MotionReveal } from '@/components/MotionReveal';
import { useCallback, useState } from 'react';
import { Image, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';

type DashboardStats = {
  tasksDone: number; tasksTotal: number; notes: number; nextExam: string; nextExamDate: string;
  chaptersDone: number; chaptersTotal: number; habitsDone: number; habitsTotal: number;
  goalsDone: number; goalsTotal: number; focusMinutes: number;
};
const emptyStats: DashboardStats = {
  tasksDone: 0, tasksTotal: 0, notes: 0, nextExam: 'No upcoming exam', nextExamDate: '',
  chaptersDone: 0, chaptersTotal: 0, habitsDone: 0, habitsTotal: 0,
  goalsDone: 0, goalsTotal: 0, focusMinutes: 0,
};
const localDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const ratio = (done: number, total: number) => total > 0 ? Math.min(100, Math.round(done / total * 100)) : 0;

export default function TodayScreen() {
  const theme = useAppTheme();
  const [stats, setStats] = useState<DashboardStats>(emptyStats);
  const [profileName, setProfileName] = useState('');
  const [profileImageUri, setProfileImageUri] = useState('');

  useFocusEffect(useCallback(() => {
    let active = true;
    const load = async () => {
      try {
        const db = await getDatabase();
        const today = localDate(new Date());
        const [tasks, notes, exams, subjects, habits, habitLogs, goals, focus, name, imageUri] = await Promise.all([
          db.getFirstAsync<{ total: number; done: number }>('SELECT COUNT(*) as total, COALESCE(SUM(CASE WHEN completed = 1 THEN 1 ELSE 0 END), 0) as done FROM tasks'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM notes'),
          db.getFirstAsync<{ title: string; exam_date: string }>('SELECT title, exam_date FROM exams WHERE exam_date >= ? ORDER BY exam_date ASC LIMIT 1', today),
          db.getAllAsync<{ chapters_json: string }>('SELECT chapters_json FROM subjects'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM habits WHERE archived = 0'),
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM habit_logs l INNER JOIN habits h ON h.id = l.habit_id WHERE h.archived = 0 AND l.log_date = ? AND l.completed = 1', today),
          db.getFirstAsync<{ total: number; done: number }>('SELECT COUNT(*) as total, COALESCE(SUM(CASE WHEN progress = 100 THEN 1 ELSE 0 END), 0) as done FROM goals'),
          db.getFirstAsync<{ seconds: number }>('SELECT COALESCE(SUM(duration_seconds), 0) as seconds FROM focus_sessions WHERE completed = 1 AND substr(started_at, 1, 10) = ?', today),
          getSetting<string>('profile.name', ''),
          getSetting<string>('profile.imageUri', ''),
        ]);
        let chaptersTotal = 0, chaptersDone = 0;
        for (const subject of subjects) {
          try {
            const chapters = JSON.parse(subject.chapters_json) as { done?: boolean }[];
            if (Array.isArray(chapters)) {
              chaptersTotal += chapters.length;
              chaptersDone += chapters.filter(chapter => chapter.done).length;
            }
          } catch { /* Ignore malformed legacy chapter data. */ }
        }
        if (active) {
          setStats({
            tasksDone: tasks?.done ?? 0, tasksTotal: tasks?.total ?? 0, notes: notes?.total ?? 0,
            nextExam: exams?.title ?? 'No upcoming exam', nextExamDate: exams?.exam_date ?? '',
            chaptersDone, chaptersTotal, habitsDone: habitLogs?.total ?? 0, habitsTotal: habits?.total ?? 0,
            goalsDone: goals?.done ?? 0, goalsTotal: goals?.total ?? 0,
            focusMinutes: Math.floor((focus?.seconds ?? 0) / 60),
          });
          setProfileName(name);
          setProfileImageUri(imageUri);
        }
      } catch (error) {
        console.warn('Could not refresh the Go Back dashboard', error);
      }
    };
    void load();
    return () => { active = false; };
  }, []));

  const focusLabel = stats.focusMinutes >= 60
    ? `${Math.floor(stats.focusMinutes / 60)}h ${stats.focusMinutes % 60}m`
    : `${stats.focusMinutes} min`;
  const examDateLabel = stats.nextExamDate
    ? new Date(`${stats.nextExamDate}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
    : 'Add an exam date';

  const tools: { icon: keyof typeof Ionicons.glyphMap; label: string; metric: string; detail: string; route: string }[] = [
    { icon: 'checkbox-outline', label: 'Tasks', metric: `${stats.tasksDone}/${stats.tasksTotal}`, detail: 'tasks completed', route: '/(tabs)/tasks' },
    { icon: 'book-outline', label: 'Syllabus', metric: `${stats.chaptersDone}/${stats.chaptersTotal}`, detail: 'chapters complete', route: '/syllabus' },
    { icon: 'calendar-outline', label: 'Exams', metric: examDateLabel, detail: stats.nextExam, route: '/exams' },
    { icon: 'leaf-outline', label: 'Habits', metric: `${stats.habitsDone}/${stats.habitsTotal}`, detail: 'done today', route: '/habits' },
    { icon: 'flag-outline', label: 'Goals', metric: `${stats.goalsDone}/${stats.goalsTotal}`, detail: 'goals completed', route: '/goals' },
    { icon: 'document-text-outline', label: 'Notes', metric: String(stats.notes), detail: stats.notes === 1 ? 'saved note' : 'saved notes', route: '/(tabs)/notes' },
    { icon: 'analytics-outline', label: 'Usage', metric: 'Usage data', detail: 'screen-time and app activity', route: '/usage' },
    { icon: 'shield-checkmark-outline', label: 'App blocking', metric: 'Blocking rules', detail: 'apps and video limits', route: '/blocking' },
  ];

  return (
    <Screen>
      <View style={styles.topRow}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.muted }]}>YOUR SPACE</Text>
          <Text style={[styles.title, { color: theme.text }]}>Go Back<Text style={{ color: theme.primary }}>.</Text></Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Open profile settings" onPress={() => router.push('/(tabs)/settings')} style={[styles.avatar, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}>
          {profileImageUri ? <Image source={{ uri: profileImageUri }} style={StyleSheet.absoluteFillObject} resizeMode="cover" accessibilityLabel="Your profile photo" /> : profileName.trim() ? <Text style={{ color: theme.primary, fontSize: 20, fontWeight: '900' }}>{profileName.trim().charAt(0).toLocaleUpperCase()}</Text> : <Ionicons name="person-outline" size={22} color={theme.primary} />}
        </Pressable>
      </View>
      <Text style={[styles.greeting, { color: theme.text }]}>{profileName.trim() ? `Welcome back, ${profileName.trim()}.` : 'Welcome back.'}</Text>
      <Text style={[styles.sub, { color: theme.muted }]}>A clear view of what you’ve done today.</Text>

      <MotionReveal delay={0}>
        <Pressable accessibilityRole="button" accessibilityLabel="Open focus timer" onPress={() => router.push('/focus')} style={[styles.focusCard, { backgroundColor: theme.primary, borderRadius: theme.expressive ? 30 : 22 }]}>
          <View style={styles.focusTop}>
            <View style={[styles.focusIcon, { backgroundColor: theme.onPrimary }]}><Ionicons name="timer-outline" size={22} color={theme.primary} /></View>
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={[styles.focusLabel, { color: theme.onPrimary }]}>FOCUS TIME TODAY</Text>
              <Text style={[styles.focusMetric, { color: theme.onPrimary }]}>{focusLabel}</Text>
              <Text style={[styles.focusHint, { color: theme.onPrimary }]}>Completed sessions · saved history</Text>
            </View>
            <Ionicons name="arrow-forward-circle-outline" size={28} color={theme.onPrimary} />
          </View>
          <View style={[styles.focusDivider, { backgroundColor: theme.onPrimary }]} />
          <View style={styles.focusBottom}>
            <Text style={[styles.focusBottomText, { color: theme.onPrimary }]}>Make room for one more session</Text>
            <View style={[styles.startButton, { backgroundColor: theme.onPrimary }]}><Ionicons name="play" size={14} color={theme.primary} /><Text style={[styles.startText, { color: theme.primary }]}>Start focus</Text></View>
          </View>
        </Pressable>
      </MotionReveal>

      <View style={styles.sectionHead}>
        <View style={{ flex: 1 }}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Today’s progress</Text>
          <Text style={[styles.sectionSubtitle, { color: theme.muted }]}>Based on your saved tasks and syllabus</Text>
        </View>
      </View>
      <View style={styles.progressGrid}>
        <Pressable accessibilityRole="button" accessibilityLabel={`Tasks ${stats.tasksDone} of ${stats.tasksTotal} completed`} onPress={() => router.push('/(tabs)/tasks')} style={[styles.progressCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.progressHeading}><Ionicons name="checkbox-outline" size={17} color={theme.primary} /><Text style={[styles.progressLabel, { color: theme.muted }]}>TASKS</Text></View>
          <Text style={[styles.progressValue, { color: theme.text }]}>{stats.tasksDone}<Text style={[styles.progressTotal, { color: theme.muted }]}> / {stats.tasksTotal}</Text></Text>
          <View style={[styles.track, { backgroundColor: theme.border }]}><View style={[styles.fill, { backgroundColor: theme.primary, width: `${ratio(stats.tasksDone, stats.tasksTotal)}%` }]} /></View>
          <Text style={[styles.progressFoot, { color: theme.muted }]}>{ratio(stats.tasksDone, stats.tasksTotal)}% complete</Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel={`Syllabus ${stats.chaptersDone} of ${stats.chaptersTotal} chapters completed`} onPress={() => router.push('/syllabus')} style={[styles.progressCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.progressHeading}><Ionicons name="book-outline" size={17} color={theme.secondary} /><Text style={[styles.progressLabel, { color: theme.muted }]}>SYLLABUS</Text></View>
          <Text style={[styles.progressValue, { color: theme.text }]}>{stats.chaptersDone}<Text style={[styles.progressTotal, { color: theme.muted }]}> / {stats.chaptersTotal}</Text></Text>
          <View style={[styles.track, { backgroundColor: theme.border }]}><View style={[styles.fill, { backgroundColor: theme.secondary, width: `${ratio(stats.chaptersDone, stats.chaptersTotal)}%` }]} /></View>
          <Text style={[styles.progressFoot, { color: theme.muted }]}>{ratio(stats.chaptersDone, stats.chaptersTotal)}% complete</Text>
        </Pressable>
      </View>

      <View style={styles.sectionHead}>
        <View style={{ flex: 1 }}>
          <Text style={[styles.sectionTitle, { color: theme.text }]}>Your tools</Text>
          <Text style={[styles.sectionSubtitle, { color: theme.muted }]}>More details and controls below</Text>
        </View>
      </View>
      <View style={[styles.toolList, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        {tools.map((tool, index) => (
          <Pressable key={tool.label} accessibilityRole="button" accessibilityLabel={`Open ${tool.label}: ${tool.metric}`} onPress={() => router.push(tool.route as never)} style={[styles.toolRow, { borderBottomColor: theme.border, borderBottomWidth: index === tools.length - 1 ? 0 : StyleSheet.hairlineWidth }]}>
            <View style={[styles.toolIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name={tool.icon} size={19} color={theme.primary} /></View>
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={[styles.toolTitle, { color: theme.text }]}>{tool.label}</Text>
              <Text numberOfLines={1} style={[styles.toolDetail, { color: theme.muted }]}>{tool.detail}</Text>
            </View>
            <View style={styles.toolRight}>
              <Text numberOfLines={1} style={[styles.toolMetric, { color: theme.text }]}>{tool.metric}</Text>
              <Ionicons name="chevron-forward" size={15} color={theme.muted} />
            </View>
          </Pressable>
        ))}
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
  greeting: { fontSize: 25, fontWeight: '800', letterSpacing: -0.7, marginTop: 7 },
  sub: { fontSize: 13, lineHeight: 19, marginTop: -11 },
  focusCard: { padding: 18, gap: 14, overflow: 'hidden' },
  focusTop: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  focusIcon: { width: 44, height: 44, borderRadius: 15, alignItems: 'center', justifyContent: 'center' },
  focusLabel: { fontSize: 9, fontWeight: '900', letterSpacing: 1.4, opacity: 0.9 },
  focusMetric: { fontSize: 29, fontWeight: '900', letterSpacing: -0.7 },
  focusHint: { fontSize: 10, opacity: 0.85 },
  focusDivider: { height: 1, opacity: 0.2 },
  focusBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  focusBottomText: { fontSize: 11, fontWeight: '700', opacity: 0.9, flex: 1 },
  startButton: { borderRadius: 13, paddingHorizontal: 11, paddingVertical: 8, flexDirection: 'row', alignItems: 'center', gap: 5 },
  startText: { fontSize: 11, fontWeight: '900' },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 3 },
  sectionTitle: { fontSize: 19, fontWeight: '900', letterSpacing: -0.4 },
  sectionSubtitle: { fontSize: 11, marginTop: 3 },
  progressGrid: { flexDirection: 'row', gap: 10 },
  progressCard: { flex: 1, minWidth: 0, borderWidth: 1, borderRadius: 20, padding: 14, gap: 9 },
  progressHeading: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  progressLabel: { fontSize: 9, fontWeight: '900', letterSpacing: 1.1 },
  progressValue: { fontSize: 25, fontWeight: '900', letterSpacing: -0.7 },
  progressTotal: { fontSize: 14, fontWeight: '700' },
  track: { height: 5, borderRadius: 3, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 3 },
  progressFoot: { fontSize: 10, fontWeight: '700' },
  toolList: { borderWidth: 1, borderRadius: 20, paddingHorizontal: 12 },
  toolRow: { minHeight: 66, flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 9 },
  toolIcon: { width: 38, height: 38, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  toolTitle: { fontSize: 12, fontWeight: '900' },
  toolDetail: { fontSize: 10, lineHeight: 14 },
  toolRight: { maxWidth: 120, flexDirection: 'row', alignItems: 'center', gap: 5 },
  toolMetric: { maxWidth: 100, fontSize: 11, fontWeight: '800', textAlign: 'right' },
  footer: { textAlign: 'center', fontSize: 9, letterSpacing: 2, fontWeight: '800', marginTop: 3 },
});
