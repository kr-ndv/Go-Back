import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';

export default function TabsLayout() {
  const theme = useAppTheme();
  return (
    <Tabs screenOptions={{
      headerShown: false,
      tabBarActiveTintColor: theme.primary,
      tabBarInactiveTintColor: theme.muted,
      tabBarStyle: { backgroundColor: theme.surface, borderTopColor: theme.border, height: 68, paddingTop: 8, paddingBottom: 8 },
      tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
    }}>
      <Tabs.Screen name="index" options={{ title: 'Today', tabBarIcon: ({ color, size }) => <Ionicons name="grid-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="tasks" options={{ title: 'Tasks', tabBarIcon: ({ color, size }) => <Ionicons name="checkbox-outline" color={color} size={size} /> }} />
      <Tabs.Screen name="focus-tab" options={{ title: 'Focus', tabBarIcon: ({ color, size }) => <Ionicons name="timer-outline" color={color} size={size} }} />
      <Tabs.Screen name="notes" options={{ title: 'Notes', tabBarIcon: ({ color, size }) => <Ionicons name="document-text-outline" color={color} size={size} }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarIcon: ({ color, size }) => <Ionicons name="options-outline" color={color} size={size} }} />
    </Tabs>
  );
}