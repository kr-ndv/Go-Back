import { channelTextMatches, normalizeChannelToken } from '@/lib/youtubeAllowlist';

describe('YouTube allowlist matching', () => {
  test('matches an exact handle', () => {
    expect(channelTextMatches('Video title @StudyWithMe Subscribe', ['@StudyWithMe'])).toBe(true);
  });

  test('matches a normalized channel name', () => {
    expect(channelTextMatches('Physics Academy · 1.2M subscribers', ['Physics Academy'])).toBe(true);
  });

  test('does not accept a short substring as a channel match', () => {
    expect(channelTextMatches('The Great Physics Academy', ['Phy'])).toBe(false);
  });

  test('normalizes handles without the at sign', () => {
    expect(normalizeChannelToken('@My_Channel').raw).toBe('my_channel');
  });
});
