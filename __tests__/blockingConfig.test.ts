import { normalizeBlockingConfig } from '@/lib/blockingConfig';

describe('blocking config normalization', () => {
  test('deduplicates channels and packages while preserving enabled flags', () => {
    const result = normalizeBlockingConfig({
      blockYoutube: true,
      blockShorts: true,
      blockReels: false,
      blockOnlyDuringFocus: true,
      allowedChannels: [' @Study ', '@Study', 'Physics Academy'],
      blockedPackages: ['com.example.one', 'bad package', 'com.example.one', 'com.goback.focus'],
    });
    expect(result.blockYoutube).toBe(true);
    expect(result.allowedChannels).toEqual(['@Study', 'Physics Academy']);
    expect(result.blockedPackages).toEqual(['com.example.one']);
  });
});
