import { adaptLayout, PX_PER_IN, Surface } from './surface';
import { ModulePanel } from './modules';
import { SAMPLE_ON_SITE } from './sampleApp';

// The "app" render mode: a real application screen -- top bar + nav + a grid of
// feature modules -- scaled to app density (not one huge card). What the surface
// is big enough to show (the letter / tablet / desktop) is the whole app.

// Presence/GPS glyph -- was consoleTheme.glyphs.online (theme/consoleTheme.ts,
// deleted this task); same literal, now local since it has no other consumer.
const ONLINE = '((+))';

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
      className="bg-sn-bg-base text-sn-ink font-mono rounded-md border border-sn-line shadow-sm overflow-hidden flex flex-col"
    >
      {/* top bar */}
      <div className="flex items-center justify-between bg-sn-ink text-sn-bg-base px-[0.8em] py-[0.4em]">
        <span className="font-bold tracking-[0.2em] text-[0.85em]">SMITH NET</span>
        <span className="text-[0.72em] text-sn-status-online font-semibold">
          {ONLINE} {SAMPLE_ON_SITE} on site
        </span>
      </div>

      {/* body: nav rail + module grid */}
      <div className="flex flex-1 min-h-0">
        <nav className="border-r border-sn-line px-[0.6em] py-[0.6em] flex flex-col gap-[0.35em] text-[0.8em] shrink-0">
          {NAV.map((n, i) => (
            <span key={n} className={i === 0 ? 'text-sn-accent font-semibold' : 'text-sn-ink-muted'}>
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
