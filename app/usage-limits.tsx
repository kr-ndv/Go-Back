import { useCallback, useState } from 'react';
import { NativeModules, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { evaluateAndNotifyUsageLimits, type UsageLimit } from '@/lib/usageLimits';
import { syncUsageLimitsToNative } from '@/lib/usageNativeSync';
import { showThemedDialog } from '@/state/dialog';

type UsageStat = { packageName: string; appName: string; foregroundTimeMs: number };
type UsageBridge = { hasUsageAccess: () => Promise<boolean>; openUsageAccessSettings: () => Promise<boolean>; getUsageStats: (days: number) => Promise<UsageStat[]> };
const usage = NativeModules.GoBackUsage as UsageBridge | undefined;
const formatDuration = (ms: number) => { const min = Math.floor(ms / 60000); return min >= 60 ? `${Math.floor(min/60)}h ${min%60}m` : `${min}m`; };

export default function UsageLimitsScreen() {
  const theme = useAppTheme();
  const [stats, setStats] = useState<UsageStat[]>([]);
  const [limits, setLimits] = useState<UsageLimit[]>([]);
  const [hasAccess, setHasAccess] = useState(false);
  const [search, setSearch] = useState('');
  const [editingPackage, setEditingPackage] = useState('');
  const [minutes, setMinutes] = useState('60');
  const [warningPercent, setWarningPercent] = useState('80');
  const [status, setStatus] = useState('');

  const load = useCallback(async () => {
    try {
      const db = await getDatabase();
      const saved = await db.getAllAsync<UsageLimit>('SELECT id, package_name, app_name, limit_minutes, warning_percent, enabled FROM usage_limits ORDER BY app_name ASC');
      setLimits(saved);
      if (!usage) { setHasAccess(false); setStats([]); return; }
      const allowed = await usage.hasUsageAccess();
      setHasAccess(allowed);
      setStats(allowed ? await usage.getUsageStats(1) : []);
      await syncUsageLimitsToNative(saved);
      setStatus('');
    } catch { setStatus('Could not load screen-time limits.'); }
  }, []);

  useFocusEffect(useCallback(() => { void load(); }, [load]));
  const visible = stats.filter(item => !search.trim() || item.appName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()) || item.packageName.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));
  const findLimit = (pkg: string) => limits.find(limit => limit.package_name === pkg);

  const begin = (stat: UsageStat) => {
    const existing = findLimit(stat.packageName);
    setEditingPackage(stat.packageName);
    setMinutes(String(existing?.limit_minutes ?? 60));
    setWarningPercent(String(existing?.warning_percent ?? 80));
  };

  const saveLimit = async (stat: UsageStat) => {
    const limitMinutes = Number(minutes);
    const warn = Number(warningPercent);
    if (!Number.isInteger(limitMinutes) || limitMinutes < 1 || limitMinutes > 1440) { showThemedDialog('Check the daily limit', 'Choose a whole number from 1 to 1440 minutes.'); return; }
    if (!Number.isInteger(warn) || warn < 50 || warn > 99) { showThemedDialog('Check the warning level', 'Choose a warning percentage from 50 to 99.'); return; }
    try {
      const db = await getDatabase();
      await db.runAsync(
        'INSERT INTO usage_limits (id, package_name, app_name, limit_minutes, warning_percent, enabled, created_at) VALUES (?, ?, ?, ?, ?, 1, ?) ON CONFLICT(package_name) DO UPDATE SET app_name=excluded.app_name, limit_minutes=excluded.limit_minutes, warning_percent=excluded.warning_percent, enabled=1',
        findLimit(stat.packageName)?.id ?? createLocalId('limit'), stat.packageName, stat.appName.slice(0,120), limitMinutes, warn, new Date().toISOString()
      );
      setEditingPackage(''); await load(); await evaluateAndNotifyUsageLimits();
    } catch { showThemedDialog('Limit not saved', 'Go Back could not save this app limit.'); }
  };

  const remove = async (packageName: string) => {
    const db = await getDatabase(); await db.runAsync('DELETE FROM usage_limits WHERE package_name = ?', packageName); await load();
  };

  const openAccess = async () => { try { await usage?.openUsageAccessSettings(); } catch { setStatus('Open Android Settings → Special app access → Usage access.'); } };

  return <SafeAreaView style={[styles.safe,{backgroundColor:theme.background}]}>
    <View style={styles.header}><Pressable onPress={() => router.back()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="arrow-back" size={20} color={theme.text}/></Pressable><Text style={[styles.headerTitle,{color:theme.text}]}>Screen-time limits</Text><Pressable onPress={() => void load()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="refresh" size={18} color={theme.text}/></Pressable></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow,{color:theme.muted}]}>GENTLE BOUNDARIES</Text><Text style={[styles.title,{color:theme.text}]}>Notice before time slips away.</Text><Text style={[styles.subtitle,{color:theme.muted}]}>Set local daily alerts for selected apps. Android usage data can be delayed, so these are reminders—not guaranteed real-time enforcement.</Text>
      {!usage ? <View style={[styles.notice,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Ionicons name="construct-outline" size={23} color={theme.primary}/><Text style={[styles.hint,{color:theme.muted,flex:1}]}>Install a native Android build to use Usage Access.</Text></View> : !hasAccess ? <View style={[styles.notice,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Ionicons name="phone-portrait-outline" size={23} color={theme.primary}/><View style={{flex:1,gap:7}}><Text style={[styles.rowTitle,{color:theme.text}]}>Usage Access required</Text><Text style={[styles.hint,{color:theme.muted}]}>Go Back needs Android Usage Access to compare today’s app time with your limits.</Text><Pressable onPress={() => void openAccess()} style={[styles.smallPrimary,{backgroundColor:theme.primary}]}><Text style={{color:theme.onPrimary,fontWeight:'800'}}>Open Android settings</Text></Pressable></View></View> : <>
        <TextInput value={search} onChangeText={setSearch} placeholder="Search apps used today…" placeholderTextColor={theme.muted} style={[styles.input,{color:theme.text,backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}/>
        {visible.map(stat => { const limit=findLimit(stat.packageName); const editing=editingPackage===stat.packageName; return <View key={stat.packageName} style={[styles.card,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={styles.row}><View style={[styles.icon,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="apps-outline" size={18} color={theme.primary}/></View><View style={{flex:1,gap:3}}><Text style={[styles.rowTitle,{color:theme.text}]}>{stat.appName}</Text><Text style={[styles.hint,{color:theme.muted}]}>{formatDuration(stat.foregroundTimeMs)} today{limit ? ` · ${limit.limit_minutes}m limit · warn at ${limit.warning_percent}%` : ''}</Text></View>{limit && !editing ? <Pressable onPress={() => begin(stat)} style={[styles.edit,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="create-outline" size={17} color={theme.text}/></Pressable> : null}</View>{editing ? <View style={styles.editor}><TextInput value={minutes} onChangeText={setMinutes} keyboardType="number-pad" placeholder="Daily minutes" placeholderTextColor={theme.muted} style={[styles.editorInput,{color:theme.text,borderColor:theme.border}]}/><TextInput value={warningPercent} onChangeText={setWarningPercent} keyboardType="number-pad" placeholder="Warn %" placeholderTextColor={theme.muted} style={[styles.editorInput,{color:theme.text,borderColor:theme.border}]}/><Pressable onPress={() => void saveLimit(stat)} style={[styles.save,{backgroundColor:theme.primary}]}><Text style={{color:theme.onPrimary,fontWeight:'900'}}>Save</Text></Pressable><Pressable onPress={()=>setEditingPackage('')} style={styles.cancel}><Text style={{color:theme.muted,fontWeight:'800'}}>Cancel</Text></Pressable></View> : <View style={styles.actions}>{limit ? <Pressable onPress={()=>void remove(stat.packageName)} style={styles.link}><Text style={{color:theme.danger,fontSize:11,fontWeight:'800'}}>Remove limit</Text></Pressable> : <Pressable onPress={()=>begin(stat)} style={[styles.add,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="add" size={16} color={theme.primary}/><Text style={{color:theme.text,fontSize:11,fontWeight:'800'}}>Set daily limit</Text></Pressable>}</View>}</View>; })}
        {visible.length===0 && <View style={[styles.notice,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Text style={[styles.hint,{color:theme.muted}]}>No matching usage data is available yet.</Text></View>}
      </>}
      {!!status && <Text style={{color:theme.danger,fontSize:11}}>{status}</Text>}
    </ScrollView>
  </SafeAreaView>;
}
const styles=StyleSheet.create({safe:{flex:1},header:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingHorizontal:20,paddingVertical:10},back:{width:44,height:44,borderRadius:16,alignItems:'center',justifyContent:'center'},headerTitle:{fontSize:16,fontWeight:'800'},content:{padding:20,paddingBottom:40,gap:13},eyebrow:{fontSize:10,fontWeight:'800',letterSpacing:1.6},title:{fontSize:28,fontWeight:'900',letterSpacing:-1},subtitle:{fontSize:12,lineHeight:18,marginTop:-7},notice:{borderWidth:1,borderRadius:22,padding:16,flexDirection:'row',alignItems:'center',gap:11},hint:{fontSize:11,lineHeight:17},rowTitle:{fontSize:13,fontWeight:'800'},smallPrimary:{alignSelf:'flex-start',minHeight:40,borderRadius:12,paddingHorizontal:12,justifyContent:'center'},input:{borderWidth:1,borderRadius:14,minHeight:46,paddingHorizontal:12,fontSize:12},card:{borderWidth:1,borderRadius:19,padding:13,gap:9},row:{flexDirection:'row',alignItems:'center',gap:10},icon:{width:39,height:39,borderRadius:13,alignItems:'center',justifyContent:'center'},edit:{width:40,height:40,borderRadius:12,alignItems:'center',justifyContent:'center'},actions:{alignItems:'flex-end'},add:{minHeight:38,borderRadius:12,paddingHorizontal:11,flexDirection:'row',alignItems:'center',gap:5},link:{minHeight:38,justifyContent:'center',paddingHorizontal:6},editor:{flexDirection:'row',gap:6,flexWrap:'wrap',alignItems:'center'},editorInput:{borderWidth:1,borderRadius:12,minHeight:42,paddingHorizontal:10,minWidth:100,flex:1},save:{minHeight:42,borderRadius:12,paddingHorizontal:15,justifyContent:'center'},cancel:{minHeight:42,paddingHorizontal:8,justifyContent:'center'}});
