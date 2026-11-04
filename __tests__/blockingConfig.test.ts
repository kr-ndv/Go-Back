import { DEFAULT_BLOCKING_CONFIG, normalizeBlockingConfig } from '@/lib/blockingConfig';

describe('blocking config normalization', () => {
  test('deduplicates channels and packages while preserving enabled flags', () => {
    const result = normalizeBlockingConfig({
      blockYoutube: true,
      blockShorts: true,
      blockReels: false,
      blockSpotlight: true,
      blockAdultSites: true,
      blockSplitScreen: true,
      strictUninstallProtection: true,
      strictMode: true,
      blockOnlyDuringFocus: true,
      allowedChannels: [' @Study ', '@Study', 'Physics Academy'],
      blockedPackages: ['com.example.one', 'bad package', 'com.example.one', 'com.goback.focus'],
    });
    expect(result.blockYoutube).toBe(true);
    expect(result.blockShorts).toBe(true);
    expect(result.blockSpotlight).toBe(true);
    expect(result.blockAdultSites).toBe(true);
    expect(result.blockSplitScreen).toBe(true);
    expect(result.strictUninstallProtection).toBe(true);
    expect(result.strictMode).toBe(true);
    expect(result.allowedChannels).toEqual(['@Study', 'Physics Academy']);
    expect(result.blockedPackages).toEqual(['com.example.one']);
  });

  test('provides sensible defaults for missing properties', () => {
    const result = normalizeBlockingConfig({});
    expect(result).toEqual(DEFAULT_BLOCKING_CONFIG);
  });
});
