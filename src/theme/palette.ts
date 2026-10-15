export type ThemeMode = 'system' | 'light' | 'dark';
/** Stored values remain stable so existing users keep their selected accent. */
export type AccentColor = 'forest' | 'blue' | 'violet' | 'amber';

export const paletteNames: Record<AccentColor, string> = {
  forest: 'Forest Lime',
  blue: 'Ocean Blue',
  violet: 'Violet Pulse',
  amber: 'Sunset Amber',
};

export const accentColors: Record<AccentColor, { light: string; dark: string; secondary: string }> = {
  forest: { light: '#365C32', dark: '#B8F36B', secondary: '#D9F7B5' },
  blue: { light: '#245CA8', dark: '#91C2FF', secondary: '#C4DDFF' },
  violet: { light: '#6542A6', dark: '#D0B8FF', secondary: '#E5D8FF' },
  amber: { light: '#875000', dark: '#FFD18A', secondary: '#FFE3BA' },
};

export const palette = {
  forest: '#10231D',
  forestSoft: '#1A342B',
  forestMuted: '#27463A',
  lime: '#B8F36B',
  limeSoft: '#D9F7B5',
  paper: '#F5F6F0',
  white: '#FFFFFF',
  ink: '#17231D',
  muted: '#66756B',
  line: '#E0E7DD',
  danger: '#BA1A1A',
  amber: '#E9B75B',
};

const lightBase = {
  background: '#F5F6F0',
  surface: '#FFFFFF',
  surfaceAlt: '#E9EFE5',
  surfaceElevated: '#FFFFFF',
  text: '#17231D',
  muted: '#66756B',
  border: '#DCE5D9',
  success: '#276749',
  warning: '#875000',
  danger: '#BA1A1A',
  onPrimary: '#FFFFFF',
};

const darkBase = {
  background: '#0C1712',
  surface: '#14231B',
  surfaceAlt: '#1C3025',
  surfaceElevated: '#20362A',
  text: '#F2F6EF',
  muted: '#A9B8AB',
  border: '#2C4435',
  success: '#A6D9B5',
  warning: '#FFD18A',
  danger: '#FFB4AB',
  onPrimary: '#102016',
};

export function createTheme(mode: Exclude<ThemeMode, 'system'>, accent: AccentColor, expressive = true) {
  const base = mode === 'light' ? lightBase : darkBase;
  const selected = accentColors[accent];
  const primary = mode === 'light' ? selected.light : selected.dark;
  return {
    ...base,
    primary,
    accent: selected.secondary,
    secondary: selected.secondary,
    primaryContainer: mode === 'light' ? selected.secondary : '#263B30',
    onPrimary: accent === 'forest' && mode === 'dark' ? '#102016' : mode === 'light' ? '#FFFFFF' : '#111A13',
    outline: base.border,
    expressive,
  };
}

/** Kept for callers and settings that use the original default theme constants. */
export const lightTheme = createTheme('light', 'forest');
export const darkTheme = createTheme('dark', 'forest');
