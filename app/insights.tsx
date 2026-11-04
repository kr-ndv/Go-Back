import { useCallback, useMemo, useState } from 'react';
import { NativeModules, ScrollView, StyleSheet, Text, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getDatabase, getSetting } from '@/lib/database';
import { aggregatePeriod, dateRangeEndingToday, percentChange, previousRange, type DailyUsagePoint, type UsageCategory } from '@/lib/insights';
import { focusGoalStats, type FocusHistoryPoint } from '@/lib/focusInsights';

type UsageBridge = { hasUsageAccess: () => Promise<boolean>; getDailyUsageStats?: (days: number) => Promise<DailyUsagePoint[]> };
const usage = NativeModules.GoBackUsage as UsageBridge | undefined;
const formatMinutes = (seconds: number) => { const min=Math.floor(seconds/60); return min>=60?`${Math.floor(min/60)}h ${min%60}m`:`${min}m`; };
const formatMs = (ms:number) => { const min=Math.floor(ms/60000); return min>=60?`${Math.floor(min/60)}h ${min%60}m`:`${min}m`; };
const changeLabel=(value:number|null)=> value===null?'New data':value===0?'No change':`${value>0?'+':''}${value}%`;

export default function InsightsScreen(){
  const theme=useAppTheme();
  const [rangeDays,setRangeDays]=useState<7|30>(7);
  const [sessions,setSessions]=useState<FocusHistoryPoint[]>([]);
  const [usageRows,setUsageRows]=useState<DailyUsagePoint[]>([]);
  const [categories,setCategories]=useState<Record<string,UsageCategory>>({});
  const [goalMinutes,setGoalMinutes]=useState(120);
  const [usageAvailable,setUsageAvailable]=useState(false);
  const [error,setError]=useState('');

  const load=useCallback(async()=>{
    try{
      const db=await getDatabase();
      const [history,savedCategories,savedGoal]=await Promise.all([
        db.getAllAsync<FocusHistoryPoint>('SELECT started_at, duration_seconds, session_type FROM focus_sessions WHERE datetime(started_at) >= datetime(?, ?) ORDER BY started_at ASC',new Date().toISOString(),'-90 days'),
        getSetting<Record<string,UsageCategory>>('usage.categories',{}),
        getSetting<number>('focus.dailyGoalMinutes',120),
      ]);
      setSessions(history); setCategories(savedCategories&&typeof savedCategories==='object'?savedCategories:{}); setGoalMinutes(Number.isInteger(savedGoal)&&savedGoal>=15&&savedGoal<=480?savedGoal:120);
      if(usage?.getDailyUsageStats&&await usage.hasUsageAccess()){ setUsageRows(await usage.getDailyUsageStats(60)); setUsageAvailable(true); } else { setUsageRows([]); setUsageAvailable(false); }
      setError('');
    }catch{setError('Insights could not be calculated right now. Your saved data has not been changed.');}
  },[]);
  useFocusEffect(useCallback(()=>{void load();},[load]));

  const currentRange=useMemo(()=>dateRangeEndingToday(rangeDays),[rangeDays]);
  const previous=useMemo(()=>previousRange(currentRange),[currentRange]);
  const current=useMemo(()=>aggregatePeriod(sessions,usageRows,categories,currentRange),[sessions,usageRows,categories,currentRange]);
  const before=useMemo(()=>aggregatePeriod(sessions,usageRows,categories,previous),[sessions,usageRows,categories,previous]);
  const streak=useMemo(()=>focusGoalStats(sessions,goalMinutes),[sessions,goalMinutes]);
  const focusChange=percentChange(current.focusSeconds,before.focusSeconds); const usageChange=percentChange(current.usageMs,before.usageMs);

  const Metric=({icon,label,value,detail}:{icon:keyof typeof Ionicons.glyphMap;label:string;value:string;detail:string})=><View style={[styles.metric,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={[styles.icon,{backgroundColor:theme.surfaceAlt}]}><Ionicons name={icon} size={18} color={theme.primary}/></View><Text style={[styles.metricLabel,{color:theme.muted}]}>{label}</Text><Text style={[styles.metricValue,{color:theme.text}]}>{value}</Text><Text style={[styles.hint,{color:theme.muted}]}>{detail}</Text></View>;

  return <SafeAreaView style={[styles.safe,{backgroundColor:theme.background}]}>
    <View style={styles.header}><Pressable onPress={()=>router.back()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="arrow-back" size={20} color={theme.text}/></Pressable><Text style={[styles.headerTitle,{color:theme.text}]}>Insights</Text><Pressable onPress={()=>void load()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="refresh" size={18} color={theme.text}/></Pressable></View>
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={[styles.eyebrow,{color:theme.muted}]}>PATTERNS, NOT PRESSURE</Text><Text style={[styles.title,{color:theme.text}]}>See what your time is doing.</Text><Text style={[styles.subtitle,{color:theme.muted}]}>Focus comes from your recorded sessions. Phone usage comes from Android Usage Access when available.</Text>
      <View style={styles.periods}>{([7,30] as const).map(days=><Pressable key={days} onPress={()=>setRangeDays(days)} style={[styles.period,{backgroundColor:rangeDays===days?theme.primary:theme.surface,borderColor:rangeDays===days?theme.primary:theme.border}]}><Text style={{color:rangeDays===days?theme.onPrimary:theme.text,fontSize:12,fontWeight:'800'}}>{days===7?'Weekly':'Monthly'}</Text></Pressable>)}</View>
      <View style={styles.grid}><Metric icon="timer-outline" label="FOCUS" value={formatMinutes(current.focusSeconds)} detail={`${changeLabel(focusChange)} vs previous period`}/><Metric icon="phone-portrait-outline" label="PHONE USAGE" value={usageAvailable?formatMs(current.usageMs):'Unavailable'} detail={usageAvailable?`${changeLabel(usageChange)} vs previous period`:'Allow Usage Access for this comparison'}/></View>
      <View style={[styles.card,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={styles.cardHead}><View style={[styles.icon,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="flame-outline" size={19} color={theme.primary}/></View><View style={{flex:1}}><Text style={[styles.cardTitle,{color:theme.text}]}>Focus consistency</Text><Text style={[styles.hint,{color:theme.muted}]}>A day counts when you reach your {goalMinutes}-minute focus goal.</Text></View></View><View style={styles.streakRow}><View style={styles.streakItem}><Text style={[styles.streakValue,{color:theme.text}]}>{streak.currentStreak}</Text><Text style={[styles.hint,{color:theme.muted}]}>current streak</Text></View><View style={styles.streakItem}><Text style={[styles.streakValue,{color:theme.text}]}>{streak.bestStreak}</Text><Text style={[styles.hint,{color:theme.muted}]}>best streak</Text></View><View style={styles.streakItem}><Text style={[styles.streakValue,{color:theme.text}]}>{rangeDays===7?streak.goalDaysThisWeek:streak.goalDaysThisMonth}</Text><Text style={[styles.hint,{color:theme.muted}]}>goal days</Text></View></View>{current.bestFocusDay&&<Text style={[styles.hint,{color:theme.muted}]}>Most focused day in this period · {current.bestFocusDay.date} · {formatMinutes(current.bestFocusDay.seconds)}</Text>}</View>
      {usageAvailable&&<><Text style={[styles.section,{color:theme.text}]}>Usage balance</Text><View style={styles.grid3}><Metric icon="checkmark-circle-outline" label="PRODUCTIVE" value={formatMs(current.productiveMs)} detail="your local categories"/><Metric icon="alert-circle-outline" label="DISTRACTING" value={formatMs(current.distractingMs)} detail="your local categories"/><Metric icon="ellipse-outline" label="NEUTRAL" value={formatMs(current.neutralMs)} detail="your local categories"/></View><Text style={[styles.section,{color:theme.text}]}>Top distracting apps</Text>{current.topDistractingApps.length?current.topDistractingApps.map(app=><View key={app.packageName} style={[styles.appRow,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={[styles.icon,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="apps-outline" size={17} color={theme.primary}/></View><Text numberOfLines={1} style={[styles.appName,{color:theme.text}]}>{app.appName}</Text><Text style={[styles.appTime,{color:theme.muted}]}>{formatMs(app.foregroundTimeMs)}</Text></View>):<View style={[styles.card,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Text style={[styles.hint,{color:theme.muted}]}>No distracting-app time is recorded for this period. Categories can be changed in Screen-time insights.</Text></View>}</>}
      {!!error&&<Text style={{color:theme.danger,fontSize:12,lineHeight:18}}>{error}</Text>}
    </ScrollView>
  </SafeAreaView>;
}
const styles=StyleSheet.create({safe:{flex:1},header:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingHorizontal:20,paddingVertical:10},back:{width:44,height:44,borderRadius:16,alignItems:'center',justifyContent:'center'},headerTitle:{fontSize:16,fontWeight:'800'},content:{padding:20,paddingBottom:40,gap:13},eyebrow:{fontSize:10,fontWeight:'800',letterSpacing:1.6},title:{fontSize:28,fontWeight:'900',letterSpacing:-1},subtitle:{fontSize:12,lineHeight:18,marginTop:-7},periods:{flexDirection:'row',gap:8},period:{flex:1,minHeight:44,borderWidth:1,borderRadius:13,alignItems:'center',justifyContent:'center'},grid:{flexDirection:'row',gap:8},grid3:{gap:8},metric:{flex:1,minWidth:0,borderWidth:1,borderRadius:20,padding:14,gap:5},icon:{width:38,height:38,borderRadius:13,alignItems:'center',justifyContent:'center'},metricLabel:{fontSize:9,fontWeight:'800',letterSpacing:1},metricValue:{fontSize:23,fontWeight:'900',letterSpacing:-.7},hint:{fontSize:10,lineHeight:15},card:{borderWidth:1,borderRadius:22,padding:15,gap:12},cardHead:{flexDirection:'row',gap:10,alignItems:'center'},cardTitle:{fontSize:14,fontWeight:'800'},streakRow:{flexDirection:'row',gap:8},streakItem:{flex:1},streakValue:{fontSize:25,fontWeight:'900'},section:{fontSize:18,fontWeight:'800',marginTop:4},appRow:{borderWidth:1,borderRadius:17,padding:12,flexDirection:'row',alignItems:'center',gap:10},appName:{flex:1,fontSize:12,fontWeight:'800'},appTime:{fontSize:11,fontWeight:'700'}});
