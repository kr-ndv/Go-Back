import { useEffect, useState } from 'react';
import { Linking } from 'react-native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import { ThemeDialogHost } from '@/components/ThemeDialogHost';
import { useAppTheme, useThemeStore } from '@/state/theme';
import { getDatabase } from '@/lib/database';
import { runForegroundMaintenance } from '@/lib/maintenance';
import { notificationUrl } from '@/lib/notificationLinks';
import { rescheduleAdvancedFocusNotifications } from '@/lib/scheduleNotifications';

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
      // Maintenance is deliberately non-blocking: the local app remains usable offline.
      void runForegroundMaintenance().catch(() => undefined);
      void rescheduleAdvancedFocusNotifications().catch(() => undefined);
    })();
    return () => { active = false; };
  }, [hydrateTheme]);

  useEffect(() => {
    if (isReady) void SplashScreen.hideAsync().catch(() => undefined);
  }, [isReady]);

  useEffect(() => {
    const subscription = Notifications.addNotificationResponseReceivedListener(response => {
      const url = notificationUrl(response.notification.request.content.data);
      if (url) void Linking.openURL(url).catch(() => undefined);
    });
    return () => subscription.remove();
  }, []);

  if (!isReady) return null;

  return (
    <>
      <StatusBar style={theme.mode === 'light' ? 'dark' : 'light'} />
      <Stack screenOptions={{ headerShown: false, animation: 'none' }}>
        <Stack.Screen name="(tabs)" options={{ animation: 'none' }} />
        <Stack.Screen name="focus" options={{ presentation: 'modal', animation: 'none' }} />
        <Stack.Screen name="note-editor" options={{ presentation: 'modal', animation: 'none' }} />
        <Stack.Screen name="sessions" options={{ animation: 'none' }} />
        <Stack.Screen name="schedule" options={{ animation: 'none' }} />
        <Stack.Screen name="schedule-advanced" options={{ animation: 'none' }} />
        <Stack.Screen name="insights" options={{ animation: 'none' }} />
        <Stack.Screen name="usage-limits" options={{ animation: 'none' }} />
        <Stack.Screen name="focus-presets" options={{ animation: 'none' }} />
        <Stack.Screen name="alarm-sound" options={{ animation: 'none' }} />
      </Stack>
      <ThemeDialogHost />
    </>
  );
}
