# SmithCore ROM in the Portal (browser host) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load the exact same `smithcore.wasm` (ABI 3) the Node backend loads into the React portal, with a Vitest parity gate proving the browser ROM reproduces the committed golden vectors and the pinned hash.

**Architecture:** A new `desktop/portal/src/console/core/` module is the browser twin of `backend/src/core/smithCore.ts` -- same ABI and wire formats, with `Buffer` -> `Uint8Array`/`DataView`/`TextEncoder` and `fs.readFileSync` -> `fetch`. The ROM is served as a static asset from `public/`, pinned by a hash test. No UI consumes it yet; the console boot kicks off a soft-failing load so the ROM is live in the running app.

**Tech Stack:** Vite 5 + React 18 + TypeScript (strict, ES2020) + Vitest (jsdom). Built-in `WebAssembly`. Golden vectors in `core/testdata/`.

**Spec:** `docs/superpowers/specs/2026-05-24-smithcore-rom-portal-host-design.md`

---

## File structure (locked before tasks)

| File | Responsibility |
|---|---|
| `desktop/portal/public/smithcore.wasm` (create) | The ROM, byte-identical to `core/dist/smithcore.wasm`. Served at `/smithcore.wasm`. |
| `core/build.sh` (modify line 74) | Add `sync_rom "../desktop/portal/public"` so rebuilds copy the same ROM bytes into the portal. |
| `desktop/portal/src/vite-env.d.ts` (create) | Vite client types + `VITE_SMITHCORE_ENABLED` env typing so `tsc --noEmit` passes. |
| `desktop/portal/src/console/core/smithCore.ts` (create) | Browser host: load/instantiate/ABI gate, arena staging, vclock codec, op wrappers (`sha256`, `vclockMerge/Compare`, `ledgerEncode`, `entitlementsEncode`). |
| `desktop/portal/src/console/core/ledgerCanonical.ts` (create) | Pure byte packers `packLedgerInput` + `packEntitlements` (no wasm). |
| `desktop/portal/src/console/core/smithCore.parity.test.ts` (create, grows per task) | The merge gate: ROM identity, sha256, vclock, ledger, entitlements parity. |
| `desktop/portal/src/console/ConsoleShell.tsx` (modify) | Kick off `initSmithCore()` on mount (soft-fail). |

**Conventions:** All commands run from `desktop/portal/` unless noted. Stage only the files each task names -- never `git add -A`/`.`. The repo rule requires the commit trailer shown in each task. No emoji anywhere. `npm run build` is `vite build` (esbuild transpile, no type-check), so each task also relies on Vitest (esbuild) for runtime; the final task adds an explicit `npx tsc --noEmit` type gate.

---

### Task 1: Pin the ROM asset in the portal

**Files:**
- Create: `desktop/portal/public/smithcore.wasm`
- Modify: `core/build.sh:74`
- Test: `desktop/portal/src/console/core/smithCore.parity.test.ts`

- [ ] **Step 1: Write the failing test**

Create `desktop/portal/src/console/core/smithCore.parity.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { fileURLToPath } from 'node:url';
import { webcrypto } from 'node:crypto';
import * as fs from 'node:fs';

// Test-only filesystem + crypto (Vitest runs in Node). The module under test
// stays browser-pure: we read the wasm bytes here and inject them via
// instantiate(), never fetch(). Paths resolve from this file via import.meta.url
// so they do not depend on the process cwd.
const wasmUrl = new URL('../../../public/smithcore.wasm', import.meta.url);
const stampUrl = new URL('../../../../../core/dist/smithcore.wasm.sha256', import.meta.url);
const romBytes = () => new Uint8Array(fs.readFileSync(fileURLToPath(wasmUrl)));

const toHex = (b: Uint8Array) =>
  Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
const refSha256 = async (b: Uint8Array) =>
  toHex(new Uint8Array(await webcrypto.subtle.digest('SHA-256', b)));

describe('ROM identity', () => {
  it('portal wasm matches the recorded ROM hash stamp', async () => {
    const stamp = fs
      .readFileSync(fileURLToPath(stampUrl), 'utf8')
      .trim()
      .split(/\s+/)[0];
    expect(await refSha256(romBytes())).toBe(stamp);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- smithCore.parity`
Expected: FAIL -- `ENOENT` reading `public/smithcore.wasm` (asset not present yet).

- [ ] **Step 3: Create the asset and wire the build sync**

Copy the pinned ROM bytes into the portal (run from repo root):

```bash
mkdir -p desktop/portal/public
cp core/dist/smithcore.wasm desktop/portal/public/smithcore.wasm
```

In `core/build.sh`, replace line 74:

```bash
# Future shells (M5): portal public dir, iOS bundle, Pi host -- add here.
```

with:

```bash
sync_rom "../desktop/portal/public"
# Future shells: iOS bundle, Pi host -- add here.
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- smithCore.parity`
Expected: PASS (1 test). Portal wasm hash equals the dist stamp.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/public/smithcore.wasm core/build.sh desktop/portal/src/console/core/smithCore.parity.test.ts
git commit -m "$(cat <<'EOF'
feat(core): pin smithcore.wasm into the portal + ROM-identity gate (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Browser host -- load, ABI gate, SHA-256

**Files:**
- Create: `desktop/portal/src/vite-env.d.ts`
- Create: `desktop/portal/src/console/core/smithCore.ts`
- Test: `desktop/portal/src/console/core/smithCore.parity.test.ts` (append)

- [ ] **Step 1: Write the failing test**

Append to `smithCore.parity.test.ts`:

```ts
import { beforeAll } from 'vitest';
import { instantiate, isSmithCoreReady, sha256 } from './smithCore';

beforeAll(async () => {
  await instantiate(romBytes());
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- smithCore.parity`
Expected: FAIL -- cannot resolve `./smithCore` (module not created yet).

- [ ] **Step 3: Create the env typing shim and the host module**

Create `desktop/portal/src/vite-env.d.ts`:

```ts
/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_SMITHCORE_ENABLED?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
```

Create `desktop/portal/src/console/core/smithCore.ts`:

```ts
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

/**
 * Instantiate the ROM from raw bytes. The testable seam: production fetches the
 * bytes (initSmithCore); tests read them from disk. Asserts the ABI. Idempotent
 * and soft-failing: a bad buffer logs and leaves the host not-ready.
 */
export async function instantiate(bytes: Uint8Array): Promise<void> {
  if (_ex) return;
  try {
    const { instance } = await WebAssembly.instantiate(bytes, {});
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
  try {
    const url = `${import.meta.env.BASE_URL}smithcore.wasm`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`fetch ${url} -> ${res.status}`);
    await instantiate(new Uint8Array(await res.arrayBuffer()));
  } catch (err) {
    console.warn('[smithcore] ROM not loaded; degrading to no-op:', err);
  }
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- smithCore.parity`
Expected: PASS (ready + 11 sha256 cases). ROM SHA-256 == WebCrypto across block boundaries.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/vite-env.d.ts desktop/portal/src/console/core/smithCore.ts desktop/portal/src/console/core/smithCore.parity.test.ts
git commit -m "$(cat <<'EOF'
feat(core): portal browser host for smithcore.wasm -- load, ABI gate, sha256 (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Vector clock -- canonical codec + merge/compare

**Files:**
- Modify: `desktop/portal/src/console/core/smithCore.ts` (append codec + wrappers)
- Test: `desktop/portal/src/console/core/smithCore.parity.test.ts` (append)

- [ ] **Step 1: Write the failing test**

Append to `smithCore.parity.test.ts`:

```ts
import type { VectorClockState } from './smithCore';
import { vclockMerge, vclockCompare } from './smithCore';

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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- smithCore.parity`
Expected: FAIL -- `vclockMerge`/`vclockCompare` not exported from `./smithCore`.

- [ ] **Step 3: Implementation**

Append to `desktop/portal/src/console/core/smithCore.ts`:

```ts
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- smithCore.parity`
Expected: PASS (golden cases + the 2000-pair fuzz + canonicalization).

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/core/smithCore.ts desktop/portal/src/console/core/smithCore.parity.test.ts
git commit -m "$(cat <<'EOF'
feat(core): portal ROM vclock merge/compare + canonical codec (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Ledger -- packer + canonical encode vs goldens

**Files:**
- Create: `desktop/portal/src/console/core/ledgerCanonical.ts`
- Modify: `desktop/portal/src/console/core/smithCore.ts` (append `ledgerEncode`)
- Test: `desktop/portal/src/console/core/smithCore.parity.test.ts` (append)

- [ ] **Step 1: Write the failing test**

Append to `smithCore.parity.test.ts`:

```ts
import { ledgerEncode } from './smithCore';
import { packLedgerInput, type LedgerArtifactInput } from './ledgerCanonical';

const ledgerGoldenUrl = new URL('../../../../../core/testdata/ledger-golden.json', import.meta.url);

describe('ledger parity: golden vectors', () => {
  const golden = JSON.parse(fs.readFileSync(fileURLToPath(ledgerGoldenUrl), 'utf8')) as {
    vectors: Array<{ label: string; artifact: LedgerArtifactInput; canonicalHex: string; hashHex: string }>;
  };

  it.each(golden.vectors.map((v) => [v.label, v] as const))(
    'reproduces canonical bytes + hash for %s',
    (_label, v) => {
      const canonical = ledgerEncode(packLedgerInput(v.artifact));
      expect(toHex(canonical)).toBe(v.canonicalHex);
      expect(toHex(sha256(canonical))).toBe(v.hashHex);
    },
  );
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- smithCore.parity`
Expected: FAIL -- cannot resolve `./ledgerCanonical` / `ledgerEncode` not exported.

- [ ] **Step 3: Implementation**

Create `desktop/portal/src/console/core/ledgerCanonical.ts`:

```ts
/*
 * ledgerCanonical.ts -- pure byte packers for the SmithCore ROM browser host.
 * Mirrors backend/src/ledgerCanonical.ts (packLedgerInput) but uses
 * DataView/TextEncoder instead of Node Buffer. No wasm here. The packed buffer
 * is the input to sc_ledger_encode (see core/include/smithcore.h): v2 field
 * order WITHOUT the header, with the three id arrays left UNSORTED (the core
 * sorts them). The single Math.round is the host's one allowed float op; the
 * core is float-free.
 */

export interface LedgerArtifactInput {
  serial: string;
  intentVersionId: string;
  scopeStatement: string;
  workPerformed: string[];
  laborRecorded: string[];
  materialsUsed: string[];
  contextualNotes: string[];
  totalCost: number;
  totalHours: number;
  jobIds: string[];
  timeEntryIds: string[];
  chatMessageIds: string[];
}

const _utf8 = new TextEncoder();

function concat(parts: Uint8Array[]): Uint8Array {
  let n = 0;
  for (const p of parts) n += p.length;
  const out = new Uint8Array(n);
  let off = 0;
  for (const p of parts) {
    out.set(p, off);
    off += p.length;
  }
  return out;
}

function u32le(v: number): Uint8Array {
  const b = new Uint8Array(4);
  new DataView(b.buffer).setUint32(0, v >>> 0, true);
  return b;
}

function encStr(s: string): Uint8Array {
  const bytes = _utf8.encode(s);
  return concat([u32le(bytes.length), bytes]);
}

function encStrArray(arr: string[]): Uint8Array {
  return concat([u32le(arr.length), ...arr.map(encStr)]);
}

function encI64(v: bigint): Uint8Array {
  const b = new Uint8Array(8);
  new DataView(b.buffer).setBigInt64(0, v, true);
  return b;
}

/** Pack a SummaryArtifact-shaped value into the sc_ledger_encode input buffer. */
export function packLedgerInput(a: LedgerArtifactInput): Uint8Array {
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return concat([
    encStr(a.serial),
    encStr(a.intentVersionId),
    encStr(a.scopeStatement),
    encStrArray(a.workPerformed),
    encStrArray(a.laborRecorded),
    encStrArray(a.materialsUsed),
    encStrArray(a.contextualNotes),
    encI64(cents),
    encI64(centihours),
    encStrArray(a.jobIds),
    encStrArray(a.timeEntryIds),
    encStrArray(a.chatMessageIds),
  ]);
}

/** Pack the entitlements record input: [u8 tierCode][u32 bitmask LE]. */
export function packEntitlements(tierCode: number, bitmask: number): Uint8Array {
  const out = new Uint8Array(5);
  out[0] = tierCode & 0xff;
  new DataView(out.buffer).setUint32(1, bitmask >>> 0, true);
  return out;
}
```

Append to `desktop/portal/src/console/core/smithCore.ts`:

```ts
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- smithCore.parity`
Expected: PASS (4 ledger vectors: empty/simple/utf8/big -- bytes and hash both match).

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/core/ledgerCanonical.ts desktop/portal/src/console/core/smithCore.ts desktop/portal/src/console/core/smithCore.parity.test.ts
git commit -m "$(cat <<'EOF'
feat(core): portal ROM ledger encode + packer == golden vectors (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Entitlements -- packer + encode vs goldens

**Files:**
- Modify: `desktop/portal/src/console/core/smithCore.ts` (append `entitlementsEncode`)
- Test: `desktop/portal/src/console/core/smithCore.parity.test.ts` (append)
- (`packEntitlements` already added in Task 4.)

- [ ] **Step 1: Write the failing test**

Append to `smithCore.parity.test.ts`:

```ts
import { entitlementsEncode } from './smithCore';
import { packEntitlements } from './ledgerCanonical';

const entGoldenUrl = new URL('../../../../../core/testdata/entitlements-golden.json', import.meta.url);

describe('entitlements parity: golden vectors', () => {
  const golden = JSON.parse(fs.readFileSync(fileURLToPath(entGoldenUrl), 'utf8')) as {
    vectors: Array<{ tier: string; tierCode: number; bitmask: number; recordHex: string; hashHex: string }>;
  };

  it.each(golden.vectors.map((v) => [v.tier, v] as const))(
    'reproduces record + hash for %s',
    (_tier, v) => {
      const record = entitlementsEncode(packEntitlements(v.tierCode, v.bitmask));
      expect(toHex(record)).toBe(v.recordHex);
      expect(toHex(sha256(record))).toBe(v.hashHex);
    },
  );
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- smithCore.parity`
Expected: FAIL -- `entitlementsEncode` not exported from `./smithCore`.

- [ ] **Step 3: Implementation**

Append to `desktop/portal/src/console/core/smithCore.ts`:

```ts
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- smithCore.parity`
Expected: PASS (4 tiers: open/solo/advanced/enterprise -- record bytes and hash both match).

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/core/smithCore.ts desktop/portal/src/console/core/smithCore.parity.test.ts
git commit -m "$(cat <<'EOF'
feat(core): portal ROM entitlements encode + packer == golden vectors (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Boot wiring + full-suite + build + type gate

**Files:**
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`
- Verify: full test suite, `vite build`, `tsc --noEmit`

- [ ] **Step 1: Wire the ROM load at console boot**

Edit `desktop/portal/src/console/ConsoleShell.tsx`.

Line 1, change:

```ts
import { ReactNode } from 'react';
```

to:

```ts
import { ReactNode, useEffect } from 'react';
```

Add to the import block (after the existing imports):

```ts
import { initSmithCore, isSmithCoreReady } from './core/smithCore';
```

Inside `ConsoleShell`, immediately after `const user = useAuthStore((s) => s.user);`, add:

```ts
  // Load the deterministic ROM once when the console mounts. Soft-fail: no UI
  // consumes it yet (SP1 foundation), so a missing/old wasm just stays not-ready.
  useEffect(() => {
    void initSmithCore().then(() => {
      if (isSmithCoreReady()) console.info('[smithcore] ROM ready (ABI 3)');
    });
  }, []);
```

- [ ] **Step 2: Run the full portal test suite**

Run: `npm run test:run`
Expected: PASS -- the new `smithCore.parity` suite plus all pre-existing suites (no regressions). The parity suite reports: ROM identity (1), host ready (1), sha256 (11), vclock golden (21) + fuzz (1), ledger (4), entitlements (4).

- [ ] **Step 3: Type-check + production build**

Run: `npx tsc --noEmit && npm run build`
Expected: `tsc` reports no errors (the `vite-env.d.ts` shim types `import.meta.env.VITE_SMITHCORE_ENABLED`; strict/noUnusedLocals satisfied), then Vite builds and emits `dist/smithcore.wasm` copied from `public/`.

- [ ] **Step 4: Manual runtime check (deferred-verify)**

Run: `npm run dev`, log in, open the console. In devtools: expect `[smithcore] ROM ready (ABI 3)` logged and `GET /smithcore.wasm 200` in the Network tab. (Needs the backend up + login to reach the console; the wasm load itself is backend-independent.)

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/ConsoleShell.tsx
git commit -m "$(cat <<'EOF'
feat(core): load smithcore.wasm at portal console boot (SP1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-review (against the spec)

**Spec coverage:**
- Section 3 (architecture: browser twin, public/ + fetch) -> Tasks 1, 2.
- Section 4 (ABI, wire formats) -> `CoreExports` Task 2; vclock codec Task 3; packers Task 4/5.
- Section 5 (files: smithCore.ts, ledgerCanonical.ts, parity test, public asset, build.sh, vite-env.d.ts) -> Tasks 1-5; vite-env.d.ts Task 2.
- Section 6 (testing: ROM identity / ledger / entitlements / vclock / sha256) -> Tasks 1, 4, 5, 3, 2.
- Section 7 (flag + soft-fail + boot) -> `initSmithCore` Task 2; boot wiring Task 6.
- Section 8 (pinned bytes, single `Math.round`, additive) -> Task 1 hash gate + `build.sh`; `packLedgerInput` keeps one `Math.round`; no backend/route/auth changes.

**Placeholder scan:** No TBD/TODO; every code step shows complete code. No "add error handling"/"similar to Task N" hand-waves.

**Type consistency:** `VectorClockState`, `LedgerArtifactInput`, `instantiate(bytes: Uint8Array)`, `sha256/ledgerEncode/entitlementsEncode(input?): Uint8Array`, `packLedgerInput`/`packEntitlements` signatures match across the implementation and the test. `EXPECTED_ABI` (Task 2), `SC_ERR`/`SC_CMP_ERR` (Task 3) are each defined once before first use and reused. Test helpers `toHex`, `refSha256`, `romBytes`, `fileURLToPath`, `webcrypto` are defined once in Task 1 and reused. Multiple `import` lines from `./smithCore` accumulate across tasks (valid ESM); they may be consolidated at the end but are not required to be.

**Determinism/security:** No floating point beyond the single host `Math.round` (matches backend). Portal wasm pinned to the dist stamp by the Task 1 gate and the `build.sh` copy. Additive only -- no backend, route, auth, CORS, or audit surface touched; zero-import instantiation (`WebAssembly.instantiate(bytes, {})`).
