import { create } from 'zustand';
import { useColorScheme } from 'react-native';
import { createTheme, type AccentColor, type ThemeMode } from '@/theme/palette';
import { getSetting, setSetting } from '@/lib/database';

type ThemeState = {
  mode: ThemeMode;
  accent: AccentColor;
  expressive: boolean;
  ready: boolean;
  hydrate: () => Promise<void>;
  setMode: (mode: ThemeMode) => Promise<void>;
  setAccent: (accent: AccentColor) => Promise<void>;
  setExpressive: (expressive: boolean) => Promise<void>;
};

export const useThemeStore = create<ThemeState>((set) => ({
  mode: 'system',
  accent: 'forest',
  expressive: true,
  ready: false,
  hydrate: async () => {
    const [storedMode, storedAccent, storedExpressive] = await Promise.all([
      getSetting<unknown>('theme.mode', 'system'),
      getSetting<unknown>('theme.accent', 'forest'),
      getSetting<unknown>('theme.expressive', true),
    ]);
    const mode: ThemeMode = storedMode === 'light' || storedMode === 'dark' || storedMode === 'system' ? storedMode : 'system';
    const accent: AccentColor = storedAccent === 'blue' || storedAccent === 'violet' || storedAccent === 'amber' || storedAccent === 'forest' ? storedAccent : 'forest';
    const expressive = typeof storedExpressive === 'boolean' ? storedExpressive : true;
    set({ mode, accent, expressive, ready: true });
  },
  setMode: async (mode) => {
    set({ mode });
    await setSetting('theme.mode', mode);
  },
  setAccent: async (accent) => {
    set({ accent });
    await setSetting('theme.accent', accent);
  },
  setExpressive: async (expressive) => {
    set({ expressive });
    await setSetting('theme.expressive', expressive);
  },
}));

export function useAppTheme() {
  const mode = useThemeStore((state) => state.mode);
  const accent = useThemeStore((state) => state.accent);
  const expressive = useThemeStore((state) => state.expressive);
  const systemScheme = useColorScheme();
  const resolvedMode = mode === 'system' ? (systemScheme === 'light' ? 'light' : 'dark') : mode;
  return createTheme(resolvedMode, accent, expressive);
}
