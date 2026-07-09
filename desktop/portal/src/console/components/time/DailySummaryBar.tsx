import { computeHourGlyphs, isAtTarget } from './dailySummary';
import { formatElapsed } from '../header/shiftFormat';

// Mirrors the APK daily summary row: TODAY  [glyphs]  H:MM / 8:00. The 8 hour
// glyphs are sage (accent) until 8h, green (ok) at/above; overtime re-colors the
// leading hours red (warn). The total turns red once at/over the 8h target.
export function DailySummaryBar({ secondsWorked }: { secondsWorked: number }) {
  const glyphs = computeHourGlyphs(secondsWorked);
  const atTarget = isAtTarget(secondsWorked);
  const worked = formatElapsed(secondsWorked).slice(0, 5); // HH:MM (includes overtime)

  const toneClass = (tone: string) =>
    tone === 'overtime'
      ? 'text-sn-attention'
      : tone === 'shift'
        ? atTarget
          ? 'text-sn-status-online'
          : 'text-sn-accent'
        : 'text-sn-ink-muted';

  return (
    <div className="font-mono text-sm flex items-center justify-center gap-6" data-testid="daily-bar">
      <span className="text-[11px] uppercase tracking-wide text-sn-ink-muted">Today</span>
      <span className="tabular-nums">
        <span className="text-sn-ink-muted">[</span>
        {glyphs.map((g, i) => (
          <span key={i} className={toneClass(g.tone)}>{g.glyph}</span>
        ))}
        <span className="text-sn-ink-muted">]</span>
      </span>
      <span className={`tabular-nums ${atTarget ? 'text-sn-attention' : 'text-sn-ink'}`}>
        {worked} / 8:00
      </span>
    </div>
  );
}
