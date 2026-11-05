import React, { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Svg, { Circle, G } from 'react-native-svg';
import { useAppTheme } from '@/state/theme';
import { motion } from '@/theme/motion';

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

export type M3CircularProgressProps = {
  /** Outer square diameter in px; defaults to 220 */
  size?: number;
  /** Progress between 0.0 and 1.0 (or 0 to 100) */
  progress: number;
  /** Thickness of the circular stroke; defaults to 8 */
  strokeWidth?: number;
  /** Whether the associated task/timer is running to activate expressive breathing */
  active?: boolean;
  /** Primary indicator color; defaults to theme.primary */
  color?: string;
  /** Background track color; defaults to theme.surfaceAlt */
  trackColor?: string;
  /** Indeterminate mode with continuous expressive rotation */
  indeterminate?: boolean;
  /** Inner content rendered in the center of the ring (e.g. countdown clock) */
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
};

export function M3CircularProgress({
  size = 220,
  progress,
  strokeWidth = 8,
  active = false,
  color,
  trackColor,
  indeterminate = false,
  children,
  style,
}: M3CircularProgressProps) {
  const theme = useAppTheme();
  const [reduceMotion, setReduceMotion] = useState(false);

  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;

  const normalizedProgress = Math.max(0, Math.min(1, progress > 1 ? progress / 100 : progress));
  const animatedProgress = useRef(new Animated.Value(normalizedProgress)).current;
  const pulseScale = useRef(new Animated.Value(1)).current;
  const rotation = useRef(new Animated.Value(0)).current;

  const stroke = color ?? theme.primary;
  const track = trackColor ?? theme.surfaceAlt;

  useEffect(() => {
    let mounted = true;
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    void AccessibilityInfo.isReduceMotionEnabled().then(reduced => {
      if (mounted) setReduceMotion(reduced);
    }).catch(() => undefined);
    return () => {
      mounted = false;
      sub.remove();
    };
  }, []);

  // Spring animated progress arc
  useEffect(() => {
    if (reduceMotion) {
      animatedProgress.setValue(normalizedProgress);
      return;
    }
    Animated.spring(animatedProgress, {
      toValue: normalizedProgress,
      ...motion.spring.expressive,
      useNativeDriver: false,
    }).start();
  }, [normalizedProgress, reduceMotion]);

  // Expressive breathing pulse when active
  useEffect(() => {
    if (reduceMotion || !active || indeterminate) {
      pulseScale.setValue(1);
      return;
    }
    const pulseAnim = Animated.loop(
      Animated.sequence([
        Animated.timing(pulseScale, {
          toValue: 1.028,
          duration: 1800,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(pulseScale, {
          toValue: 1.0,
          duration: 1800,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ])
    );
    pulseAnim.start();
    return () => pulseAnim.stop();
  }, [active, indeterminate, reduceMotion]);

  // Indeterminate rotation
  useEffect(() => {
    if (reduceMotion || !indeterminate) {
      rotation.setValue(0);
      return;
    }
    const rotateAnim = Animated.loop(
      Animated.timing(rotation, {
        toValue: 1,
        duration: 1600,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );
    rotateAnim.start();
    return () => rotateAnim.stop();
  }, [indeterminate, reduceMotion]);

  const strokeDashoffset = animatedProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [circumference, 0],
  });

  const spin = rotation.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  return (
    <View style={[styles.root, { width: size, height: size }, style]}>
      {/* Outer breathing/spinning SVG ring */}
      <Animated.View
        style={[
          StyleSheet.absoluteFillObject,
          {
            transform: [
              { scale: pulseScale },
              { rotate: indeterminate ? spin : '-90deg' },
            ],
          },
        ]}
      >
        <Svg width={size} height={size}>
          <G originX={size / 2} originY={size / 2}>
            {/* Background Track Circle */}
            <Circle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              stroke={track}
              strokeWidth={strokeWidth}
              fill="transparent"
            />
            {/* Animated Active Arc */}
            <AnimatedCircle
              cx={size / 2}
              cy={size / 2}
              r={radius}
              stroke={stroke}
              strokeWidth={strokeWidth}
              strokeDasharray={`${circumference} ${circumference}`}
              strokeDashoffset={indeterminate ? circumference * 0.75 : strokeDashoffset}
              strokeLinecap="round"
              fill="transparent"
            />
          </G>
        </Svg>
      </Animated.View>

      {/* Centered content (Timer clock, label, icons) */}
      {children && (
        <View style={[styles.centerContent, { width: size, height: size }]}>
          {children}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
  },
  centerContent: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 16,
  },
});
