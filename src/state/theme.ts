import { create } from 'zustand';
import { darkTheme, lightTheme, type ThemeMode } from '@/theme/palette';
import { getSetting, setSetting } from '@/lib/database';

type ThemeState = {
  mode: ThemeMode;
  ready: boolean;
  hydrate: () => Promise<void>;
  setMode: (mode: ThemeMode) => Promise<void>;
};

export const useThemeStore = create<ThemeState>((set) => ({
  mode: 'system',
  ready: false,
  hydrate: async () => {
    const mode = await getSetting<ThemeMode>('theme.mode', 'system');
    set({ mode, ready: true });
  },
  setMode: async (mode) => {
    set({ mode });
    await setSetting('theme.mode', mode);
  },
}));

export function useAppTheme() {
  const mode = useThemeStore((state) => state.mode);
  // System defaults to the dark-friendly forest theme until native appearance integration is initialized.
  return mode === 'light' ? lightTheme : darkTheme;
}