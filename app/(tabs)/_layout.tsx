import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';

export default function TabsLayout() {
  const theme = useAppTheme();
  return (
    <Tabs screenOptions={{
      headerShown: false,
      animation: 'fade',
      tabBarHideOnKeyboard: true,
      tabBarActiveTintColor: theme.primary,
      tabBarInactiveTintColor: theme.muted,
      tabBarStyle: {
        position: 'absolute',
        left: 18,
        right: 18,
        bottom: 18,
        height: 70,
        paddingTop: 8,
        paddingBottom: 8,
        paddingHorizontal: 4,
        borderRadius: 24,
        backgroundColor: theme.surface,
        borderTopWidth: 0,
        borderWidth: 1,
        borderColor: theme.border,
        elevation: 14,
        shadowColor: '#000000',
        shadowOpacity: 0.12,
        shadowRadius: 12,
        shadowOffset: { width: 0, height: 3 },
      },
      tabBarLabelStyle: { fontSize: 10, fontWeight: '700' },
      tabBarItemStyle: { borderRadius: 18, marginVertical: 3 },
    }}>
      <Tabs.Screen name="index" options={{ title: 'Home', tabBarIcon: ({ color, size }) => <Ionicons name="home-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="tasks" options={{ title: 'Tasks', tabBarIcon: ({ color, size }) => <Ionicons name="checkbox-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="focus-tab" options={{ title: 'Focus', tabBarIcon: ({ color, size }) => <Ionicons name="timer-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="notes" options={{ title: 'Notes', tabBarIcon: ({ color, size }) => <Ionicons name="document-text-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarIcon: ({ color, size }) => <Ionicons name="options-outline" color={color} size={size} /> }} />
    </Tabs>
  );
}