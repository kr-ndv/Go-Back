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

type Surfaces = { background: string; surface: string; surfaceAlt: string; surfaceElevated: string; border: string; container: string; secondaryContainer: string };
const surfaces: Record<AccentColor, { light: Surfaces; dark: Surfaces }> = {
  forest: {
    light: { background: '#F5F6F0', surface: '#FFFFFF', surfaceAlt: '#E9EFE5', surfaceElevated: '#FFFFFF', border: '#DCE5D9', container: '#D9F7B5', secondaryContainer: '#E5EEDF' },
    dark: { background: '#0C1712', surface: '#14231B', surfaceAlt: '#1C3025', surfaceElevated: '#20362A', border: '#2C4435', container: '#263B30', secondaryContainer: '#304638' },
  },
  blue: {
    light: { background: '#F3F6FC', surface: '#FFFFFF', surfaceAlt: '#E3EBF8', surfaceElevated: '#FBFCFF', border: '#D2DDF0', container: '#D6E5FF', secondaryContainer: '#E1EAF8' },
    dark: { background: '#0D1524', surface: '#141F32', surfaceAlt: '#1C2B43', surfaceElevated: '#22324A', border: '#334763', container: '#263D5B', secondaryContainer: '#2C3C53' },
  },
  violet: {
    light: { background: '#F7F4FC', surface: '#FFFFFF', surfaceAlt: '#EDE5F8', surfaceElevated: '#FFFCFF', border: '#E0D5F0', container: '#E5D8FF', secondaryContainer: '#EDE5F7' },
    dark: { background: '#171122', surface: '#211830', surfaceAlt: '#30233F', surfaceElevated: '#392949', border: '#4A385F', container: '#45345C', secondaryContainer: '#3C2E4E' },
  },
  amber: {
    light: { background: '#FCF6ED', surface: '#FFFEFA', surfaceAlt: '#F4E7D4', surfaceElevated: '#FFFFFF', border: '#E8D7BE', container: '#FFE3BA', secondaryContainer: '#F4E7D6' },
    dark: { background: '#1C140C', surface: '#291D12', surfaceAlt: '#3B2A1A', surfaceElevated: '#44301D', border: '#5A422B', container: '#52391F', secondaryContainer: '#493421' },
  },
};

const semantic = {
  light: { text: '#17231D', muted: '#66756B', success: '#276749', warning: '#875000', danger: '#BA1A1A' },
  dark: { text: '#F2F6EF', muted: '#A9B8AB', success: '#A6D9B5', warning: '#FFD18A', danger: '#FFB4AB' },
};

export const palette = {
  forest: '#10231D', forestSoft: '#1A342B', forestMuted: '#27463A',
  lime: '#B8F36B', limeSoft: '#D9F7B5', paper: '#F5F6F0',
  white: '#FFFFFF', ink: '#17231D', muted: '#66756B', line: '#E0E7DD',
  danger: '#BA1A1A', amber: '#E9B75B',
};

export function createTheme(mode: Exclude<ThemeMode, 'system'>, accent: AccentColor, expressive = true) {
  const palette = surfaces[accent][mode];
  const selected = accentColors[accent];
  const colors = semantic[mode];
  const primary = mode === 'light' ? selected.light : selected.dark;
  const onPrimary = mode === 'light' ? '#FFFFFF' : (accent === 'forest' || accent === 'amber') ? '#182015' : '#171322';
  return {
    mode,
    ...palette,
    ...colors,
    primary,
    accent: selected.secondary,
    secondary: selected.secondary,
    primaryContainer: palette.container,
    secondaryContainer: palette.secondaryContainer,
    onPrimary,
    onSecondaryContainer: colors.text,
    outline: palette.border,
    expressive,
  };
}

/** Kept for callers and settings that use the original default theme constants. */
export const lightTheme = createTheme('light', 'forest');
export const darkTheme = createTheme('dark', 'forest');
