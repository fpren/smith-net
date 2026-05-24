import { consoleTheme } from '../../theme/consoleTheme';
import { adaptLayout, PX_PER_IN, Surface } from './surface';
import { ModulePanel } from './modules';
import { SAMPLE_ON_SITE } from './sampleApp';

// The "app" render mode: a real application screen -- top bar + nav + a grid of
// feature modules -- scaled to app density (not one huge card). What the surface
// is big enough to show (the letter / tablet / desktop) is the whole app.

interface Props {
  surface: Surface;
}

const NAV = ['Jobs', 'Comm', 'Map', 'Crew', 'Clients'];

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

export function AppShell({ surface }: Props) {
  const plan = adaptLayout(surface);
  const wPx = surface.wIn * PX_PER_IN;
  const hPx = surface.hIn * PX_PER_IN;
  // App density: small/normal type even on big surfaces (it is packed with modules).
  const fontPx = clamp(Math.min(wPx, hPx) / 38, 9, 14);

  return (
    <div
      style={{ width: wPx, height: hPx, fontSize: fontPx }}
      className="bg-console-bg text-console-text font-mono rounded-md border border-console-border shadow-sm overflow-hidden flex flex-col"
    >
      {/* top bar */}
      <div className="flex items-center justify-between bg-console-text text-console-bg px-[0.8em] py-[0.4em]">
        <span className="font-bold tracking-[0.2em] text-[0.85em]">SMITH NET</span>
        <span className="text-[0.72em] text-console-ok font-semibold">
          {consoleTheme.glyphs.online} {SAMPLE_ON_SITE} on site
        </span>
      </div>

      {/* body: nav rail + module grid */}
      <div className="flex flex-1 min-h-0">
        <nav className="border-r border-console-border px-[0.6em] py-[0.6em] flex flex-col gap-[0.35em] text-[0.8em] shrink-0">
          {NAV.map((n, i) => (
            <span key={n} className={i === 0 ? 'text-console-accent font-semibold' : 'text-console-text-muted'}>
              {n}
            </span>
          ))}
        </nav>
        <div
          className="flex-1 p-[0.6em] grid gap-[0.55em] min-h-0 overflow-hidden auto-rows-fr"
          style={{ gridTemplateColumns: `repeat(${plan.columns}, minmax(0, 1fr))` }}
        >
          {plan.modules.map((m) => (
            <ModulePanel key={m} id={m} />
          ))}
        </div>
      </div>
    </div>
  );
}
