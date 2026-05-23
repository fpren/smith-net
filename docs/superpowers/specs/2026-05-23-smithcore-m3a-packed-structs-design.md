# SmithCore M3a — Packed Structs: the v2 ledger encoder in the C core

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Predecessors:** M1 (vclock + sha256 in the ROM), M1.5 (size gate), M2 (host-side v2 ledger encoder + golden vectors)
**Roadmap position:** first half of M3 ("mesh + packed structs"). M3 was decomposed into **M3a (packed structs, this spec)** and **M3b (mesh, a later separate cycle)**.

---

## 0. North star

SmithCore is ROM-like infrastructure: ONE deterministic `smithcore.wasm` every host loads
unchanged. The determinism moat requires the same bytes out for the same bytes in on every
device. M2 put the v2 ledger canonical encoding into a host-side encoder, mirrored in TS and
Kotlin, with a committed golden-vector fixture. **M3a moves that encoding into the C core** so
the canonical bytes have ONE authoritative implementation, validated against the M2 golden
vectors. Small, simple, efficient: one new C file, one new ABI export, no general framework.

---

## 1. Scope

In scope (core + backend, both fully buildable/testable in the dev environment — the wasi-sdk
is present at `~/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos`):
1. A new ROM export `sc_ledger_encode` that produces the canonical v2 bytes.
2. Backend delegates `encodeLedgerArtifactV2` to the ROM when enabled+ready, keeping the M2
   host-side encoder as the readiness fallback.
3. A parity gate proving the C encoder == the M2 golden vectors == the host fallback encoder.

Non-goals (explicitly NOT in M3a):
- **Android JNI delegation** — deferred to a follow-up (Android can't compile in this
  environment; gradle SDK/NDK is broken). Android keeps its golden-proven M2 host encoder
  (`LedgerCanon.kt`); it will delegate to the ROM in a later cycle (pairs with M3b mesh).
- **A general packed-struct framework** (reusable record layout, far-pointer `(section,offset)`
  refs, enum-code registry) — YAGNI until mesh needs it. Build the ledger encoder concretely;
  generalize in M3b if warranted.
- **Mesh** — M3b.
- **Removing the host encoders** — they stay as the readiness fallback (the M1/M2 degradation
  guarantee: a missing/old ROM degrades to the proven-identical host path, never fails).
- **Changing the v2 byte format** — M3a reproduces the EXACT M2 format; the golden vectors are
  the unchanged oracle.

---

## 2. Responsibility split (core vs host)

The C core is float-free (no NaN/rounding divergence). Therefore:
- **Host keeps the one float op:** rounding `totalCost`/`totalHours` to integer cents/centihours
  (`Math.round(x*100)`), exactly as in M2. The host passes integers in.
- **Core owns the drift-prone deterministic logic:** the fixed canonical layout, the `SMC`
  header, and **sorting the three id arrays by unsigned UTF-8 bytes** (where two host languages
  could most easily diverge). The core's byte-compare matches `vclock.c`'s existing comparator:
  compare `min(len)` bytes unsigned; on tie, shorter sorts first.

---

## 3. ABI: `sc_ledger_encode`

```c
/* core/include/smithcore.h */
#define SC_VERSION 2   /* was 1; the ROM now carries the ledger encoder */

/* Encode a ledger artifact to canonical v2 bytes. Reads the packed input buffer
 * (below), writes the canonical form (header + fixed field order; the three id
 * arrays sorted ascending by utf-8 bytes). Returns out_len, or SC_ERR. */
i32 sc_ledger_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);
```

### 3.1 Input packed buffer (host -> core)
Little-endian throughout, using the same primitives as the v2 wire format:
- `string` = `[u32 len][len UTF-8 bytes]`
- `strarray` = `[u32 count]` then `count` strings

Field order in the input buffer:
1. `serial` (string)
2. `intentVersionId` (string)
3. `scopeStatement` (string)
4. `workPerformed` (strarray, insertion order)
5. `laborRecorded` (strarray, insertion order)
6. `materialsUsed` (strarray, insertion order)
7. `contextualNotes` (strarray, insertion order)
8. `totalCostCents` (i64, 8 bytes LE)
9. `totalHoursCenti` (i64, 8 bytes LE)
10. `jobIds` (strarray, **unsorted** — core sorts)
11. `timeEntryIds` (strarray, **unsorted** — core sorts)
12. `chatMessageIds` (strarray, **unsorted** — core sorts)

### 3.2 Output (canonical v2 — identical to M2)
`[0x53 0x4d 0x43 0x01 0x02]` (header "SMC" + abi 0x01 + format 0x02)
then fields 1-9 **copied verbatim from the input** (the prefix region is byte-identical between
input and output), then fields 10-12 each emitted as `[u32 count]` + elements **sorted** by
unsigned UTF-8 bytes.

Note the header's third/fourth bytes remain the M2 format tag (`0x01 0x02` = abi-tag/format-2 of
the *encoding*), independent of `SC_VERSION`. The encoding format is unchanged from M2; only the
ROM's ABI version (the export surface) bumps to 2.

### 3.3 Algorithm (core/src/ledger.c)
1. Validate `in_len`/`out_cap` (return `SC_ERR` on any bounds/parse failure).
2. Walk the input to find the byte offset where field 10 (`jobIds`) begins — call it `prefix_end`
   (fields 1-9 are a contiguous, format-identical region).
3. Write the 5-byte header, then `memcpy` the input prefix `[0 .. prefix_end)` to the output.
4. For each of the three id arrays: read its elements as `(ptr,len)` pairs, sort them by unsigned
   UTF-8 bytes (small arrays; a simple insertion sort over a pointer array allocated from the
   arena — no libc `qsort`, no recursion), then emit `[u32 count]` + each sorted element as a
   `string`.
5. Return the total output length.

Memory: uses the existing arena model (`sc_reset`/`sc_alloc`), like the vclock exports. The sort
scratch (pointer array) is `sc_alloc`'d.

---

## 4. Host wiring (backend)

### 4.1 `backend/src/core/smithCore.ts`
- `EXPECTED_ABI` 1 -> 2.
- Add `sc_ledger_encode(i,il,o,oc): number` to `CoreExports`.
- Add `export function ledgerEncode(input: Buffer): Buffer` — stage `input`, `sc_alloc` an
  output buffer (cap = `input.length + 8` headroom: +5 header, sort never grows total), call the
  export, slice out `out_len` bytes. Throw on `SC_ERR`.

### 4.2 `backend/src/ledgerCanonical.ts`
- Add `packLedgerInput(a: SummaryArtifact): Buffer` — the input buffer of section 3.1 (strings +
  content arrays + i64 cents/centihours + **unsorted** id arrays). Reuses the existing
  `encStr`/`encStrArray`/`encI64` helpers; cents/centihours via the same `Math.round(x*100)`.
- Rename the current `encodeLedgerArtifactV2` body to `encodeLedgerArtifactV2Local` (the host
  fallback; unchanged bytes — still sorts id arrays itself).
- `encodeLedgerArtifactV2(a)` becomes the gated dispatcher:
  `SMITHCORE_ENABLED==='1' && isSmithCoreReady()` ? `ledgerEncode(packLedgerInput(a))` :
  `encodeLedgerArtifactV2Local(a)`.
- `ledgerHashV2(a) = sha256HexGated(encodeLedgerArtifactV2(a))` — unchanged signature; now ROM-
  backed when enabled.

This mirrors `vectorClock.ts`'s `merge`/`mergeLocal` split exactly.

---

## 5. Build

- `core/build.sh`: add `src/ledger.c` to the clang source list.
- Rebuild: `cd core && ./build.sh` -> new `dist/smithcore.wasm` + regenerated
  `dist/smithcore.wasm.sha256`, and the auto-synced `android/app/src/main/assets/smithcore.wasm`
  (the Android ROM bytes update even though Android delegation is deferred — the ROM is just
  newer; Android's M2 host encoder keeps working).
- The M1.5 APK size-delta gate will report the (small) size increase automatically.

---

## 6. Parity gate (fully runnable in this environment)

New backend Jest test `backend/src/__tests__/ledger-core-parity.test.ts` (after `initSmithCore()`
and with `SMITHCORE_ENABLED=1`):
1. **ROM ABI:** `sc_version() === 2`.
2. **C == golden bytes:** for every vector in `core/testdata/ledger-golden.json`,
   `ledgerEncode(packLedgerInput(artifact)).toString('hex') === vector.canonicalHex`.
3. **C hash == golden:** `ledgerHashV2(artifact) === vector.hashHex`.
4. **C == host fallback:** `ledgerEncode(packLedgerInput(a))` equals `encodeLedgerArtifactV2Local(a)`
   for every golden vector AND for N randomized fuzz artifacts (deterministic PRNG, like the M1
   vclock fuzz) — including multibyte strings and multi-element id arrays needing sort.
5. **ROM identity stamp:** loaded `smithcore.wasm` sha256 == `dist/smithcore.wasm.sha256` (the
   M2 smithcore-parity test already does this; the rebuilt stamp keeps it green).

Existing M2 `ledger-hash.test.ts` stays green: with the flag off, `encodeLedgerArtifactV2` is the
host path = the golden bytes.

---

## 7. Files touched

- **core/**: `src/ledger.c` (new), `include/smithcore.h` (SC_VERSION + export + input doc),
  `src/core_internal.h` (proto), `src/smithcore.c` (export wrapper), `build.sh` (+source),
  `dist/smithcore.wasm` + `dist/smithcore.wasm.sha256` (rebuilt), `README.md` (status).
- **android/**: `app/src/main/assets/smithcore.wasm` (auto-resynced by build.sh; no code change).
- **backend/**: `src/core/smithCore.ts`, `src/ledgerCanonical.ts`,
  `src/__tests__/ledger-core-parity.test.ts` (new).
- **docs/**: this spec.

---

## 8. Risks / open items

1. **wasm build reproducibility:** the rebuilt `smithcore.wasm` must be byte-stable (the stamp is
   committed). `build.sh` already pins `-O2 -nostdlib -ffreestanding --strip-all`; adding one
   source file changes the bytes deterministically. CI rebuilds and compares to the stamp.
2. **Output capacity:** sorting never increases total size (same elements, reordered) and the
   header adds 5 bytes; `cap = input.length + 8` is sufficient. The encoder still bounds-checks.
3. **ABI bump fallout:** every host that hard-checks `EXPECTED_ABI` must move to 2 in lockstep
   with shipping the new ROM. M3a updates the backend; Android does not hard-check ABI (it logs
   `nativeVersion` and gates on init success), so the resynced ABI-2 ROM is safe there.
4. **Android divergence window:** until the deferred Android delegation lands, Android computes
   the canonical bytes with its own M2 `LedgerCanon.kt`, the backend with the C core. They are
   proven identical by the shared golden vectors, so the moat holds; the duplication is the known
   M2 follow-up, narrowed to Android-only.
5. **Float op stays host-side:** `Math.round(x*100)` is the only non-core step; it is unchanged
   from M2 and the fuzz test (non-quantized inputs) guards it.

---

## 9. M3b direction (pre-shaped, not built)

Mesh is the second half of M3, its own brainstorm -> spec -> plan cycle. It builds on M3a's
deterministic core encoding for frame canonicalization, and on the security skill's F12.1 mesh
transport (AES-GCM-256 + HMAC-SHA256 encrypt-then-MAC + 5-min replay window + frame
`[version][messageId][timestamp][ciphertext][HMAC]`). When the Android toolchain is available,
the deferred Android ledger delegation (JNI binding for `sc_ledger_encode`) is folded in there.
