import { describe, it, expect } from 'vitest';
import { computeHourGlyphs, overtimeMinutes, isAtTarget, HOUR_SLOTS } from '../dailySummary';

describe('dailySummary (APK 8-hour-glyph bar)', () => {
  it('all empty at zero', () => {
    const g = computeHourGlyphs(0);
    expect(g).toHaveLength(HOUR_SLOTS);
    expect(g.every((s) => s.glyph === '□' && s.tone === 'empty')).toBe(true);
    expect(isAtTarget(0)).toBe(false);
    expect(overtimeMinutes(0)).toBe(0);
  });

  it('3.5h -> 3 full-hour glyphs + 1 half-hour glyph, rest empty', () => {
    const g = computeHourGlyphs(3.5 * 3600);
    expect(g.slice(0, 3).every((s) => s.glyph === '■' && s.tone === 'shift')).toBe(true);
    expect(g[3]).toEqual({ glyph: '▣', tone: 'shift' });
    expect(g[4].glyph).toBe('□');
  });

  it('8h -> all full shift glyphs, at target, no overtime', () => {
    const g = computeHourGlyphs(8 * 3600);
    expect(g.every((s) => s.glyph === '■' && s.tone === 'shift')).toBe(true);
    expect(isAtTarget(8 * 3600)).toBe(true);
    expect(overtimeMinutes(8 * 3600)).toBe(0);
  });

  it('10h -> overtime re-colors the first hours red (overlay), reports OT minutes', () => {
    const g = computeHourGlyphs(10 * 3600); // halfSegs=20, shiftHalves=16, otHalves=4
    expect(g[0]).toEqual({ glyph: '■', tone: 'overtime' });
    expect(g[1]).toEqual({ glyph: '■', tone: 'overtime' });
    expect(g[2].tone).toBe('shift');
    expect(overtimeMinutes(10 * 3600)).toBe(120);
  });
});
