import type { FocusMode, FocusSessionType } from '@/lib/focusLogic';

export type RecoverableFocusSession = {
  sessionId?: string;
  minutes: number;
  remaining: number;
  endAt: number | null;
  startedAt: string;
  sessionType?: FocusSessionType;
  mode?: FocusMode;
  elapsedSeconds?: number;
  stopwatchRunStartedAt?: number | null;
  pomodoroRounds?: number;
  tag?: string;
  notes?: string;
  presetId?: string;
  presetName?: string;
  blockedPackages?: string[];
};

export type RecoveredFocusSession = {
  sessionId: string;
  minutes: number;
  remaining: number;
  endAt: number | null;
  startedAt: string;
  sessionType: FocusSessionType;
  mode: FocusMode;
  elapsedSeconds: number;
  stopwatchRunStartedAt: number | null;
  pomodoroRounds: number;
  tag: string;
  notes: string;
  presetId: string;
  presetName: string;
  blockedPackages: string[];
  running: boolean;
  expired: boolean;
};

const validMode = (value: unknown): value is FocusMode => value === 'timer' || value === 'pomodoro' || value === 'stopwatch';
const validType = (value: unknown): value is FocusSessionType => value === 'focus' || value === 'short-break' || value === 'long-break';
const cleanPackages = (value: unknown) => Array.isArray(value) ? [...new Set(value.filter(item => typeof item === 'string' && /^[A-Za-z0-9_.]+$/.test(item) && item.includes('.')))].slice(0, 100) : [];

export function normalizeRecoverableFocusSession(value: unknown, now = Date.now()): RecoveredFocusSession | null {
  if (!value || typeof value !== 'object') return null;
  const raw = value as Partial<RecoverableFocusSession>;
  const minutes = typeof raw.minutes === 'number' && Number.isInteger(raw.minutes) ? raw.minutes : null;
  if (minutes === null || minutes < 1 || minutes > 180) return null;
  if (typeof raw.startedAt !== 'string' || !Number.isFinite(new Date(raw.startedAt).getTime())) return null;
  const mode: FocusMode = validMode(raw.mode) ? raw.mode : 'timer';
  const sessionType: FocusSessionType = validType(raw.sessionType) ? raw.sessionType : 'focus';
  const sessionId = typeof raw.sessionId === 'string' && raw.sessionId.length >= 3 && raw.sessionId.length <= 200 ? raw.sessionId : `focus-recovered-${new Date(raw.startedAt).getTime()}`;
  const elapsedSeconds = Number.isFinite(raw.elapsedSeconds) ? Math.max(0, Math.floor(raw.elapsedSeconds ?? 0)) : 0;
  const runStarted = typeof raw.stopwatchRunStartedAt === 'number' && Number.isFinite(raw.stopwatchRunStartedAt) && raw.stopwatchRunStartedAt <= now ? raw.stopwatchRunStartedAt : null;
  const endAt = typeof raw.endAt === 'number' && Number.isFinite(raw.endAt) ? raw.endAt : null;
  const remaining = mode === 'stopwatch'
    ? 0
    : endAt !== null
      ? Math.max(0, Math.ceil((endAt - now) / 1000))
      : Number.isFinite(raw.remaining)
        ? Math.max(0, Math.floor(raw.remaining ?? 0))
        : minutes * 60;
  const expired = mode !== 'stopwatch' && endAt !== null && endAt <= now;
  return {
    sessionId,
    minutes,
    remaining,
    endAt,
    startedAt: raw.startedAt,
    sessionType,
    mode,
    elapsedSeconds,
    stopwatchRunStartedAt: runStarted,
    pomodoroRounds: Number.isInteger(raw.pomodoroRounds) && Number(raw.pomodoroRounds) >= 0 ? Math.min(999, Number(raw.pomodoroRounds)) : 0,
    tag: typeof raw.tag === 'string' ? raw.tag.slice(0, 40) : '',
    notes: typeof raw.notes === 'string' ? raw.notes.slice(0, 300) : '',
    presetId: typeof raw.presetId === 'string' ? raw.presetId.slice(0, 200) : '',
    presetName: typeof raw.presetName === 'string' ? raw.presetName.slice(0, 60) : '',
    blockedPackages: cleanPackages(raw.blockedPackages),
    running: mode === 'stopwatch' ? runStarted !== null : endAt !== null && !expired,
    expired,
  };
}

export function recoveredElapsedSeconds(session: RecoveredFocusSession, now = Date.now()): number {
  if (session.mode === 'stopwatch') {
    const extra = session.stopwatchRunStartedAt === null ? 0 : Math.max(0, Math.floor((now - session.stopwatchRunStartedAt) / 1000));
    return Math.max(0, session.elapsedSeconds + extra);
  }
  if (session.expired) return session.minutes * 60;
  return Math.max(0, session.minutes * 60 - session.remaining);
}
