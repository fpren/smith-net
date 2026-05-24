// Surface Lab -- the pure "what fits here" logic layer.
//
// This mirrors the SmithCore split: the CARTRIDGE decides what can be shown for
// a given surface (this file -- pure, deterministic, no React, no IO); the HOST
// only draws it. Being pure + deterministic, adaptLayout is exactly the kind of
// rule that could later move into the wasm core so every platform shares one
// "what-fits" decision.
//
// Key idea: a small surface shows a SINGLE feature (the work card); a large
// surface shows the WHOLE app (nav + several feature modules). The surface
// decides how much of the app surfaces, not just how big one card is.

export type SurfaceShape = 'rect' | 'square' | 'circle';

export interface Surface {
  wIn: number;
  hIn: number;
  shape: SurfaceShape;
}

export type LayoutProfile = 'app' | 'full' | 'compact' | 'glance' | 'minimal';

// How the surface renders: a single glyph, a single feature card, or the app.
export type RenderMode = 'glyph' | 'card' | 'app';

// Slots of the single work-card feature.
export type SlotId =
  | 'title'
  | 'status'
  | 'metric'
  | 'progress'
  | 'details'
  | 'tasks'
  | 'actions'
  | 'statusGlyph';

// The app's feature modules (its containers).
export type ModuleId = 'job' | 'jobs' | 'comm' | 'map' | 'crew' | 'clients';
export const ALL_MODULES: ModuleId[] = ['job', 'jobs', 'comm', 'map', 'crew', 'clients'];

export interface LayoutPlan {
  profile: LayoutProfile;
  mode: RenderMode;
  slots: SlotId[]; // single-feature card (glyph/card modes)
  modules: ModuleId[]; // feature modules (app mode)
  columns: number; // grid columns (app mode)
  orientation: 'stack' | 'row';
  padScale: 1 | 2 | 3;
  abbreviate: boolean;
}

/** Simulated physical size on screen (px per inch). */
export const PX_PER_IN = 56;

/** CSS reference px-per-inch (browsers report ~96 CSS px per inch). Used to turn
 *  a real measured container into a Surface so the live viewport drives the same
 *  adaptLayout decision as the sandbox. */
export const CSS_PX_PER_IN = 96;

/** Build a Surface from a real measured container (always rectangular). */
export function surfaceFromPx(widthPx: number, heightPx: number): Surface {
  return { wIn: widthPx / CSS_PX_PER_IN, hIn: heightPx / CSS_PX_PER_IN, shape: 'rect' };
}

/**
 * Usable content area in in^2. A circle only fits its inscribed rectangle
 * (~half the bounding box), so the same box "shows less" when round.
 */
export function usableArea(s: Surface): number {
  const box = s.wIn * s.hIn;
  return s.shape === 'circle' ? box * 0.5 : box;
}

/** Decide the layout for a surface. Pure function of the surface only. */
export function adaptLayout(s: Surface): LayoutPlan {
  const a = usableArea(s);

  // Large surface -> the whole app: nav + several feature modules.
  if (a >= 30) {
    const moduleCount = a >= 90 ? 6 : a >= 55 ? 5 : 4;
    const columns = s.wIn >= 9 ? 3 : s.wIn >= 5.5 ? 2 : 1;
    return {
      profile: 'app',
      mode: 'app',
      slots: [],
      modules: ALL_MODULES.slice(0, moduleCount),
      columns,
      orientation: 'stack',
      padScale: 3,
      abbreviate: false,
    };
  }

  // Medium surface -> one rich work card (the single feature).
  if (a >= 10) {
    return {
      profile: 'full',
      mode: 'card',
      slots: ['title', 'status', 'metric', 'progress', 'details', 'tasks', 'actions'],
      modules: [],
      columns: 1,
      orientation: 'stack',
      padScale: 3,
      abbreviate: false,
    };
  }
  if (a >= 4) {
    return {
      profile: 'compact',
      mode: 'card',
      slots: ['title', 'status', 'metric', 'actions'],
      modules: [],
      columns: 1,
      orientation: 'stack',
      padScale: 2,
      abbreviate: false,
    };
  }
  if (a >= 1.5) {
    return {
      profile: 'glance',
      mode: 'card',
      slots: ['title', 'metric', 'statusGlyph'],
      modules: [],
      columns: 1,
      orientation: 'row',
      padScale: 1,
      abbreviate: true,
    };
  }
  return {
    profile: 'minimal',
    mode: 'glyph',
    slots: ['statusGlyph', 'metric'],
    modules: [],
    columns: 1,
    orientation: 'row',
    padScale: 1,
    abbreviate: true,
  };
}
