import Constants from 'expo-constants';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getSetting, setSetting } from '@/lib/database';

export const RELEASES_API = 'https://api.github.com/repos/kr-ndv/Go-Back/releases';
export const RELEASES_PAGE = 'https://github.com/kr-ndv/Go-Back/releases';
const UPDATE_CHANNEL_ID = 'go-back-updates-v1';

type GithubRelease = {
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

export type UpdateCheckResult =
  | { status: 'disabled' }
  | { status: 'up-to-date'; installedVersion: string; latestVersion: string | null }
  | { status: 'available'; installedVersion: string; release: StableRelease; notified: boolean }
  | { status: 'unavailable'; reason: string };

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
  } catch { return false; }
}

export function selectLatestStableRelease(value: unknown): StableRelease | null {
  if (!Array.isArray(value)) return null;
  const stable: StableRelease[] = [];
  for (const item of value as GithubRelease[]) {
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

async function notifyForRelease(release: StableRelease) {
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(UPDATE_CHANNEL_ID, {
      name: 'Go Back updates',
      importance: Notifications.AndroidImportance.DEFAULT,
      vibrationPattern: [0, 180, 120, 180],
    });
  }
  const trigger = Platform.OS === 'android'
    ? ({ channelId: UPDATE_CHANNEL_ID } as Notifications.NotificationTriggerInput)
    : null;
  await Notifications.scheduleNotificationAsync({
    content: {
      title: 'A Go Back update is available',
      body: `${release.version} is available. Tap to view the official release notes.`,
      data: { goBackKind: 'app-update', url: release.url, version: release.version },
    },
    trigger,
  });
}

export async function checkForStableUpdate(options: { notify?: boolean; force?: boolean } = {}): Promise<UpdateCheckResult> {
  const enabled = options.force ? true : await getSetting<boolean>('updates.enabled', true);
  if (!enabled) return { status: 'disabled' };
  const installedVersion = Constants.expoConfig?.version ?? '0.0.0';
  try {
    const response = await fetch(RELEASES_API, { headers: { Accept: 'application/vnd.github+json' } });
    if (!response.ok) return { status: 'unavailable', reason: `GitHub returned ${response.status}.` };
    const latest = selectLatestStableRelease(await response.json());
    await setSetting('updates.lastCheckedAt', new Date().toISOString());
    if (!latest || compareStableVersions(latest.version, installedVersion) <= 0) {
      return { status: 'up-to-date', installedVersion, latestVersion: latest?.version ?? null };
    }
    let notified = false;
    if (options.notify !== false) {
      const lastNotified = await getSetting<string>('updates.lastNotifiedVersion', '');
      if (lastNotified !== latest.version) {
        let permission = await Notifications.getPermissionsAsync();
        if (!permission.granted) permission = await Notifications.requestPermissionsAsync();
        if (permission.granted) {
          await notifyForRelease(latest);
          await setSetting('updates.lastNotifiedVersion', latest.version);
          notified = true;
        }
      }
    }
    return { status: 'available', installedVersion, release: latest, notified };
  } catch (error) {
    return { status: 'unavailable', reason: error instanceof Error ? error.message : 'Could not check GitHub releases.' };
  }
}
