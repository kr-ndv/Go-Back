export type FocusAlarmSound =
  | 'default'
  | 'focus_bell.wav'
  | 'focus_chime.wav'
  | 'focus_digital.wav';

export type FocusAlarmOption = {
  value: FocusAlarmSound;
  label: string;
  description: string;
};

export const FOCUS_ALARM_SETTING_KEY = 'focus.alarmSound';

export const FOCUS_ALARM_OPTIONS: readonly FocusAlarmOption[] = [
  { value: 'default', label: 'System default', description: 'Use your phone’s default notification sound.' },
  { value: 'focus_bell.wav', label: 'Soft bell', description: 'A gentle focus bell.' },
  { value: 'focus_chime.wav', label: 'Warm chime', description: 'A short calm chime.' },
  { value: 'focus_digital.wav', label: 'Digital', description: 'A clear timer-style alert.' },
];

export function normalizeFocusAlarmSound(value: unknown): FocusAlarmSound {
  const migrated = value === 'focus-bell.wav' ? 'focus_bell.wav' : value === 'focus-chime.wav' ? 'focus_chime.wav' : value === 'focus-digital.wav' ? 'focus_digital.wav' : value;
  return FOCUS_ALARM_OPTIONS.some(option => option.value === migrated) ? migrated as FocusAlarmSound : 'default';
}

export function focusAlarmLabel(value: FocusAlarmSound): string {
  return FOCUS_ALARM_OPTIONS.find(option => option.value === value)?.label ?? 'System default';
}

export function focusAlarmChannelId(value: FocusAlarmSound): string {
  const suffix = value === 'default' ? 'default' : value.replace(/\.wav$/i, '').replace(/[^a-z0-9-]/gi, '-').toLowerCase();
  return `focus-alerts-v3-${suffix}`;
}
