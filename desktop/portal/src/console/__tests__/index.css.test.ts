import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

// String-check on the generated stylesheet source: cheapest way to assert the
// panel slide-in motion (Plan 5 Task 5, spec section 9) is defined with a
// reduced-motion guard, without spinning up a full CSS parser/animation
// runtime in jsdom.
const css = readFileSync(resolve(__dirname, '../index.css'), 'utf-8');

describe('index.css panel motion (Design System v2 Plan 5 Task 5)', () => {
  it('defines the panelIn keyframes sliding in from the right', () => {
    expect(css).toMatch(/@keyframes\s+panelIn\s*{/);
    expect(css).toMatch(/from\s*{\s*opacity:\s*0;\s*transform:\s*translateX\(12px\);?\s*}/);
  });

  it('defines a .panel-in class using the spec timing (200-250ms, the sn ease curve)', () => {
    expect(css).toMatch(/\.panel-in\s*{[^}]*animation:\s*panelIn\s+\.22s\s+cubic-bezier\(\.2,\.8,\.2,1\)\s+both;?[^}]*}/);
  });

  it('guards .panel-in under prefers-reduced-motion: reduce', () => {
    const reducedMotionBlock = css.match(/@media \(prefers-reduced-motion: reduce\)\s*{[^}]*}/g)?.join('\n') ?? '';
    expect(reducedMotionBlock).toMatch(/\.panel-in/);
  });
});
