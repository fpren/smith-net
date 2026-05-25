import { computeSlots, overtimeSeconds, TARGET_SECONDS } from './dailySummary';
import { formatElapsed } from '../header/shiftFormat';

const GLYPH = ['-', '+', '#']; // empty / half / full (ASCII, no emoji)

export function DailySummaryBar({ secondsWorked }: { secondsWorked: number }) {
  const slots = computeSlots(secondsWorked);
  const ot = overtimeSeconds(secondsWorked);
  const over = ot > 0;
  const worked = formatElapsed(secondsWorked).slice(0, 5); // HH:MM
  return (
    <div className="font-mono text-sm flex items-center gap-2" data-testid="daily-bar">
      <span className={over ? 'text-console-warn' : 'text-console-accent'}>
        [{slots.map((s) => GLYPH[s]).join('')}]
      </span>
      <span className={over ? 'text-console-warn tabular-nums' : 'text-console-text-muted tabular-nums'}>
        {worked} / {formatElapsed(TARGET_SECONDS).slice(0, 5)}
        {over ? ` (+${formatElapsed(ot).slice(0, 5)} OT)` : ''}
      </span>
    </div>
  );
}
