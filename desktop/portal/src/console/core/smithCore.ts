/*
 * smithCore.ts -- browser host binding for the SmithNet "ROM"
 * (public/smithcore.wasm). The portal loads the EXACT same .wasm bytes the Node
 * backend (backend/src/core/smithCore.ts) and the other shells load, so the
 * Ledger hash and vector-clock merge are byte-identical across every host (the
 * determinism moat, NFR-D1..D5).
 *
 * Browser twin of the Node host: Buffer -> Uint8Array/DataView/TextEncoder,
 * fs.readFileSync -> fetch. The host owns IO/time/keys; this module only stages
 * bytes into wasm linear memory, calls an export, and reads bytes back. See
 * core/include/smithcore.h for the ABI and canonical wire formats.
 */

export type VectorClockState = Record<string, number>;

interface WasmMemory {
  buffer: ArrayBuffer;
}

interface CoreExports {
  memory: WasmMemory;
  sc_version(): number;
  sc_reset(): void;
  sc_alloc(len: number): number;
  sc_vclock_merge(a: number, al: number, b: number, bl: number, o: number, oc: number): number;
  sc_vclock_compare(a: number, al: number, b: number, bl: number): number;
  sc_vclock_canon(i: number, il: number, o: number, oc: number): number;
  sc_sha256(d: number, l: number, o: number): number;
  sc_ledger_encode(i: number, il: number, o: number, oc: number): number;
  sc_entitlements_encode(i: number, il: number, o: number, oc: number): number;
}

const EXPECTED_ABI = 3;

let _ex: CoreExports | null = null;

// TS 5.x types Uint8Array as Uint8Array<ArrayBufferLike>, which does not match
// the WebAssembly.instantiate(BufferSource) overload -- the compiler then picks
// the Module overload and loses `.instance`. Bind through a locally-typed handle
// that declares the bytes overload explicitly (mirrors backend/src/core/smithCore.ts).
const WA = (globalThis as unknown as {
  WebAssembly: {
    instantiate(bytes: Uint8Array, imports: object): Promise<{ instance: { exports: unknown } }>;
  };
}).WebAssembly;

let _initPromise: Promise<void> | null = null;

/**
 * Instantiate the ROM from raw bytes. The testable seam: production fetches the
 * bytes (initSmithCore); tests read them from disk. Asserts the ABI. Idempotent
 * and soft-failing: a bad buffer logs and leaves the host not-ready.
 */
export async function instantiate(bytes: Uint8Array): Promise<void> {
  if (_ex) return;
  try {
    const { instance } = await WA.instantiate(bytes, {});
    const ex = instance.exports as unknown as CoreExports;
    const abi = ex.sc_version();
    if (abi !== EXPECTED_ABI) {
      throw new Error(`smithcore ABI mismatch: wasm=${abi} expected=${EXPECTED_ABI}`);
    }
    _ex = ex;
  } catch (err) {
    console.warn('[smithcore] instantiate failed; ROM not loaded:', err);
  }
}

/**
 * Load + instantiate the ROM from the portal's static asset. Gated by
 * VITE_SMITHCORE_ENABLED (set to '0' to skip). Soft-fails so a missing/old ROM
 * never blocks or crashes the app. Idempotent.
 */
export async function initSmithCore(): Promise<void> {
  if (_ex) return;
  if (import.meta.env.VITE_SMITHCORE_ENABLED === '0') return;
  if (_initPromise) return _initPromise;
  _initPromise = (async () => {
    try {
      const url = `${import.meta.env.BASE_URL}smithcore.wasm`;
      const res = await fetch(url);
      if (!res.ok) throw new Error(`fetch ${url} -> ${res.status}`);
      await instantiate(new Uint8Array(await res.arrayBuffer()));
    } catch (err) {
      console.warn('[smithcore] ROM not loaded; degrading to no-op:', err);
    }
  })();
  return _initPromise;
}

export function isSmithCoreReady(): boolean {
  return _ex !== null;
}

function core(): CoreExports {
  if (!_ex) throw new Error('smithcore not initialized; call initSmithCore() first');
  return _ex;
}

function mem(): Uint8Array {
  return new Uint8Array(core().memory.buffer);
}

/** Allocate in the arena and copy bytes in; returns the pointer. */
function stage(buf: Uint8Array): number {
  const p = core().sc_alloc(buf.length);
  if (p === 0 && buf.length > 0) throw new Error('smithcore arena OOM');
  mem().set(buf, p);
  return p;
}

/** SHA-256 via the ROM (the same primitive the Ledger/audit chain hashes with). */
export function sha256(data: Uint8Array): Uint8Array {
  const e = core();
  e.sc_reset();
  const dp = stage(data);
  const op = e.sc_alloc(32);
  if (op === 0) throw new Error('smithcore arena OOM');
  const rc = e.sc_sha256(dp, data.length, op);
  if (rc !== 0) throw new Error('sc_sha256 failed');
  return mem().slice(op, op + 32);
}

const SC_ERR = -1;
const SC_CMP_ERR = 2;

/* --- canonical vector-clock wire codec (mirrors core/src/vclock.c) ---
 * wire: u16 n LE; then n entries sorted ascending by id bytes:
 *   u16 id_len LE; u8 id[id_len] (utf-8); u32 count LE (always >= 1).
 */
const _utf8e = new TextEncoder();
const _utf8d = new TextDecoder();

function compareBytes(a: Uint8Array, b: Uint8Array): number {
  const n = Math.min(a.length, b.length);
  for (let i = 0; i < n; i++) {
    if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
  }
  return a.length - b.length;
}

export function encodeClock(clock: VectorClockState): Uint8Array {
  const entries = Object.keys(clock)
    .map((id) => ({ id: _utf8e.encode(id), count: clock[id] }))
    .filter((e) => e.count !== 0)
    .sort((a, b) => compareBytes(a.id, b.id));
  let size = 2;
  for (const e of entries) size += 2 + e.id.length + 4;
  const out = new Uint8Array(size);
  const dv = new DataView(out.buffer);
  dv.setUint16(0, entries.length, true);
  let off = 2;
  for (const e of entries) {
    if (e.id.length > 0xffff) throw new Error('device id exceeds 65535 bytes');
    dv.setUint16(off, e.id.length, true);
    off += 2;
    out.set(e.id, off);
    off += e.id.length;
    dv.setUint32(off, e.count >>> 0, true);
    off += 4;
  }
  return out;
}

function decodeClock(buf: Uint8Array): VectorClockState {
  const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const n = dv.getUint16(0, true);
  const out: VectorClockState = {};
  let off = 2;
  for (let i = 0; i < n; i++) {
    const idl = dv.getUint16(off, true);
    off += 2;
    out[_utf8d.decode(buf.subarray(off, off + idl))] = dv.getUint32(off + idl, true);
    off += idl + 4;
  }
  return out;
}

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
  return decodeClock(mem().slice(op, op + n));
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

/** Canonical v2 ledger encode via the ROM. Input is the host-packed field buffer
 *  (see ledgerCanonical.packLedgerInput / smithcore.h); returns the canonical
 *  bytes ("SMC" + abi + format, fields verbatim, id arrays sorted). */
export function ledgerEncode(input: Uint8Array): Uint8Array {
  const e = core();
  e.sc_reset();
  const ip = stage(input);
  const cap = input.length + 8; // +5 header; sorting never grows total size
  const op = e.sc_alloc(cap);
  if (op === 0) throw new Error('smithcore arena OOM');
  const n = e.sc_ledger_encode(ip, input.length, op, cap);
  if (n === SC_ERR || n < 0) throw new Error('sc_ledger_encode failed');
  return mem().slice(op, op + n);
}

/** Canonical entitlements record encode via the ROM. Input is the host-packed
 *  [u8 tierCode][u32 bitmask LE]; returns [0x01][tierCode][bitmask LE] (6 bytes). */
export function entitlementsEncode(input: Uint8Array): Uint8Array {
  const e = core();
  e.sc_reset();
  const ip = stage(input);
  const op = e.sc_alloc(8);
  if (op === 0) throw new Error('smithcore arena OOM');
  const n = e.sc_entitlements_encode(ip, input.length, op, 8);
  if (n === SC_ERR || n < 0) throw new Error('sc_entitlements_encode failed');
  return mem().slice(op, op + n);
}
