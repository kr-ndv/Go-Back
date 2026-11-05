import { shapes } from '../src/theme/shapes';
import { typography } from '../src/theme/typography';
import { motion } from '../src/theme/motion';

describe('Material 3 Expressive Tokens', () => {
  describe('Shapes', () => {
    it('defines standard M3 corner radii tokens', () => {
      expect(shapes.corner.none).toBe(0);
      expect(shapes.corner.xs).toBe(4);
      expect(shapes.corner.sm).toBe(8);
      expect(shapes.corner.md).toBe(12);
      expect(shapes.corner.lg).toBe(16);
      expect(shapes.corner.xl).toBe(24);
      expect(shapes.corner.full).toBe(9999);
    });

    it('defines M3 expressive component radii', () => {
      expect(shapes.expressive.container).toBe(28);
      expect(shapes.expressive.pill).toBe(9999);
      expect(shapes.expressive.modal).toBe(32);
      expect(shapes.expressive.button).toBe(24);
      expect(shapes.expressive.chip).toBe(16);
    });
  });

  describe('Typography', () => {
    it('provides display scales with tabular numerals for timers', () => {
      expect(typography.display.large.fontVariant).toContain('tabular-nums');
      expect(typography.display.large.fontSize).toBe(56);
      expect(typography.display.medium.fontVariant).toContain('tabular-nums');
      expect(typography.display.small.fontVariant).toContain('tabular-nums');
    });

    it('provides high-contrast labels with wide tracking for badges', () => {
      expect(typography.label.small.letterSpacing).toBeGreaterThanOrEqual(1.0);
      expect(typography.label.small.fontWeight).toBe('900');
    });
  });

  describe('Motion & Wave Physics', () => {
    it('provides wave ripple parameters for squiggly progress', () => {
      expect(motion.wave.amplitude).toBeGreaterThan(0);
      expect(motion.wave.wavelength).toBeGreaterThan(10);
      expect(motion.wave.speedMs).toBeGreaterThan(500);
    });

    it('provides spring curves for expressive tab switching and tactile feedback', () => {
      expect(motion.spring.tabSwitch.stiffness).toBeGreaterThan(200);
      expect(motion.spring.expressive.damping).toBeGreaterThan(15);
      expect(motion.scale.tabPressed).toBeLessThan(1.0);
    });
  });
});
