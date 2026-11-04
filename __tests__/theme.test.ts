import { accentColors, createTheme, paletteNames, type AccentColor } from '../src/theme/palette';

const luminance = (hex: string) => {
  const channels = hex.replace('#', '').match(/.{2}/g)!.map(value => parseInt(value, 16) / 255)
    .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
};
const contrastRatio = (foreground: string, background: string) => {
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a);
  return (values[0] + 0.05) / (values[1] + 0.05);
};

describe('Material 3 Expressive palettes', () => {
  const accents: AccentColor[] = ['forest', 'blue', 'violet', 'amber', 'rose', 'teal', 'berry', 'sakura', 'graphite', 'coral', 'lavender', 'crimson'];

  it('provides a named palette and distinct primary colours for each accent', () => {
    const lightPrimaries = accents.map(accent => createTheme('light', accent).primary);
    expect(paletteNames.forest).toBe('Forest Lime');
    expect(paletteNames.blue).toBe('Ocean Blue');
    expect(paletteNames.violet).toBe('Violet Pulse');
    expect(paletteNames.amber).toBe('Sunset Amber');
    expect(paletteNames.rose).toBe('Rose Quartz');
    expect(paletteNames.teal).toBe('Arctic Teal');
    expect(paletteNames.berry).toBe('Midnight Berry');
    expect(paletteNames.sakura).toBe('Sakura Pink');
    expect(paletteNames.graphite).toBe('Graphite Mint');
    expect(paletteNames.coral).toBe('Coral Bloom');
    expect(paletteNames.lavender).toBe('Lavender Sky');
    expect(paletteNames.crimson).toBe('Crimson Ember');
    expect(new Set(lightPrimaries).size).toBe(accents.length);
  });

  it('provides complete semantic tokens in light and dark modes', () => {
    for (const accent of accents) {
      for (const mode of ['light', 'dark'] as const) {
        const theme = createTheme(mode, accent);
        expect(theme.background).toBeTruthy();
        expect(theme.surface).toBeTruthy();
        expect(theme.surfaceElevated).toBeTruthy();
        expect(theme.text).toBeTruthy();
        expect(theme.muted).toBeTruthy();
        expect(theme.border).toBeTruthy();
        expect(theme.primary).toBe(accentColors[accent][mode]);
        expect(theme.success).toBeTruthy();
        expect(theme.warning).toBeTruthy();
        expect(theme.danger).toBeTruthy();
        expect(theme.onPrimary).toBeTruthy();
      }
    }
  });

  it('keeps primary button text at accessible contrast in every palette and mode', () => {
    for (const accent of accents) {
      for (const mode of ['light', 'dark'] as const) {
        const theme = createTheme(mode, accent);
        expect(contrastRatio(theme.onPrimary, theme.primary)).toBeGreaterThanOrEqual(4.5);
      }
    }
  });

  it('allows expressive styling to be switched off without changing the palette', () => {
    const expressive = createTheme('dark', 'violet', true);
    const restrained = createTheme('dark', 'violet', false);
    expect(expressive.expressive).toBe(true);
    expect(restrained.expressive).toBe(false);
    expect(expressive.primary).toBe(restrained.primary);
    expect(expressive.background).toBe(restrained.background);
  });
});
