import { useCallback, useMemo, useState } from 'react';
import { NativeModules, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting, setSetting } from '@/lib/database';
import { aggregatePeriod, dateRangeEndingToday, percentChange, previousRange, type DailyUsagePoint, type UsageCategory } from '@/lib/insights';
import { dailyFocusTotals, focusGoalStats, type FocusHistoryPoint } from '@/lib/focusInsights';
import { getNativeUnlockCount } from '@/lib/nativeBridge';

type UsageStat = { packageName: string; appName: string; foregroundTimeMs: number };
type UsageBridge = {
  hasUsageAccess: () => Promise<boolean>;
  openUsageAccessSettings: () => Promise<boolean>;
  getUsageStats: (days: number) => Promise<UsageStat[]>;
  getDailyUsageStats?: (days: number) => Promise<DailyUsagePoint[]>;
};
const usage = NativeModules.GoBackUsage as UsageBridge | undefined;

let cachedDailyUsage: { timestamp: number; rows: DailyUsagePoint[] } | null = null;
const USAGE_CACHE_TTL_MS = 90_000;

const formatMinutes = (seconds: number) => {
  const min = Math.floor(seconds / 60);
  return min >= 60 ? `${Math.floor(min / 60)}h ${min % 60}m` : `${min}m`;
};
const formatMs = (ms: number) => {
  const min = Math.floor(ms / 60000);
  return min >= 60 ? `${Math.floor(min / 60)}h ${min % 60}m` : `${min}m`;
};
const changeLabel = (value: number | null) =>
  value === null ? 'New' : value === 0 ? 'No change' : `${value > 0 ? '+' : ''}${value}%`;

const CATEGORIES: { value: UsageCategory; label: string; icon: keyof typeof Ionicons.glyphMap; color: string }[] = [
  { value: 'productive', label: 'Productive', icon: 'checkmark-circle', color: '#6EE7B7' },
  { value: 'distracting', label: 'Distracting', icon: 'alert-circle', color: '#F87171' },
  { value: 'neutral', label: 'Neutral', icon: 'ellipse-outline', color: '#9CA3AF' },
];

export default function AnalyticsScreen() {
  const theme = useAppTheme();
  const [period, setPeriod] = useState<1 | 7 | 30>(7);
  const [sessions, setSessions] = useState<FocusHistoryPoint[]>([]);
  const [usageRows, setUsageRows] = useState<DailyUsagePoint[]>([]);
  const [categories, setCategories] = useState<Record<string, UsageCategory>>({});
  const [goalMinutes, setGoalMinutes] = useState(120);
  const [unlocksToday, setUnlocksToday] = useState(0);
  const [usageAvailable, setUsageAvailable] = useState(false);
  const [appSearch, setAppSearch] = useState('');
  const [error, setError] = useState('');

  const load = useCallback(async (forceRefresh = false, isMounted?: () => boolean) => {
    try {
      const db = await getDatabase();
      if (isMounted && !isMounted()) return;
      const [history, savedCategories, savedGoal, unlocks] = await Promise.all([
        db.getAllAsync<FocusHistoryPoint>(
          'SELECT started_at, duration_seconds, session_type FROM focus_sessions WHERE session_type = \'focus\' AND datetime(started_at) >= datetime(?, \'-90 days\') ORDER BY started_at ASC',
          new Date().toISOString()
        ),
        getSetting<Record<string, UsageCategory>>('usage.categories', {}),
        getSetting<number>('focus.dailyGoalMinutes', 120),
        getNativeUnlockCount(1),
      ]);
      if (isMounted && !isMounted()) return;
      setSessions(history);
      setCategories(savedCategories && typeof savedCategories === 'object' ? savedCategories : {});
      setGoalMinutes(Number.isInteger(savedGoal) && savedGoal >= 15 && savedGoal <= 480 ? savedGoal : 120);
      setUnlocksToday(unlocks);

      if (usage && (await usage.hasUsageAccess())) {
        if (isMounted && !isMounted()) return;
        let daily: DailyUsagePoint[];
        if (!forceRefresh && cachedDailyUsage && (Date.now() - cachedDailyUsage.timestamp < USAGE_CACHE_TTL_MS)) {
          daily = cachedDailyUsage.rows;
        } else if (usage.getDailyUsageStats) {
          daily = await usage.getDailyUsageStats(60);
          cachedDailyUsage = { timestamp: Date.now(), rows: daily };
        } else {
          daily = [];
        }
        if (isMounted && !isMounted()) return;
        setUsageRows(daily);
        setUsageAvailable(true);
      } else {
        if (isMounted && !isMounted()) return;
        setUsageRows([]);
        setUsageAvailable(false);
      }
      setError('');
    } catch {
      if (isMounted && !isMounted()) return;
      setError('Could not refresh usage analytics.');
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      let isCurrent = true;
      void load(false, () => isCurrent);
      return () => {
        isCurrent = false;
      };
    }, [load])
  );

  const currentRange = useMemo(() => dateRangeEndingToday(period), [period]);
  const previous = useMemo(() => previousRange(currentRange), [currentRange]);
  const current = useMemo(() => aggregatePeriod(sessions, usageRows, categories, currentRange), [sessions, usageRows, categories, currentRange]);
  const before = useMemo(() => aggregatePeriod(sessions, usageRows, categories, previous), [sessions, usageRows, categories, previous]);
  const streak = useMemo(() => focusGoalStats(sessions, goalMinutes), [sessions, goalMinutes]);

  const focusChange = percentChange(current.focusSeconds, before.focusSeconds);
  const usageChange = percentChange(current.usageMs, before.usageMs);

  // Group daily usage points for chart
  const dailyChartPoints = useMemo(() => {
    const focusByDay = new Map(dailyFocusTotals(sessions).map(d => [d.date, d.seconds]));
    const usageByDay = new Map<string, number>();
    for (const row of usageRows) {
      if (row.date >= currentRange.start && row.date <= currentRange.end) {
        usageByDay.set(row.date, (usageByDay.get(row.date) ?? 0) + row.foregroundTimeMs);
      }
    }
    const days: { date: string; label: string; focusMinutes: number; usageMinutes: number }[] = [];
    const count = Math.min(period, 14); // show up to 14 bars for readability
    const now = new Date();
    for (let i = count - 1; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      const fSec = focusByDay.get(key) ?? 0;
      const uMs = usageByDay.get(key) ?? 0;
      const weekday = d.toLocaleDateString(undefined, { weekday: 'narrow' });
      const dayNum = d.getDate();
      days.push({
        date: key,
        label: period <= 7 ? weekday : `${dayNum}`,
        focusMinutes: Math.floor(fSec / 60),
        usageMinutes: Math.floor(uMs / 60000),
      });
    }
    return days;
  }, [sessions, usageRows, currentRange, period]);

  const maxMinutesInChart = Math.max(60, ...dailyChartPoints.map(p => Math.max(p.focusMinutes, p.usageMinutes)));

  // Filter and sort apps for category management
  const aggregatedApps = useMemo(() => {
    const map = new Map<string, { packageName: string; appName: string; foregroundTimeMs: number }>();
    for (const row of usageRows) {
      if (row.date >= currentRange.start && row.date <= currentRange.end) {
        const existing = map.get(row.packageName);
        map.set(row.packageName, {
          packageName: row.packageName,
          appName: row.appName,
          foregroundTimeMs: (existing?.foregroundTimeMs ?? 0) + row.foregroundTimeMs,
        });
      }
    }
    const list = [...map.values()].sort((a, b) => b.foregroundTimeMs - a.foregroundTimeMs);
    if (!appSearch.trim()) return list;
    const q = appSearch.trim().toLowerCase();
    return list.filter(a => a.appName.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q));
  }, [usageRows, currentRange, appSearch]);

  const assignCategory = async (packageName: string, category: UsageCategory) => {
    const next = { ...categories, [packageName]: category };
    setCategories(next);
    try {
      await setSetting('usage.categories', next);
    } catch {
      setCategories(categories);
    }
  };

  const openUsageAccess = async () => {
    try { await usage?.openUsageAccessSettings(); }
    catch { /* Ignore */ }
  };

  const totalTimeCategory = current.productiveMs + current.distractingMs + current.neutralMs;
  const prodPct = totalTimeCategory > 0 ? Math.round((current.productiveMs / totalTimeCategory) * 100) : 0;
  const distPct = totalTimeCategory > 0 ? Math.round((current.distractingMs / totalTimeCategory) * 100) : 0;
  const neutPct = Math.max(0, 100 - prodPct - distPct);

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
      <View style={styles.header}>
        <View>
          <Text style={[styles.eyebrow, { color: theme.muted }]}>REGAIN INSIGHTS</Text>
          <Text style={[styles.headerTitle, { color: theme.text }]}>Usage Analytics</Text>
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel="Refresh analytics" onPress={() => void load(true)} style={[styles.iconButton, { backgroundColor: theme.surfaceAlt }]}>
          <Ionicons name="refresh" size={18} color={theme.text} />
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {/* Period Selector */}
        <View style={styles.periodRow}>
          {([1, 7, 30] as const).map(p => (
            <Pressable key={p} accessibilityRole="button" accessibilityState={{ selected: period === p }} onPress={() => setPeriod(p)} style={[styles.periodTab, { backgroundColor: period === p ? theme.primary : theme.surfaceElevated, borderColor: period === p ? theme.primary : theme.border }]}>
              <Text style={{ color: period === p ? theme.onPrimary : theme.text, fontWeight: '800', fontSize: 12 }}>
                {p === 1 ? 'Today' : p === 7 ? '7 Days' : '30 Days'}
              </Text>
            </Pressable>
          ))}
        </View>

        {/* Top KPI Grid */}
        <View style={styles.kpiGrid}>
          <View style={[styles.kpiCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
            <View style={[styles.kpiIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="timer-outline" size={18} color={theme.primary} /></View>
            <Text style={[styles.kpiLabel, { color: theme.muted }]}>FOCUS TIME</Text>
            <Text style={[styles.kpiVal, { color: theme.text }]}>{formatMinutes(current.focusSeconds)}</Text>
            <Text style={[styles.kpiHint, { color: theme.muted }]}>{changeLabel(focusChange)} vs prev period</Text>
          </View>
          <View style={[styles.kpiCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
            <View style={[styles.kpiIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="phone-portrait-outline" size={18} color={theme.primary} /></View>
            <Text style={[styles.kpiLabel, { color: theme.muted }]}>SCREEN TIME</Text>
            <Text style={[styles.kpiVal, { color: theme.text }]}>{usageAvailable ? formatMs(current.usageMs) : 'Off'}</Text>
            <Text style={[styles.kpiHint, { color: theme.muted }]}>{usageAvailable ? `${changeLabel(usageChange)} vs prev period` : 'Enable Usage Access'}</Text>
          </View>
        </View>

        {/* Unlocks & Consistency Card */}
        <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.cardHeader}>
            <View style={[styles.kpiIcon, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="flame-outline" size={20} color={theme.primary} /></View>
            <View style={{ flex: 1 }}>
              <Text style={[styles.cardTitle, { color: theme.text }]}>Focus Streak &amp; Phone Unlocks</Text>
              <Text style={[styles.cardSubtitle, { color: theme.muted }]}>Consistency builds long-term digital focus</Text>
            </View>
          </View>
          <View style={styles.statsStrip}>
            <View style={styles.statBox}>
              <Text style={[styles.statVal, { color: theme.text }]}>🔥 {streak.currentStreak}</Text>
              <Text style={[styles.statSub, { color: theme.muted }]}>current streak</Text>
            </View>
            <View style={styles.statBox}>
              <Text style={[styles.statVal, { color: theme.text }]}>🏆 {streak.bestStreak}</Text>
              <Text style={[styles.statSub, { color: theme.muted }]}>best streak</Text>
            </View>
            <View style={styles.statBox}>
              <Text style={[styles.statVal, { color: theme.text }]}>🔓 {unlocksToday}</Text>
              <Text style={[styles.statSub, { color: theme.muted }]}>unlocks today</Text>
            </View>
          </View>
        </View>

        {/* Visual Comparison Chart: Focus vs Screen Time */}
        <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={styles.cardHeader}>
            <Ionicons name="bar-chart-outline" size={19} color={theme.primary} />
            <Text style={[styles.cardTitle, { color: theme.text }]}>Focus vs Screen Time</Text>
          </View>
          <View style={styles.chartLegend}>
            <View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: theme.primary }]} /><Text style={[styles.legendText, { color: theme.muted }]}>Focus</Text></View>
            <View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: '#60A5FA' }]} /><Text style={[styles.legendText, { color: theme.muted }]}>Screen</Text></View>
          </View>
          <View style={styles.barChartContainer}>
            {dailyChartPoints.map(p => {
              const fHeight = Math.max(4, Math.round((p.focusMinutes / maxMinutesInChart) * 110));
              const uHeight = Math.max(4, Math.round((p.usageMinutes / maxMinutesInChart) * 110));
              return (
                <View key={p.date} style={styles.barCol}>
                  <View style={styles.barsGroup}>
                    <View style={[styles.singleBar, { height: fHeight, backgroundColor: theme.primary }]} />
                    <View style={[styles.singleBar, { height: uHeight, backgroundColor: '#60A5FA' }]} />
                  </View>
                  <Text style={[styles.barLabel, { color: theme.muted }]}>{p.label}</Text>
                </View>
              );
            })}
          </View>
        </View>

        {/* Categorization Split Bar */}
        {usageAvailable && (
          <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
            <Text style={[styles.cardTitle, { color: theme.text }]}>App Categories Breakdown</Text>
            <View style={styles.splitBar}>
              {prodPct > 0 && <View style={[styles.splitSegment, { width: `${prodPct}%`, backgroundColor: '#34D399' }]} />}
              {distPct > 0 && <View style={[styles.splitSegment, { width: `${distPct}%`, backgroundColor: '#F87171' }]} />}
              {neutPct > 0 && <View style={[styles.splitSegment, { width: `${neutPct}%`, backgroundColor: '#9CA3AF' }]} />}
            </View>
            <View style={styles.splitLegend}>
              <View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: '#34D399' }]} /><Text style={[styles.legendText, { color: theme.text }]}>Productive: {formatMs(current.productiveMs)} ({prodPct}%)</Text></View>
              <View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: '#F87171' }]} /><Text style={[styles.legendText, { color: theme.text }]}>Distracting: {formatMs(current.distractingMs)} ({distPct}%)</Text></View>
              <View style={styles.legendItem}><View style={[styles.legendDot, { backgroundColor: '#9CA3AF' }]} /><Text style={[styles.legendText, { color: theme.text }]}>Neutral: {formatMs(current.neutralMs)} ({neutPct}%)</Text></View>
            </View>
          </View>
        )}

        {/* App Classification List */}
        {!usageAvailable ? (
          <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, alignItems: 'center', padding: 20, gap: 10 }]}>
            <Ionicons name="shield-outline" size={32} color={theme.primary} />
            <Text style={[styles.cardTitle, { color: theme.text }]}>Usage Access Required</Text>
            <Text style={[styles.cardSubtitle, { color: theme.muted, textAlign: 'center' }]}>Allow Android Usage Access so Go Back can measure your screen time and calculate app categories.</Text>
            <Pressable onPress={() => void openUsageAccess()} style={[styles.primaryButton, { backgroundColor: theme.primary }]}>
              <Text style={{ color: theme.onPrimary, fontWeight: '800' }}>Open Android Settings</Text>
            </Pressable>
          </View>
        ) : (
          <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
            <View style={styles.cardHeader}>
              <Text style={[styles.cardTitle, { color: theme.text }]}>Classify Your Apps</Text>
              <Pressable onPress={() => router.push('/usage-limits')} style={{ padding: 4 }}>
                <Text style={{ color: theme.primary, fontSize: 11, fontWeight: '800' }}>Manage Limits</Text>
              </Pressable>
            </View>
            <TextInput value={appSearch} onChangeText={setAppSearch} placeholder="Search apps to categorize…" placeholderTextColor={theme.muted} style={[styles.searchInput, { color: theme.text, backgroundColor: theme.surfaceAlt, borderColor: theme.border }]} />
            <View style={styles.appList}>
              {aggregatedApps.slice(0, 30).map(app => {
                const currentCat = categories[app.packageName] ?? 'neutral';
                return (
                  <View key={app.packageName} style={[styles.appRow, { borderBottomColor: theme.border }]}>
                    <View style={{ flex: 1, gap: 2 }}>
                      <Text numberOfLines={1} style={[styles.appName, { color: theme.text }]}>{app.appName}</Text>
                      <Text style={[styles.appDuration, { color: theme.muted }]}>{formatMs(app.foregroundTimeMs)}</Text>
                    </View>
                    <View style={styles.catButtons}>
                      {CATEGORIES.map(c => {
                        const active = currentCat === c.value;
                        return (
                          <Pressable key={c.value} accessibilityRole="button" accessibilityLabel={`Mark ${app.appName} as ${c.label}`} onPress={() => void assignCategory(app.packageName, c.value)} style={[styles.catBtn, { backgroundColor: active ? c.color : theme.surfaceAlt, borderColor: active ? c.color : theme.border }]}>
                            <Ionicons name={c.icon} size={14} color={active ? '#081B14' : theme.muted} />
                          </Pressable>
                        );
                      })}
                    </View>
                  </View>
                );
              })}
              {aggregatedApps.length === 0 && (
                <Text style={[styles.emptyText, { color: theme.muted }]}>No app usage recorded in this period.</Text>
              )}
            </View>
          </View>
        )}

        {!!error && <Text style={{ color: theme.danger, fontSize: 12 }}>{error}</Text>}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 12 },
  eyebrow: { fontSize: 10, letterSpacing: 1.8, fontWeight: '800' },
  headerTitle: { fontSize: 26, fontWeight: '900', letterSpacing: -0.8 },
  iconButton: { width: 44, height: 44, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  content: { padding: 18, paddingBottom: 40, gap: 14 },
  periodRow: { flexDirection: 'row', gap: 8 },
  periodTab: { flex: 1, minHeight: 40, borderRadius: 13, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  kpiGrid: { flexDirection: 'row', gap: 10 },
  kpiCard: { flex: 1, borderWidth: 1, borderRadius: 20, padding: 14, gap: 5 },
  kpiIcon: { width: 36, height: 36, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  kpiLabel: { fontSize: 9, fontWeight: '800', letterSpacing: 1 },
  kpiVal: { fontSize: 22, fontWeight: '900', letterSpacing: -0.6 },
  kpiHint: { fontSize: 10 },
  card: { borderWidth: 1, borderRadius: 22, padding: 16, gap: 12 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'space-between' },
  cardTitle: { fontSize: 15, fontWeight: '800' },
  cardSubtitle: { fontSize: 11 },
  statsStrip: { flexDirection: 'row', gap: 8 },
  statBox: { flex: 1, alignItems: 'center', paddingVertical: 6, gap: 2 },
  statVal: { fontSize: 20, fontWeight: '900' },
  statSub: { fontSize: 10 },
  chartLegend: { flexDirection: 'row', gap: 12, justifyContent: 'flex-end' },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  legendDot: { width: 8, height: 8, borderRadius: 4 },
  legendText: { fontSize: 11 },
  barChartContainer: { flexDirection: 'row', height: 140, alignItems: 'flex-end', justifyContent: 'space-between', paddingTop: 10 },
  barCol: { flex: 1, alignItems: 'center', gap: 6 },
  barsGroup: { flexDirection: 'row', alignItems: 'flex-end', gap: 2, height: 110 },
  singleBar: { width: 8, borderTopLeftRadius: 4, borderTopRightRadius: 4 },
  barLabel: { fontSize: 9, fontWeight: '700' },
  splitBar: { height: 10, borderRadius: 5, overflow: 'hidden', flexDirection: 'row' },
  splitSegment: { height: '100%' },
  splitLegend: { gap: 4, marginTop: 4 },
  primaryButton: { borderRadius: 14, paddingHorizontal: 16, paddingVertical: 12, alignItems: 'center' },
  searchInput: { minHeight: 42, borderWidth: 1, borderRadius: 13, paddingHorizontal: 12, fontSize: 12 },
  appList: { gap: 6 },
  appRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 9, borderBottomWidth: StyleSheet.hairlineWidth },
  appName: { fontSize: 13, fontWeight: '800' },
  appDuration: { fontSize: 11 },
  catButtons: { flexDirection: 'row', gap: 6 },
  catBtn: { width: 32, height: 32, borderRadius: 10, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { textAlign: 'center', fontSize: 12, paddingVertical: 16 },
});
