// Pure math for the daily summary bar -- faithful to the APK TimeTrackingScreen:
// 8 hour-glyphs inside [ ], each glyph encoding that hour at half-hour resolution
// (full hour / half hour / empty). Past 8h, overtime half-hours re-color the bar
// left-to-right ("wrap-around overlay") on top of the shift fill.
export const HOUR_SLOTS = 8;
export const TARGET_MINUTES = HOUR_SLOTS * 60; // 480 = 8h

export type SlotTone = 'shift' | 'overtime' | 'empty';
export interface HourGlyph {
  glyph: '■' | '▣' | '□'; // full / half / empty
  tone: SlotTone;
}

const FULL = '■'; // black square
const HALF = '▣'; // square with fill
const EMPTY = '□'; // white square

export function computeHourGlyphs(secondsWorked: number): HourGlyph[] {
  const totalMin = Math.max(0, Math.floor(secondsWorked / 60));
  const halfSegs = Math.max(0, Math.floor(totalMin / 30));
  const shiftHalves = Math.min(halfSegs, 16);
  const otHalves = Math.max(0, halfSegs - 16);

  const out: HourGlyph[] = [];
  for (let h = 0; h < HOUR_SLOTS; h++) {
    const otFull = h * 2 + 2 <= otHalves;
    const otHalf = !otFull && h * 2 + 1 === otHalves;
    const shiftFull = h * 2 + 2 <= shiftHalves;
    const shiftHalf = !shiftFull && h * 2 + 1 === shiftHalves;
    if (otFull) out.push({ glyph: FULL, tone: 'overtime' });
    else if (otHalf) out.push({ glyph: HALF, tone: 'overtime' });
    else if (shiftFull) out.push({ glyph: FULL, tone: 'shift' });
    else if (shiftHalf) out.push({ glyph: HALF, tone: 'shift' });
    else out.push({ glyph: EMPTY, tone: 'empty' });
  }
  return out;
}

export function overtimeMinutes(secondsWorked: number): number {
  return Math.max(0, Math.floor(secondsWorked / 60) - TARGET_MINUTES);
}

export function isAtTarget(secondsWorked: number): boolean {
  return Math.floor(secondsWorked / 60) >= TARGET_MINUTES;
}
