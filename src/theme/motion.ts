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
  },
  scale: {
    pressed: 0.965,
    restrainedPressed: 0.985,
  },
} as const;
