import { useEffect, useRef } from 'react';
import { AccessibilityInfo, Animated, type StyleProp, type ViewStyle } from 'react-native';
import { motion } from '@/theme/motion';

type MotionRevealProps = {
  children: React.ReactNode;
  delay?: number;
  style?: StyleProp<ViewStyle>;
};

/** A one-time entrance that respects Android's reduced-motion preference. */
export function MotionReveal({ children, delay = 0, style }: MotionRevealProps) {
  const opacity = useRef(new Animated.Value(0)).current;
  const offsetY = useRef(new Animated.Value(12)).current;

  useEffect(() => {
    let active = true;
    let animation: Animated.CompositeAnimation | undefined;
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', reduced => {
      if (reduced) {
        animation?.stop();
        opacity.setValue(1);
        offsetY.setValue(0);
      }
    });

    void AccessibilityInfo.isReduceMotionEnabled().then(reduced => {
      if (!active) return;
      if (reduced) {
        opacity.setValue(1);
        offsetY.setValue(0);
        return;
      }
      animation = Animated.parallel([
        Animated.timing(opacity, {
          toValue: 1,
          duration: motion.duration.emphasis,
          delay,
          useNativeDriver: true,
        }),
        Animated.spring(offsetY, {
          toValue: 0,
          ...motion.spring.expressive,
          delay,
          useNativeDriver: true,
        }),
      ]);
      animation.start();
    }).catch(() => {
      opacity.setValue(1);
      offsetY.setValue(0);
    });

    return () => {
      active = false;
      animation?.stop();
      subscription.remove();
    };
  }, [delay, offsetY, opacity]);

  return <Animated.View style={[style, { opacity, transform: [{ translateY: offsetY }] }]}>{children}</Animated.View>;
}
