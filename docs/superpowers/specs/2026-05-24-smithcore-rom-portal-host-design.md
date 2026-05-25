# SmithCore ROM in the Portal (browser host) -- Design

> SP1 of the "portable artifact" vision. Status: design approved 2026-05-24.

**Goal:** The React portal loads the *exact same* `smithcore.wasm` (7,135 bytes,
ABI 3) the Node backend loads, and a Vitest suite proves the browser-loaded ROM
computes byte-identically to the committed golden vectors and the pinned hash.
No new UI ships in SP1.

---

## 1. Context -- why this exists

SmithCore is a single deterministic C core compiled to ONE `smithcore.wasm` that
every host (Node backend, Android, iOS, Pi, browser) loads **unchanged**. The
host provides display / IO / time / keys; it never edits the ROM (the Game Boy
cartridge model). This is what enforces the determinism moat (NFR-D1..D5): the
same bytes out for the same bytes in on every device, so the cross-platform
Ledger cannot drift.

Host status today:

| Host | Binding | Runtime | State |
|---|---|---|---|
| Node backend | `backend/src/core/smithCore.ts` | built-in `WebAssembly` | wired + green, parity-gated in CI |
| Android | `core/SmithCore.kt` + JNI | WAMR (stub until vendored) | falls back to legacy Kotlin vclock |
| iOS / Pi | (greenfield) | WAMR | not started |
| **Browser / portal** | **(this spec)** | **built-in `WebAssembly`** | **not started -- SP1** |

The portal currently loads no wasm. `surface.ts` only *mentions* wasm in a
comment.

### The larger vision this is the foundation of

The user's end goal is one portable artifact that bundles UI + deterministic
core and runs on web / desktop / mobile, installable and offline-capable. The
agreed approach reuses the existing React portal as the single UI (the ROM is
already wasm, so a web-based UI needs zero core porting) and wraps it in a native
shell later. That decomposes into independently shippable sub-projects:

```
SP1  ROM in the portal (THIS SPEC)   load smithcore.wasm, parity-test, flag-gated
SP2  Desktop artifact                wrap the portal in a Tauri shell, bundle the ROM
SP3  Mobile artifact                 Tauri mobile (Android/iOS); supersede Compose
SP4  Offline-capable                 local persistence + sync via the vclock engine
```

SP1 is the prerequisite for all of them and delivers value on its own (proves the
ROM runs identically in the browser). SP2-SP4 are out of scope here.

---

## 2. Scope

### In scope (SP1)
- A browser host module under `desktop/portal/src/console/core/` that loads and
  drives `smithcore.wasm` -- the browser twin of `backend/src/core/smithCore.ts`.
- The ROM served as a static asset from the portal, byte-identical to
  `core/dist/smithcore.wasm`, pinned by a hash test.
- A Vitest parity suite asserting the browser ROM matches the committed golden
  vectors and the recorded ROM hash stamp.
- A readiness gate + env flag so a missing/old ROM degrades gracefully instead of
  crashing the app.

### Out of scope (SP1)
- No new UI feature; nothing in the portal consumes the ROM yet.
- No Tauri / desktop / mobile shell (SP2-SP3).
- No offline persistence or client-side reconciliation (SP4).
- No backend changes, no new routes, no auth/security surface, no LLM, no
  fire-and-forget work.
- No Android JNI delegation (separate deferred item).

---

## 3. Architecture

`console/core/` is the browser twin of `backend/src/core/smithCore.ts`. Same ABI,
same wire formats. Only the host primitives change:

- `Buffer` -> `Uint8Array` / `DataView` / `TextEncoder` / `TextDecoder`
- `fs.readFileSync(path)` -> `fetch(url)`

```
core/dist/smithcore.wasm
        |  build.sh sync_rom (cp, byte-identical, never regenerated per target)
        v
desktop/portal/public/smithcore.wasm
        |  fetch(`${import.meta.env.BASE_URL}smithcore.wasm`)
        v
WebAssembly.instantiate(bytes, {})   // zero imports
        |
console/core/smithCore.ts  (ABI check === 3; stage bytes -> call export -> read back)
        |
vclockMerge / vclockCompare / sha256 / ledgerEncode / entitlementsEncode
```

### Wasm delivery choice
Chosen: **`public/` static asset + `fetch`**. It matches the existing
`build.sh` TODO ("Future shells (M5): portal public dir") and is the simplest
path. The pinned-bytes guarantee is enforced by a hash test (section 6) rather
than by Vite asset hashing. Rejected alternatives: Vite `?url` import (extra
plumbing for no SP1 benefit) and base64 inlining (bloats the JS bundle).

---

## 4. ABI reference (from `core/include/smithcore.h`, SC_VERSION = 3)

Memory model: host calls `sc_reset()`, then `sc_alloc(len)` to obtain a pointer
into wasm linear memory, writes input bytes there, calls an export, reads output
bytes back. `free` is a no-op; `sc_reset()` rewinds the whole arena between ops.

```
i32  sc_version(void)                                  -> 3
void sc_reset(void)
i32  sc_alloc(i32 len)                                 -> ptr, or 0 on OOM
i32  sc_vclock_merge(a,al,b,bl,out,outcap)             -> out_len, or SC_ERR(-1)
i32  sc_vclock_compare(a,al,b,bl)                      -> -1 | 0 | 1, or SC_CMP_ERR(2)
i32  sc_vclock_canon(in,inl,out,outcap)               -> out_len, or SC_ERR
i32  sc_sha256(data,len,out32)                         -> 0, or SC_ERR
i32  sc_ledger_encode(in,inl,out,outcap)              -> out_len, or SC_ERR
i32  sc_entitlements_encode(in,inl,out,outcap)        -> out_len(6), or SC_ERR
```

Sentinels: any negative return is an error; compare uses `2` (`SC_CMP_ERR`)
because its valid range includes `-1`.

### Wire formats (must match the C core byte-for-byte)

**Vector-clock canonical wire form** (`encodeClock`):
```
u16 n                       ; entry count, LE
repeat n, sorted ascending by id bytes:
  u16 id_len                ; LE
  u8  id[id_len]            ; UTF-8 device id
  u32 count                 ; LE, always >= 1 (zero entries omitted)
```
`{x:0}` and `{}` therefore encode identically. Sort key is the raw UTF-8 bytes.

**Ledger packed input** (`packLedgerInput`, what `sc_ledger_encode` reads -- v2
field order WITHOUT the header, id arrays left UNSORTED; the core sorts them):
```
serial, intentVersionId, scopeStatement                       ; 3x string
workPerformed, laborRecorded, materialsUsed, contextualNotes  ; 4x strarray (insertion order)
totalCostCents (i64 LE), totalHoursCenti (i64 LE)             ; integer minor units
jobIds, timeEntryIds, chatMessageIds                          ; 3x strarray (UNSORTED)
```
where `string = [u32 len LE][utf8 bytes]` and
`strarray = [u32 count LE] then count strings`.
`cents = round(totalCost * 100)`, `centihours = round(totalHours * 100)`. The
single `Math.round` is the host's one allowed float op (the core is float-free);
the Synthesizer pre-quantizes both fields to 2 decimals so the rounding resolves
identically on every host.

Core output (canonical v2): `"SMC"` + `0x01` + `0x02`, fields 1-9 verbatim, then
the three id arrays sorted ascending by unsigned UTF-8 bytes.

**Entitlements packed input** (`packEntitlements`): `[u8 tierCode][u32 bitmask LE]`.
Core output (6 bytes): `[u8 format=0x01][u8 tierCode][u32 bitmask LE]`.

---

## 5. Components / files

### Create `desktop/portal/public/smithcore.wasm`
The ROM, byte-identical to `core/dist/smithcore.wasm`. New `public/` dir for the
portal (none exists today). Committed; the bytes are pinned.

### Modify `core/build.sh`
Replace the line-74 TODO with `sync_rom "../desktop/portal/public"` so a rebuild
copies the same ROM bytes into the portal and the ROM can never drift from dist.
(The function already mkdir's and `cp`s; it guards on the parent dir existing.)

### Create `console/core/smithCore.ts` -- browser host
Mirrors `backend/src/core/smithCore.ts`. Responsibilities:
- `instantiate(bytes: Uint8Array): Promise<void>` -- the testable seam. Calls
  `WebAssembly.instantiate(bytes, {})`, reads exports, asserts
  `sc_version() === 3` (throw on mismatch).
- `initSmithCore(): Promise<void>` -- idempotent; gated by
  `import.meta.env.VITE_SMITHCORE_ENABLED` (see section 7). Default loader:
  `fetch(\`${import.meta.env.BASE_URL}smithcore.wasm\`)` -> `arrayBuffer()` ->
  `instantiate`. Soft-fails: on any error, log once, leave not-ready, do not
  throw to the app.
- `isSmithCoreReady(): boolean`.
- Arena helpers: `mem()` (`new Uint8Array(memory.buffer)`), `stage(bytes)`
  (`sc_alloc` + copy in).
- vclock wire codec: `encodeClock(clock)` / `decodeClock(bytes)` using `DataView`
  (LE) + `TextEncoder`/`TextDecoder`, entries filtered of zeros and sorted by
  UTF-8 bytes.
- Public ops, each `sc_reset()` first: `vclockMerge(a,b)`, `vclockCompare(a,b)`,
  `sha256(bytes): Uint8Array`, `ledgerEncode(input): Uint8Array`,
  `entitlementsEncode(input): Uint8Array`.

Exports types: `VectorClockState = Record<string, number>`.

### Create `console/core/ledgerCanonical.ts` -- pure byte packers
No wasm. `packLedgerInput(a: LedgerArtifactInput): Uint8Array` and
`packEntitlements(tierCode: number, bitmask: number): Uint8Array`, plus the small
encoders `encStr` / `encStrArray` / `encI64` (via `DataView.setBigInt64`) /
`utf8`. `LedgerArtifactInput` is a local minimal type carrying only the fields
the packer reads (the portal does not import backend types).

### Create `console/core/smithCore.parity.test.ts` -- the gate
See section 6.

---

## 6. Testing -- the merge gate (mirrors `smithcore-parity.test.ts`)

Vitest runs in Node (jsdom env), so the **test file** may use `fs`/`path` to read
the wasm + goldens and `crypto.subtle` as an independent SHA-256 reference. The
**module under test stays browser-pure**: the test reads the wasm bytes from disk
and calls `instantiate(bytes)` directly (the seam), never `fetch`.

Acceptance criteria -- all must pass:

1. **ROM identity / pinned bytes.**
   `sha256(read public/smithcore.wasm)` (hex) == the first token of
   `core/dist/smithcore.wasm.sha256`. Catches portal/dist drift.

2. **Ledger goldens.** For each vector in `core/testdata/ledger-golden.json`
   (`empty`, `simple`, `utf8`, `big`):
   - `toHex(ledgerEncode(packLedgerInput(artifact)))` == `vector.canonicalHex`
   - `toHex(sha256(that))` == `vector.hashHex`

3. **Entitlements goldens.** For each vector in
   `core/testdata/entitlements-golden.json` (open/solo/advanced/enterprise):
   - `toHex(entitlementsEncode(packEntitlements(tierCode, bitmask)))` ==
     `vector.recordHex`
   - `toHex(sha256(that))` == `vector.hashHex`

4. **Vector clock parity.** ROM `vclockMerge` / `vclockCompare` over the backend
   suite's golden cases plus a deterministic-PRNG fuzz loop, compared against a
   local ~10-line union-max merge and causal-compare reference defined in the
   test (semantic equality after zero-stripping; merge commutative, compare
   antisymmetric).

5. **SHA-256 independent reference.** ROM `sha256(buf)` ==
   `crypto.subtle.digest('SHA-256', buf)` across lengths
   `[0, 1, 55, 56, 57, 63, 64, 65, 127, 128, 1000]` (block-boundary coverage).

Run: `cd desktop/portal && npm run test:run`. Build clean:
`cd desktop/portal && npm run build`.

---

## 7. Error handling and the "fallback" reconciliation

The backend and Android keep a legacy implementation and route through the ROM
only when `SMITHCORE_ENABLED=1` and the ROM is ready, falling back to the legacy
path otherwise. The browser is different: **nothing in the portal ever computed
vclock/ledger before -- the server did.** There is no TS twin in the browser to
fall back to.

So in SP1 the gate has this shape:
- `import.meta.env.VITE_SMITHCORE_ENABLED` controls whether `initSmithCore()`
  attempts to load at all (default on in dev).
- If fetch fails, ABI != 3, or instantiate throws: log once, `isSmithCoreReady()`
  stays `false`, and **the app continues normally** because no SP1 UI consumes
  the ROM.
- "Fallback" therefore means a graceful no-op until SP4 wires a consumer -- same
  spirit as the backend gate (degrade, never crash), without a parallel compute
  path. This asymmetry with the backend is intentional and recorded here.

`initSmithCore()` is fire-and-forget at app boot in the sense of "kick it off,
do not block first paint" -- but it is a self-contained Promise that owns its own
error path and touches no request scope, so it does not fall under the backend
route-handler fire-and-forget rule (that rule governs `backend/src/` routes).

---

## 8. Determinism and security notes

- The portal-served wasm MUST equal `core/dist` (the moat). Enforced two ways:
  `build.sh` copies (never regenerates), and test #1 pins the hash in CI.
- No floating point is added. `packLedgerInput` keeps exactly the one
  `Math.round` quantization the backend uses; identical inputs -> identical bytes
  -> identical hash on every host.
- Additive only: no backend, route, auth, CORS, or audit surface is touched.
- Zero-import wasm: `WebAssembly.instantiate(bytes, {})` passes no host imports,
  matching the core's "no syscalls, no host imports" invariant.

---

## 9. Open questions

None. The wasm-delivery choice (`public/` + fetch) and the browser fallback
semantics (graceful no-op) are decided above.
