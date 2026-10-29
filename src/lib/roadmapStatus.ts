export const ROADMAP_FEATURES = [
  'github-release-checker',
  'youtube-allowed-channels',
  'session-recovery-permissions',
  'backup-restore',
  'weekly-monthly-insights',
  'focus-presets',
  'daily-screen-time-limits',
  'widget-quick-settings',
  'focus-streaks-goals',
  'advanced-scheduling',
] as const;

export type RoadmapFeature = typeof ROADMAP_FEATURES[number];
