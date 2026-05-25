import { describe, it, expect } from 'vitest';
import { computeSlots, SLOT_COUNT, overtimeSeconds } from '../dailySummary';

describe('dailySummary', () => {
  it('all empty at zero', () => {
    expect(computeSlots(0)).toEqual(new Array(SLOT_COUNT).fill(0));
    expect(overtimeSeconds(0)).toBe(0);
  });

  it('3.5h fills 7 full slots then empties (half-hour resolution)', () => {
    const slots = computeSlots(3.5 * 3600); // 7 * 1800s
    expect(slots.slice(0, 7)).toEqual(new Array(7).fill(2)); // 2 = full
    expect(slots[7]).toBe(0);
  });

  it('a partial slot reads as half (1)', () => {
    const slots = computeSlots(15 * 60); // 15 min = half of one 30-min slot
    expect(slots[0]).toBe(1);
  });

  it('caps the bar at 8h and reports overtime separately', () => {
    const slots = computeSlots(9 * 3600);
    expect(slots).toEqual(new Array(SLOT_COUNT).fill(2)); // all full
    expect(overtimeSeconds(9 * 3600)).toBe(3600); // 1h OT
  });
});
