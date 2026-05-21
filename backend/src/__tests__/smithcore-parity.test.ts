/*
 * smithcore-parity.test.ts -- the M1 merge gate.
 *
 * Proves the wasm ROM (core/dist/smithcore.wasm) computes vector-clock merge /
 * compare identically to the legacy TS implementation it replaces, and that its
 * bundled SHA-256 matches Node's crypto byte-for-byte. If this ever goes red,
 * the cross-platform Ledger could diverge -- do not merge.
 */
import * as fs from 'fs';
import * as crypto from 'crypto';
import type { VectorClockState } from '../types';
import { mergeLocal, compareLocal, merge, compare } from '../vectorClock';
import {
  initSmithCore,
  smithCoreWasmPath,
  vclockMerge,
  vclockCompare,
  sha256,
} from '../core/smithCore';

// Strip zero-valued entries: the ROM canonicalizes {x:0} === {}, so we compare
// clocks for SEMANTIC equality (the wire-level byte canonicalization is the
// core's job and is exercised separately).
function norm(c: VectorClockState): VectorClockState {
  const out: VectorClockState = {};
  for (const k of Object.keys(c)) if (c[k] !== 0) out[k] = c[k];
  return out;
}

beforeAll(async () => {
  await initSmithCore();
});

describe('ROM identity', () => {
  it('loaded wasm matches the recorded ROM hash stamp', () => {
    const wasm = fs.readFileSync(smithCoreWasmPath());
    const actual = crypto.createHash('sha256').update(wasm).digest('hex');
    const stamp = fs
      .readFileSync(smithCoreWasmPath() + '.sha256', 'utf8')
      .trim()
      .split(/\s+/)[0];
    expect(actual).toBe(stamp);
  });
});

describe('vclock parity: golden cases', () => {
  const cases: Array<[VectorClockState, VectorClockState]> = [
    [{}, {}],
    [{ a: 1 }, {}],
    [{}, { a: 1 }],
    [{ a: 1 }, { a: 1 }],
    [{ a: 1 }, { a: 2 }],
    [{ a: 2, b: 1 }, { a: 1, b: 2 }], // concurrent
    [{ a: 3, b: 3 }, { a: 1, b: 1 }], // a dominates
    [{ a: 1, ab: 1 }, { ab: 2 }],     // prefix-collision ids (a < ab)
    [{ 'café': 3 }, { 'café': 1, x: 5 }], // multibyte utf-8 id
    [{ device0: 7, device1: 2 }, { device1: 9, device2: 1 }],
  ];

  it.each(cases)('merge(%j, %j) matches legacy', (a, b) => {
    expect(norm(vclockMerge(a, b))).toEqual(norm(mergeLocal(a, b)));
  });

  it.each(cases)('compare(%j, %j) matches legacy', (a, b) => {
    expect(vclockCompare(a, b)).toBe(compareLocal(a, b));
  });
});

describe('vclock parity: randomized fuzz', () => {
  const ids = ['a', 'ab', 'abc', 'd0', 'd1', 'd2', 'node-7', 'café', 'zz'];
  function randClock(rng: () => number): VectorClockState {
    const c: VectorClockState = {};
    for (const id of ids) {
      if (rng() < 0.5) c[id] = 1 + Math.floor(rng() * 25);
    }
    return c;
  }
  // Deterministic PRNG so a failure is reproducible.
  let seed = 0x9e3779b9;
  const rng = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return seed / 0x100000000;
  };

  it('merge + compare agree with legacy over 2000 random pairs', () => {
    for (let i = 0; i < 2000; i++) {
      const a = randClock(rng);
      const b = randClock(rng);
      expect(norm(vclockMerge(a, b))).toEqual(norm(mergeLocal(a, b)));
      expect(vclockCompare(a, b)).toBe(compareLocal(a, b));
      // reverse direction must also match legacy (merge commutative, compare antisymmetric)
      expect(norm(vclockMerge(b, a))).toEqual(norm(mergeLocal(b, a)));
      expect(vclockCompare(b, a)).toBe(compareLocal(b, a));
    }
  });
});

describe('canonicalization (intentional, documented divergence)', () => {
  it('treats explicit zero counts as absent', () => {
    // Legacy keeps {x:0}; the ROM drops it. Both are semantically equal and the
    // ROM form is adopted as canonical.
    expect(vclockCompare({ x: 0 }, {})).toBe(0);
    expect(norm(vclockMerge({ x: 0 }, {}))).toEqual({});
  });
});

describe('delegation: public merge/compare route through the ROM when enabled', () => {
  const prev = process.env.SMITHCORE_ENABLED;
  beforeAll(() => { process.env.SMITHCORE_ENABLED = '1'; });
  afterAll(() => {
    if (prev === undefined) delete process.env.SMITHCORE_ENABLED;
    else process.env.SMITHCORE_ENABLED = prev;
  });

  it('public API matches legacy with the flag on and ROM ready', () => {
    const a = { node0: 4, node1: 2 };
    const b = { node1: 5, node2: 1 };
    expect(norm(merge(a, b))).toEqual(norm(mergeLocal(a, b)));
    expect(compare(a, b)).toBe(compareLocal(a, b));
  });
});

describe('sha256 parity vs Node crypto', () => {
  const lengths = [0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000];
  it.each(lengths)('matches for a %d-byte buffer', (len) => {
    const data = crypto.randomBytes(len);
    const got = sha256(data).toString('hex');
    const exp = crypto.createHash('sha256').update(data).digest('hex');
    expect(got).toBe(exp);
  });
});
