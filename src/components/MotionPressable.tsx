import { useRef } from 'react';
import { Animated, Pressable, type PressableProps, type StyleProp, type ViewStyle } from 'react-native';

type MotionPressableProps = Omit<PressableProps, 'style'> & {
  style?: StyleProp<ViewStyle>;
};

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

/** Small spring feedback for tappable surfaces; intentionally subtle and fast. */
export function MotionPressable({ style, onPressIn, onPressOut, ...props }: MotionPressableProps) {
  const scale = useRef(new Animated.Value(1)).current;
  const animate = (toValue: number) => {
    Animated.spring(scale, {
      toValue,
      useNativeDriver: true,
      stiffness: 420,
      damping: 28,
      mass: 0.55,
    }).start();
  };

  return (
    <AnimatedPressable
      {...props}
      style={[style, { transform: [{ scale }] }]}
      onPressIn={event => {
        animate(0.975);
        onPressIn?.(event);
      }}
      onPressOut={event => {
        animate(1);
        onPressOut?.(event);
      }}
    />
  );
}
