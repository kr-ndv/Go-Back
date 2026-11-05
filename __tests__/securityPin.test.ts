jest.mock('@/lib/database', () => ({
  getSetting: jest.fn(),
  setSetting: jest.fn(),
  getDatabase: jest.fn(),
}));

import { isValidPinFormat, matchesPin, setSecurityPin, getSecurityPin, verifySecurityPin, hashPin } from '@/lib/securityPin';
import { getSetting, setSetting } from '@/lib/database';

describe('securityPin', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('isValidPinFormat', () => {
    it('accepts valid 4-digit numeric pins', () => {
      expect(isValidPinFormat('1234')).toBe(true);
      expect(isValidPinFormat('0000')).toBe(true);
      expect(isValidPinFormat('9876')).toBe(true);
    });

    it('rejects non-4-digit strings', () => {
      expect(isValidPinFormat('123')).toBe(false);
      expect(isValidPinFormat('12345')).toBe(false);
      expect(isValidPinFormat('')).toBe(false);
      expect(isValidPinFormat('abcd')).toBe(false);
      expect(isValidPinFormat('12a4')).toBe(false);
      expect(isValidPinFormat(null)).toBe(false);
      expect(isValidPinFormat(undefined)).toBe(false);
    });
  });

  describe('matchesPin', () => {
    it('returns true if no stored pin exists', () => {
      expect(matchesPin(null, '1234')).toBe(true);
      expect(matchesPin(undefined, '1234')).toBe(true);
      expect(matchesPin('', '1234')).toBe(true);
    });

    it('validates matching candidate pin against hash', () => {
      const hashed = hashPin('1234');
      expect(matchesPin(hashed, '1234')).toBe(true);
      expect(matchesPin(hashed, '4321')).toBe(false);
    });

    it('validates matching candidate pin against legacy plaintext', () => {
      expect(matchesPin('1234', '1234')).toBe(true);
      expect(matchesPin('1234', '4321')).toBe(false);
    });
  });

  describe('getSecurityPin and setSecurityPin', () => {
    it('returns null when no pin configured', async () => {
      (getSetting as jest.Mock).mockResolvedValueOnce(null);
      const pin = await getSecurityPin();
      expect(pin).toBeNull();
    });

    it('returns valid pin string', async () => {
      const hashed = hashPin('4321');
      (getSetting as jest.Mock).mockResolvedValueOnce(hashed);
      const pin = await getSecurityPin();
      expect(pin).toBe(hashed);
    });

    it('stores new 4-digit pin as salted hash', async () => {
      await setSecurityPin('5678');
      expect(setSetting).toHaveBeenCalledWith('security.pin', hashPin('5678'));
    });

    it('clears pin when null passed', async () => {
      await setSecurityPin(null);
      expect(setSetting).toHaveBeenCalledWith('security.pin', null);
    });

    it('throws error on invalid pin', async () => {
      await expect(setSecurityPin('abc')).rejects.toThrow();
    });

    it('verifies candidate against stored pin', async () => {
      (getSetting as jest.Mock).mockResolvedValueOnce(hashPin('9999'));
      const valid = await verifySecurityPin('9999');
      expect(valid).toBe(true);
    });
  });
});
