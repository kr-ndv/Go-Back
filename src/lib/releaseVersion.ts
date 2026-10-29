export type GithubReleaseLike = {
  tag_name?: unknown;
  name?: unknown;
  html_url?: unknown;
  draft?: unknown;
  prerelease?: unknown;
};

export type StableRelease = {
  version: string;
  title: string;
  url: string;
};

function versionParts(value: string): number[] | null {
  const clean = value.trim().replace(/^v/i, '').split('-')[0];
  if (!/^\d+(?:\.\d+){0,3}$/.test(clean)) return null;
  const parts = clean.split('.').map(Number);
  while (parts.length < 4) parts.push(0);
  return parts;
}

export function compareStableVersions(a: string, b: string): number {
  const left = versionParts(a);
  const right = versionParts(b);
  if (!left || !right) return 0;
  for (let index = 0; index < 4; index += 1) {
    if (left[index] !== right[index]) return left[index]! > right[index]! ? 1 : -1;
  }
  return 0;
}

export function isOfficialReleaseUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'github.com' && /^\/kr-ndv\/Go-Back\/releases\/(tag\/[^/]+|latest)\/?$/i.test(url.pathname);
  } catch {
    return false;
  }
}

export function selectLatestStableRelease(value: unknown): StableRelease | null {
  if (!Array.isArray(value)) return null;
  const stable: StableRelease[] = [];
  for (const item of value as GithubReleaseLike[]) {
    if (!item || typeof item !== 'object' || item.draft === true || item.prerelease === true) continue;
    if (typeof item.tag_name !== 'string' || typeof item.html_url !== 'string' || !versionParts(item.tag_name) || !isOfficialReleaseUrl(item.html_url)) continue;
    stable.push({
      version: item.tag_name.replace(/^v/i, ''),
      title: typeof item.name === 'string' && item.name.trim() ? item.name.trim().slice(0, 120) : `Go Back ${item.tag_name}`,
      url: item.html_url,
    });
  }
  stable.sort((a, b) => compareStableVersions(b.version, a.version));
  return stable[0] ?? null;
}
