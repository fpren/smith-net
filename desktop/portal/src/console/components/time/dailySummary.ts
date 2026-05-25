// Pure math for the 8-hour daily summary bar. 16 half-hour slots = 8h target.
// Each slot is 0 (empty) / 1 (half, >= 15 min) / 2 (full, >= 30 min).
export const SLOT_COUNT = 16;
export const SLOT_SECONDS = 1800; // 30 min
export const TARGET_SECONDS = SLOT_COUNT * SLOT_SECONDS; // 8h

export function computeSlots(secondsWorked: number): number[] {
  const s = Math.max(0, secondsWorked);
  return Array.from({ length: SLOT_COUNT }, (_, i) => {
    const into = s - i * SLOT_SECONDS;
    if (into >= SLOT_SECONDS) return 2;
    if (into >= SLOT_SECONDS / 2) return 1;
    return 0;
  });
}

export function overtimeSeconds(secondsWorked: number): number {
  return Math.max(0, secondsWorked - TARGET_SECONDS);
}
