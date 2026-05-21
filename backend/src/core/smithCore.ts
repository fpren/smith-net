/*
 * smithCore.ts -- Node/Hetzner host binding for the SmithNet "ROM"
 * (core/dist/smithcore.wasm). The backend loads the EXACT same .wasm bytes the
 * Android/iOS/Pi/browser shells load, so the Ledger hash and vector-clock
 * merge are byte-identical across every host (the determinism moat, NFR-D1..D5).
 *
 * The host owns IO/time/keys; this module only stages bytes into the wasm
 * linear memory, calls an export, and reads bytes back. See core/include/
 * smithcore.h for the ABI and the canonical vector-clock wire format.
 *
 * Lifecycle: call initSmithCore() once at server boot and await it BEFORE
 * serving requests. The vector-clock delegation in ./../vectorClock.ts only
 * activates once isSmithCoreReady() is true, so a missing/old wasm degrades to
 * the legacy TS path instead of crashing the process.
 */
import * as fs from 'fs';
import * as path from 'path';
import type { VectorClockState } from '../types';

// Node provides the WebAssembly global at runtime; type only what we use so we
// do not have to add the DOM lib to a backend tsconfig.
interface WasmMemory { buffer: ArrayBuffer; }
const WA: {
  instantiate(bytes: Uint8Array, imports: object): Promise<{ instance: { exports: unknown } }>;
} = (globalThis as unknown as { WebAssembly: any }).WebAssembly;

interface CoreExports {
  memory: WasmMemory;
  sc_version(): number;
  sc_reset(): void;
  sc_alloc(len: number): number;
  sc_vclock_merge(a: number, al: number, b: number, bl: number, o: number, oc: number): number;
  sc_vclock_compare(a: number, al: number, b: number, bl: number): number;
  sc_vclock_canon(i: number, il: number, o: number, oc: number): number;
  sc_sha256(d: number, l: number, o: number): number;
}

const SC_ERR = -1;
const SC_CMP_ERR = 2;
const EXPECTED_ABI = 1;

let _ex: CoreExports | null = null;

export function smithCoreWasmPath(): string {
  return (
    process.env.SMITHCORE_WASM_PATH ||
    path.resolve(__dirname, '../../../core/dist/smithcore.wasm')
  );
}

/** Load + instantiate the ROM once. Idempotent. */
export async function initSmithCore(): Promise<void> {
  if (_ex) return;
  const bytes = fs.readFileSync(smithCoreWasmPath());
  const { instance } = await WA.instantiate(bytes, {});
  const ex = instance.exports as unknown as CoreExports;
  const abi = ex.sc_version();
  if (abi !== EXPECTED_ABI) {
    throw new Error(`smithcore ABI mismatch: wasm=${abi} expected=${EXPECTED_ABI}`);
  }
  _ex = ex;
}

export function isSmithCoreReady(): boolean {
  return _ex !== null;
}

function core(): CoreExports {
  if (!_ex) throw new Error('smithcore not initialized; call initSmithCore() at boot');
  return _ex;
}

function mem(): Uint8Array {
  return new Uint8Array(core().memory.buffer);
}

function stage(buf: Buffer): number {
  const p = core().sc_alloc(buf.length);
  if (p === 0 && buf.length > 0) throw new Error('smithcore arena OOM');
  mem().set(buf, p);
  return p;
}

/* --- canonical vector-clock wire codec (mirrors core/src/vclock.c) --- */

/** Encode a clock to canonical wire form: entries sorted by id bytes, zeros omitted. */
export function encodeClock(clock: VectorClockState): Buffer {
  const entries = Object.keys(clock)
    .map((id) => ({ id: Buffer.from(id, 'utf8'), count: clock[id] }))
    .filter((e) => e.count !== 0)
    .sort((a, b) => Buffer.compare(a.id, b.id));
  let size = 2;
  for (const e of entries) size += 2 + e.id.length + 4;
  const buf = Buffer.allocUnsafe(size);
  buf.writeUInt16LE(entries.length, 0);
  let off = 2;
  for (const e of entries) {
    if (e.id.length > 0xffff) throw new Error('device id exceeds 65535 bytes');
    buf.writeUInt16LE(e.id.length, off); off += 2;
    e.id.copy(buf, off); off += e.id.length;
    buf.writeUInt32LE(e.count >>> 0, off); off += 4;
  }
  return buf;
}

function decodeClock(buf: Buffer): VectorClockState {
  const n = buf.readUInt16LE(0);
  const out: VectorClockState = {};
  let off = 2;
  for (let i = 0; i < n; i++) {
    const idl = buf.readUInt16LE(off); off += 2;
    const id = buf.toString('utf8', off, off + idl); off += idl;
    out[id] = buf.readUInt32LE(off); off += 4;
  }
  return out;
}

/* --- public surface --- */

/** Merge two clocks (union, max per id) via the ROM. Returns a canonical clock. */
export function vclockMerge(a: VectorClockState, b: VectorClockState): VectorClockState {
  const e = core();
  e.sc_reset();
  const ab = encodeClock(a);
  const bb = encodeClock(b);
  const ap = stage(ab);
  const bp = stage(bb);
  const cap = ab.length + bb.length + 8;
  const op = e.sc_alloc(cap);
  if (op === 0) throw new Error('smithcore arena OOM');
  const n = e.sc_vclock_merge(ap, ab.length, bp, bb.length, op, cap);
  if (n === SC_ERR || n < 0) throw new Error('sc_vclock_merge failed');
  return decodeClock(Buffer.from(mem().slice(op, op + n)));
}

/** Causal compare via the ROM. -1 (a<b), 0 (concurrent/equal), 1 (a>b). */
export function vclockCompare(a: VectorClockState, b: VectorClockState): -1 | 0 | 1 {
  const e = core();
  e.sc_reset();
  const ab = encodeClock(a);
  const bb = encodeClock(b);
  const ap = stage(ab);
  const bp = stage(bb);
  const r = e.sc_vclock_compare(ap, ab.length, bp, bb.length);
  if (r === SC_CMP_ERR) throw new Error('sc_vclock_compare parse error');
  return r as -1 | 0 | 1;
}

/** SHA-256 via the ROM (same primitive the M2 Ledger/audit chain will use). */
export function sha256(data: Buffer): Buffer {
  const e = core();
  e.sc_reset();
  const dp = stage(data);
  const op = e.sc_alloc(32);
  if (op === 0) throw new Error('smithcore arena OOM');
  const rc = e.sc_sha256(dp, data.length, op);
  if (rc !== 0) throw new Error('sc_sha256 failed');
  return Buffer.from(mem().slice(op, op + 32));
}
