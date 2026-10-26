import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, AppState, NativeModules, ScrollView, StyleSheet, Text, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';
import { getSetting, setSetting } from '@/lib/database';

type UsageStat = { packageName: string; appName: string; foregroundTimeMs: number };
type UsageCategory = 'productive' | 'distracting' | 'neutral';
const CATEGORIES: { value: UsageCategory; label: string }[] = [{ value: 'productive', label: 'Productive' }, { value: 'distracting', label: 'Distracting' }, { value: 'neutral', label: 'Neutral' }];
type UsageBridge = {
  hasUsageAccess: () => Promise<boolean>;
  openUsageAccessSettings: () => Promise<boolean>;
  getUsageStats: (days: number) => Promise<UsageStat[]>;
};
const usage = NativeModules.GoBackUsage as UsageBridge | undefined;

function formatDuration(ms: number) {
  const minutes = Math.floor(ms / 60000);
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours ? hours + 'h ' + remainder + 'm' : remainder + 'm';
}

export default function UsageScreen() {
  const theme = useAppTheme();
  const [days, setDays] = useState(1);
  const [hasAccess, setHasAccess] = useState(false);
  const [stats, setStats] = useState<UsageStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [categories, setCategories] = useState<Record<string, UsageCategory>>({});
  const [categorySaveError, setCategorySaveError] = useState('');

  const refresh = useCallback(async () => {
    if (!usage) {
      setError('Screen-time access needs the Go Back native Android build. It is not available in Expo Go.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const granted = await usage.hasUsageAccess();
      setHasAccess(granted);
      if (!granted) { setStats([]); setLoading(false); return; }
      const [rows, savedCategories] = await Promise.all([
        usage.getUsageStats(days),
        getSetting<Record<string, UsageCategory>>('usage.categories', {}),
      ]);
      setStats(rows);
      const validCategories: Record<string, UsageCategory> = {};
      if (savedCategories && typeof savedCategories === 'object') {
        for (const [packageName, category] of Object.entries(savedCategories)) {
          if (category === 'productive' || category === 'distracting' || category === 'neutral') validCategories[packageName] = category;
        }
      }
      setCategories(validCategories);
    } catch {
      setError('Could not read screen-time data. Check the Android Usage Access setting and try again.');
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => { void refresh(); }, [refresh]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => { if (state === 'active') void refresh(); });
    return () => subscription.remove();
  }, [refresh]);

  const total = stats.reduce((sum, item) => sum + item.foregroundTimeMs, 0);
  const max = Math.max(1, ...stats.map(item => item.foregroundTimeMs));
  const categoryTotals = stats.reduce((sum, item) => {
    const category = categories[item.packageName] ?? 'neutral';
    sum[category] += item.foregroundTimeMs;
    return sum;
  }, { productive: 0, distracting: 0, neutral: 0 });
  const assignCategory = async (packageName: string, category: UsageCategory) => {
    const previous = categories;
    const next = { ...previous, [packageName]: category };
    setCategories(next);
    setCategorySaveError('');
    try {
      await setSetting('usage.categories', next);
    } catch {
      setCategories(previous);
      setCategorySaveError('App categories could not be saved. Please try again.');
    }
  };
  const grantAccess = async () => {
    if (!usage) { setError('Install a Go Back Android development or debug build to use this feature.'); return; }
    try { await usage.openUsageAccessSettings(); }
    catch { setError('Android could not open Usage Access settings. Open Settings → Apps → Special app access → Usage access manually.'); }
  };

  return <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="arrow-back" size={20} color={theme.text} /></Pressable><Text style={[styles.headerTitle, { color: theme.text }]}>Screen-time insights</Text><Pressable onPress={() => void refresh()} style={[styles.back, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="refresh" size={18} color={theme.text} /></Pressable></View>
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={[styles.eyebrow, { color: theme.muted }]}>AWARENESS, NOT JUDGEMENT</Text>
      <Text style={[styles.title, { color: theme.text }]}>Where your time goes.</Text>
      <Text style={[styles.subtitle, { color: theme.muted }]}>Usage is read from Android on this device. Go Back does not send it to a server.</Text>
      <View style={styles.periods}>{([{ value: 1, label: 'Today' }, { value: 7, label: '7 days' }, { value: 30, label: '30 days' }] as { value: number; label: string }[]).map(period => <Pressable key={period.value} onPress={() => setDays(period.value)} style={[styles.period, { backgroundColor: days === period.value ? theme.primary : theme.surface, borderColor: days === period.value ? theme.primary : theme.border }]}><Text style={{ color: days === period.value ? theme.background : theme.text, fontSize: 12, fontWeight: '800' }}>{period.label}</Text></Pressable>)}</View>

      {!usage && !loading ? <View style={[styles.permissionCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="construct-outline" size={25} color={theme.primary} /></View><Text style={[styles.cardTitle, { color: theme.text }]}>Native Android build required</Text><Text style={[styles.body, { color: theme.muted }]}>Screen-time insights use an Android native module and cannot run inside Expo Go. Install the Go Back debug APK from the repository’s Actions tab first.</Text></View> : !hasAccess && !loading ? <View style={[styles.permissionCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="phone-portrait-outline" size={25} color={theme.primary} /></View><Text style={[styles.cardTitle, { color: theme.text }]}>Allow Usage Access</Text><Text style={[styles.body, { color: theme.muted }]}>Android requires you to grant this special permission yourself. Go Back uses it only to show app usage on this device.</Text><Pressable onPress={() => void grantAccess()} style={[styles.grant, { backgroundColor: theme.primary }]}><Text style={{ color: theme.onPrimary, fontWeight: '800' }}>Open Android settings</Text><Ionicons name="open-outline" size={16} color={theme.background} /></Pressable></View> :
        loading ? <View style={styles.loading}><ActivityIndicator color={theme.primary} /><Text style={[styles.body, { color: theme.muted }]}>Reading usage on this device…</Text></View> :
        <><View style={[styles.totalCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Text style={[styles.totalLabel, { color: theme.muted }]}>{days === 1 ? 'TOTAL TODAY' : 'TOTAL OVER ' + days + ' DAYS'}</Text><Text style={[styles.totalValue, { color: theme.text }]}>{formatDuration(total)}</Text><Text style={[styles.body, { color: theme.muted }]}>{stats.length} apps with recorded foreground time</Text></View>
          <View style={styles.categorySummaryRow}>
            {([{ value: 'productive', label: 'Productive', color: theme.primary }, { value: 'distracting', label: 'Distracting', color: theme.danger }, { value: 'neutral', label: 'Neutral', color: theme.muted }] as { value: UsageCategory; label: string; color: string }[]).map(item =>
              <View key={item.value} style={[styles.categorySummary, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
                <View style={[styles.categoryMark, { backgroundColor: item.color }]} />
                <Text style={[styles.categorySummaryLabel, { color: theme.muted }]}>{item.label}</Text>
                <Text style={[styles.categorySummaryValue, { color: theme.text }]}>{formatDuration(categoryTotals[item.value])}</Text>
              </View>
            )}
          </View>
          <Text style={[styles.section, { color: theme.text }]}>Most-used apps</Text>
          <Text style={[styles.categoryHelp, { color: theme.muted }]}>Classify apps to make these reports more personal. Your labels stay on this device.</Text>
          {stats.length === 0 ? <View style={[styles.permissionCard, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><Ionicons name="analytics-outline" size={27} color={theme.primary} /><Text style={[styles.cardTitle, { color: theme.text }]}>No usage data yet</Text><Text style={[styles.body, { color: theme.muted }]}>Try again later; Android may need time to collect usage events.</Text></View> :
            stats.slice(0, 15).map(item => <View key={item.packageName} style={[styles.appRow, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}><View style={[styles.appGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="apps-outline" size={18} color={theme.primary} /></View><View style={{ flex: 1, gap: 8 }}><View style={styles.appHead}><Text numberOfLines={1} style={[styles.appName, { color: theme.text }]}>{item.appName}</Text><Text style={[styles.appTime, { color: theme.muted }]}>{formatDuration(item.foregroundTimeMs)}</Text></View><View style={[styles.track, { backgroundColor: theme.surfaceAlt }]}><View style={[styles.bar, { width: (Math.max(2, item.foregroundTimeMs / max * 100) + '%') as `${number}%`, backgroundColor: theme.primary }]} /></View><View style={styles.categoryOptions}>{CATEGORIES.map(option => {
            const selected = (categories[item.packageName] ?? 'neutral') === option.value;
            return <Pressable key={option.value} accessibilityRole="button" accessibilityState={{ selected }} accessibilityLabel={'Classify ' + item.appName + ' as ' + option.label} onPress={() => void assignCategory(item.packageName, option.value)} style={[styles.categoryOption, { backgroundColor: selected ? theme.primary : theme.surfaceAlt, borderColor: selected ? theme.primary : theme.border }]}><Text style={{ color: selected ? theme.onPrimary : theme.muted, fontSize: 9, fontWeight: '800' }}>{option.label}</Text></Pressable>;
          })}</View></View></View>)}
        </>}
      {!!error && <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>}
      {!!categorySaveError && <Text style={[styles.error, { color: theme.danger }]}>{categorySaveError}</Text>}
      <Text style={[styles.footnote, { color: theme.muted }]}>Android controls which usage data is available. Values are approximate foreground time and may differ from Digital Wellbeing.</Text>
    </ScrollView>
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 }, header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 20, paddingVertical: 10 },
  back: { width: 40, height: 40, borderRadius: 14, alignItems: 'center', justifyContent: 'center' }, headerTitle: { fontSize: 16, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 40, gap: 13 }, eyebrow: { fontSize: 10, fontWeight: '800', letterSpacing: 1.6 },
  title: { fontSize: 28, fontWeight: '900', letterSpacing: -1.1 }, subtitle: { fontSize: 12, lineHeight: 18, marginTop: -8 },
  periods: { flexDirection: 'row', gap: 8 }, period: { flex: 1, alignItems: 'center', borderRadius: 13, paddingVertical: 12, borderWidth: 1 },
  permissionCard: { alignItems: 'center', padding: 23, borderWidth: 1, borderRadius: 27, gap: 12 }, iconBubble: { width: 52, height: 52, borderRadius: 17, alignItems: 'center', justifyContent: 'center' },
  cardTitle: { fontSize: 17, fontWeight: '800', textAlign: 'center' }, body: { fontSize: 12, lineHeight: 18, textAlign: 'center' },
  grant: { width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 14, borderRadius: 15 },
  loading: { padding: 35, alignItems: 'center', gap: 13 }, totalCard: { padding: 22, borderWidth: 1, borderRadius: 23, gap: 8 },
  totalLabel: { fontSize: 10, letterSpacing: 1.5, fontWeight: '800' }, totalValue: { fontSize: 36, fontWeight: '900', letterSpacing: -1.2 },
  section: { fontSize: 18, fontWeight: '800', marginTop: 5 },
  categorySummaryRow: { flexDirection: 'row', gap: 8 },
  categorySummary: { flex: 1, borderWidth: 1, borderRadius: 17, padding: 10, gap: 5, minWidth: 0 },
  categoryMark: { width: 8, height: 8, borderRadius: 4 },
  categorySummaryLabel: { fontSize: 10, fontWeight: '700' },
  categorySummaryValue: { fontSize: 13, fontWeight: '900' },
  categoryHelp: { fontSize: 11, lineHeight: 16, marginTop: -6 },
  categoryOptions: { flexDirection: 'row', gap: 5, flexWrap: 'wrap' },
  categoryOption: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 8, minHeight: 28, alignItems: 'center', justifyContent: 'center' },
  appRow: { flexDirection: 'row', gap: 12, alignItems: 'center', borderWidth: 1, borderRadius: 17, padding: 13 },
  appGlyph: { width: 39, height: 39, borderRadius: 13, alignItems: 'center', justifyContent: 'center' }, appHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  appName: { flex: 1, fontSize: 13, fontWeight: '700' }, appTime: { fontSize: 12, fontWeight: '700' }, track: { height: 5, borderRadius: 5, overflow: 'hidden' }, bar: { height: 5, borderRadius: 5 },
  error: { fontSize: 12, lineHeight: 18 }, footnote: { fontSize: 10, lineHeight: 16, marginTop: 4 },
});