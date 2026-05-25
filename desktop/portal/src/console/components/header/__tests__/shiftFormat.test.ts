import { describe, it, expect } from 'vitest';
import { formatElapsed, startOfTodayMs, sumClosedSecondsToday } from '../shiftFormat';

describe('formatElapsed', () => {
  it('zero-pads HH:MM:SS and clamps negatives', () => {
    expect(formatElapsed(0)).toBe('00:00:00');
    expect(formatElapsed(3661)).toBe('01:01:01');
    expect(formatElapsed(8 * 3600)).toBe('08:00:00');
    expect(formatElapsed(-5)).toBe('00:00:00');
  });
});

describe('startOfTodayMs', () => {
  it('returns local midnight for the given instant', () => {
    const noon = new Date();
    noon.setHours(12, 30, 0, 0);
    const start = new Date(startOfTodayMs(noon.getTime()));
    expect(start.getHours()).toBe(0);
    expect(start.getMinutes()).toBe(0);
    expect(start.getSeconds()).toBe(0);
  });
});

describe('sumClosedSecondsToday', () => {
  const todayStart = startOfTodayMs(new Date('2026-05-25T12:00:00').getTime());
  const at = (h: number, m = 0) => new Date(todayStart + (h * 3600 + m * 60) * 1000).toISOString();

  it('sums closed shifts within today', () => {
    const shifts = [
      { startedAt: at(8), endedAt: at(12) }, // 4h
      { startedAt: at(13), endedAt: at(17) }, // 4h
    ];
    expect(sumClosedSecondsToday(shifts, todayStart)).toBe(8 * 3600);
  });

  it('ignores open shifts (endedAt null)', () => {
    expect(sumClosedSecondsToday([{ startedAt: at(9), endedAt: null }], todayStart)).toBe(0);
  });

  it('clamps a shift that started before today to local midnight', () => {
    const shifts = [{ startedAt: new Date(todayStart - 2 * 3600 * 1000).toISOString(), endedAt: at(1) }]; // 10pm->1am, today portion 1h
    expect(sumClosedSecondsToday(shifts, todayStart)).toBe(3600);
  });
});
