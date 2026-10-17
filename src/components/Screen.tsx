import React from 'react';
import { ScrollView, StyleSheet, useWindowDimensions, View, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';

export function Screen({ children, contentStyle }: { children: React.ReactNode; contentStyle?: ViewStyle }) {
  const theme = useAppTheme();
  const { width } = useWindowDimensions();
  const horizontalPadding = width < 360 ? 16 : width >= 720 ? 28 : 20;
  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingHorizontal: horizontalPadding, maxWidth: width >= 720 ? 760 : undefined },
          contentStyle,
        ]}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  content: { width: '100%', alignSelf: 'center', paddingTop: 16, paddingBottom: 40, gap: 20 },
});
