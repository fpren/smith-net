import { VectorClockState } from './types';

export function createClock(): VectorClockState {
  return {};
}

export function increment(clock: VectorClockState, deviceId: string): VectorClockState {
  return { ...clock, [deviceId]: (clock[deviceId] || 0) + 1 };
}

export function merge(a: VectorClockState, b: VectorClockState): VectorClockState {
  const result: VectorClockState = { ...a };
  for (const [deviceId, count] of Object.entries(b)) {
    result[deviceId] = Math.max(result[deviceId] || 0, count);
  }
  return result;
}

// Returns: -1 if a < b, 1 if a > b, 0 if concurrent
export function compare(a: VectorClockState, b: VectorClockState): -1 | 0 | 1 {
  const allKeys = new Set([...Object.keys(a), ...Object.keys(b)]);
  let aGreater = false;
  let bGreater = false;

  for (const key of allKeys) {
    const aVal = a[key] || 0;
    const bVal = b[key] || 0;
    if (aVal > bVal) aGreater = true;
    if (bVal > aVal) bGreater = true;
  }

  if (aGreater && !bGreater) return 1;
  if (bGreater && !aGreater) return -1;
  return 0; // concurrent
}

export function serialize(clock: VectorClockState): string {
  return JSON.stringify(clock);
}

export function deserialize(json: string): VectorClockState {
  return JSON.parse(json);
}
