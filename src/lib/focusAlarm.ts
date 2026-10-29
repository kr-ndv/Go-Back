export type FocusAlarmSound =
  | 'default'
  | 'focus-bell.wav'
  | 'focus-chime.wav'
  | 'focus-digital.wav';

export type FocusAlarmOption = {
  value: FocusAlarmSound;
  label: string;
  description: string;
};

export const FOCUS_ALARM_SETTING_KEY = 'focus.alarmSound';

export const FOCUS_ALARM_OPTIONS: readonly FocusAlarmOption[] = [
  { value: 'default', label: 'System default', description: 'Use your phone’s default notification sound.' },
  { value: 'focus-bell.wav', label: 'Soft bell', description: 'A gentle focus bell.' },
  { value: 'focus-chime.wav', label: 'Warm chime', description: 'A short calm chime.' },
  { value: 'focus-digital.wav', label: 'Digital', description: 'A clear timer-style alert.' },
];

export function normalizeFocusAlarmSound(value: unknown): FocusAlarmSound {
  return FOCUS_ALARM_OPTIONS.some(option => option.value === value) ? value as FocusAlarmSound : 'default';
}

export function focusAlarmLabel(value: FocusAlarmSound): string {
  return FOCUS_ALARM_OPTIONS.find(option => option.value === value)?.label ?? 'System default';
}

export function focusAlarmChannelId(value: FocusAlarmSound): string {
  const suffix = value === 'default' ? 'default' : value.replace(/\.wav$/i, '').replace(/[^a-z0-9-]/gi, '-').toLowerCase();
  return `focus-alerts-v3-${suffix}`;
}
