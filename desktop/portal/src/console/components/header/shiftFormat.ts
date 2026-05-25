// desktop/portal/src/console/components/header/shiftFormat.ts
//
// Pure shift-time helpers shared by the running shift clock and the day total.
// No React, no IO -- unit-testable.

/** Format a non-negative elapsed-seconds count as HH:MM:SS (zero-padded). */
export function formatElapsed(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

/** Start-of-today (local midnight) in epoch ms for the given instant. */
export function startOfTodayMs(now: number = Date.now()): number {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Sum seconds worked TODAY across CLOSED shifts (endedAt != null), clamping each
 *  shift to [todayStartMs, its end]. Open shifts are ignored (off-clock there is
 *  no running shift; on-clock the day total is not shown). */
export function sumClosedSecondsToday(
  shifts: Array<{ startedAt: string | null; endedAt: string | null }>,
  todayStartMs: number,
): number {
  let total = 0;
  for (const s of shifts) {
    if (!s.startedAt || !s.endedAt) continue;
    const from = Math.max(new Date(s.startedAt).getTime(), todayStartMs);
    const to = new Date(s.endedAt).getTime();
    if (to > from) total += (to - from) / 1000;
  }
  return total;
}
