import React, { useEffect, useRef, useState } from 'react';
import {
  AccessibilityInfo,
  Animated,
  Keyboard,
  LayoutChangeEvent,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAppTheme } from '@/state/theme';
import { motion } from '@/theme/motion';
import { shapes } from '@/theme/shapes';

export const FLOATING_TAB_BAR_HEIGHT = 64;

type TabConfig = {
  name: string;
  label: string;
  activeIcon: keyof typeof Ionicons.glyphMap;
  inactiveIcon: keyof typeof Ionicons.glyphMap;
};

const TAB_CONFIGS: TabConfig[] = [
  { name: 'index', label: 'Home', activeIcon: 'home', inactiveIcon: 'home-outline' },
  { name: 'focus-tab', label: 'Focus', activeIcon: 'timer', inactiveIcon: 'timer-outline' },
  { name: 'analytics', label: 'Analytics', activeIcon: 'pie-chart', inactiveIcon: 'pie-chart-outline' },
  { name: 'notes', label: 'Notes', activeIcon: 'document-text', inactiveIcon: 'document-text-outline' },
  { name: 'settings', label: 'Settings', activeIcon: 'options', inactiveIcon: 'options-outline' },
];

export function FloatingTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const theme = useAppTheme();
  const insets = useSafeAreaInsets();
  const [barWidth, setBarWidth] = useState(0);
  const [isKeyboardVisible, setKeyboardVisible] = useState(false);
  const [reduceMotion, setReduceMotion] = useState(false);

  const activeIndex = state.index;
  const pillPosition = useRef(new Animated.Value(activeIndex)).current;

  // Scale value for each individual tab button for micro-tactile spring press
  const pressScales = useRef(TAB_CONFIGS.map(() => new Animated.Value(1))).current;

  useEffect(() => {
    let active = true;
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    void AccessibilityInfo.isReduceMotionEnabled().then(reduced => {
      if (active) setReduceMotion(reduced);
    }).catch(() => undefined);
    return () => {
      active = false;
      sub.remove();
    };
  }, []);

  // Keyboard hide handling
  useEffect(() => {
    const showSub = Keyboard.addListener(
      Platform.OS === 'android' ? 'keyboardDidShow' : 'keyboardWillShow',
      () => setKeyboardVisible(true)
    );
    const hideSub = Keyboard.addListener(
      Platform.OS === 'android' ? 'keyboardDidHide' : 'keyboardWillHide',
      () => setKeyboardVisible(false)
    );
    return () => {
      showSub.remove();
      hideSub.remove();
    };
  }, []);

  // Smooth sliding pill transition on tab change
  useEffect(() => {
    if (reduceMotion) {
      pillPosition.setValue(activeIndex);
      return;
    }
    Animated.spring(pillPosition, {
      toValue: activeIndex,
      ...motion.spring.tabSwitch,
      useNativeDriver: true,
    }).start();
  }, [activeIndex, reduceMotion]);

  if (isKeyboardVisible) return null;

  const onLayout = (e: LayoutChangeEvent) => {
    const w = e.nativeEvent.layout.width;
    if (w > 0 && w !== barWidth) setBarWidth(w);
  };

  const tabCount = TAB_CONFIGS.length;
  // Account for 6px internal padding on each side
  const innerWidth = Math.max(0, barWidth - 12);
  const tabWidth = tabCount > 0 ? innerWidth / tabCount : 0;
  const pillPadding = 4;
  const pillWidth = Math.max(0, tabWidth - pillPadding * 2);

  const pillTranslateX = pillPosition.interpolate({
    inputRange: TAB_CONFIGS.map((_, i) => i),
    outputRange: TAB_CONFIGS.map((_, i) => 6 + i * tabWidth + pillPadding),
    extrapolate: 'clamp',
  });

  const handlePressIn = (index: number) => {
    if (reduceMotion) return;
    Animated.spring(pressScales[index], {
      toValue: motion.scale.tabPressed,
      stiffness: 500,
      damping: 25,
      useNativeDriver: true,
    }).start();
  };

  const handlePressOut = (index: number) => {
    if (reduceMotion) return;
    Animated.spring(pressScales[index], {
      toValue: 1,
      stiffness: 400,
      damping: 20,
      useNativeDriver: true,
    }).start();
  };

  return (
    <View
      pointerEvents="box-none"
      style={[
        styles.overlay,
        {
          bottom: Math.max(insets.bottom, 12),
        },
      ]}
    >
      <View
        onLayout={onLayout}
        style={[
          styles.container,
          {
            backgroundColor: theme.surfaceElevated,
            borderColor: theme.border,
          },
        ]}
      >
        {/* Sliding Active Pill Indicator */}
        {tabWidth > 0 && (
          <Animated.View
            style={[
              styles.slidingPill,
              {
                width: pillWidth,
                backgroundColor: theme.primaryContainer,
                transform: [{ translateX: pillTranslateX }],
              },
            ]}
          />
        )}

        {/* Tab Buttons */}
        {state.routes.map((route, index) => {
          const isFocused = state.index === index;
          const config = TAB_CONFIGS.find(c => c.name === route.name) ?? {
            name: route.name,
            label: route.name,
            activeIcon: 'apps' as const,
            inactiveIcon: 'apps-outline' as const,
          };

          const onPress = () => {
            const event = navigation.emit({
              type: 'tabPress',
              target: route.key,
              canPreventDefault: true,
            });

            if (!isFocused && !event.defaultPrevented) {
              navigation.navigate(route.name);
            }
          };

          const onLongPress = () => {
            navigation.emit({
              type: 'tabLongPress',
              target: route.key,
            });
          };

          const iconColor = isFocused ? theme.primary : theme.muted;
          const textColor = isFocused ? theme.primary : theme.muted;

          return (
            <Animated.View
              key={route.key}
              style={[
                styles.tabItemWrapper,
                { transform: [{ scale: pressScales[index] }] },
              ]}
            >
              <Pressable
                accessibilityRole="button"
                accessibilityState={isFocused ? { selected: true } : {}}
                accessibilityLabel={descriptors[route.key]?.options?.tabBarAccessibilityLabel ?? config.label}
                onPress={onPress}
                onLongPress={onLongPress}
                onPressIn={() => handlePressIn(index)}
                onPressOut={() => handlePressOut(index)}
                style={styles.tabButton}
              >
                <Ionicons
                  name={isFocused ? config.activeIcon : config.inactiveIcon}
                  size={20}
                  color={iconColor}
                />
                <Text
                  numberOfLines={1}
                  style={[
                    styles.tabLabel,
                    {
                      color: textColor,
                      fontWeight: isFocused ? '800' : '600',
                    },
                  ]}
                >
                  {config.label}
                </Text>
              </Pressable>
            </Animated.View>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    left: 14,
    right: 14,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 99,
  },
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    maxWidth: 540,
    height: FLOATING_TAB_BAR_HEIGHT,
    borderRadius: shapes.expressive.pill,
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 5,
    ...Platform.select({
      android: {
        elevation: 8,
      },
      default: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 4 },
        shadowOpacity: 0.12,
        shadowRadius: 16,
      },
    }),
  },
  slidingPill: {
    position: 'absolute',
    top: 6,
    bottom: 6,
    height: FLOATING_TAB_BAR_HEIGHT - 12,
    borderRadius: shapes.expressive.pill,
  },
  tabItemWrapper: {
    flex: 1,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  tabButton: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 3,
  },
  tabLabel: {
    fontSize: 10,
    letterSpacing: -0.1,
  },
});
