export const palette = {
  forest: '#10231D',
  forestSoft: '#1A342B',
  forestMuted: '#27463A',
  lime: '#B8F36B',
  limeSoft: '#D9F7B5',
  paper: '#F5F6F0',
  white: '#FFFFFF',
  ink: '#17231D',
  muted: '#7B877E',
  line: '#E4E9E0',
  danger: '#D85D52',
  amber: '#E9B75B',
};

export type ThemeMode = 'system' | 'light' | 'dark';
export type AccentColor = 'forest' | 'blue' | 'violet' | 'amber';
export const accentColors: Record<Exclude<AccentColor, 'forest'>, string> = {
  blue: '#64A9FF',
  violet: '#B49AFF',
  amber: '#F2B75E',
};

export const lightTheme = {
  background: palette.paper,
  surface: palette.white,
  surfaceAlt: '#EDF1E9',
  text: palette.ink,
  muted: palette.muted,
  border: palette.line,
  primary: palette.forest,
  accent: palette.lime,
};

export const darkTheme = {
  background: '#0C1712',
  surface: '#14231B',
  surfaceAlt: '#1C3025',
  text: '#F2F6EF',
  muted: '#A0ADA2',
  border: '#294034',
  primary: palette.lime,
  accent: palette.lime,
};