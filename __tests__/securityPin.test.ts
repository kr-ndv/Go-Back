jest.mock('@/lib/database', () => ({
  getSetting: jest.fn(),
  setSetting: jest.fn(),
  getDatabase: jest.fn(),
}));

import { isValidPinFormat, matchesPin, setSecurityPin, getSecurityPin, verifySecurityPin } from '@/lib/securityPin';
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

    it('validates matching candidate pin', () => {
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
      (getSetting as jest.Mock).mockResolvedValueOnce('4321');
      const pin = await getSecurityPin();
      expect(pin).toBe('4321');
    });

    it('stores new 4-digit pin', async () => {
      await setSecurityPin('5678');
      expect(setSetting).toHaveBeenCalledWith('security.pin', '5678');
    });

    it('clears pin when null passed', async () => {
      await setSecurityPin(null);
      expect(setSetting).toHaveBeenCalledWith('security.pin', null);
    });

    it('throws error on invalid pin', async () => {
      await expect(setSecurityPin('abc')).rejects.toThrow();
    });

    it('verifies candidate against stored pin', async () => {
      (getSetting as jest.Mock).mockResolvedValueOnce('9999');
      const valid = await verifySecurityPin('9999');
      expect(valid).toBe(true);
    });
  });
});
