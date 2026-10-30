import { compareStableVersions, isOfficialReleaseUrl, selectLatestStableRelease } from '@/lib/releaseLogic';

describe('release checker', () => {
  test('compares stable versions', () => {
    expect(compareStableVersions('1.2.0', '1.1.9')).toBe(1);
    expect(compareStableVersions('v1.0.0', '1.0')).toBe(0);
    expect(compareStableVersions('0.9.0', '1.0.0')).toBe(-1);
  });
  test('accepts only official repository release URLs', () => {
    expect(isOfficialReleaseUrl('https://github.com/kr-ndv/Go-Back/releases/tag/v1.1.0')).toBe(true);
    expect(isOfficialReleaseUrl('https://example.com/kr-ndv/Go-Back/releases/tag/v1.1.0')).toBe(false);
  });
  test('ignores drafts and prereleases', () => {
    expect(selectLatestStableRelease([
      { tag_name: 'v2.0.0', html_url: 'https://github.com/kr-ndv/Go-Back/releases/tag/v2.0.0', draft: false, prerelease: true },
      { tag_name: 'v1.2.0', html_url: 'https://github.com/kr-ndv/Go-Back/releases/tag/v1.2.0', draft: false, prerelease: false },
      { tag_name: 'v1.1.0', html_url: 'https://github.com/kr-ndv/Go-Back/releases/tag/v1.1.0', draft: false, prerelease: false },
    ])?.version).toBe('1.2.0');
  });
});
