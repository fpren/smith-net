# SmithCore M3a — Packed Structs (ledger encoder in C) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the v2 ledger canonical encoding into the ROM as a new `sc_ledger_encode` export, have the backend delegate to it (host encoder kept as readiness fallback), and prove the C output is byte-identical to the committed M2 golden vectors.

**Architecture:** A new freestanding C source `core/src/ledger.c` parses a host-packed field buffer and emits the canonical v2 bytes — the `SMC` header, fields 1-9 copied verbatim, and the three id arrays sorted by unsigned UTF-8 bytes (the core owns the sort; the host keeps only the float->integer rounding). The ROM ABI bumps `SC_VERSION` 1->2; backend `encodeLedgerArtifactV2` becomes a flag+readiness-gated dispatcher mirroring `vectorClock.ts`'s `merge`/`mergeLocal`.

**Tech Stack:** C11 freestanding -> wasm32 (wasi-sdk clang, already at `~/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos`), TypeScript (Node + Jest), the committed `core/testdata/ledger-golden.json` as the conformance oracle.

**Spec:** `docs/superpowers/specs/2026-05-23-smithcore-m3a-packed-structs-design.md`

**Conventions:** No emoji anywhere (ASCII only). Backend tests run from `backend/`. The working tree has a large UNRELATED uncommitted SmithAI/UI pile — every commit must `git add` only the exact listed paths; never `git add -A`/`.`/`-am`.

---

## File Structure

Create:
- `core/src/ledger.c` — the canonical v2 encoder (parse host-packed input, sort id arrays, emit canonical bytes).
- `backend/src/__tests__/ledger-core-parity.test.ts` — proves C encode == golden == host fallback.

Modify:
- `core/include/smithcore.h` — `SC_VERSION` 1->2; declare `sc_ledger_encode`; document the input packed format.
- `core/src/core_internal.h` — add the `ledger_encode` prototype.
- `core/src/smithcore.c` — add the exported `sc_ledger_encode` wrapper.
- `core/build.sh` — add `src/ledger.c` to the clang sources.
- `core/dist/smithcore.wasm` + `core/dist/smithcore.wasm.sha256` — rebuilt artifacts (committed).
- `android/app/src/main/assets/smithcore.wasm` — auto-resynced by build.sh (binary; no code change).
- `backend/src/core/smithCore.ts` — `EXPECTED_ABI` 1->2; add `sc_ledger_encode` to `CoreExports`; add `ledgerEncode(input)` binding.
- `backend/src/ledgerCanonical.ts` — add `packLedgerInput`; export the host encoder as `encodeLedgerArtifactV2Local`; make `encodeLedgerArtifactV2` a gated dispatcher.
- `core/README.md` — status.

---

## Task 1: C encoder + ABI bump + backend binding (atomic)

The ABI bump must be atomic: the rebuilt ROM is `SC_VERSION=2`, and the backend's `EXPECTED_ABI` must move to 2 in the same change or `initSmithCore` throws on mismatch. This task lands the C encoder, rebuilds the ROM, and syncs the backend ABI + binding.

**Files:**
- Create: `core/src/ledger.c`
- Modify: `core/include/smithcore.h`, `core/src/core_internal.h`, `core/src/smithcore.c`, `core/build.sh`, `backend/src/core/smithCore.ts`
- Rebuilt: `core/dist/smithcore.wasm`, `core/dist/smithcore.wasm.sha256`, `android/app/src/main/assets/smithcore.wasm`

- [ ] **Step 1: Write `core/src/ledger.c`**

```c
/*
 * ledger.c -- canonical v2 ledger encoding (the "packed struct"). Reads the
 * host-packed input buffer (see smithcore.h) and emits the canonical v2 bytes:
 * the "SMC" header, fields 1-9 copied verbatim, then the three id arrays sorted
 * ascending by unsigned utf-8 bytes. The sort is the cross-language drift point
 * the core centralizes; the host keeps only the float->integer rounding (the
 * core is float-free). Byte layout is identical to the M2 host encoder, proven
 * by the committed golden vectors.
 */
#include "core_internal.h"

#define MAX_IDS 1024   /* per id-array element cap (ids per artifact are tiny) */

typedef struct { const u8 *ptr; u32 len; } Str;

static u32 rd_u32(const u8 *p) {
    return (u32)p[0] | ((u32)p[1] << 8) | ((u32)p[2] << 16) | ((u32)p[3] << 24);
}
static void wr_u32(u8 *p, u32 v) { p[0]=(u8)v; p[1]=(u8)(v>>8); p[2]=(u8)(v>>16); p[3]=(u8)(v>>24); }

/* Unsigned byte compare; shorter is less on a prefix tie (matches Buffer.compare). */
static int cmp_bytes(const u8 *a, u32 alen, const u8 *b, u32 blen) {
    u32 n = alen < blen ? alen : blen;
    for (u32 i = 0; i < n; i++) if (a[i] != b[i]) return a[i] < b[i] ? -1 : 1;
    if (alen == blen) return 0;
    return alen < blen ? -1 : 1;
}

/* Skip one string [u32 len][bytes]; advance *off. Returns 0 ok, -1 overflow. */
static int skip_str(const u8 *buf, i32 len, i32 *off) {
    if (*off + 4 > len) return -1;
    u32 l = rd_u32(buf + *off); *off += 4;
    if (l > (u32)(len - *off)) return -1;   /* *off <= len here; no signed overflow */
    *off += (i32)l;
    return 0;
}

/* Skip one strarray [u32 count][count strings]; advance *off. */
static int skip_strarray(const u8 *buf, i32 len, i32 *off) {
    if (*off + 4 > len) return -1;
    u32 c = rd_u32(buf + *off); *off += 4;
    for (u32 i = 0; i < c; i++) if (skip_str(buf, len, off) < 0) return -1;
    return 0;
}

/* Read a strarray into Str[] (ptr/len pairs). Returns count, or -1. */
static int read_strarray(const u8 *buf, i32 len, i32 *off, Str *out, int cap) {
    if (*off + 4 > len) return -1;
    u32 c = rd_u32(buf + *off); *off += 4;
    if (c > (u32)cap) return -1;
    for (u32 i = 0; i < c; i++) {
        if (*off + 4 > len) return -1;
        u32 l = rd_u32(buf + *off); *off += 4;
        if (l > (u32)(len - *off)) return -1;
        out[i].ptr = buf + *off; out[i].len = l; *off += (i32)l;
    }
    return (int)c;
}

/* Insertion sort Str[] ascending by unsigned utf-8 bytes (n is small). */
static void sort_strs(Str *s, int n) {
    for (int i = 1; i < n; i++) {
        Str key = s[i];
        int j = i - 1;
        while (j >= 0 && cmp_bytes(s[j].ptr, s[j].len, key.ptr, key.len) > 0) {
            s[j + 1] = s[j]; j--;
        }
        s[j + 1] = key;
    }
}

/* Emit a strarray: [u32 count] + each [u32 len][bytes]. Advance *off. 0/-1. */
static int emit_strarray(u8 *out, i32 out_cap, i32 *off, const Str *s, int n) {
    if (*off + 4 > out_cap) return -1;
    wr_u32(out + *off, (u32)n); *off += 4;
    for (int i = 0; i < n; i++) {
        if ((i64)*off + 4 + (i64)s[i].len > (i64)out_cap) return -1;
        wr_u32(out + *off, s[i].len); *off += 4;
        for (u32 k = 0; k < s[i].len; k++) out[*off + k] = s[i].ptr[k];
        *off += (i32)s[i].len;
    }
    return 0;
}

i32 ledger_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap) {
    if (in_len < 0 || out_cap < 0) return SC_ERR;

    /* Walk fields 1-9 (3 strings + 4 strarrays + 2 i64) to find prefix_end. */
    i32 off = 0;
    for (int i = 0; i < 3; i++) if (skip_str(in, in_len, &off) < 0) return SC_ERR;
    for (int i = 0; i < 4; i++) if (skip_strarray(in, in_len, &off) < 0) return SC_ERR;
    if (off + 16 > in_len) return SC_ERR;   /* totalCostCents + totalHoursCenti */
    off += 16;
    i32 prefix_end = off;                    /* fields 1-9 = [0, prefix_end) */

    /* Header + verbatim prefix copy. */
    if ((i64)5 + (i64)prefix_end > (i64)out_cap) return SC_ERR;
    i32 woff = 0;
    out[woff++] = 0x53; out[woff++] = 0x4d; out[woff++] = 0x43; /* "SMC" */
    out[woff++] = 0x01;                       /* encoding abi tag (matches M2 golden) */
    out[woff++] = 0x02;                       /* format v2 */
    for (i32 i = 0; i < prefix_end; i++) out[woff + i] = in[i];
    woff += prefix_end;

    /* Three id arrays: read, sort, emit. */
    Str ids[MAX_IDS];
    for (int k = 0; k < 3; k++) {
        int n = read_strarray(in, in_len, &off, ids, MAX_IDS);
        if (n < 0) return SC_ERR;
        sort_strs(ids, n);
        if (emit_strarray(out, out_cap, &woff, ids, n) < 0) return SC_ERR;
    }
    if (off != in_len) return SC_ERR;         /* exact consumption */
    return woff;
}
```

- [ ] **Step 2: Edit `core/include/smithcore.h`**

Change the version macro:
```c
#define SC_VERSION 2
```
(It is currently `#define SC_VERSION 1`.)

Add a new section after the sha256 declaration (after the `i32 sc_sha256(...)` line, before `#endif`):
```c
/* --- ledger (M3a packed struct) --- */
/* Encode a ledger artifact to canonical v2 bytes. The host packs the fields
 * into the input buffer below (little-endian; string = [u32 len][bytes];
 * strarray = [u32 count] then count strings):
 *   serial, intentVersionId, scopeStatement            ; 3x string
 *   workPerformed, laborRecorded, materialsUsed, contextualNotes ; 4x strarray (insertion order)
 *   totalCostCents, totalHoursCenti                     ; 2x i64 LE
 *   jobIds, timeEntryIds, chatMessageIds                ; 3x strarray (UNSORTED; core sorts)
 * Output is the canonical v2 form: "SMC" + 0x01 + 0x02, fields 1-9 verbatim,
 * then the three id arrays sorted ascending by unsigned utf-8 bytes. The
 * encoding format is byte-identical to the M2 host encoder (golden vectors).
 * Returns out_len, or SC_ERR. */
i32 sc_ledger_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);
```

- [ ] **Step 3: Edit `core/src/core_internal.h`**

Add before `#endif`:
```c
/* ledger.c -- canonical v2 ledger encoding. in = host-packed fields; out =
 * canonical bytes (header + verbatim prefix + sorted id arrays). out_len/-1. */
i32 ledger_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap);
```

- [ ] **Step 4: Edit `core/src/smithcore.c`**

Add after the `sc_sha256` wrapper (the last export in the file):
```c
/* --- ledger --- */
__attribute__((export_name("sc_ledger_encode")))
i32 sc_ledger_encode(i32 in, i32 il, i32 o, i32 oc) {
    if (il < 0 || oc < 0) return SC_ERR;
    return ledger_encode(P(in), il, P(o), oc);
}
```

- [ ] **Step 5: Edit `core/build.sh`**

Add `src/ledger.c` to the clang source list. Change the line:
```
  src/sha256.c src/vclock.c src/smithcore.c \
```
to:
```
  src/sha256.c src/vclock.c src/ledger.c src/smithcore.c \
```

- [ ] **Step 6: Rebuild the ROM**

Run: `cd core && ./build.sh`
Expected: prints `[+] built dist/smithcore.wasm (<bytes>)`, `[+] ROM sha256: <hash>`, and `[+] synced ROM -> ../android/app/src/main/assets/smithcore.wasm`. If it errors with "no wasm-capable clang", export the SDK first: `export WASI_SDK=$HOME/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos` then re-run. The build must succeed with `-Werror` (the C above is warning-clean).

- [ ] **Step 7: Edit `backend/src/core/smithCore.ts`**

Change the ABI constant:
```ts
const EXPECTED_ABI = 2;
```
(It is currently `1`.)

Add to the `CoreExports` interface (after the `sc_sha256` line):
```ts
  sc_ledger_encode(i: number, il: number, o: number, oc: number): number;
```

Add this exported function at the end of the file (after `sha256`):
```ts
/** Canonical v2 ledger encode via the ROM. Input is the host-packed field
 *  buffer (see ledgerCanonical.packLedgerInput / smithcore.h). Returns the
 *  canonical bytes. */
export function ledgerEncode(input: Buffer): Buffer {
  const e = core();
  e.sc_reset();
  const ip = stage(input);
  const cap = input.length + 8; // +5 header; sorting never grows total size
  const op = e.sc_alloc(cap);
  if (op === 0) throw new Error('smithcore arena OOM');
  const n = e.sc_ledger_encode(ip, input.length, op, cap);
  if (n === SC_ERR || n < 0) throw new Error('sc_ledger_encode failed');
  return Buffer.from(mem().slice(op, op + n));
}
```

- [ ] **Step 8: Verify nothing regressed (existing gate still green at ABI 2)**

Run: `cd backend && npx jest smithcore-parity ledger-hash sha256-gate -v 2>&1 | tail -20`
Expected: all PASS. This proves the rebuilt ABI-2 ROM loads (`initSmithCore` with `EXPECTED_ABI=2`), the ROM-identity stamp matches the regenerated `.sha256`, and vclock/sha256/host-ledger behavior is unchanged. (`ledger-hash` still uses the host path since the dispatcher is added in Task 2.)

- [ ] **Step 9: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add core/src/ledger.c core/include/smithcore.h core/src/core_internal.h \
  core/src/smithcore.c core/build.sh core/dist/smithcore.wasm \
  core/dist/smithcore.wasm.sha256 android/app/src/main/assets/smithcore.wasm \
  backend/src/core/smithCore.ts
git commit -m "feat(core): sc_ledger_encode ROM export + ABI 2 (M3a)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: backend delegation + parity gate

**Files:**
- Modify: `backend/src/ledgerCanonical.ts`
- Test: `backend/src/__tests__/ledger-core-parity.test.ts`

- [ ] **Step 1: Write the failing parity test**

Create `backend/src/__tests__/ledger-core-parity.test.ts`:
```ts
import * as fs from 'fs';
import * as path from 'path';
import { SummaryArtifact } from '../types';
import { initSmithCore, ledgerEncode } from '../core/smithCore';
import { packLedgerInput, encodeLedgerArtifactV2Local, ledgerHashV2 } from '../ledgerCanonical';

const goldenPath = path.resolve(__dirname, '../../../core/testdata/ledger-golden.json');
const golden = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));

function artifactFrom(o: any): SummaryArtifact {
  return { id: 'x', createdAt: 0, ...o } as SummaryArtifact;
}

beforeAll(async () => { await initSmithCore(); });

describe('M3a: C ledger encoder parity', () => {
  it('C sc_ledger_encode reproduces every golden vector (bytes + hash)', () => {
    process.env.SMITHCORE_ENABLED = '1';
    try {
      for (const v of golden.vectors) {
        const a = artifactFrom(v.artifact);
        const cBytes = ledgerEncode(packLedgerInput(a));
        expect(`${v.label}:${cBytes.toString('hex')}`).toBe(`${v.label}:${v.canonicalHex}`);
        expect(`${v.label}:${ledgerHashV2(a)}`).toBe(`${v.label}:${v.hashHex}`);
      }
    } finally {
      delete process.env.SMITHCORE_ENABLED;
    }
  });

  it('C encode == host fallback over golden + randomized fuzz', () => {
    for (const v of golden.vectors) {
      const a = artifactFrom(v.artifact);
      expect(ledgerEncode(packLedgerInput(a)).equals(encodeLedgerArtifactV2Local(a))).toBe(true);
    }
    // Deterministic PRNG so any failure reproduces.
    let seed = 0x12345678;
    const rng = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 0x100000000; };
    const pool = ['a', 'ab', 'abc', 'cafe', 'café', '日本', 'm1', 'zz', 'té-1', 'job-1', 'job-2', '🔥'];
    const pick = () => pool[Math.floor(rng() * pool.length)];
    const arr = () => Array.from({ length: Math.floor(rng() * 4) }, pick);
    for (let i = 0; i < 500; i++) {
      const a = artifactFrom({
        serial: pick(), intentVersionId: pick(), scopeStatement: pick(),
        workPerformed: arr(), laborRecorded: arr(), materialsUsed: arr(), contextualNotes: arr(),
        totalCost: Math.floor(rng() * 1e7) / 100, totalHours: Math.floor(rng() * 1e5) / 100,
        jobIds: arr(), timeEntryIds: arr(), chatMessageIds: arr(),
      });
      expect(ledgerEncode(packLedgerInput(a)).equals(encodeLedgerArtifactV2Local(a))).toBe(true);
    }
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && npx jest ledger-core-parity -v`
Expected: FAIL — `packLedgerInput` / `encodeLedgerArtifactV2Local` are not exported from `../ledgerCanonical`.

- [ ] **Step 3: Refactor `backend/src/ledgerCanonical.ts`**

Add the import at the top (after the existing imports):
```ts
import { isSmithCoreReady, ledgerEncode } from './core/smithCore';
```

Add `packLedgerInput` (after `sortedByUtf8`, before the encoder). NOTE the id arrays are passed UNSORTED — the core sorts them:
```ts
/** Pack a SummaryArtifact into the input buffer the ROM's sc_ledger_encode reads
 *  (see smithcore.h). Same field order as v2 but WITHOUT the header and with the
 *  id arrays left UNSORTED (the core sorts them). The host keeps the one float op
 *  (Math.round to integer minor units), since the core is float-free. */
export function packLedgerInput(a: SummaryArtifact): Buffer {
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return Buffer.concat([
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
```

Rename the current `export function encodeLedgerArtifactV2(...)` to `encodeLedgerArtifactV2Local` (keep the body and its comment exactly; just change the name and keep it exported as the fallback + parity reference):
```ts
/** Host-side v2 encoder. Kept as the readiness fallback and the parity
 *  reference; identical bytes to the ROM's sc_ledger_encode (golden vectors). */
export function encodeLedgerArtifactV2Local(a: SummaryArtifact): Buffer {
  const header = Buffer.from([0x53, 0x4d, 0x43, ABI, FORMAT_V2]); // "SMC"
  // Synthesizer pre-quantizes both fields to 2 decimal places (Math.round(x*100)/100),
  // so any residual IEEE epsilon is smaller than 0.005 and Math.round resolves it to
  // the same integer on all hosts. Non-quantized inputs are not a production path.
  const cents = BigInt(Math.round(a.totalCost * 100));
  const centihours = BigInt(Math.round(a.totalHours * 100));
  return Buffer.concat([
    header,
    encStr(a.serial),
    encStr(a.intentVersionId),
    encStr(a.scopeStatement),
    encStrArray(a.workPerformed),
    encStrArray(a.laborRecorded),
    encStrArray(a.materialsUsed),
    encStrArray(a.contextualNotes),
    encI64(cents),
    encI64(centihours),
    encStrArray(sortedByUtf8(a.jobIds)),
    encStrArray(sortedByUtf8(a.timeEntryIds)),
    encStrArray(sortedByUtf8(a.chatMessageIds)),
  ]);
}

/** v2 canonical encoding. ROM-backed when SMITHCORE_ENABLED and the ROM is
 *  loaded; otherwise the host fallback (proven identical by the golden vectors).
 *  Mirrors vectorClock.ts's merge/mergeLocal gating. */
export function encodeLedgerArtifactV2(a: SummaryArtifact): Buffer {
  if (process.env.SMITHCORE_ENABLED === '1' && isSmithCoreReady()) {
    return ledgerEncode(packLedgerInput(a));
  }
  return encodeLedgerArtifactV2Local(a);
}
```

`ledgerHashV2` is unchanged (it already calls `encodeLedgerArtifactV2`).

- [ ] **Step 4: Run the new parity gate**

Run: `cd backend && npx jest ledger-core-parity -v`
Expected: PASS (2 tests — golden bytes+hash via the C core, and C == host over golden + 500 fuzz artifacts).

- [ ] **Step 5: Run the M2 ledger tests to confirm no regression**

Run: `cd backend && npx jest ledger-hash -v && SMITHCORE_ENABLED=1 npx jest ledger-hash -v`
Expected: PASS both runs. Flag-off uses the host path (golden bytes); flag-on routes `encodeLedgerArtifactV2` through the C core, which produces the same bytes (so the M2 "golden vector" and "ROM sha == node sha" tests still hold).

- [ ] **Step 6: Typecheck**

Run: `cd backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 7: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/ledgerCanonical.ts backend/src/__tests__/ledger-core-parity.test.ts
git commit -m "feat(core): backend delegates ledger encode to the ROM + parity gate (M3a)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: full gate + README status

**Files:**
- Modify: `core/README.md`

- [ ] **Step 1: Run the full backend gate (default flag state)**

Run: `cd backend && npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity auditChain 2>&1 | tail -30`
Expected: smithcore-parity, sha256-gate, ledger-hash, ledger-core-parity PASS; auditChain SKIP (pg-gated). Zero failures. If anything truly FAILS, STOP and report BLOCKED — do not edit the README.

- [ ] **Step 2: Run the full gate with the ROM path forced on**

Run: `cd backend && SMITHCORE_ENABLED=1 npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity auditChain 2>&1 | tail -30`
Expected: same pass/skip counts (the flag never changes a hash).

- [ ] **Step 3: Update `core/README.md` status**

Replace the `## Status` bullet list with:
```markdown
- M1: vector clock + SHA-256 through the ROM; backend wired + green.
- M1.5: APK size-delta CI gate.
- M2: ledger seal (canonical v2 encoding) + audit-chain checksum hash through the
  ROM; per-entry hash_version with version-aware /api/ledger/verify; golden-vector
  parity across the TS + Kotlin encoders.
- M3a: v2 ledger encoder moved into the ROM (sc_ledger_encode, ABI 2); backend
  delegates with the host encoder as readiness fallback; C output proven == the
  M2 golden vectors. Android JNI delegation deferred.
- Next: M3b mesh; then M4 entitlements bitmask, M5 portal/iOS/Pi shells.
```
Read the current `## Status` section first and replace only its bullet list, preserving the rest of the file. (If the `## Hosts` table still says Android binding is WAMR + SmithCore.kt, leave it; the ledger delegation note belongs only in Status for now.)

- [ ] **Step 4: Commit (stage ONLY this path)**

```bash
cd /Users/fegensprenelon/smith-net
git add core/README.md
git commit -m "docs(core): mark M3a (ledger encoder in the ROM) complete

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed during planning)

**Spec coverage:** sc_ledger_encode export + input format (Task 1 Steps 1-2), SC_VERSION 1->2 + backend EXPECTED_ABI sync (Task 1 Steps 2,7), core owns layout+header+id-sort / host keeps rounding (Task 1 Step 1 `ledger_encode` + Task 2 `packLedgerInput`), build wiring + ROM rebuild + android resync (Task 1 Steps 5-6,9), backend `ledgerEncode` binding (Task 1 Step 7), gated dispatcher with host fallback (Task 2 Step 3), parity gate C==golden==host+fuzz (Task 2 Step 1), full gate both flag states (Task 3), README (Task 3). Non-goals honored: no Android JNI, no general framework, no format change (output stays `534d4301 02 ...`; verified by golden), host encoders kept as fallback.

**Placeholder scan:** every code/test step has complete content; commands have expected output; the only environment-conditional note (WASI_SDK export) includes the exact command. auditChain skip is expected (pg-gated).

**Type consistency:** `ledgerEncode(input: Buffer): Buffer` defined in Task 1, used in Task 2's `encodeLedgerArtifactV2` and the test. `packLedgerInput`/`encodeLedgerArtifactV2Local` defined in Task 2 Step 3, used in Task 2 Step 1's test (written first — it fails until Step 3, the intended RED). C `ledger_encode` (Task 1 Step 1) matches the `core_internal.h` proto (Step 3) and the `sc_ledger_encode` wrapper (Step 4). Input field order in `packLedgerInput` (TS) matches the `ledger_encode` parse order (C) matches the smithcore.h doc — all 12 fields, id arrays last and unsorted.

**Hand-verified:** the empty golden vector through `ledger_encode` yields header(5) + 44 verbatim prefix zeros + 3x `[u32 0]` = `534d430102` + 112 zero hex chars, matching `core/testdata/ledger-golden.json`.
