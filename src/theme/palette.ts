export type ThemeMode = 'system' | 'light' | 'dark';
/** Stored values remain stable so existing users keep their selected accent. */
export type AccentColor = 'forest' | 'blue' | 'violet' | 'amber' | 'rose' | 'teal' | 'berry' | 'sakura' | 'graphite' | 'coral' | 'lavender' | 'crimson';

export const paletteNames: Record<AccentColor, string> = {
  forest: 'Forest Lime',
  blue: 'Ocean Blue',
  violet: 'Violet Pulse',
  amber: 'Sunset Amber',
  rose: 'Rose Quartz',
  teal: 'Arctic Teal',
  berry: 'Midnight Berry',
  sakura: 'Sakura Pink',
  graphite: 'Graphite Mint',
  coral: 'Coral Bloom',
  lavender: 'Lavender Sky',
  crimson: 'Crimson Ember',
};

export const accentColors: Record<AccentColor, { light: string; dark: string; secondary: string }> = {
  forest: { light: '#365C32', dark: '#B8F36B', secondary: '#D9F7B5' },
  blue: { light: '#245CA8', dark: '#91C2FF', secondary: '#C4DDFF' },
  violet: { light: '#6542A6', dark: '#D0B8FF', secondary: '#E5D8FF' },
  amber: { light: '#875000', dark: '#FFD18A', secondary: '#FFE3BA' },
  rose: { light: '#A33D62', dark: '#FFB1C8', secondary: '#FFD9E3' },
  teal: { light: '#006A68', dark: '#72DAD5', secondary: '#B9F1EC' },
  berry: { light: '#8A3478', dark: '#F6A9E8', secondary: '#FAD7F1' },
  sakura: { light: '#A33B78', dark: '#FFB0D8', secondary: '#FFD8E9' },
  graphite: { light: '#42665A', dark: '#A8DCC8', secondary: '#D1F0E3' },
  coral: { light: '#B44732', dark: '#FFB4A3', secondary: '#FFDAD2' },
  lavender: { light: '#5758A5', dark: '#C4C3FF', secondary: '#E3E1FF' },
  crimson: { light: '#A42D3A', dark: '#FFB3B8', secondary: '#FFDADB' },
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
  rose: {
    light: { background: '#FFF7F9', surface: '#FFFFFF', surfaceAlt: '#F8E6EC', surfaceElevated: '#FFFCFD', border: '#EBD1DA', container: '#FFD9E3', secondaryContainer: '#F5E0E7' },
    dark: { background: '#211219', surface: '#301923', surfaceAlt: '#442431', surfaceElevated: '#4C2B38', border: '#674151', container: '#603347', secondaryContainer: '#56303E' },
  },
  teal: {
    light: { background: '#F1FAF9', surface: '#FFFFFF', surfaceAlt: '#DDEFEF', surfaceElevated: '#FBFFFF', border: '#C9E3E2', container: '#B9F1EC', secondaryContainer: '#D7EAE8' },
    dark: { background: '#091C1C', surface: '#102727', surfaceAlt: '#193838', surfaceElevated: '#1D4140', border: '#315655', container: '#244D4B', secondaryContainer: '#2A4745' },
  },
  berry: {
    light: { background: '#FCF5FB', surface: '#FFFFFF', surfaceAlt: '#F2E1F0', surfaceElevated: '#FFFBFF', border: '#E8D0E5', container: '#FAD7F1', secondaryContainer: '#F0DDEC' },
    dark: { background: '#20111E', surface: '#2F1A2D', surfaceAlt: '#432640', surfaceElevated: '#4B2C48', border: '#63405F', container: '#5B3154', secondaryContainer: '#51304C' },
  },
  sakura: {
    light: { background: '#FFF7FA', surface: '#FFFFFF', surfaceAlt: '#F8E3ED', surfaceElevated: '#FFFCFE', border: '#EFD1E0', container: '#FFD8E9', secondaryContainer: '#F6DFE8' },
    dark: { background: '#21121A', surface: '#301B26', surfaceAlt: '#452534', surfaceElevated: '#4D2B3B', border: '#694153', container: '#60334A', secondaryContainer: '#563044' },
  },
  graphite: {
    light: { background: '#F3F8F5', surface: '#FFFFFF', surfaceAlt: '#E1EEE7', surfaceElevated: '#FBFEFC', border: '#D0E0D8', container: '#D1F0E3', secondaryContainer: '#DFEBE4' },
    dark: { background: '#101A17', surface: '#192722', surfaceAlt: '#23362E', surfaceElevated: '#293E35', border: '#3C5147', container: '#30483E', secondaryContainer: '#30443B' },
  },
  coral: {
    light: { background: '#FFF7F4', surface: '#FFFFFF', surfaceAlt: '#F8E5DE', surfaceElevated: '#FFFCFA', border: '#EDD2C9', container: '#FFDAD2', secondaryContainer: '#F3E0D9' },
    dark: { background: '#21130F', surface: '#301D17', surfaceAlt: '#442B23', surfaceElevated: '#4C3027', border: '#68463C', container: '#60382E', secondaryContainer: '#56372F' },
  },
  lavender: {
    light: { background: '#F7F6FF', surface: '#FFFFFF', surfaceAlt: '#E9E7FA', surfaceElevated: '#FDFBFF', border: '#DAD8F0', container: '#E3E1FF', secondaryContainer: '#E6E3F4' },
    dark: { background: '#141426', surface: '#1F2036', surfaceAlt: '#2C2D49', surfaceElevated: '#343550', border: '#47496A', container: '#393A5F', secondaryContainer: '#35364F' },
  },
  crimson: {
    light: { background: '#FFF7F6', surface: '#FFFFFF', surfaceAlt: '#F7E3E3', surfaceElevated: '#FFFCFC', border: '#EACFCF', container: '#FFDADB', secondaryContainer: '#F2DEDE' },
    dark: { background: '#211214', surface: '#301B1F', surfaceAlt: '#44262C', surfaceElevated: '#4C2A31', border: '#684049', container: '#60313A', secondaryContainer: '#56303A' },
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
  const darkOnPrimary = ['forest', 'amber', 'teal', 'graphite'].includes(accent);
  const onPrimary = mode === 'light' ? '#FFFFFF' : darkOnPrimary ? '#10201B' : '#24151D';
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
