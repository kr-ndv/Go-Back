import { useEffect, useState } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import { ThemeDialogHost } from '@/components/ThemeDialogHost';
import { useAppTheme, useThemeStore } from '@/state/theme';
import { getDatabase } from '@/lib/database';

void SplashScreen.preventAutoHideAsync().catch(() => undefined);
SplashScreen.setOptions({ duration: 350, fade: true });
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
});

export default function RootLayout() {
  const hydrateTheme = useThemeStore((state) => state.hydrate);
  const theme = useAppTheme();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        await getDatabase();
        await hydrateTheme();
      } catch (error) {
        console.error('Go Back initialization failed', error);
      } finally {
        if (active) setIsReady(true);
      }
    })();
    return () => { active = false; };
  }, [hydrateTheme]);

  useEffect(() => {
    if (isReady) void SplashScreen.hideAsync().catch(() => undefined);
  }, [isReady]);

  if (!isReady) return null;

  return (
    <>
      <StatusBar style={theme.mode === 'light' ? 'dark' : 'light'} />
      <Stack screenOptions={{ headerShown: false, animation: 'none' }}>
        <Stack.Screen name="(tabs)" options={{ animation: 'none' }} />
        <Stack.Screen name="focus" options={{ presentation: 'modal', animation: 'none' }} />
        <Stack.Screen name="note-editor" options={{ presentation: 'modal', animation: 'none' }} />
        <Stack.Screen name="sessions" options={{ animation: 'none' }} />
      </Stack>
      <ThemeDialogHost />
    </>
  );
}
