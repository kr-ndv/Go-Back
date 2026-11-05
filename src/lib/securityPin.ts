import { getSetting, setSetting } from '@/lib/database';

export const PIN_SETTING_KEY = 'security.pin';
const PIN_SALT = 'goback_pin_salt_v1_';

export function hashPin(pin: string): string {
  const input = `${PIN_SALT}${pin}`;
  let h1 = 0xdeadbeef;
  let h2 = 0x41c6ce57;
  for (let i = 0; i < input.length; i++) {
    const ch = input.charCodeAt(i);
    h1 = Math.imul(h1 ^ ch, 2654435761);
    h2 = Math.imul(h2 ^ ch, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return (4294967296 * (2097151 & h2) + (h1 >>> 0)).toString(16).padStart(16, '0');
}

export function isValidPinFormat(pin: unknown): pin is string {
  return typeof pin === 'string' && /^\d{4}$/.test(pin);
}

export function matchesPin(storedPin: string | null | undefined, candidate: string): boolean {
  if (!storedPin) return true;
  if (storedPin.length === 4 && /^\d{4}$/.test(storedPin)) {
    return candidate === storedPin;
  }
  return hashPin(candidate) === storedPin;
}

export async function getSecurityPin(): Promise<string | null> {
  const pin = await getSetting<string | null>(PIN_SETTING_KEY, null);
  if (!pin || typeof pin !== 'string') return null;
  if (isValidPinFormat(pin)) {
    const hashed = hashPin(pin);
    void setSetting(PIN_SETTING_KEY, hashed).catch(() => undefined);
    return hashed;
  }
  return pin;
}

export async function setSecurityPin(pin: string | null): Promise<void> {
  if (pin === null || pin === '') {
    await setSetting(PIN_SETTING_KEY, null);
    return;
  }
  if (!isValidPinFormat(pin)) {
    throw new Error('PIN must be exactly 4 numeric digits.');
  }
  await setSetting(PIN_SETTING_KEY, hashPin(pin));
}

export async function verifySecurityPin(candidate: string): Promise<boolean> {
  const current = await getSecurityPin();
  return matchesPin(current, candidate);
}
