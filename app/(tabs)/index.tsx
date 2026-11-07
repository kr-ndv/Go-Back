import { router, useFocusEffect } from 'expo-router';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { MotionReveal } from '@/components/MotionReveal';
import { useCallback, useState } from 'react';
import { AppState, Image, NativeModules, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Screen } from '@/components/Screen';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';
import { calculateStreakMilestone, focusGoalStats, type FocusHistoryPoint } from '@/lib/focusInsights';
import { getNativeUnlockCount, syncWidgetSnapshot } from '@/lib/nativeBridge';
import { M3SquigglyProgress } from '@/components/M3SquigglyProgress';

type UsageStat = { packageName: string; appName: string; foregroundTimeMs: number };
type UsageBridge = { hasUsageAccess: () => Promise<boolean>; getUsageStats: (days: number) => Promise<UsageStat[]> };
const usageBridge = NativeModules.GoBackUsage as UsageBridge | undefined;
const formatUsage = (ms: number) => { const minutes = Math.floor(ms / 60000); const hours = Math.floor(minutes / 60); return hours ? `${hours}h ${minutes % 60}m` : `${minutes}m`; };

type DashboardStats = {
  notes: number;
  nextExam: string;
  nextExamDate: string;
  chaptersDone: number;
  chaptersTotal: number;
  focusMinutes: number;
  dailyGoalMinutes: number;
  currentStreak: number;
  unlocksToday: number;
};

const emptyStats: DashboardStats = {
  notes: 0,
  nextExam: 'No upcoming exam',
  nextExamDate: '',
  chaptersDone: 0,
  chaptersTotal: 0,
  focusMinutes: 0,
  dailyGoalMinutes: 120,
  currentStreak: 0,
  unlocksToday: 0,
};

const localDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const ratio = (done: number, total: number) => total > 0 ? Math.min(100, Math.round(done / total * 100)) : 0;

export default function TodayScreen() {
  const theme = useAppTheme();
  const [stats, setStats] = useState<DashboardStats>(emptyStats);
  const [profileName, setProfileName] = useState('');
  const [profileImageUri, setProfileImageUri] = useState('');
  const [usageToday, setUsageToday] = useState('Allow access');

  useFocusEffect(useCallback(() => {
    let active = true;
    const load = async () => {
      try {
        const db = await getDatabase();
        const today = localDate(new Date());
        const [notes, exams, subjects, focusRows, savedGoal, name, imageUri, unlocks] = await Promise.all([
          db.getFirstAsync<{ total: number }>('SELECT COUNT(*) as total FROM notes'),
          db.getFirstAsync<{ title: string; exam_date: string }>('SELECT title, exam_date FROM exams WHERE exam_date >= ? ORDER BY exam_date ASC LIMIT 1', today),
          db.getAllAsync<{ chapters_json: string }>('SELECT chapters_json FROM subjects'),
          db.getAllAsync<FocusHistoryPoint>('SELECT started_at, duration_seconds, session_type FROM focus_sessions WHERE session_type = \'focus\' AND datetime(started_at) >= datetime(?, \'-90 days\')', new Date().toISOString()),
          getSetting<number>('focus.dailyGoalMinutes', 120),
          getSetting<string>('profile.name', ''),
          getSetting<string>('profile.imageUri', ''),
          getNativeUnlockCount(1),
        ]);

        let chaptersTotal = 0, chaptersDone = 0;
        for (const subject of subjects) {
          try {
            const chapters = JSON.parse(subject.chapters_json) as { done?: boolean }[];
            if (Array.isArray(chapters)) {
              chaptersTotal += chapters.length;
              chaptersDone += chapters.filter(chapter => chapter.done).length;
            }
          } catch { /* Ignore malformed chapter data */ }
        }

        const validGoal = Number.isInteger(savedGoal) && savedGoal >= 15 && savedGoal <= 480 ? savedGoal : 120;
        const goalStats = focusGoalStats(focusRows, validGoal);
        const focusTodayMinutes = Math.floor(goalStats.todaySeconds / 60);

        if (active) {
          setStats({
            notes: notes?.total ?? 0,
            nextExam: exams?.title ?? 'No upcoming exam',
            nextExamDate: exams?.exam_date ?? '',
            chaptersDone,
            chaptersTotal,
            focusMinutes: focusTodayMinutes,
            dailyGoalMinutes: validGoal,
            currentStreak: goalStats.currentStreak,
            unlocksToday: unlocks,
          });
          setProfileName(name);
          setProfileImageUri(imageUri);
        }

        const examDays = exams?.exam_date ? Math.ceil((new Date(`${exams.exam_date}T12:00:00`).getTime() - Date.now()) / 86400000) : -1;
        void syncWidgetSnapshot({
          focusMinutesToday: focusTodayMinutes,
          dailyGoalMinutes: validGoal,
          streakDays: goalStats.currentStreak,
          nextExamTitle: exams?.title ?? '',
          nextExamDate: exams?.exam_date ?? '',
          nextExamDays: examDays,
          totalNotes: notes?.total ?? 0,
          syllabusProgress: chaptersTotal > 0 ? `${chaptersDone}/${chaptersTotal} done` : '',
        });
      } catch (error) {
        console.warn('Could not refresh Go Back dashboard', error);
      }
    };
    void load();

    const loadUsage = async () => {
      if (!usageBridge) { if (active) setUsageToday('Native build required'); return; }
      try {
        const allowed = await usageBridge.hasUsageAccess();
        if (!allowed) { if (active) setUsageToday('Allow Usage Access'); return; }
        const rows = await usageBridge.getUsageStats(1);
        const total = rows.reduce((sum, item) => sum + Math.max(0, item.foregroundTimeMs || 0), 0);
        if (active) setUsageToday(formatUsage(total));
      } catch { if (active) setUsageToday('Tap to refresh'); }
    };
    void loadUsage();
    const sub = AppState.addEventListener('change', state => { if (state === 'active') void loadUsage(); });
    return () => { active = false; sub.remove(); };
  }, []));

  const focusLabel = stats.focusMinutes >= 60
    ? `${Math.floor(stats.focusMinutes / 60)}h ${stats.focusMinutes % 60}m`
    : `${stats.focusMinutes} min`;
  const goalLabel = stats.dailyGoalMinutes >= 60
    ? `${Math.floor(stats.dailyGoalMinutes / 60)}h ${stats.dailyGoalMinutes % 60}m goal`
    : `${stats.dailyGoalMinutes}m goal`;
  const focusGoalPercent = Math.min(100, Math.round((stats.focusMinutes / Math.max(1, stats.dailyGoalMinutes)) * 100));

  const examDaysRemaining = stats.nextExamDate ? Math.ceil((new Date(`${stats.nextExamDate}T12:00:00`).getTime() - Date.now()) / 86400000) : null;
  const streakMilestone = calculateStreakMilestone(stats.currentStreak);
  const examDateLabel = stats.nextExamDate
    ? new Date(`${stats.nextExamDate}T12:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
    : 'No exam planned';

  const tools: { icon: keyof typeof Ionicons.glyphMap; label: string; metric: string; detail: string; route: string }[] = [
    { icon: 'shield-checkmark-outline', label: 'App & Content Blocking', metric: 'Distraction rules', detail: 'Shorts, Reels, Spotlight & adult sites', route: '/blocking' },
    { icon: 'hourglass-outline', label: 'Screen-time limits', metric: 'Daily boundaries', detail: 'Gentle alerts before time slips away', route: '/usage-limits' },
    { icon: 'calendar-outline', label: 'Focus schedule', metric: 'Weekly routine', detail: 'Repeating study reminders', route: '/schedule' },
    { icon: 'book-outline', label: 'Syllabus tracker', metric: `${stats.chaptersDone}/${stats.chaptersTotal}`, detail: 'chapters completed', route: '/syllabus' },
    { icon: 'school-outline', label: 'Exam timetable', metric: examDateLabel, detail: stats.nextExam, route: '/exams' },
    { icon: 'document-text-outline', label: 'Personal notes', metric: String(stats.notes), detail: stats.notes === 1 ? 'saved note' : 'saved notes', route: '/(tabs)/notes' },
  ];

  return (
    <Screen>
      <View style={styles.topRow}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.muted }]}>FOCUS &amp; REGAIN YOUR TIME</Text>
          <Text style={[styles.title, { color: theme.text }]}>Go Back<Text style={{ color: theme.primary }}>.</Text></Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Open profile settings" onPress={() => router.push('/(tabs)/settings')} style={[styles.avatar, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}>
          {profileImageUri ? <Image source={{ uri: profileImageUri }} style={StyleSheet.absoluteFillObject} resizeMode="cover" accessibilityLabel="Your profile photo" /> : profileName.trim() ? <Text style={{ color: theme.primary, fontSize: 20, fontWeight: '900' }}>{profileName.trim().charAt(0).toLocaleUpperCase()}</Text> : <Ionicons name="person-outline" size={22} color={theme.primary} />}
        </Pressable>
      </View>
      <Text style={[styles.greeting, { color: theme.text }]}>{profileName.trim() ? `Welcome back, ${profileName.trim()}.` : 'Welcome back.'}</Text>
      <Text style={[styles.sub, { color: theme.muted }]}>Keep your digital focus intentional today.</Text>

      {/* Focus Hero Card */}
      <MotionReveal delay={0}>
        <Pressable accessibilityRole="button" accessibilityLabel="Open focus timer" onPress={() => router.push('/focus')} style={[styles.focusCard, { backgroundColor: theme.primary, borderRadius: theme.expressive ? 30 : 22 }]}>
          <View style={styles.focusTop}>
            <View style={[styles.focusIcon, { backgroundColor: theme.onPrimary }]}><Ionicons name="timer-outline" size={22} color={theme.primary} /></View>
            <View style={{ flex: 1, gap: 3 }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                <Text style={[styles.focusLabel, { color: theme.onPrimary }]}>TODAY'S FOCUS</Text>
                {stats.currentStreak > 0 && (
                  <View style={[styles.streakPill, { backgroundColor: 'rgba(0,0,0,0.18)' }]}>
                    <Text style={{ color: theme.onPrimary, fontSize: 10, fontWeight: '900' }}>
                      🔥 {stats.currentStreak}d streak · {streakMilestone.badge} {streakMilestone.label}
                    </Text>
                  </View>
                )}
              </View>
              <Text style={[styles.focusMetric, { color: theme.onPrimary }]}>{focusLabel} <Text style={{ fontSize: 14, fontWeight: '700', opacity: 0.9 }}>/ {goalLabel}</Text></Text>
              <M3SquigglyProgress
                progress={focusGoalPercent}
                color={theme.onPrimary}
                trackColor="rgba(255,255,255,0.3)"
                active={stats.focusMinutes > 0}
                height={14}
                strokeWidth={3.5}
              />
            </View>
            <Ionicons name="arrow-forward-circle-outline" size={28} color={theme.onPrimary} />
          </View>
          <View style={[styles.focusDivider, { backgroundColor: theme.onPrimary }]} />
          <View style={styles.focusBottom}>
            <Text style={[styles.focusBottomText, { color: theme.onPrimary }]}>{focusGoalPercent >= 100 ? 'Daily goal reached! Keep momentum.' : `${focusGoalPercent}% of daily focus goal completed`}</Text>
            <View style={[styles.startButton, { backgroundColor: theme.onPrimary }]}><Ionicons name="play" size={14} color={theme.primary} /><Text style={[styles.startText, { color: theme.primary }]}>Start focus</Text></View>
          </View>
        </Pressable>
      </MotionReveal>

      {/* Screen Time & Unlocks Glance */}
      <Pressable accessibilityRole="button" accessibilityLabel="View detailed usage analytics" onPress={() => router.push('/(tabs)/analytics')} style={[styles.usageGlance, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <View style={styles.glanceIconBox}><Ionicons name="phone-portrait-outline" size={20} color={theme.primary} /></View>
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={[styles.glanceEyebrow, { color: theme.muted }]}>SCREEN TIME TODAY</Text>
          <Text style={[styles.glanceValue, { color: theme.text }]}>{usageToday} <Text style={{ fontSize: 13, color: theme.muted, fontWeight: '600' }}>· {stats.unlocksToday > 0 ? `${stats.unlocksToday} unlocks` : 'Unlock tracking ready'}</Text></Text>
        </View>
        <Ionicons name="chevron-forward" size={18} color={theme.muted} />
      </Pressable>

      {/* Progress Cards: Syllabus & Next Exam */}
      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Study milestones</Text>
      </View>
      <View style={styles.progressGrid}>
        <Pressable accessibilityRole="button" accessibilityLabel={`Syllabus ${stats.chaptersDone} of ${stats.chaptersTotal} chapters completed`} onPress={() => router.push('/syllabus')} style={[styles.progressCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.progressHeading}><Ionicons name="book-outline" size={17} color={theme.primary} /><Text style={[styles.progressLabel, { color: theme.muted }]}>SYLLABUS</Text></View>
          <Text style={[styles.progressValue, { color: theme.text }]}>{stats.chaptersDone}<Text style={[styles.progressTotal, { color: theme.muted }]}> / {stats.chaptersTotal}</Text></Text>
          <M3SquigglyProgress
            progress={ratio(stats.chaptersDone, stats.chaptersTotal)}
            color={theme.primary}
            trackColor={theme.border}
            active={stats.chaptersDone > 0}
            height={14}
            strokeWidth={3.5}
          />
          <Text style={[styles.progressFoot, { color: theme.muted }]}>{ratio(stats.chaptersDone, stats.chaptersTotal)}% chapters done</Text>
        </Pressable>

        <Pressable accessibilityRole="button" accessibilityLabel={`Next exam ${stats.nextExam}`} onPress={() => router.push('/exams')} style={[styles.progressCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.progressHeading}><Ionicons name="school-outline" size={17} color={theme.primary} /><Text style={[styles.progressLabel, { color: theme.muted }]}>NEXT EXAM</Text></View>
          <Text numberOfLines={1} style={[styles.progressValue, { fontSize: 17, color: theme.text }]}>{stats.nextExam}</Text>
          <Text style={[styles.examDateFoot, { color: theme.primary }]}>{examDaysRemaining !== null ? (examDaysRemaining <= 0 ? 'Today' : `${examDaysRemaining} days to go`) : 'Add date'}</Text>
          <Text style={[styles.progressFoot, { color: theme.muted }]}>{examDateLabel}</Text>
        </Pressable>
      </View>

      {/* Tools List */}
      <View style={styles.sectionHead}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Focus &amp; Study Tools</Text>
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
      <Text style={[styles.footer, { color: theme.muted }]}>100% LOCAL-FIRST · REGAIN YOUR TIME</Text>
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
  streakPill: { paddingHorizontal: 7, paddingVertical: 2, borderRadius: 8 },
  focusMetric: { fontSize: 28, fontWeight: '900', letterSpacing: -0.7, marginTop: 2 },
  goalTrack: { height: 4, borderRadius: 2, backgroundColor: 'rgba(0,0,0,0.2)', marginTop: 5, overflow: 'hidden' },
  goalFill: { height: '100%', borderRadius: 2 },
  focusDivider: { height: 1, opacity: 0.2 },
  focusBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  focusBottomText: { fontSize: 11, fontWeight: '700', opacity: 0.9, flex: 1 },
  startButton: { borderRadius: 13, paddingHorizontal: 11, paddingVertical: 8, flexDirection: 'row', alignItems: 'center', gap: 5 },
  startText: { fontSize: 11, fontWeight: '900' },
  usageGlance: { flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 20, padding: 14 },
  glanceIconBox: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  glanceEyebrow: { fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  glanceValue: { fontSize: 16, fontWeight: '900' },
  sectionHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginTop: 4 },
  sectionTitle: { fontSize: 18, fontWeight: '900', letterSpacing: -0.4 },
  progressGrid: { flexDirection: 'row', gap: 10 },
  progressCard: { flex: 1, minWidth: 0, borderWidth: 1, borderRadius: 20, padding: 14, gap: 8 },
  progressHeading: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  progressLabel: { fontSize: 9, fontWeight: '900', letterSpacing: 1.1 },
  progressValue: { fontSize: 24, fontWeight: '900', letterSpacing: -0.7 },
  progressTotal: { fontSize: 14, fontWeight: '700' },
  track: { height: 5, borderRadius: 3, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 3 },
  progressFoot: { fontSize: 10, fontWeight: '700' },
  examDateFoot: { fontSize: 12, fontWeight: '900' },
  toolList: { borderWidth: 1, borderRadius: 20, paddingHorizontal: 12 },
  toolRow: { minHeight: 64, flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 9 },
  toolIcon: { width: 38, height: 38, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  toolTitle: { fontSize: 12, fontWeight: '900' },
  toolDetail: { fontSize: 10, lineHeight: 14 },
  toolRight: { maxWidth: 120, flexDirection: 'row', alignItems: 'center', gap: 5 },
  toolMetric: { maxWidth: 100, fontSize: 11, fontWeight: '800', textAlign: 'right' },
  footer: { textAlign: 'center', fontSize: 9, letterSpacing: 2, fontWeight: '800', marginTop: 5 },
});
