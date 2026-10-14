import { createLocalId } from '../src/lib/id';
import { daysUntilISODate, isValidISODate } from '../src/lib/date';

describe('createLocalId', () => {
  it('includes the requested prefix', () => {
    expect(createLocalId('task')).toMatch(/^task_[a-z0-9]+_[a-z0-9]+$/);
  });

  it('creates distinct IDs for successive calls', () => {
    const ids = new Set(Array.from({ length: 100 }, () => createLocalId('test')));
    expect(ids.size).toBe(100);
  });
});

describe('isValidISODate', () => {
  it('accepts real ISO calendar dates', () => {
    expect(isValidISODate('2028-02-29')).toBe(true);
    expect(isValidISODate('2026-10-14')).toBe(true);
  });

  it('rejects malformed and impossible dates', () => {
    expect(isValidISODate('2027-02-29')).toBe(false);
    expect(isValidISODate('2026-13-01')).toBe(false);
    expect(isValidISODate('14-10-2026')).toBe(false);
    expect(isValidISODate('')).toBe(false);
  });
});

describe('daysUntilISODate', () => {
  it('calculates calendar days ahead and behind', () => {
    const now = new Date('2026-10-14T17:00:00.000Z');
    expect(daysUntilISODate('2026-10-15', now)).toBe(1);
    expect(daysUntilISODate('2026-10-14', now)).toBe(0);
    expect(daysUntilISODate('2026-10-12', now)).toBe(-2);
  });

  it('rejects invalid dates', () => {
    expect(() => daysUntilISODate('2026-02-30')).toThrow();
  });
});
