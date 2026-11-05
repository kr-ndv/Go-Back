import React from 'react';
import { ScrollView, StyleSheet, useWindowDimensions, type ViewStyle } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';
import { FLOATING_TAB_BAR_HEIGHT } from '@/components/FloatingTabBar';

export function Screen({
  children,
  contentStyle,
  hasTabBar = true,
}: {
  children: React.ReactNode;
  contentStyle?: ViewStyle;
  hasTabBar?: boolean;
}) {
  const theme = useAppTheme();
  const insets = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const horizontalPadding = width < 360 ? 16 : width >= 720 ? 28 : 20;
  const bottomClearance = hasTabBar ? FLOATING_TAB_BAR_HEIGHT + insets.bottom + 24 : insets.bottom + 28;

  return (
    <SafeAreaView style={[styles.safe, { backgroundColor: theme.background }]} edges={['top']}>
      <ScrollView
        contentContainerStyle={[
          styles.content,
          {
            paddingHorizontal: horizontalPadding,
            maxWidth: width >= 720 ? 760 : undefined,
            paddingBottom: bottomClearance,
          },
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
  content: { width: '100%', alignSelf: 'center', paddingTop: 16, gap: 20 },
});
