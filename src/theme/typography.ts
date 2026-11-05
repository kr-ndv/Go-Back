import type { TextStyle } from 'react-native';

export const typography = {
  display: {
    large: { fontSize: 56, fontWeight: '900', letterSpacing: -2, fontVariant: ['tabular-nums'] } as TextStyle,
    medium: { fontSize: 44, fontWeight: '800', letterSpacing: -1.5, fontVariant: ['tabular-nums'] } as TextStyle,
    small: { fontSize: 36, fontWeight: '800', letterSpacing: -1.2, fontVariant: ['tabular-nums'] } as TextStyle,
  },
  headline: {
    large: { fontSize: 32, fontWeight: '900', letterSpacing: -1 } as TextStyle,
    medium: { fontSize: 26, fontWeight: '800', letterSpacing: -0.8 } as TextStyle,
    small: { fontSize: 22, fontWeight: '800', letterSpacing: -0.5 } as TextStyle,
  },
  title: {
    large: { fontSize: 18, fontWeight: '800', letterSpacing: -0.3 } as TextStyle,
    medium: { fontSize: 16, fontWeight: '700', letterSpacing: -0.2 } as TextStyle,
    small: { fontSize: 14, fontWeight: '700' } as TextStyle,
  },
  body: {
    large: { fontSize: 15, fontWeight: '400', lineHeight: 22 } as TextStyle,
    medium: { fontSize: 13, fontWeight: '400', lineHeight: 19 } as TextStyle,
    small: { fontSize: 11, fontWeight: '400', lineHeight: 16 } as TextStyle,
  },
  label: {
    large: { fontSize: 13, fontWeight: '800', letterSpacing: 0.5 } as TextStyle,
    medium: { fontSize: 11, fontWeight: '800', letterSpacing: 0.8 } as TextStyle,
    small: { fontSize: 9, fontWeight: '900', letterSpacing: 1.2 } as TextStyle,
  },
} as const;

export type TypographyToken = typeof typography;
