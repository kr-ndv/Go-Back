import { create } from 'zustand';
import { useColorScheme } from 'react-native';
import { accentColors, darkTheme, lightTheme, type AccentColor, type ThemeMode } from '@/theme/palette';
import { getSetting, setSetting } from '@/lib/database';

type ThemeState = {
  mode: ThemeMode;
  accent: AccentColor;
  ready: boolean;
  hydrate: () => Promise<void>;
  setMode: (mode: ThemeMode) => Promise<void>;
  setAccent: (accent: AccentColor) => Promise<void>;
};

export const useThemeStore = create<ThemeState>((set) => ({
  mode: 'system',
  accent: 'forest',
  ready: false,
  hydrate: async () => {
    const [storedMode, storedAccent] = await Promise.all([getSetting<unknown>('theme.mode', 'system'), getSetting<unknown>('theme.accent', 'forest')]);
    const mode: ThemeMode = storedMode === 'light' || storedMode === 'dark' || storedMode === 'system' ? storedMode : 'system';
    const accent: AccentColor = storedAccent === 'blue' || storedAccent === 'violet' || storedAccent === 'amber' || storedAccent === 'forest' ? storedAccent : 'forest';
    set({ mode, accent, ready: true });
  },
  setMode: async (mode) => {
    set({ mode });
    await setSetting('theme.mode', mode);
  },
  setAccent: async (accent) => {
    set({ accent });
    await setSetting('theme.accent', accent);
  },
}));

export function useAppTheme() {
  const mode = useThemeStore((state) => state.mode);
  const accent = useThemeStore((state) => state.accent);
  const systemScheme = useColorScheme();
  const base = mode === 'light' ? lightTheme : mode === 'dark' ? darkTheme : systemScheme === 'light' ? lightTheme : darkTheme;
  if (accent === 'forest') return base;
  const color = accentColors[accent];
  return { ...base, primary: color, accent: color };
}