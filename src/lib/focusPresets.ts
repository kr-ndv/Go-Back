import type { FocusMode } from '@/lib/focusLogic';

export type FocusPreset = {
  id: string;
  name: string;
  mode: FocusMode;
  duration_minutes: number;
  blocked_packages_json: string;
  created_at: string;
};

export function parsePresetPackages(value: string): string[] {
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter(item => typeof item === 'string' && /^[A-Za-z0-9_.]+$/.test(item) && item.includes('.')))].slice(0, 100);
  } catch { return []; }
}

export function sanitizePresetMode(value: unknown): FocusMode {
  return value === 'pomodoro' || value === 'stopwatch' ? value : 'timer';
}

export function sanitizePresetDuration(value: unknown, mode: FocusMode): number {
  if (mode === 'stopwatch') return 25;
  const numeric = Number(value);
  return Number.isInteger(numeric) && numeric >= 1 && numeric <= 180 ? numeric : mode === 'pomodoro' ? 25 : 25;
}
