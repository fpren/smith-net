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
