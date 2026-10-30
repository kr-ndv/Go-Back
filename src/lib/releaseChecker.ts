import Constants from 'expo-constants';
import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { getSetting, setSetting } from '@/lib/database';
import { compareStableVersions, selectLatestStableRelease, type StableRelease } from '@/lib/releaseLogic';

export { compareStableVersions, isOfficialReleaseUrl, selectLatestStableRelease } from '@/lib/releaseLogic';
export type { StableRelease } from '@/lib/releaseLogic';

export const RELEASES_API = 'https://api.github.com/repos/kr-ndv/Go-Back/releases';
export const RELEASES_PAGE = 'https://github.com/kr-ndv/Go-Back/releases';
const UPDATE_CHANNEL_ID = 'go-back-updates-v1';

export type UpdateCheckResult =
  | { status: 'disabled' }
  | { status: 'up-to-date'; installedVersion: string; latestVersion: string | null }
  | { status: 'available'; installedVersion: string; release: StableRelease; notified: boolean }
  | { status: 'unavailable'; reason: string };

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
