export function normalizeChannelToken(value: string): { raw: string; words: string } {
  const raw = value.trim().replace(/^@/, '').toLocaleLowerCase();
  const words = raw.replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  return { raw, words };
}

export function channelTextMatches(visibleText: string, configured: string[]): boolean {
  const rawVisible = ` ${visibleText.toLocaleLowerCase()} `;
  const wordVisible = ` ${visibleText.toLocaleLowerCase().replace(/[^\p{L}\p{N}@]+/gu, ' ').trim()} `;
  for (const channel of configured) {
    const token = normalizeChannelToken(channel);
    if (token.raw.length >= 3 && (rawVisible.includes(`@${token.raw}`) || wordVisible.includes(` @${token.raw} `))) return true;
    if (token.words.length >= 3 && wordVisible.includes(` ${token.words} `)) return true;
  }
  return false;
}
