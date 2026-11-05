import React, { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, LayoutChangeEvent, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { useAppTheme } from '@/state/theme';
import { motion } from '@/theme/motion';

export type M3SquigglyProgressProps = {
  /** Progress between 0.0 and 1.0 (or 0 to 100 if percent) */
  progress: number;
  /** Whether the associated task/timer is actively running to ripple the wave */
  active?: boolean;
  /** Primary wave stroke color; defaults to theme.primary */
  color?: string;
  /** Background track color; defaults to theme.surfaceAlt */
  trackColor?: string;
  /** Height of the indicator container; defaults to 16 */
  height?: number;
  /** Stroke thickness of the wave and track line; defaults to 3.5 */
  strokeWidth?: number;
  /** Amplitude of the sine wave peaks; defaults to motion.wave.amplitude */
  amplitude?: number;
  /** Cycle width of the wave in px; defaults to motion.wave.wavelength */
  wavelength?: number;
  style?: StyleProp<ViewStyle>;
};

function buildSineWavePath(totalWidth: number, centerY: number, amplitude: number, wavelength: number): string {
  if (totalWidth <= 0 || wavelength <= 0) return '';
  const half = wavelength / 2;
  const quarter = wavelength / 4;
  let d = `M 0 ${centerY}`;
  let x = 0;
  let up = true;

  while (x < totalWidth) {
    const nextX = x + half;
    const peakY = up ? centerY - amplitude : centerY + amplitude;
    const ctrlX = x + quarter;
    // Quadratic bezier curve from x to nextX using control point (ctrlX, peakY)
    d += ` Q ${ctrlX.toFixed(2)} ${peakY.toFixed(2)} ${nextX.toFixed(2)} ${centerY.toFixed(2)}`;
    x = nextX;
    up = !up;
  }
  return d;
}

export function M3SquigglyProgress({
  progress,
  active = true,
  color,
  trackColor,
  height = 16,
  strokeWidth = 3.5,
  amplitude = motion.wave.amplitude,
  wavelength = motion.wave.wavelength,
  style,
}: M3SquigglyProgressProps) {
  const theme = useAppTheme();
  const [containerWidth, setContainerWidth] = useState(0);
  const [reduceMotion, setReduceMotion] = useState(false);

  const normalizedProgress = Math.max(0, Math.min(1, progress > 1 ? progress / 100 : progress));
  const animatedProgress = useRef(new Animated.Value(normalizedProgress)).current;
  const wavePhase = useRef(new Animated.Value(0)).current;

  const stroke = color ?? theme.primary;
  const track = trackColor ?? theme.surfaceAlt;
  const centerY = height / 2;

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

  // Animate progress changes with an M3 expressive spring
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

  // Continuously ripple the wave when active
  useEffect(() => {
    if (reduceMotion || !active || normalizedProgress <= 0) {
      wavePhase.setValue(0);
      return;
    }

    const rippleAnim = Animated.loop(
      Animated.timing(wavePhase, {
        toValue: -wavelength,
        duration: motion.wave.speedMs,
        easing: Easing.linear,
        useNativeDriver: true,
      })
    );
    rippleAnim.start();

    return () => {
      rippleAnim.stop();
    };
  }, [active, normalizedProgress, reduceMotion, wavelength]);

  const onLayout = (e: LayoutChangeEvent) => {
    const w = Math.round(e.nativeEvent.layout.width);
    if (w > 0 && w !== containerWidth) setContainerWidth(w);
  };

  const activeWidth = animatedProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [0, containerWidth || 1],
    extrapolate: 'clamp',
  });

  // Render extended wave width to allow smooth continuous horizontal translation
  const extendedWaveWidth = containerWidth > 0 ? containerWidth + wavelength * 2 : 300;
  const wavePath = React.useMemo(() => {
    return buildSineWavePath(extendedWaveWidth, centerY, amplitude, wavelength);
  }, [extendedWaveWidth, centerY, amplitude, wavelength]);

  return (
    <View onLayout={onLayout} style={[styles.container, { height }, style]}>
      {/* 1. Inactive baseline track */}
      <View
        style={[
          styles.trackBaseline,
          {
            top: centerY - strokeWidth / 2,
            height: strokeWidth,
            backgroundColor: track,
            borderRadius: strokeWidth / 2,
          },
        ]}
      />

      {/* 2. Active squiggly wave (clipped to progress width) */}
      {containerWidth > 0 && (
        <Animated.View
          style={[
            styles.activeClip,
            {
              width: activeWidth,
              height,
            },
          ]}
        >
          {reduceMotion ? (
            // Flat straight active bar when reduce motion is enabled
            <View
              style={[
                styles.flatFill,
                {
                  top: centerY - strokeWidth / 2,
                  height: strokeWidth,
                  backgroundColor: stroke,
                  borderRadius: strokeWidth / 2,
                },
              ]}
            />
          ) : (
            <Animated.View
              style={{
                width: extendedWaveWidth,
                height,
                transform: [{ translateX: wavePhase }],
              }}
            >
              <Svg width={extendedWaveWidth} height={height}>
                <Path
                  d={wavePath}
                  stroke={stroke}
                  strokeWidth={strokeWidth}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  fill="none"
                />
              </Svg>
            </Animated.View>
          )}
        </Animated.View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  trackBaseline: {
    position: 'absolute',
    left: 0,
    right: 0,
    width: '100%',
  },
  activeClip: {
    position: 'absolute',
    left: 0,
    top: 0,
    overflow: 'hidden',
  },
  flatFill: {
    position: 'absolute',
    left: 0,
    right: 0,
  },
});
