import type { UpdateCheckResult } from '@/lib/releaseChecker';

export function updateStatusLabel(result: UpdateCheckResult | null): string {
  if (!result) return 'Stable GitHub releases only.';
  if (result.status === 'disabled') return 'Update notifications are off.';
  if (result.status === 'unavailable') return 'Could not check for updates right now.';
  if (result.status === 'up-to-date') return result.latestVersion ? `Up to date · ${result.installedVersion}` : `Installed · ${result.installedVersion}`;
  return `Update ${result.release.version} available`;
}
