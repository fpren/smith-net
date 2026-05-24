import { describe, it, expect } from 'vitest';
import { adaptLayout, usableArea, Surface } from './surface';

describe('usableArea', () => {
  it('is the full box for rect and square', () => {
    expect(usableArea({ wIn: 5, hIn: 3, shape: 'rect' })).toBe(15);
    expect(usableArea({ wIn: 3, hIn: 3, shape: 'square' })).toBe(9);
  });
  it('is half the box for a circle (inscribed rectangle)', () => {
    expect(usableArea({ wIn: 2, hIn: 2, shape: 'circle' })).toBe(2);
  });
});

describe('adaptLayout', () => {
  it('gives a big rect the full layout (title, details, actions)', () => {
    const plan = adaptLayout({ wIn: 5, hIn: 5, shape: 'rect' });
    expect(plan.profile).toBe('full');
    expect(plan.slots).toContain('details');
    expect(plan.slots).toContain('actions');
    expect(plan.abbreviate).toBe(false);
  });

  it('renders the whole app (multiple feature modules) on a large surface', () => {
    const plan = adaptLayout({ wIn: 9, hIn: 6, shape: 'rect' });
    expect(plan.profile).toBe('app');
    expect(plan.mode).toBe('app');
    expect(plan.modules).toContain('comm');
    expect(plan.modules).toContain('map');
    expect(plan.modules.length).toBeGreaterThanOrEqual(4);
  });

  it('shows every module on a full letter page', () => {
    const plan = adaptLayout({ wIn: 8.5, hIn: 11, shape: 'rect' });
    expect(plan.mode).toBe('app');
    expect(plan.modules).toHaveLength(6);
  });

  it('keeps a 5x5 rect a single rich work card, not the app', () => {
    const plan = adaptLayout({ wIn: 5, hIn: 5, shape: 'rect' });
    expect(plan.profile).toBe('full');
    expect(plan.mode).toBe('card');
    expect(plan.modules).toHaveLength(0);
    expect(plan.slots).toContain('progress');
    expect(plan.slots).toContain('tasks');
  });

  it('collapses a 1x1 circle to just a status glyph + metric', () => {
    const plan = adaptLayout({ wIn: 1, hIn: 1, shape: 'circle' });
    expect(plan.profile).toBe('minimal');
    expect(plan.mode).toBe('glyph');
    expect(plan.slots).toEqual(['statusGlyph', 'metric']);
    expect(plan.abbreviate).toBe(true);
  });

  it('lets shape change the outcome at the same box size', () => {
    // 2x2 rect (area 4) clears the compact threshold; the same box as a circle
    // (usable 2) drops to glance -- roundness shows less.
    expect(adaptLayout({ wIn: 2, hIn: 2, shape: 'rect' }).profile).toBe('compact');
    expect(adaptLayout({ wIn: 2, hIn: 2, shape: 'circle' }).profile).toBe('glance');
  });

  it('never shows more slots on a smaller surface (monotonic)', () => {
    const minimal = adaptLayout({ wIn: 1, hIn: 1, shape: 'circle' }).slots.length;
    const glance = adaptLayout({ wIn: 2, hIn: 2, shape: 'circle' }).slots.length;
    const compact = adaptLayout({ wIn: 2, hIn: 2, shape: 'rect' }).slots.length;
    const full = adaptLayout({ wIn: 5, hIn: 5, shape: 'rect' }).slots.length;
    expect(minimal).toBeLessThanOrEqual(glance);
    expect(glance).toBeLessThanOrEqual(compact);
    expect(compact).toBeLessThanOrEqual(full);
  });

  it('always renders something (slots for a card, modules for the app)', () => {
    const cases: Surface[] = [
      { wIn: 0.75, hIn: 0.75, shape: 'circle' }, // tiny -> a card slot
      { wIn: 12, hIn: 8, shape: 'rect' }, // huge -> app modules
    ];
    for (const s of cases) {
      const plan = adaptLayout(s);
      expect(plan.slots.length + plan.modules.length).toBeGreaterThan(0);
    }
  });
});
