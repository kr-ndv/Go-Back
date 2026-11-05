export const motion = {
  duration: {
    quick: 140,
    standard: 220,
    emphasis: 320,
    enter: 420,
  },
  spring: {
    press: { stiffness: 420, damping: 28, mass: 0.55 },
    expressive: { stiffness: 260, damping: 22, mass: 0.8 },
    gentle: { stiffness: 300, damping: 30, mass: 0.7 },
    tabSwitch: { stiffness: 340, damping: 26, mass: 0.65 },
    bouncy: { stiffness: 400, damping: 20, mass: 0.6 },
    wavePulse: { stiffness: 180, damping: 18, mass: 0.9 },
  },
  scale: {
    pressed: 0.965,
    restrainedPressed: 0.985,
    tabPressed: 0.92,
  },
  wave: {
    amplitude: 3.5, // Wave peak displacement in px
    wavelength: 24, // Wave cycle width in px
    speedMs: 1400,  // Milliseconds per complete wave cycle
  },
} as const;

export type MotionToken = typeof motion;
