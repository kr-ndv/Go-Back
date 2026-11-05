export const shapes = {
  corner: {
    none: 0,
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    full: 9999,
  },
  expressive: {
    container: 28, // Standard M3 Expressive card radius
    pill: 9999,    // Floating tabs, active pills, badges
    modal: 32,     // Bottom sheets and dialogs
    button: 24,    // Expressive rounded buttons
    chip: 16,      // Expressive category badges
    fab: 28,       // Floating action buttons
  },
} as const;

export type ShapeToken = typeof shapes;
