import { formatWeekdays, isValidFocusDuration, isValidTimeOfDay, parseWeekdays } from '../src/lib/scheduleLogic';

describe('focus schedule time validation', () => {
  it('accepts a valid 24-hour time', () => {
    expect(isValidTimeOfDay('00:00')).toBe(true);
    expect(isValidTimeOfDay('09:05')).toBe(true);
    expect(isValidTimeOfDay('23:59')).toBe(true);
  });

  it('rejects malformed or impossible times', () => {
    for (const value of ['24:00', '12:60', '9:05', '12.30', '', 'aa:bb']) {
      expect(isValidTimeOfDay(value)).toBe(false);
    }
  });
});

describe('focus schedule weekday helpers', () => {
  it('parses, validates, deduplicates and sorts weekday values', () => {
    expect(parseWeekdays('[5,1,1,8,-1,"2"]')).toEqual([1, 5]);
    expect(parseWeekdays('not json')).toEqual([]);
    expect(parseWeekdays('{}')).toEqual([]);
  });

  it('labels common weekly patterns', () => {
    expect(formatWeekdays([1,2,3,4,5])).toBe('Weekdays');
    expect(formatWeekdays([0,6])).toBe('Weekends');
    expect(formatWeekdays([0,1,2,3,4,5,6])).toBe('Every day');
    expect(formatWeekdays([2,4])).toBe('Tue, Thu');
    expect(formatWeekdays([])).toBe('');
  });
});

describe('focus schedule duration validation', () => {
  it('accepts whole minutes between 5 and 180', () => {
    expect(isValidFocusDuration(5)).toBe(true);
    expect(isValidFocusDuration(180)).toBe(true);
    expect(isValidFocusDuration(25)).toBe(true);
  });

  it('rejects out-of-range and fractional values', () => {
    expect(isValidFocusDuration(4)).toBe(false);
    expect(isValidFocusDuration(181)).toBe(false);
    expect(isValidFocusDuration(25.5)).toBe(false);
  });
});
