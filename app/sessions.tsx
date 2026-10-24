import { useCallback, useMemo, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { getDatabase, getSetting, setSetting } from '@/lib/database';
import { useAppTheme } from '@/state/theme';
import { formatFocusTime, type FocusMode, type FocusSessionType } from '@/lib/focusLogic';

type SessionRow = {
  id: string;
  started_at: string;
  duration_seconds: number;
  completed: number;
  session_type: FocusSessionType;
  mode: FocusMode;
  tag: string;
  notes: string;
};
type Filter = 'all' | 'focus' | 'break';

const safeDate = (value: string) => {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return 'Unknown date';
  return date.toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
};
const modeLabel = (mode: FocusMode) => mode === 'stopwatch' ? 'Stopwatch' : mode === 'pomodoro' ? 'Pomodoro' : 'Timer';
const typeLabel = (type: FocusSessionType) => type === 'long-break' ? 'Long break' : type === 'short-break' ? 'Short break' : 'Focus';

export default function SessionsScreen() {
  const theme = useAppTheme();
  const [sessions, setSessions] = useState<SessionRow[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dailyGoalMinutes, setDailyGoalMinutes] = useState(120);
  const [goalSaveError, setGoalSaveError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const db = await getDatabase();
      const [rows, savedGoal] = await Promise.all([
        db.getAllAsync<SessionRow>(
          'SELECT id, started_at, duration_seconds, completed, session_type, mode, tag, notes FROM focus_sessions ORDER BY started_at DESC LIMIT 200'
        ),
        getSetting<number>('focus.dailyGoalMinutes', 120),
      ]);
      setDailyGoalMinutes(Number.isInteger(savedGoal) && savedGoal >= 15 && savedGoal <= 480 ? savedGoal : 120);
      setSessions(rows.map(row => ({
        ...row,
        duration_seconds: Number.isFinite(row.duration_seconds) ? Math.max(0, row.duration_seconds) : 0,
        completed: row.completed === 1 ? 1 : 0,
        mode: row.mode === 'pomodoro' || row.mode === 'stopwatch' ? row.mode : 'timer',
        session_type: row.session_type === 'short-break' || row.session_type === 'long-break' ? row.session_type : 'focus',
        tag: typeof row.tag === 'string' ? row.tag : '',
        notes: typeof row.notes === 'string' ? row.notes : '',
      })));
    } catch {
      setError('Go Back could not load your session history. Your saved sessions have not been changed.');
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(useCallback(() => {
    void load();
  }, [load]));

  const visibleSessions = useMemo(() => sessions.filter(session => {
    if (filter === 'focus') return session.session_type === 'focus';
    if (filter === 'break') return session.session_type !== 'focus';
    return true;
  }), [sessions, filter]);

  const todayFocusSeconds = useMemo(() => {
    const now = new Date();
    const todayKey = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    return sessions
      .filter(session => {
        const date = new Date(session.started_at);
        const dateKey = date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
        return session.session_type === 'focus' && Number.isFinite(date.getTime()) && dateKey === todayKey;
      })
      .reduce((sum, session) => sum + session.duration_seconds, 0);
  }, [sessions]);

  const updateDailyGoal = async (delta: number) => {
    const previous = dailyGoalMinutes;
    const next = Math.max(15, Math.min(480, previous + delta));
    setDailyGoalMinutes(next);
    setGoalSaveError('');
    try {
      await setSetting('focus.dailyGoalMinutes', next);
    } catch {
      setDailyGoalMinutes(previous);
      setGoalSaveError('The focus goal could not be saved. Please try again.');
    }
  };

  const goalProgress = Math.min(1, todayFocusSeconds / (dailyGoalMinutes * 60));

  const summary = useMemo(() => {
    const now = new Date();
    const weekStart = new Date(now);
    weekStart.setDate(now.getDate() - 6);
    weekStart.setHours(0, 0, 0, 0);
    const recent = sessions.filter(session => {
      const date = new Date(session.started_at);
      return Number.isFinite(date.getTime()) && date >= weekStart && session.session_type === 'focus';
    });
    return {
      focusSeconds: recent.reduce((sum, session) => sum + session.duration_seconds, 0),
      completed: recent.filter(session => session.completed === 1).length,
      total: recent.length,
    };
  }, [sessions]);

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}>
      <Pressable accessibilityRole="button" accessibilityLabel="Go back" onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={[styles.headerTitle, { color: theme.text }]}>Session history</Text>
        <Text style={[styles.headerSubtitle, { color: theme.muted }]}>Your effort, recorded locally</Text>
      </View>
      <Pressable accessibilityRole="button" accessibilityLabel="Refresh session history" onPress={() => void load()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="refresh" size={18} color={theme.text} /></Pressable>
    </View>
    <ScrollView contentContainerStyle={styles.content}>
      <View style={[styles.goalCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <View style={styles.goalHeader}>
          <View style={[styles.goalIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="flag-outline" size={22} color={theme.primary} /></View>
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={[styles.goalTitle, { color: theme.text }]}>Today's focus goal</Text>
            <Text style={[styles.goalSubtitle, { color: theme.muted }]}>{Math.floor(todayFocusSeconds / 60)} of {dailyGoalMinutes} minutes</Text>
          </View>
          <Text style={[styles.goalPercent, { color: theme.primary }]}>{Math.round(goalProgress * 100)}%</Text>
        </View>
        <View style={[styles.goalTrack, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.goalFill, { backgroundColor: theme.primary, width: ((goalProgress * 100) + '%') as `${number}%` }]} /></View>
        <View style={styles.goalActions}>
          <Text style={[styles.goalHint, { color: theme.muted }]}>Set a goal that feels achievable.</Text>
          <View style={styles.goalControls}>
            <Pressable accessibilityRole="button" accessibilityLabel="Reduce daily focus goal by 15 minutes" disabled={dailyGoalMinutes <= 15} onPress={() => void updateDailyGoal(-15)} style={[styles.goalControl, { backgroundColor: theme.surfaceAlt, opacity: dailyGoalMinutes <= 15 ? 0.45 : 1 }]}><Ionicons name="remove" size={17} color={theme.text} /></Pressable>
            <Pressable accessibilityRole="button" accessibilityLabel="Increase daily focus goal by 15 minutes" disabled={dailyGoalMinutes >= 480} onPress={() => void updateDailyGoal(15)} style={[styles.goalControl, { backgroundColor: theme.surfaceAlt, opacity: dailyGoalMinutes >= 480 ? 0.45 : 1 }]}><Ionicons name="add" size={17} color={theme.text} /></Pressable>
          </View>
        </View>
        {!!goalSaveError && <Text style={[styles.goalError, { color: theme.danger }]}>{goalSaveError}</Text>}
      </View>

      <View style={[styles.hero, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
        <View style={[styles.heroIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="time-outline" size={24} color={theme.primary} /></View>
        <Text style={[styles.eyebrow, { color: theme.muted }]}>LAST 7 DAYS</Text>
        <Text style={[styles.heroTime, { color: theme.text }]}>{formatFocusTime(summary.focusSeconds, summary.focusSeconds >= 3600)}</Text>
        <Text style={[styles.heroCaption, { color: theme.muted }]}>Focus time recorded</Text>
        <View style={styles.summaryRow}>
          <View style={[styles.summaryChip, { backgroundColor: theme.surface }]}><Text style={[styles.summaryValue, { color: theme.text }]}>{summary.completed}</Text><Text style={[styles.summaryLabel, { color: theme.muted }]}>Completed</Text></View>
          <View style={[styles.summaryChip, { backgroundColor: theme.surface }]}><Text style={[styles.summaryValue, { color: theme.text }]}>{summary.total}</Text><Text style={[styles.summaryLabel, { color: theme.muted }]}>Focus sessions</Text></View>
        </View>
      </View>

      <View style={styles.listHeader}>
        <Text style={[styles.sectionTitle, { color: theme.text }]}>Recent sessions</Text>
        <Text style={[styles.count, { color: theme.muted }]}>{visibleSessions.length} shown</Text>
      </View>
      <View style={[styles.filterRow, { backgroundColor: theme.surface, borderColor: theme.border }]}>
        {([{ value: 'all', label: 'All' }, { value: 'focus', label: 'Focus' }, { value: 'break', label: 'Breaks' }] as { value: Filter; label: string }[]).map(item =>
          <Pressable key={item.value} accessibilityRole="button" accessibilityState={{ selected: filter === item.value }} onPress={() => setFilter(item.value)} style={[styles.filter, { backgroundColor: filter === item.value ? theme.primary : 'transparent' }]}>
            <Text style={{ color: filter === item.value ? theme.onPrimary : theme.text, fontSize: 12, fontWeight: '800' }}>{item.label}</Text>
          </Pressable>
        )}
      </View>

      {loading ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="hourglass-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>Loading your sessions…</Text></View>
      : error ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="alert-circle-outline" size={28} color={theme.danger} /><Text style={[styles.emptyTitle, { color: theme.text }]}>History unavailable</Text><Text style={[styles.emptyText, { color: theme.muted }]}>{error}</Text><Pressable onPress={() => void load()} style={[styles.retry, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontWeight: '800' }}>Try again</Text></Pressable></View>
      : visibleSessions.length === 0 ? <View style={[styles.empty, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="hourglass-outline" size={28} color={theme.primary} /><Text style={[styles.emptyTitle, { color: theme.text }]}>{sessions.length === 0 ? 'Your first session starts here' : 'No sessions in this filter'}</Text><Text style={[styles.emptyText, { color: theme.muted }]}>{sessions.length === 0 ? 'Start a focus timer or stopwatch. Completed and saved sessions will appear here.' : 'Choose another filter to see your other saved sessions.'}</Text>{sessions.length === 0 && <Pressable onPress={() => router.push('/focus')} style={[styles.retry, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontWeight: '800' }}>Start a session</Text></Pressable>}</View>
      : visibleSessions.map(session =>
        <View key={session.id} style={[styles.sessionCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={[styles.sessionIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name={session.session_type === 'focus' ? 'leaf-outline' : 'cafe-outline'} size={21} color={theme.primary} /></View>
          <View style={{ flex: 1, gap: 5 }}>
            <View style={styles.sessionTitleRow}>
              <Text style={[styles.sessionTitle, { color: theme.text }]}>{typeLabel(session.session_type)}</Text>
              <Text style={[styles.duration, { color: theme.primary }]}>{formatFocusTime(session.duration_seconds, session.duration_seconds >= 3600)}</Text>
            </View>
            <Text style={[styles.date, { color: theme.muted }]}>{safeDate(session.started_at)} · {modeLabel(session.mode)}</Text>
            {!!session.tag && <View style={[styles.tag, { backgroundColor: theme.surfaceAlt }]}><Text style={[styles.tagText, { color: theme.primary }]}>{session.tag}</Text></View>}
            {!!session.notes && <Text style={[styles.note, { color: theme.text }]}>{session.notes}</Text>}
            <View style={styles.sessionFooter}>
              <View style={[styles.statusDot, { backgroundColor: session.completed ? theme.success : theme.warning }]} />
              <Text style={[styles.status, { color: theme.muted }]}>{session.completed ? 'Completed' : 'Ended early'}</Text>
            </View>
          </View>
        </View>
      )}
      <Text style={[styles.privacy, { color: theme.muted }]}>Your session history is stored on this device and included in Go Back backups.</Text>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { paddingHorizontal: 16, paddingVertical: 7, flexDirection: 'row', alignItems: 'center', gap: 12 },
  back: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  headerTitle: { fontSize: 21, fontWeight: '900', letterSpacing: -0.5 },
  headerSubtitle: { fontSize: 11 },
  content: { padding: 18, paddingBottom: 30, gap: 14 },
  goalCard: { borderWidth: 1, borderRadius: 24, padding: 16, gap: 12 },
  goalHeader: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  goalIcon: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  goalTitle: { fontSize: 14, fontWeight: '900' },
  goalSubtitle: { fontSize: 11 },
  goalPercent: { fontSize: 21, fontWeight: '900', fontVariant: ['tabular-nums'] },
  goalTrack: { height: 9, borderRadius: 5, overflow: 'hidden' },
  goalFill: { height: '100%', borderRadius: 5 },
  goalActions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  goalHint: { fontSize: 10, flex: 1 },
  goalControls: { flexDirection: 'row', gap: 8 },
  goalControl: { width: 36, height: 36, borderRadius: 13, alignItems: 'center', justifyContent: 'center' },
  goalError: { fontSize: 11 },
  hero: { borderWidth: 1, borderRadius: 28, padding: 20, alignItems: 'center', gap: 5 },
  heroIcon: { width: 48, height: 48, borderRadius: 17, alignItems: 'center', justifyContent: 'center', marginBottom: 3 },
  eyebrow: { fontSize: 10, fontWeight: '900', letterSpacing: 1.8 },
  heroTime: { fontSize: 34, fontWeight: '900', letterSpacing: -1.2, fontVariant: ['tabular-nums'], marginTop: 4 },
  heroCaption: { fontSize: 12 },
  summaryRow: { flexDirection: 'row', gap: 10, width: '100%', marginTop: 13 },
  summaryChip: { flex: 1, minHeight: 65, borderRadius: 18, alignItems: 'center', justifyContent: 'center', gap: 2 },
  summaryValue: { fontSize: 21, fontWeight: '900', fontVariant: ['tabular-nums'] },
  summaryLabel: { fontSize: 10, fontWeight: '700' },
  listHeader: { flexDirection: 'row', alignItems: 'baseline', justifyContent: 'space-between', marginTop: 5 },
  sectionTitle: { fontSize: 19, fontWeight: '900', letterSpacing: -0.4 },
  count: { fontSize: 11, fontWeight: '700' },
  filterRow: { flexDirection: 'row', padding: 5, borderWidth: 1, borderRadius: 18, gap: 4 },
  filter: { flex: 1, minHeight: 42, alignItems: 'center', justifyContent: 'center', borderRadius: 13 },
  sessionCard: { borderWidth: 1, borderRadius: 22, padding: 14, flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  sessionIcon: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  sessionTitleRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sessionTitle: { fontSize: 14, fontWeight: '900', flexShrink: 1 },
  duration: { fontSize: 14, fontWeight: '900', fontVariant: ['tabular-nums'] },
  date: { fontSize: 10, lineHeight: 15 },
  tag: { alignSelf: 'flex-start', paddingHorizontal: 9, paddingVertical: 5, borderRadius: 10 },
  tagText: { fontSize: 10, fontWeight: '800' },
  note: { fontSize: 12, lineHeight: 17 },
  sessionFooter: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 2 },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  status: { fontSize: 10, fontWeight: '700' },
  empty: { borderWidth: 1, borderRadius: 24, alignItems: 'center', gap: 10, padding: 24 },
  emptyTitle: { fontSize: 16, fontWeight: '900', textAlign: 'center' },
  emptyText: { fontSize: 12, lineHeight: 18, textAlign: 'center' },
  retry: { marginTop: 4, borderRadius: 16, paddingHorizontal: 18, paddingVertical: 12, minHeight: 44, justifyContent: 'center' },
  privacy: { fontSize: 10, textAlign: 'center', lineHeight: 16, marginTop: 4 },
});
