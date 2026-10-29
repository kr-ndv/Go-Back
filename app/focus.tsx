import { showThemedDialog } from '@/state/dialog';
import { useEffect, useRef, useState } from 'react';
import { AppState, Platform, ScrollView, StyleSheet, Text, TextInput, View, useWindowDimensions } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import * as Notifications from 'expo-notifications';
import { SafeAreaView } from 'react-native-safe-area-context';
import { getDatabase, getSetting, setSetting } from '@/lib/database';
import { createLocalId } from '@/lib/id';
import { useAppTheme } from '@/state/theme';
import { formatFocusTime, nextPomodoroPhase, stopwatchElapsedSeconds, type FocusMode, type FocusSessionType } from '@/lib/focusLogic';
import { FOCUS_STATUS_CHANNEL_ID, ensureFocusAlertChannel, ensureFocusStatusChannel, getSelectedFocusAlarm } from '@/lib/focusNotifications';
import { normalizeRecoverableFocusSession, type RecoverableFocusSession } from '@/lib/focusRecovery';
import { setNativeSessionBlockedPackages, syncNativeFocusState } from '@/lib/nativeBridge';

const FOCUS_PRESETS = [15, 25, 45, 60];
type SessionType = FocusSessionType;
type PendingPreset = { id:string; name:string; mode:FocusMode; durationMinutes:number; blockedPackages:string[] };
type ActiveFocusSession = RecoverableFocusSession;

const validMode = (value: unknown): value is FocusMode => value === 'timer' || value === 'pomodoro' || value === 'stopwatch';
const validSessionType = (value: unknown): value is SessionType => value === 'focus' || value === 'short-break' || value === 'long-break';
const cleanPackages = (value: unknown) => Array.isArray(value) ? [...new Set(value.filter(item => typeof item === 'string' && /^[A-Za-z0-9_.]+$/.test(item) && item.includes('.')))].slice(0,100) : [];

export default function FocusScreen() {
  const theme = useAppTheme();
  const { width, height } = useWindowDimensions();
  const ringSize = Math.max(210, Math.min(270, width - 48, height * 0.36));
  const compactLayout = height < 720;
  const [minutes, setMinutes] = useState(25);
  const [sessionType, setSessionType] = useState<SessionType>('focus');
  const [focusMode, setFocusMode] = useState<FocusMode>('timer');
  const [customMinutes, setCustomMinutes] = useState('');
  const [remaining, setRemaining] = useState(25 * 60);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [tag, setTag] = useState('');
  const [sessionNotes, setSessionNotes] = useState('');
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [pomodoroRounds, setPomodoroRounds] = useState(0);
  const [running, setRunning] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [presetName, setPresetName] = useState('');
  const lastNotificationUpdateRef = useRef(0);
  const startedAt = useRef<string | null>(null);
  const sessionIdRef = useRef<string | null>(null);
  const remainingRef = useRef(25 * 60);
  const minutesRef = useRef(25);
  const sessionTypeRef = useRef<SessionType>('focus');
  const modeRef = useRef<FocusMode>('timer');
  const elapsedSecondsRef = useRef(0);
  const stopwatchRunStartedAtRef = useRef<number | null>(null);
  const tagRef = useRef('');
  const notesRef = useRef('');
  const pomodoroRoundsRef = useRef(0);
  const endAtRef = useRef<number | null>(null);
  const presetIdRef = useRef('');
  const presetNameRef = useRef('');
  const blockedPackagesRef = useRef<string[]>([]);
  const notificationIdRef = useRef<string | null>(null);
  const ongoingNotificationIdRef = useRef<string | null>(null);
  const finishingRef = useRef(false);

  useEffect(() => { remainingRef.current = remaining; }, [remaining]);
  useEffect(() => { minutesRef.current = minutes; }, [minutes]);
  useEffect(() => { sessionTypeRef.current = sessionType; }, [sessionType]);
  useEffect(() => { modeRef.current = focusMode; }, [focusMode]);
  useEffect(() => { tagRef.current = tag; }, [tag]);
  useEffect(() => { notesRef.current = sessionNotes; }, [sessionNotes]);
  useEffect(() => { pomodoroRoundsRef.current = pomodoroRounds; }, [pomodoroRounds]);

  const nativeTitle = () => presetNameRef.current || (modeRef.current === 'stopwatch' ? 'Stopwatch' : sessionTypeRef.current === 'focus' ? 'Focus session' : 'Break');
  const syncNativeState = (active: boolean) => void syncNativeFocusState({
    active,
    mode: modeRef.current,
    endAt: endAtRef.current,
    elapsedSeconds: elapsedSecondsRef.current,
    stopwatchRunStartedAt: stopwatchRunStartedAtRef.current,
    title: nativeTitle(),
  });

  useEffect(() => { syncNativeState(running); }, [running, focusMode]);

  const persistActiveSession = () => {
    if (!startedAt.current) return;
    void setSetting('focus.active', {
      sessionId: sessionIdRef.current ?? createLocalId('focus'),
      minutes: minutesRef.current,
      remaining: remainingRef.current,
      endAt: endAtRef.current,
      startedAt: startedAt.current,
      sessionType: sessionTypeRef.current,
      mode: modeRef.current,
      elapsedSeconds: elapsedSecondsRef.current,
      stopwatchRunStartedAt: stopwatchRunStartedAtRef.current,
      pomodoroRounds: pomodoroRoundsRef.current,
      tag: tagRef.current,
      notes: notesRef.current,
      presetId: presetIdRef.current,
      presetName: presetNameRef.current,
      blockedPackages: blockedPackagesRef.current,
    } satisfies ActiveFocusSession);
  };

  const cancelNotification = async () => {
    const id = notificationIdRef.current; notificationIdRef.current = null;
    if (id) await Notifications.cancelScheduledNotificationAsync(id).catch(() => undefined);
    const ongoingId = ongoingNotificationIdRef.current; ongoingNotificationIdRef.current = null;
    if (ongoingId) await Notifications.dismissNotificationAsync(ongoingId).catch(() => undefined);
    try {
      const scheduled = await Notifications.getAllScheduledNotificationsAsync();
      const owned = scheduled.filter(item => item.content.data?.goBackKind === 'focus-complete' || item.content.data?.goBackKind === 'focus-status');
      await Promise.all(owned.map(item => Notifications.cancelScheduledNotificationAsync(item.identifier).catch(() => undefined)));
    } catch { /* Notification state can be unavailable. */ }
  };

  const scheduleOngoingNotification = async () => {
    try {
      await ensureFocusStatusChannel();
      let permission = await Notifications.getPermissionsAsync();
      if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
      if (!permission.granted) return;
      const modeLabel = modeRef.current === 'stopwatch' ? 'Stopwatch' : sessionTypeRef.current === 'focus' ? 'Focus session' : 'Break';
      const timerText = modeRef.current === 'stopwatch'
        ? formatFocusTime(stopwatchElapsedSeconds(elapsedSecondsRef.current, stopwatchRunStartedAtRef.current), true) + ' elapsed'
        : formatFocusTime(remainingRef.current, true) + ' remaining';
      ongoingNotificationIdRef.current = await Notifications.scheduleNotificationAsync({
        content: { title: 'Focus timer running', body: `${timerText} · ${modeLabel.toLowerCase()}`, sticky: true, autoDismiss: false, sound: false, data: { goBackKind: 'focus-status' } },
        trigger: Platform.OS === 'android' ? ({ channelId: FOCUS_STATUS_CHANNEL_ID } as any) : null,
      });
      lastNotificationUpdateRef.current = Date.now();
    } catch { /* Timer state remains usable. */ }
  };

  const refreshOngoingNotification = async () => {
    if (!running || Date.now() - lastNotificationUpdateRef.current < 15000) return;
    const previousId = ongoingNotificationIdRef.current; ongoingNotificationIdRef.current = null;
    if (previousId) await Notifications.dismissNotificationAsync(previousId).catch(() => undefined);
    await scheduleOngoingNotification();
  };

  const clearPreset = async () => {
    presetIdRef.current = ''; presetNameRef.current = ''; blockedPackagesRef.current = []; setPresetName('');
    await setNativeSessionBlockedPackages([]);
  };

  const resetMetadata = () => {
    tagRef.current=''; notesRef.current=''; setTag(''); setSessionNotes(''); setDetailsOpen(false);
  };

  const finish = async (completed: boolean) => {
    if (finishingRef.current) return;
    finishingRef.current = true;
    const finishedMode = modeRef.current;
    const finishedType = sessionTypeRef.current;
    const sessionStart = startedAt.current ?? new Date().toISOString();
    const sessionId = sessionIdRef.current ?? createLocalId('focus');
    let elapsed = finishedMode === 'stopwatch'
      ? stopwatchElapsedSeconds(elapsedSecondsRef.current, stopwatchRunStartedAtRef.current)
      : completed ? minutesRef.current * 60 : Math.max(0, minutesRef.current * 60 - remainingRef.current);
    elapsed = Math.max(0, Math.floor(elapsed));
    if (finishedMode === 'stopwatch') { elapsedSecondsRef.current = elapsed; setElapsedSeconds(elapsed); }
    const finishedTag = tagRef.current.trim().slice(0,40); const finishedNotes=notesRef.current.trim().slice(0,300);
    startedAt.current=null; sessionIdRef.current=null; endAtRef.current=null; stopwatchRunStartedAtRef.current=null; setRunning(false); syncNativeState(false);
    await cancelNotification(); await setNativeSessionBlockedPackages([]);
    try {
      const db=await getDatabase();
      if(elapsed>0) await db.runAsync('INSERT OR IGNORE INTO focus_sessions (id,started_at,duration_seconds,completed,session_type,mode,tag,notes) VALUES (?,?,?,?,?,?,?,?)',sessionId,sessionStart,elapsed,completed?1:0,finishedType,finishedMode,finishedTag,finishedNotes);
      await setSetting('focus.active',null); resetMetadata(); await clearPreset();
      if(completed&&finishedMode==='pomodoro'){
        const next=nextPomodoroPhase(finishedType,pomodoroRoundsRef.current); pomodoroRoundsRef.current=next.completedFocusRounds; setPomodoroRounds(next.completedFocusRounds); sessionTypeRef.current=next.sessionType; setSessionType(next.sessionType); minutesRef.current=next.minutes; setMinutes(next.minutes); remainingRef.current=next.minutes*60; setRemaining(next.minutes*60); await setSetting('focus.pomodoroRounds',next.completedFocusRounds); await setSetting('focus.pomodoroNextType',next.sessionType); const nextLabel=next.sessionType==='long-break'?'a long break':next.sessionType==='short-break'?'a short break':'your next focus round'; showThemedDialog('Pomodoro phase complete','Your session is saved. Next up: '+nextLabel+'. Start it when you are ready.');
      }else if(completed) showThemedDialog(finishedType==='focus'?'Focus session complete':'Break complete',finishedType==='focus'?'You showed up for yourself. Take a short break.':'Your break is complete. Return when you feel ready.');
    }catch{showThemedDialog('Could not save session','Your session ended, but its history could not be saved. Please check storage space and try again.');}
    finally{finishingRef.current=false;}
  };

  useEffect(() => {
    let active=true;
    void(async()=>{
      try{
        const[rawSession,savedRounds,savedMode,savedPhase,pending]=await Promise.all([
          getSetting<ActiveFocusSession|null>('focus.active',null), getSetting<number>('focus.pomodoroRounds',0), getSetting<FocusMode>('focus.mode','timer'), getSetting<SessionType>('focus.pomodoroNextType','focus'), getSetting<PendingPreset|null>('focus.pendingPreset',null),
        ]);
        if(!active)return;
        const restored=normalizeRecoverableFocusSession(rawSession);
        const rounds=Number.isInteger(savedRounds)&&savedRounds>=0?savedRounds:0; pomodoroRoundsRef.current=rounds; setPomodoroRounds(rounds);
        if(restored){
          sessionIdRef.current=restored.sessionId; modeRef.current=restored.mode; sessionTypeRef.current=restored.sessionType; minutesRef.current=restored.minutes; remainingRef.current=restored.remaining; endAtRef.current=restored.endAt; elapsedSecondsRef.current=restored.elapsedSeconds; stopwatchRunStartedAtRef.current=restored.stopwatchRunStartedAt; startedAt.current=restored.startedAt; tagRef.current=restored.tag; notesRef.current=restored.notes; presetIdRef.current=restored.presetId; presetNameRef.current=restored.presetName; blockedPackagesRef.current=restored.blockedPackages;
          setFocusMode(restored.mode); setSessionType(restored.sessionType); setMinutes(restored.minutes); setRemaining(restored.remaining); setElapsedSeconds(stopwatchElapsedSeconds(restored.elapsedSeconds,restored.stopwatchRunStartedAt)); setTag(restored.tag); setSessionNotes(restored.notes); setPresetName(restored.presetName); await setNativeSessionBlockedPackages(restored.blockedPackages);
          if(restored.expired){ setRunning(false); await finish(true); }
          else setRunning(restored.running);
        } else if(pending && validMode(pending.mode) && typeof pending.name==='string'){
          const blocked=cleanPackages(pending.blockedPackages); const duration=Number.isInteger(pending.durationMinutes)&&pending.durationMinutes>=1&&pending.durationMinutes<=180?pending.durationMinutes:25;
          modeRef.current=pending.mode; setFocusMode(pending.mode); sessionTypeRef.current='focus'; setSessionType('focus'); minutesRef.current=pending.mode==='stopwatch'?25:duration; setMinutes(minutesRef.current); remainingRef.current=minutesRef.current*60; setRemaining(remainingRef.current); presetIdRef.current=String(pending.id??'').slice(0,200); presetNameRef.current=pending.name.slice(0,60); setPresetName(presetNameRef.current); blockedPackagesRef.current=blocked; await setNativeSessionBlockedPackages(blocked); await setSetting('focus.mode',pending.mode); await setSetting('focus.pendingPreset',null);
        } else {
          const restoredMode=validMode(savedMode)?savedMode:'timer'; modeRef.current=restoredMode; setFocusMode(restoredMode);
          if(restoredMode==='pomodoro'&&validSessionType(savedPhase)){sessionTypeRef.current=savedPhase;setSessionType(savedPhase);const duration=savedPhase==='long-break'?15:savedPhase==='short-break'?5:25;minutesRef.current=duration;setMinutes(duration);remainingRef.current=duration*60;setRemaining(duration*60);}
        }
      }catch{showThemedDialog('Could not restore timer','A previous focus session could not be restored.');}
      finally{if(active)setRestoring(false);}
    })();
    return()=>{active=false;};
  },[]);

  const tick=()=>{
    if(modeRef.current==='stopwatch'){if(stopwatchRunStartedAtRef.current===null)return;setElapsedSeconds(stopwatchElapsedSeconds(elapsedSecondsRef.current,stopwatchRunStartedAtRef.current));return;}
    if(endAtRef.current===null)return;const secondsLeft=Math.max(0,Math.ceil((endAtRef.current-Date.now())/1000));remainingRef.current=secondsLeft;setRemaining(secondsLeft);if(secondsLeft===0)void finish(true);
  };
  useEffect(()=>{if(!running)return;const interval=setInterval(()=>{tick();if(Date.now()-lastNotificationUpdateRef.current>=15000)void refreshOngoingNotification();},500);const sub=AppState.addEventListener('change',state=>{if(state==='active')tick();});return()=>{clearInterval(interval);sub.remove();};},[running,focusMode]);

  const resetPreparedSession=()=>{sessionIdRef.current=null;startedAt.current=null;endAtRef.current=null;stopwatchRunStartedAtRef.current=null;void cancelNotification();void setSetting('focus.active',null);void clearPreset();};
  const applyDuration=(value:number)=>{minutesRef.current=value;setMinutes(value);remainingRef.current=value*60;setRemaining(value*60);resetPreparedSession();};
  const choose=(value:number)=>{if(startedAt.current){showThemedDialog('Replace current session?','The paused session has not been saved yet. Replace it and discard its progress?',[{text:'Keep session',style:'cancel'},{text:'Replace session',style:'destructive',onPress:()=>applyDuration(value)}]);return;}applyDuration(value);};
  const changeMode=(value:FocusMode)=>{if(running||restoring||value===modeRef.current)return;if(startedAt.current){showThemedDialog('Finish the current session first','Save or end the paused session before changing timer modes.');return;}modeRef.current=value;setFocusMode(value);void setSetting('focus.mode',value);elapsedSecondsRef.current=0;setElapsedSeconds(0);resetPreparedSession();sessionTypeRef.current='focus';setSessionType('focus');const duration=value==='pomodoro'?25:25;minutesRef.current=duration;setMinutes(duration);remainingRef.current=duration*60;setRemaining(duration*60);if(value==='pomodoro')void setSetting('focus.pomodoroNextType','focus');};
  const changeSessionType=(value:SessionType)=>{if(running||restoring)return;if(startedAt.current){showThemedDialog('Finish the current session first','Save or end the paused session before switching between focus and break modes.');return;}sessionTypeRef.current=value;setSessionType(value);choose(value==='focus'?25:value==='short-break'?5:15);};
  const applyCustomMinutes=()=>{const value=Number(customMinutes.trim());if(!Number.isInteger(value)||value<1||value>180){showThemedDialog('Choose a valid duration','Enter a whole number from 1 to 180 minutes.');return;}if(startedAt.current){showThemedDialog('Finish the current session first','Save or end the paused session before changing its duration.');return;}sessionTypeRef.current='focus';setSessionType('focus');applyDuration(value);setCustomMinutes('');};

  const start=async()=>{
    if(running||restoring||finishingRef.current)return;
    if(!sessionIdRef.current)sessionIdRef.current=createLocalId('focus');
    if(!startedAt.current)startedAt.current=new Date().toISOString();
    if(modeRef.current==='stopwatch'){
      stopwatchRunStartedAtRef.current=Date.now();setRunning(true);persistActiveSession();syncNativeState(true);void scheduleOngoingNotification();return;
    }
    const seconds=remainingRef.current>0?remainingRef.current:minutesRef.current*60;if(remainingRef.current<=0){remainingRef.current=seconds;setRemaining(seconds);}endAtRef.current=Date.now()+seconds*1000;persistActiveSession();setRunning(true);syncNativeState(true);
    try{
      let permission=await Notifications.getPermissionsAsync();if(!permission.granted)permission=await Notifications.requestPermissionsAsync();if(permission.granted){await cancelNotification();await scheduleOngoingNotification();const alarm=await getSelectedFocusAlarm();const channelId=await ensureFocusAlertChannel(alarm);notificationIdRef.current=await Notifications.scheduleNotificationAsync({content:{title:sessionTypeRef.current==='focus'?'Focus session complete':'Break complete',body:sessionTypeRef.current==='focus'?'Nice work. Take a short break before your next session.':'Your break is over. Return when you feel ready.',sound:alarm,priority:Notifications.AndroidNotificationPriority.MAX,data:{goBackKind:'focus-complete',sessionId:sessionIdRef.current}},trigger:Platform.OS==='android'&&channelId?({type:Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,seconds,channelId} as any):({type:Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,seconds} as any)});}
    }catch{/* Timer continues even if notification scheduling fails. */}
  };

  const pause=()=>{if(modeRef.current==='stopwatch'){const total=stopwatchElapsedSeconds(elapsedSecondsRef.current,stopwatchRunStartedAtRef.current);elapsedSecondsRef.current=total;setElapsedSeconds(total);stopwatchRunStartedAtRef.current=null;setRunning(false);syncNativeState(false);void cancelNotification();persistActiveSession();return;}if(endAtRef.current!==null){const secondsLeft=Math.max(0,Math.ceil((endAtRef.current-Date.now())/1000));remainingRef.current=secondsLeft;setRemaining(secondsLeft);endAtRef.current=null;}setRunning(false);syncNativeState(false);void cancelNotification();if(remainingRef.current===0)void finish(true);else if(startedAt.current)persistActiveSession();};
  const endSession=async(goBack=false)=>{if(modeRef.current!=='stopwatch'&&endAtRef.current!==null){remainingRef.current=Math.max(0,Math.ceil((endAtRef.current-Date.now())/1000));setRemaining(remainingRef.current);endAtRef.current=null;}setRunning(false);syncNativeState(false);await cancelNotification();await finish(modeRef.current==='stopwatch');if(goBack)router.back();else if(modeRef.current!=='stopwatch'){const reset=minutesRef.current*60;remainingRef.current=reset;setRemaining(reset);}};
  const confirmEndSession=(goBack=false)=>{const isStopwatch=modeRef.current==='stopwatch';showThemedDialog(isStopwatch?'Save stopwatch session?':'End this session?',isStopwatch?'Save the elapsed time to your focus history?':'Your progress so far will be saved as an incomplete session.',[{text:'Keep session',style:'cancel'},{text:isStopwatch?'Save session':'End session',style:'destructive',onPress:()=>{void endSession(goBack);}}]);};
  const close=()=>{if(!running&&!startedAt.current){router.back();return;}confirmEndSession(true);};
  const setTagValue=(value:string)=>{const next=value.slice(0,40);tagRef.current=next;setTag(next);if(startedAt.current)persistActiveSession();};
  const setNotesValue=(value:string)=>{const next=value.slice(0,300);notesRef.current=next;setSessionNotes(next);if(startedAt.current)persistActiveSession();};

  const displaySeconds=focusMode==='stopwatch'?elapsedSeconds:remaining;
  const phaseLabel=sessionType==='short-break'?'SHORT BREAK':sessionType==='long-break'?'LONG BREAK':focusMode==='pomodoro'?'POMODORO FOCUS':focusMode==='stopwatch'?'STOPWATCH':'FOCUS SESSION';
  const actionLabel=restoring?'Restoring session…':running?(focusMode==='stopwatch'?'Pause stopwatch':'Pause session'):focusMode==='stopwatch'?(elapsedSeconds>0?'Resume stopwatch':'Start stopwatch'):sessionType==='short-break'?'Start short break':sessionType==='long-break'?'Start long break':focusMode==='pomodoro'?'Start Pomodoro phase':'Start focus session';

  return <SafeAreaView style={[styles.safe,{backgroundColor:theme.background}]}>
    <View style={styles.top}><Pressable accessibilityRole="button" accessibilityLabel="Close focus session" onPress={close} style={[styles.close,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="close" size={22} color={theme.text}/></Pressable><Text style={[styles.topLabel,{color:theme.muted}]}>FOCUS SESSION</Text><Pressable accessibilityRole="button" accessibilityLabel="Open focus session history" onPress={()=>router.push('/sessions')} style={[styles.close,{backgroundColor:theme.surfaceAlt}]}><Ionicons name="list-outline" size={21} color={theme.text}/></Pressable></View>
    <ScrollView contentContainerStyle={[styles.main,{gap:compactLayout?14:20,paddingHorizontal:width<360?16:24}]} keyboardShouldPersistTaps="handled">
      <View style={[styles.ring,{borderColor:theme.primary,width:ringSize,height:ringSize,borderRadius:ringSize/2}]}><Text style={[styles.status,{color:theme.muted}]}>{running?'STAY IN THE MOMENT':phaseLabel}</Text><Text style={[styles.time,{color:theme.text,fontSize:Math.min(58,ringSize*.215)}]}>{formatFocusTime(displaySeconds,focusMode==='stopwatch')}</Text><Text style={[styles.mins,{color:theme.muted}]}>{focusMode==='stopwatch'?'ELAPSED TIME':running?'YOU’RE DOING GREAT':focusMode==='pomodoro'?'ROUND '+(sessionType==='focus'?Math.min(4,pomodoroRounds+1):Math.max(1,pomodoroRounds))+' OF 4':'MINUTES OF INTENTION'}</Text></View>
      {!running&&<><Pressable accessibilityRole="button" accessibilityLabel="Open focus presets" onPress={()=>router.push('/focus-presets')} style={[styles.presetManager,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><View style={{flex:1,gap:3}}><Text style={{color:theme.text,fontSize:12,fontWeight:'800'}}>{presetName?`Preset · ${presetName}`:'Focus presets'}</Text><Text style={{color:theme.muted,fontSize:10}}>{presetName?'This preset is prepared for the next session.':'Reuse a timer mode and session-only blocked apps.'}</Text></View><Ionicons name="chevron-forward" size={17} color={theme.muted}/></Pressable><View style={[styles.modeSwitch,{backgroundColor:theme.surface,borderColor:theme.border}]}>{([{value:'timer',label:'Timer'},{value:'pomodoro',label:'Pomodoro'},{value:'stopwatch',label:'Stopwatch'}] as {value:FocusMode;label:string}[]).map(item=><Pressable key={item.value} accessibilityRole="button" accessibilityState={{selected:focusMode===item.value}} onPress={()=>changeMode(item.value)} style={[styles.modeOption,{backgroundColor:focusMode===item.value?theme.primary:'transparent'}]}><Text style={{color:focusMode===item.value?theme.onPrimary:theme.text,fontWeight:'800',fontSize:11}}>{item.label}</Text></Pressable>)}</View>
        {focusMode==='timer'&&<><View style={styles.modes}>{([{value:'focus',label:'Focus'},{value:'short-break',label:'Short break'},{value:'long-break',label:'Long break'}] as {value:SessionType;label:string}[]).map(item=><Pressable key={item.value} onPress={()=>changeSessionType(item.value)} style={[styles.mode,{backgroundColor:sessionType===item.value?theme.surfaceAlt:theme.surface,borderColor:sessionType===item.value?theme.primary:theme.border}]}><Text style={{color:sessionType===item.value?theme.primary:theme.text,fontWeight:'800',fontSize:11}}>{item.label}</Text></Pressable>)}</View><View style={styles.presets}>{FOCUS_PRESETS.map(p=><Pressable key={p} onPress={()=>choose(p)} style={[styles.preset,{backgroundColor:minutes===p?theme.primary:theme.surface,borderColor:minutes===p?theme.primary:theme.border}]}><Text style={{color:minutes===p?theme.onPrimary:theme.text,fontWeight:'800'}}>{p}m</Text></Pressable>)}</View><View style={styles.customRow}><TextInput value={customMinutes} onChangeText={setCustomMinutes} keyboardType="number-pad" maxLength={3} placeholder="Custom minutes (1–180)" placeholderTextColor={theme.muted} style={[styles.customInput,{color:theme.text,backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}/><Pressable onPress={applyCustomMinutes} style={[styles.customButton,{backgroundColor:theme.surfaceAlt}]}><Text style={{color:theme.text,fontSize:11,fontWeight:'800'}}>Set</Text></Pressable></View></>}
        {focusMode==='pomodoro'&&<View style={[styles.pomodoroInfo,{backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}><Ionicons name="repeat-outline" size={20} color={theme.primary}/><View style={{flex:1,gap:3}}><Text style={{color:theme.text,fontWeight:'800',fontSize:13}}>25 min focus · 5 min break</Text><Text style={{color:theme.muted,fontSize:11,lineHeight:16}}>Every fourth completed focus round prepares a 15-minute long break. You choose when to start each phase.</Text></View></View>}
        {focusMode==='stopwatch'&&<Text style={[styles.runningNote,{color:theme.muted}]}>Measure a study session without a countdown. Pause whenever you need to; elapsed time is saved when you finish.</Text>}</>}
      {running&&<Text style={[styles.runningNote,{color:theme.muted}]}>{focusMode==='stopwatch'?'The stopwatch keeps its elapsed time while the app is in the background.':'The countdown uses an end timestamp, so it catches up when you return. A completion notification is scheduled when permission is granted.'}</Text>}
      <View style={[styles.detailsPanel,{borderColor:theme.border,backgroundColor:theme.surface}]}><Pressable onPress={()=>setDetailsOpen(!detailsOpen)} style={styles.detailsToggle}><View style={{flexDirection:'row',alignItems:'center',gap:8,flex:1}}><Ionicons name="pricetag-outline" size={17} color={theme.primary}/><Text style={{color:theme.text,fontWeight:'800',fontSize:12}}>Session tag and note</Text>{(tag||sessionNotes)?<View style={[styles.savedDot,{backgroundColor:theme.primary}]}/>:null}</View><Ionicons name={detailsOpen?'chevron-up':'chevron-down'} size={18} color={theme.muted}/></Pressable>{detailsOpen&&<><TextInput value={tag} onChangeText={setTagValue} placeholder="Tag (e.g. History, Reading)" placeholderTextColor={theme.muted} maxLength={40} style={[styles.detailInput,{color:theme.text,backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}/><TextInput value={sessionNotes} onChangeText={setNotesValue} placeholder="What are you working on? (optional)" placeholderTextColor={theme.muted} maxLength={300} multiline style={[styles.detailInput,styles.noteInput,{color:theme.text,backgroundColor:theme.surfaceElevated,borderColor:theme.border}]}/></> }</View>
      <Pressable accessibilityRole="button" accessibilityLabel={actionLabel} disabled={restoring} onPress={()=>running?pause():void start()} style={[styles.start,{backgroundColor:theme.primary,opacity:restoring?.65:1}]}><Ionicons name={running?'pause':'play'} size={19} color={theme.onPrimary}/><Text style={[styles.startText,{color:theme.onPrimary}]}>{actionLabel}</Text></Pressable>{startedAt.current&&<Pressable onPress={()=>confirmEndSession(false)} style={{minHeight:48,justifyContent:'center',paddingHorizontal:12}}><Text style={[styles.endText,{color:theme.muted}]}>{focusMode==='stopwatch'?'Finish and save session':'End session and save progress'}</Text></Pressable>}
    </ScrollView>
  </SafeAreaView>;
}

const styles=StyleSheet.create({safe:{flex:1},top:{paddingHorizontal:16,flexDirection:'row',alignItems:'center',justifyContent:'space-between'},close:{width:44,height:44,borderRadius:16,alignItems:'center',justifyContent:'center'},topLabel:{fontSize:10,fontWeight:'800',letterSpacing:2},main:{flexGrow:1,alignItems:'center',justifyContent:'center',paddingVertical:14,gap:20},ring:{borderWidth:5,alignItems:'center',justifyContent:'center',gap:13},status:{fontSize:9,fontWeight:'800',letterSpacing:1.3},time:{fontSize:58,fontWeight:'800',letterSpacing:-2.5,fontVariant:['tabular-nums']},mins:{fontSize:9,fontWeight:'700',letterSpacing:1.2},presetManager:{width:'100%',borderWidth:1,borderRadius:17,padding:12,flexDirection:'row',alignItems:'center',gap:8},modeSwitch:{width:'100%',flexDirection:'row',padding:5,gap:4,borderRadius:18,borderWidth:1},modeOption:{flex:1,minHeight:43,paddingHorizontal:6,borderRadius:14,alignItems:'center',justifyContent:'center'},modes:{flexDirection:'row',gap:6,flexWrap:'wrap',justifyContent:'center'},mode:{minHeight:44,paddingVertical:10,paddingHorizontal:13,borderRadius:14,borderWidth:1,alignItems:'center',justifyContent:'center'},customRow:{width:'100%',flexDirection:'row',gap:8},customInput:{flex:1,borderWidth:1,borderRadius:14,paddingHorizontal:12,paddingVertical:10,fontSize:12,minHeight:46},customButton:{minHeight:46,paddingHorizontal:17,borderRadius:14,justifyContent:'center'},presets:{flexDirection:'row',gap:9,flexWrap:'wrap',justifyContent:'center'},preset:{minHeight:46,minWidth:58,paddingVertical:11,paddingHorizontal:16,borderRadius:16,borderWidth:1,alignItems:'center',justifyContent:'center'},pomodoroInfo:{width:'100%',borderWidth:1,borderRadius:18,padding:14,flexDirection:'row',gap:10,alignItems:'flex-start'},runningNote:{fontSize:12,textAlign:'center',lineHeight:18,maxWidth:360},detailsPanel:{width:'100%',borderWidth:1,borderRadius:18,padding:6},detailsToggle:{minHeight:43,paddingHorizontal:8,flexDirection:'row',alignItems:'center',justifyContent:'space-between'},savedDot:{width:6,height:6,borderRadius:3},detailInput:{margin:5,paddingVertical:10,paddingHorizontal:12,borderWidth:1,borderRadius:13,minHeight:44,fontSize:12},noteInput:{minHeight:64,textAlignVertical:'top'},start:{width:'100%',paddingVertical:16,minHeight:54,borderRadius:18,flexDirection:'row',alignItems:'center',justifyContent:'center',gap:10},startText:{fontSize:15,fontWeight:'800'},endText:{fontSize:13,fontWeight:'700',padding:8}});
