import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export default function TabsLayout() {
  const theme = useAppTheme();
  const insets = useSafeAreaInsets();
  return (
    <Tabs screenOptions={{
      headerShown: false,
      animation: 'none',
      tabBarHideOnKeyboard: true,
      tabBarActiveTintColor: theme.primary,
      tabBarInactiveTintColor: theme.muted,
      tabBarStyle: {
        height: 64 + insets.bottom,
        paddingTop: 8,
        paddingBottom: Math.max(insets.bottom, 8),
        paddingHorizontal: 6,
        backgroundColor: theme.surface,
        borderTopWidth: 1,
        borderTopColor: theme.border,
        elevation: 0,
      },
      tabBarLabelStyle: { fontSize: 10, fontWeight: '700', marginTop: 2 },
      tabBarItemStyle: { borderRadius: 22, marginVertical: 3 },
    }}>
      <Tabs.Screen name="index" options={{ title: 'Home', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'home' : 'home-outline'} color={color} size={focused ? size + 1 : size} /> }} />
      <Tabs.Screen name="tasks" options={{ title: 'Tasks', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'checkbox' : 'checkbox-outline'} color={color} size={focused ? size + 1 : size} /> }} />
      <Tabs.Screen name="focus-tab" options={{ title: 'Focus', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'timer' : 'timer-outline'} color={color} size={focused ? size + 1 : size} /> }} />
      <Tabs.Screen name="notes" options={{ title: 'Notes', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'document-text' : 'document-text-outline'} color={color} size={focused ? size + 1 : size} /> }} />
      <Tabs.Screen name="settings" options={{ title: 'Settings', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'options' : 'options-outline'} color={color} size={focused ? size + 1 : size} /> }} />
    </Tabs>
  );
}