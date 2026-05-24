import { ReactNode, useState } from 'react';
import { clsx } from 'clsx';
import { AdaptiveSurface } from './AdaptiveSurface';
import { adaptLayout, PX_PER_IN, Surface, SurfaceShape } from './surface';
import { SAMPLE_JOB } from './sampleJob';

// The interactive proof: the SAME job content re-fits itself to whatever surface
// it sits on. Logic (what fits) is a pure function -- the cartridge decides; the
// surface only changes how it is drawn -- the host.

interface Preset {
  label: string;
  surface: Surface;
}

// A spread of real containers, tiny -> page -> desktop, including print formats.
const PRESETS: Preset[] = [
  { label: 'Watch 1x1 circle', surface: { wIn: 1, hIn: 1, shape: 'circle' } },
  { label: 'Dial 2x2 circle', surface: { wIn: 2, hIn: 2, shape: 'circle' } },
  { label: 'Label 3x2 (print)', surface: { wIn: 3, hIn: 2, shape: 'rect' } },
  { label: 'Receipt 2.5x6 (print)', surface: { wIn: 2.5, hIn: 6, shape: 'rect' } },
  { label: 'Phone 3x6', surface: { wIn: 3, hIn: 6, shape: 'rect' } },
  { label: 'Square 4x4', surface: { wIn: 4, hIn: 4, shape: 'square' } },
  { label: 'Tablet 7x9', surface: { wIn: 7, hIn: 9, shape: 'rect' } },
  { label: 'Letter 8.5x11 (print)', surface: { wIn: 8.5, hIn: 11, shape: 'rect' } },
  { label: 'Desktop 12x7', surface: { wIn: 12, hIn: 7, shape: 'rect' } },
];

const SHAPES: SurfaceShape[] = ['rect', 'square', 'circle'];
const GALLERY_TILE = 190;

const BTN_BASE = 'font-mono text-xs px-3 py-1.5 border transition-all';
const BTN_INACTIVE =
  'bg-console-surface text-console-text border-console-border hover:border-console-accent hover:text-console-accent hover:shadow-md';

function round2(n: number): number {
  return Math.round(n * 100) / 100;
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border border-console-border bg-console-surface shadow-sm">
      <div className="bg-console-text text-console-bg text-[10px] font-semibold uppercase tracking-[0.2em] px-3 py-1.5">
        {title}
      </div>
      <div className="p-4">{children}</div>
    </section>
  );
}

/** A gallery thumbnail: an adapted card scaled down (only) to fit a tile. */
function GalleryTile({ preset }: { preset: Preset }) {
  const wPx = preset.surface.wIn * PX_PER_IN;
  const hPx = preset.surface.hIn * PX_PER_IN;
  const scale = Math.min(1, GALLERY_TILE / wPx, GALLERY_TILE / hPx);
  return (
    <div className="flex flex-col items-center gap-2">
      <div style={{ width: wPx * scale, height: hPx * scale }}>
        <div style={{ transform: `scale(${scale})`, transformOrigin: 'top left', width: wPx, height: hPx }}>
          <AdaptiveSurface surface={preset.surface} job={SAMPLE_JOB} />
        </div>
      </div>
      <div className="text-[10px] text-console-text-muted text-center">
        {preset.label}
        <span className="ml-1 bg-console-accent text-white px-1 rounded-sm">
          {adaptLayout(preset.surface).profile}
        </span>
      </div>
    </div>
  );
}

export function SurfaceLabContent() {
  const [surface, setSurface] = useState<Surface>({ wIn: 5, hIn: 3, shape: 'rect' });
  const plan = adaptLayout(surface);
  const square = surface.shape !== 'rect';
  const isPreset = (p: Preset) =>
    p.surface.wIn === surface.wIn && p.surface.hIn === surface.hIn && p.surface.shape === surface.shape;

  function setShape(shape: SurfaceShape) {
    if (shape !== 'rect') {
      const size = Math.max(surface.wIn, surface.hIn);
      setSurface({ wIn: size, hIn: size, shape });
    } else {
      setSurface({ ...surface, shape });
    }
  }

  function setSize(which: 'w' | 'h', value: number) {
    if (square) setSurface({ ...surface, wIn: value, hIn: value });
    else if (which === 'w') setSurface({ ...surface, wIn: value });
    else setSurface({ ...surface, hIn: value });
  }

  return (
    <div className="font-mono text-console-text max-w-5xl">
      <header className="border-b-2 border-console-text pb-3">
        <h1 className="text-2xl font-bold tracking-tight">SURFACE LAB</h1>
        <p className="text-xs text-console-text-muted mt-1 max-w-2xl">
          One job, many containers. A pure adaptLayout(surface) decides what fits (the cartridge);
          the renderer scales + draws it to fill the surface (the host). Reshape it -- watch, label,
          receipt, phone, page, desktop -- and the same content re-fits.
        </p>
      </header>

      <div className="mt-5 grid gap-5 lg:grid-cols-2">
        {/* Controls */}
        <Panel title="Surface">
          <div className="text-[10px] uppercase tracking-widest text-console-text-muted mb-2">Shape</div>
          <div className="flex flex-wrap gap-2 mb-4">
            {SHAPES.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setShape(s)}
                className={clsx(
                  BTN_BASE,
                  surface.shape === s ? 'bg-console-accent text-white border-console-accent shadow-sm' : BTN_INACTIVE,
                )}
              >
                {s}
              </button>
            ))}
          </div>

          <div className="flex flex-col gap-3 text-xs">
            <label className="flex items-center gap-3">
              <span className="w-14 text-console-text-muted">{square ? 'size' : 'width'}</span>
              <input
                type="range"
                min={0.5}
                max={14}
                step={0.25}
                value={surface.wIn}
                onChange={(e) => setSize('w', Number(e.target.value))}
                className="flex-1 accent-console-accent"
              />
              <span className="tabular-nums w-14 text-right">{round2(surface.wIn)} in</span>
            </label>
            {!square && (
              <label className="flex items-center gap-3">
                <span className="w-14 text-console-text-muted">height</span>
                <input
                  type="range"
                  min={0.5}
                  max={14}
                  step={0.25}
                  value={surface.hIn}
                  onChange={(e) => setSize('h', Number(e.target.value))}
                  className="flex-1 accent-console-accent"
                />
                <span className="tabular-nums w-14 text-right">{round2(surface.hIn)} in</span>
              </label>
            )}
          </div>

          <div className="text-[10px] uppercase tracking-widest text-console-text-muted mt-4 mb-2">Containers</div>
          <div className="flex flex-wrap gap-2">
            {PRESETS.map((p) => (
              <button
                key={p.label}
                type="button"
                onClick={() => setSurface(p.surface)}
                className={clsx(
                  BTN_BASE,
                  isPreset(p) ? 'bg-console-text text-console-bg border-console-text shadow-sm' : BTN_INACTIVE,
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
        </Panel>

        {/* Live + plan */}
        <Panel title="Live">
          <div className="flex flex-wrap items-start gap-5">
            <div className="bg-console-bg p-4 flex items-center justify-center min-h-[180px] min-w-[180px] overflow-auto max-h-[520px]">
              <AdaptiveSurface surface={surface} job={SAMPLE_JOB} />
            </div>
            <div className="text-xs leading-relaxed">
              <div className="text-[10px] uppercase tracking-widest text-console-text-muted mb-1">
                Decided by adaptLayout
              </div>
              <div>
                profile{' '}
                <span className="bg-console-accent text-white px-1.5 py-0.5 rounded-sm font-semibold">
                  {plan.profile}
                </span>
              </div>
              <div className="mt-1 text-console-text-muted">slots: {plan.slots.join(', ')}</div>
              <div className="text-console-text-muted">orient: {plan.orientation}</div>
              <div className="text-console-text-muted">abbrev: {String(plan.abbreviate)}</div>
            </div>
          </div>
        </Panel>
      </div>

      {/* Gallery */}
      <div className="mt-5">
        <Panel title="Same job across containers">
          <div className="bg-console-bg p-5 flex flex-wrap items-end gap-8">
            {PRESETS.map((p) => (
              <GalleryTile key={p.label} preset={p} />
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}
