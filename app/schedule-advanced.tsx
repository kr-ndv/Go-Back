import { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { SafeAreaView } from 'react-native-safe-area-context';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { useAppTheme } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { isValidISODate } from '@/lib/date';
import { detectedTimeZone, sanitizeSkipDates, type AdvancedSchedule } from '@/lib/scheduleAdvanced';
import { rescheduleAdvancedFocusNotifications } from '@/lib/scheduleNotifications';
import { showThemedDialog } from '@/state/dialog';

type ScheduleRow = AdvancedSchedule & { created_at: string };
const REMINDERS=[0,5,10,15,30];
const ROUTINES=[{value:'custom',label:'Current days'},{value:'weekday',label:'Weekdays'},{value:'weekend',label:'Weekend'}] as const;

export default function AdvancedScheduleScreen(){
  const theme=useAppTheme();
  const [schedules,setSchedules]=useState<ScheduleRow[]>([]);
  const [skipInputs,setSkipInputs]=useState<Record<string,string>>({});
  const [status,setStatus]=useState('');
  const load=useCallback(async()=>{try{const db=await getDatabase();setSchedules(await db.getAllAsync<ScheduleRow>('SELECT * FROM focus_schedules ORDER BY enabled DESC, start_time ASC'));setStatus('');}catch{setStatus('Advanced schedule settings could not be loaded.');}},[]);
  useFocusEffect(useCallback(()=>{void load();},[load]));

  const update=async(schedule:ScheduleRow,fields:Partial<Pick<ScheduleRow,'reminder_minutes'|'skip_dates_json'|'routine_type'>>)=>{
    try{
      const db=await getDatabase();
      const reminder=fields.reminder_minutes??schedule.reminder_minutes??0; const skips=fields.skip_dates_json??schedule.skip_dates_json??'[]'; const routine=fields.routine_type??schedule.routine_type??'custom';
      await db.runAsync('UPDATE focus_schedules SET reminder_minutes = ?, skip_dates_json = ?, routine_type = ?, timezone = ? WHERE id = ?',reminder,skips,routine,detectedTimeZone(),schedule.id);
      await load(); const result=await rescheduleAdvancedFocusNotifications({askPermission:true});
      setStatus(result.denied?'Schedule saved. Allow notifications to receive reminders.':result.errors?`Schedule saved · ${result.errors} reminder(s) could not be scheduled.`:`${result.scheduled} upcoming reminder(s) scheduled.`);
    }catch{showThemedDialog('Schedule not updated','Go Back could not save the advanced schedule options.');}
  };

  const addSkip=async(schedule:ScheduleRow)=>{
    const input=(skipInputs[schedule.id]??'').trim();
    if(!isValidISODate(input)){showThemedDialog('Check the skip date','Use a real date in YYYY-MM-DD format.');return;}
    const next=[...new Set([...sanitizeSkipDates(schedule.skip_dates_json),input])].sort();
    setSkipInputs(current=>({...current,[schedule.id]:''})); await update(schedule,{skip_dates_json:JSON.stringify(next)});
  };

  const removeSkip=async(schedule:ScheduleRow,date:string)=>update(schedule,{skip_dates_json:JSON.stringify(sanitizeSkipDates(schedule.skip_dates_json).filter(item=>item!==date))});

  return <SafeAreaView style={[styles.safe,{backgroundColor:theme.background}]}>
    <View style={styles.header}><Pressable onPress={()=>router.back()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="arrow-back" size={20} color={theme.text}/></Pressable><Text style={[styles.headerTitle,{color:theme.text}]}>Advanced schedule</Text><Pressable onPress={()=>void load()} style={[styles.back,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="refresh" size={18} color={theme.text}/></Pressable></View>
    <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={[styles.eyebrow,{color:theme.muted}]}>FLEXIBLE ROUTINES</Text><Text style={[styles.title,{color:theme.text}]}>Keep plans realistic.</Text><Text style={[styles.subtitle,{color:theme.muted}]}>Your existing schedules stay intact. Add a weekday/weekend routine, a reminder before start, or dates to skip.</Text>
      {schedules.length===0?<View style={[styles.empty,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Ionicons name="calendar-outline" size={25} color={theme.primary}/><Text style={[styles.hint,{color:theme.muted}]}>Create a focus schedule first, then return here for advanced options.</Text><Pressable onPress={()=>router.replace('/schedule')} style={[styles.primary,{backgroundColor:theme.primary}]}><Text style={{color:theme.onPrimary,fontWeight:'900'}}>Create schedule</Text></Pressable></View>:schedules.map(schedule=>{const skips=sanitizeSkipDates(schedule.skip_dates_json);return <View key={schedule.id} style={[styles.card,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={styles.cardHead}><View style={[styles.icon,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="calendar-outline" size={19} color={theme.primary}/></View><View style={{flex:1,gap:3}}><Text style={[styles.cardTitle,{color:theme.text}]}>{schedule.title}</Text><Text style={[styles.hint,{color:theme.muted}]}>{schedule.start_time} · {schedule.duration_minutes} min · {schedule.enabled===1?'Active':'Paused'}</Text></View></View><Text style={[styles.label,{color:theme.text}]}>Routine</Text><View style={styles.chips}>{ROUTINES.map(item=><Pressable key={item.value} onPress={()=>void update(schedule,{routine_type:item.value})} style={[styles.chip,{backgroundColor:(schedule.routine_type??'custom')===item.value?theme.primary:theme.surface,borderColor:(schedule.routine_type??'custom')===item.value?theme.primary:theme.border}]}><Text style={{color:(schedule.routine_type??'custom')===item.value?theme.onPrimary:theme.text,fontSize:10,fontWeight:'800'}}>{item.label}</Text></Pressable>)}</View><Text style={[styles.label,{color:theme.text}]}>Remind before</Text><View style={styles.chips}>{REMINDERS.map(value=><Pressable key={value} onPress={()=>void update(schedule,{reminder_minutes:value})} style={[styles.chip,{backgroundColor:(schedule.reminder_minutes??0)===value?theme.primary:theme.surface,borderColor:(schedule.reminder_minutes??0)===value?theme.primary:theme.border}]}><Text style={{color:(schedule.reminder_minutes??0)===value?theme.onPrimary:theme.text,fontSize:10,fontWeight:'800'}}>{value===0?'At start':`${value} min`}</Text></Pressable>)}</View><Text style={[styles.label,{color:theme.text}]}>Skip dates</Text><View style={styles.skipEntry}><TextInput value={skipInputs[schedule.id]??''} onChangeText={value=>setSkipInputs(current=>({...current,[schedule.id]:value}))} placeholder="YYYY-MM-DD" placeholderTextColor={theme.muted} style={[styles.input,{color:theme.text,borderColor:theme.border}]}/><Pressable onPress={()=>void addSkip(schedule)} style={[styles.add,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="add" size={18} color={theme.primary}/></Pressable></View>{skips.length?<View style={styles.chips}>{skips.map(date=><View key={date} style={[styles.skipChip,{backgroundColor:theme.surfaceAlt,borderColor:theme.border}]}><Text style={{color:theme.text,fontSize:10,fontWeight:'700'}}>{date}</Text><Pressable onPress={()=>void removeSkip(schedule,date)}><Ionicons name="close-circle" size={16} color={theme.muted}/></Pressable></View>)}</View>:<Text style={[styles.hint,{color:theme.muted}]}>No dates skipped.</Text>}</View>})}
      {!!status&&<Text style={[styles.status,{color:status.includes('could not')?theme.danger:theme.muted}]}>{status}</Text>}
      <Text style={[styles.footnote,{color:theme.muted}]}>Schedules are regenerated as concrete upcoming reminders so skip dates and pre-reminders can be respected. Timezone changes preserve the local clock time on the next reschedule.</Text>
    </ScrollView>
  </SafeAreaView>;
}
const styles=StyleSheet.create({safe:{flex:1},header:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',paddingHorizontal:20,paddingVertical:10},back:{width:44,height:44,borderRadius:16,alignItems:'center',justifyContent:'center'},headerTitle:{fontSize:16,fontWeight:'800'},content:{padding:20,paddingBottom:40,gap:13},eyebrow:{fontSize:10,fontWeight:'800',letterSpacing:1.6},title:{fontSize:28,fontWeight:'900',letterSpacing:-1},subtitle:{fontSize:12,lineHeight:18,marginTop:-7},empty:{borderWidth:1,borderRadius:22,padding:20,alignItems:'center',gap:10},hint:{fontSize:11,lineHeight:17},primary:{minHeight:44,borderRadius:13,paddingHorizontal:16,justifyContent:'center'},card:{borderWidth:1,borderRadius:22,padding:14,gap:10},cardHead:{flexDirection:'row',alignItems:'center',gap:10},icon:{width:40,height:40,borderRadius:13,alignItems:'center',justifyContent:'center'},cardTitle:{fontSize:14,fontWeight:'800'},label:{fontSize:11,fontWeight:'900',marginTop:2},chips:{flexDirection:'row',flexWrap:'wrap',gap:6},chip:{minHeight:36,borderWidth:1,borderRadius:11,paddingHorizontal:10,alignItems:'center',justifyContent:'center'},skipEntry:{flexDirection:'row',gap:7},input:{flex:1,minHeight:42,borderWidth:1,borderRadius:12,paddingHorizontal:10,fontSize:11},add:{width:44,height:44,borderRadius:12,alignItems:'center',justifyContent:'center'},skipChip:{minHeight:34,borderWidth:1,borderRadius:10,paddingHorizontal:9,flexDirection:'row',alignItems:'center',gap:5},status:{fontSize:11,lineHeight:17},footnote:{fontSize:10,lineHeight:16}});
