// @vitest-environment node
import { describe, it, expect, beforeAll } from 'vitest';
import { fileURLToPath } from 'node:url';
import { webcrypto } from 'node:crypto';
import * as fs from 'node:fs';
import { instantiate, isSmithCoreReady, sha256, vclockMerge, vclockCompare } from './smithCore';
import type { VectorClockState } from './smithCore';

// Test-only filesystem + crypto (Vitest runs in Node, not the browser).
// Paths resolve from this file via import.meta.url so they do not depend on
// the process cwd. Describe blocks below test the wasm runtime by injecting
// romBytes() via instantiate(), never fetch().
const wasmUrl = new URL('../../../public/smithcore.wasm', import.meta.url);
const stampUrl = new URL('../../../../../core/dist/smithcore.wasm.sha256', import.meta.url);
const romBytes = () => new Uint8Array(fs.readFileSync(fileURLToPath(wasmUrl)));

const toHex = (b: Uint8Array) =>
  Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
const refSha256 = async (b: Uint8Array) =>
  toHex(new Uint8Array(await webcrypto.subtle.digest('SHA-256', b as unknown as NodeJS.BufferSource)));

beforeAll(async () => {
  await instantiate(romBytes());
});

describe('ROM identity', () => {
  it('portal wasm matches the recorded ROM hash stamp', async () => {
    const stamp = fs
      .readFileSync(fileURLToPath(stampUrl), 'utf8')
      .trim()
      .split(/\s+/)[0];
    expect(await refSha256(romBytes())).toBe(stamp);
  });
});

describe('host load + ABI', () => {
  it('instantiates and reports ready', () => {
    expect(isSmithCoreReady()).toBe(true);
  });
});

describe('sha256 parity vs WebCrypto', () => {
  const lengths = [0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000];
  it.each(lengths)('matches the reference for a %d-byte buffer', async (len) => {
    const data = new Uint8Array(len);
    for (let i = 0; i < len; i++) data[i] = (i * 31 + 7) & 0xff;
    expect(toHex(sha256(data))).toBe(await refSha256(data));
  });
});

// Local references (union-max merge, causal compare) so parity does not depend
// on backend code. Zero counts are dropped, matching the ROM's canonicalization.
function norm(c: VectorClockState): VectorClockState {
  const out: VectorClockState = {};
  for (const k of Object.keys(c)) if (c[k] !== 0) out[k] = c[k];
  return out;
}
function mergeRef(a: VectorClockState, b: VectorClockState): VectorClockState {
  const out: VectorClockState = {};
  for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
    out[k] = Math.max(a[k] ?? 0, b[k] ?? 0);
  }
  return norm(out);
}
function compareRef(a: VectorClockState, b: VectorClockState): -1 | 0 | 1 {
  let aG = false;
  let bG = false;
  for (const k of new Set([...Object.keys(a), ...Object.keys(b)])) {
    const x = a[k] ?? 0;
    const y = b[k] ?? 0;
    if (x > y) aG = true;
    else if (x < y) bG = true;
  }
  if (aG && bG) return 0;
  if (aG) return 1;
  if (bG) return -1;
  return 0;
}

describe('vclock parity: golden cases', () => {
  const cases: Array<[VectorClockState, VectorClockState]> = [
    [{}, {}],
    [{ a: 1 }, {}],
    [{}, { a: 1 }],
    [{ a: 1 }, { a: 1 }],
    [{ a: 1 }, { a: 2 }],
    [{ a: 2, b: 1 }, { a: 1, b: 2 }],
    [{ a: 3, b: 3 }, { a: 1, b: 1 }],
    [{ a: 1, ab: 1 }, { ab: 2 }],
    [{ 'café': 3 }, { 'café': 1, x: 5 }],
    [{ device0: 7, device1: 2 }, { device1: 9, device2: 1 }],
  ];
  it.each(cases)('merge(%j, %j)', (a, b) => {
    expect(norm(vclockMerge(a, b))).toEqual(mergeRef(a, b));
  });
  it.each(cases)('compare(%j, %j)', (a, b) => {
    expect(vclockCompare(a, b)).toBe(compareRef(a, b));
  });
  it('drops explicit zero counts (canonicalization)', () => {
    expect(vclockCompare({ x: 0 }, {})).toBe(0);
    expect(norm(vclockMerge({ x: 0 }, {}))).toEqual({});
  });
});

describe('vclock parity: randomized fuzz', () => {
  const ids = ['a', 'ab', 'abc', 'd0', 'd1', 'd2', 'node-7', 'café', 'zz'];
  let seed = 0x9e3779b9;
  const rng = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 0x100000000;
  };
  const randClock = (): VectorClockState => {
    const c: VectorClockState = {};
    for (const id of ids) if (rng() < 0.5) c[id] = 1 + Math.floor(rng() * 25);
    return c;
  };
  it('merge + compare agree with the reference over 2000 pairs', () => {
    for (let i = 0; i < 2000; i++) {
      const a = randClock();
      const b = randClock();
      expect(norm(vclockMerge(a, b))).toEqual(mergeRef(a, b));
      expect(vclockCompare(a, b)).toBe(compareRef(a, b));
      expect(norm(vclockMerge(b, a))).toEqual(mergeRef(b, a));
      expect(vclockCompare(b, a)).toBe(compareRef(b, a));
    }
  });
});
