import { isOfficialReleaseUrl } from '@/lib/releaseChecker';

export function notificationUrl(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const value = data as Record<string, unknown>;
  if (value.goBackKind === 'app-update' && typeof value.url === 'string' && isOfficialReleaseUrl(value.url)) return value.url;
  return null;
}
