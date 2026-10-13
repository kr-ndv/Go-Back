import { useEffect } from 'react';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useThemeStore } from '@/state/theme';
import { getDatabase } from '@/lib/database';

export default function RootLayout() {
  const hydrateTheme = useThemeStore((state) => state.hydrate);
  useEffect(() => {
    void getDatabase().then(hydrateTheme).catch((error) => console.error('Go Back database initialization failed', error));
  }, [hydrateTheme]);

  return (
    <>
      <StatusBar style="light" />
      <Stack screenOptions={{ headerShown: false, animation: 'fade' }}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="focus" options={{ presentation: 'modal' }} />
        <Stack.Screen name="note-editor" options={{ presentation: 'modal' }} />
      </Stack>
    </>
  );
}