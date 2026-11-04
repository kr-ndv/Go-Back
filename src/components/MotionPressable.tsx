import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';
import { useAppTheme } from '@/state/theme';
import { motion } from '@/theme/motion';

type MotionPressableProps = Omit<PressableProps, 'style'> & {
  style?: StyleProp<ViewStyle>;
};

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Shared, native-driven spring feedback. Expressive motion stays quick and controlled. */
export function MotionPressable({ style, onPressIn, onPressOut, ...props }: MotionPressableProps) {
  const scale = useRef(new Animated.Value(1)).current;
  const [reduceMotion, setReduceMotion] = useState(false);
  const theme = useAppTheme();

  useEffect(() => {
    let active = true;
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduceMotion);
    void AccessibilityInfo.isReduceMotionEnabled().then(reduced => {
      if (active) setReduceMotion(reduced);
    }).catch(() => undefined);
    return () => {
      active = false;
      subscription.remove();
    };
  }, []);

  useEffect(() => {
    if (reduceMotion) scale.setValue(1);
  }, [reduceMotion, scale]);

  const animate = (toValue: number) => {
    Animated.spring(scale, {
      toValue,
      ...(theme.expressive ? motion.spring.expressive : motion.spring.press),
      useNativeDriver: true,
    }).start();
  };

  return (
    <AnimatedPressable
      {...props}
      style={[style, { transform: [{ scale }] }]}
      onPressIn={event => {
        if (!reduceMotion) animate(theme.expressive ? motion.scale.pressed : motion.scale.restrainedPressed);
        onPressIn?.(event);
      }}
      onPressOut={event => {
        if (reduceMotion) scale.setValue(1);
        else animate(1);
        onPressOut?.(event);
      }}
    />
  );
}
