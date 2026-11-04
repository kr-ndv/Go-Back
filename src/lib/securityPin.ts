import { getSetting, setSetting } from '@/lib/database';

export const PIN_SETTING_KEY = 'security.pin';

export function isValidPinFormat(pin: unknown): pin is string {
  return typeof pin === 'string' && /^\d{4}$/.test(pin);
}

export function matchesPin(storedPin: string | null | undefined, candidate: string): boolean {
  if (!storedPin) return true;
  return candidate === storedPin;
}

export async function getSecurityPin(): Promise<string | null> {
  const pin = await getSetting<string | null>(PIN_SETTING_KEY, null);
  return isValidPinFormat(pin) ? pin : null;
}

export async function setSecurityPin(pin: string | null): Promise<void> {
  if (pin === null || pin === '') {
    await setSetting(PIN_SETTING_KEY, null);
    return;
  }
  if (!isValidPinFormat(pin)) {
    throw new Error('PIN must be exactly 4 numeric digits.');
  }
  await setSetting(PIN_SETTING_KEY, pin);
}

export async function verifySecurityPin(candidate: string): Promise<boolean> {
  const current = await getSecurityPin();
  return matchesPin(current, candidate);
}
