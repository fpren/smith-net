# SmithCore M4 — Entitlements bitmask + `entitlementsHash` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pack each tier's boolean entitlements into a fixed-bit bitmask, encode a canonical record via a new `sc_entitlements_encode` ROM export and hash it through the ROM (`entitlementsHash`), then stamp `tier` + `entitlementsHash` as JWT claims and expose `GET /api/auth/me/entitlements`.

**Architecture:** Mirrors M3a. The C core gains a tiny `sc_entitlements_encode` (ABI 2->3) that prepends a format byte to a host-packed `[tierCode][bitmask]` input. Backend `entitlements.ts` holds the append-only bit registry + `CAPS_BY_TIER`, with a ROM-gated encoder (host fallback) and `entitlementsHash`. `tierResolver.ts` derives tier from role (provisional) and composes the resolved entitlements, which `generateTokens` stamps into the access JWT and the new endpoint returns. A per-tier golden fixture proves C == host == golden.

**Tech Stack:** C11 freestanding -> wasm32 (wasi-sdk clang at `~/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos`), TypeScript (Node/Express, Jest, jsonwebtoken), the committed `core/testdata/entitlements-golden.json` oracle.

**Spec:** `docs/superpowers/specs/2026-05-23-smithcore-m4-entitlements-bitmask-design.md`

**Conventions:** No emoji. Run backend tooling from `backend/`. The working tree has a large UNRELATED uncommitted SmithAI/UI pile — every commit `git add`s only the listed paths; never `git add -A`/`.`/`-am`. Always `cd /Users/fegensprenelon/smith-net` (absolute) before git, since the shell CWD persists.

---

## File Structure

Create:
- `core/src/entitlements.c` — the canonical entitlements record encoder.
- `backend/src/entitlements.ts` — bit registry, `CAPS_BY_TIER`, tier codes, ROM-gated encode + `entitlementsHash`.
- `backend/src/tierResolver.ts` — `roleToTier` (provisional) + `resolveEntitlements`.
- `backend/scripts/gen-entitlements-golden.ts` — golden generator (writes both copies).
- `core/testdata/entitlements-golden.json` + `android/app/src/androidTest/assets/entitlements-golden.json` — golden + drift copy.
- `backend/src/__tests__/entitlements-parity.test.ts` — parity gate.

Modify:
- `core/include/smithcore.h` — `SC_VERSION` 2->3; declare `sc_entitlements_encode`; document input.
- `core/src/core_internal.h` — `entitlements_encode` prototype.
- `core/src/smithcore.c` — exported `sc_entitlements_encode` wrapper.
- `core/build.sh` — add `src/entitlements.c`.
- `core/dist/smithcore.wasm` + `.sha256`, `android/app/src/main/assets/smithcore.wasm` — rebuilt.
- `backend/src/core/smithCore.ts` — `EXPECTED_ABI` 3; `sc_entitlements_encode` in `CoreExports`; `entitlementsEncode` binding.
- `backend/src/auth.ts` — `TokenPayload` claims; `generateTokens` stamps them.
- `backend/src/authRoutes.ts` — `GET /me/entitlements`.
- `core/README.md` — status.

---

## Task 1: C `sc_entitlements_encode` + ABI 3 + backend binding (atomic)

The ABI bump must be atomic (rebuilt ROM is ABI 3; backend `EXPECTED_ABI` must move to 3 together).

**Files:** Create `core/src/entitlements.c`; modify `core/include/smithcore.h`, `core/src/core_internal.h`, `core/src/smithcore.c`, `core/build.sh`, `backend/src/core/smithCore.ts`; rebuilt wasm artifacts.

- [ ] **Step 1: Create `core/src/entitlements.c`**

```c
/*
 * entitlements.c -- canonical entitlements record (M4). A tiny packed struct:
 * input  [u8 tierCode][u32 bitmask LE]              (5 bytes, host-packed)
 * output [u8 format=0x01][u8 tierCode][u32 bitmask LE] (6 bytes)
 * The core owns the canonical byte layout; the host owns the tier->bits policy
 * (CAPS_BY_TIER). Freestanding, no libc.
 */
#include "core_internal.h"

i32 entitlements_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap) {
    if (in_len != 5) return SC_ERR;   /* u8 tierCode + u32 bitmask */
    if (out_cap < 6) return SC_ERR;
    out[0] = 0x01;                     /* entitlements record format v1 */
    for (i32 i = 0; i < 5; i++) out[1 + i] = in[i];
    return 6;
}
```

- [ ] **Step 2: Edit `core/include/smithcore.h`**

Change `#define SC_VERSION 2` to `#define SC_VERSION 3`. Add after the `sc_ledger_encode` declaration, before `#endif`:
```c
/* --- entitlements (M4 packed bitmask) --- */
/* Encode the canonical entitlements record. Input (host-packed, little-endian):
 *   [u8 tierCode][u32 bitmask]
 * Output: [u8 format=0x01][u8 tierCode][u32 bitmask LE]  (6 bytes).
 * The host owns the tier->bits policy; the core owns the byte layout.
 * Returns out_len (6), or SC_ERR. */
i32 sc_entitlements_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);
```

- [ ] **Step 3: Edit `core/src/core_internal.h`**

Add before `#endif`:
```c
/* entitlements.c -- canonical entitlements record. in = [tierCode][bitmask LE];
 * out = [0x01][tierCode][bitmask LE] (6 bytes). out_len/-1. */
i32 entitlements_encode(const u8 *in, i32 in_len, u8 *out, i32 out_cap);
```

- [ ] **Step 4: Edit `core/src/smithcore.c`**

Add after the `sc_ledger_encode` wrapper (the last export):
```c
/* --- entitlements --- */
__attribute__((export_name("sc_entitlements_encode")))
i32 sc_entitlements_encode(i32 in, i32 il, i32 o, i32 oc) {
    if (il < 0 || oc < 0) return SC_ERR;
    return entitlements_encode(P(in), il, P(o), oc);
}
```

- [ ] **Step 5: Edit `core/build.sh`**

Add `src/entitlements.c` to the clang source list. Change:
```
  src/sha256.c src/vclock.c src/ledger.c src/smithcore.c \
```
to:
```
  src/sha256.c src/vclock.c src/ledger.c src/entitlements.c src/smithcore.c \
```

- [ ] **Step 6: Rebuild the ROM**

Run: `cd /Users/fegensprenelon/smith-net/core && ./build.sh`
Expected: `[+] built dist/smithcore.wasm`, a new `[+] ROM sha256:`, and the Android sync line. If "no wasm-capable clang", `export WASI_SDK=$HOME/.smithnet-toolchain/wasi-sdk-33.0-arm64-macos` then re-run. Report the new sha256.

- [ ] **Step 7: Edit `backend/src/core/smithCore.ts`**

Change `const EXPECTED_ABI = 2;` to `const EXPECTED_ABI = 3;`. Add to `CoreExports` after `sc_ledger_encode`:
```ts
  sc_entitlements_encode(i: number, il: number, o: number, oc: number): number;
```
Add at the end of the file:
```ts
/** Canonical entitlements record encode via the ROM. Input is the host-packed
 *  [u8 tierCode][u32 bitmask LE] buffer; returns [0x01][tierCode][bitmask LE]. */
export function entitlementsEncode(input: Buffer): Buffer {
  const e = core();
  e.sc_reset();
  const ip = stage(input);
  const op = e.sc_alloc(8);
  if (op === 0) throw new Error('smithcore arena OOM');
  const n = e.sc_entitlements_encode(ip, input.length, op, 8);
  if (n === SC_ERR || n < 0) throw new Error('sc_entitlements_encode failed');
  return Buffer.from(mem().slice(op, op + n));
}
```

- [ ] **Step 8: Verify the existing gate is green at ABI 3**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest smithcore-parity ledger-core-parity ledger-hash sha256-gate -v 2>&1 | tail -20`
Expected: all PASS (proves the ABI-3 ROM loads with `EXPECTED_ABI=3`, the ROM stamp matches, and M1-M3a behavior is intact).

- [ ] **Step 9: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add core/src/entitlements.c core/include/smithcore.h core/src/core_internal.h \
  core/src/smithcore.c core/build.sh core/dist/smithcore.wasm \
  core/dist/smithcore.wasm.sha256 android/app/src/main/assets/smithcore.wasm \
  backend/src/core/smithCore.ts
git commit -m "feat(core): sc_entitlements_encode ROM export + ABI 3 (M4)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: entitlements module + tierResolver + parity gate

**Files:** Create `backend/src/entitlements.ts`, `backend/src/tierResolver.ts`, `backend/scripts/gen-entitlements-golden.ts`, the golden JSONs, `backend/src/__tests__/entitlements-parity.test.ts`.

- [ ] **Step 1: Create `backend/src/entitlements.ts`**

```ts
import { isSmithCoreReady, entitlementsEncode } from './core/smithCore';
import { sha256HexGated } from './sha256Gate';

export type Tier = 'open' | 'solo' | 'advanced' | 'enterprise';

// Append-only bit registry — never renumber or reuse a freed bit.
export const ENTITLEMENT_BITS = {
  plan_compiler: 0,
  cord_state_model: 1,
  smithai_on_device: 2,
  advanced_template: 3,
  enterprise_template: 4,
  crew_multiuser: 5,
} as const;

export const TIER_CODE: Record<Tier, number> = { open: 0, solo: 1, advanced: 2, enterprise: 3 };

export const CAPS_BY_TIER: Record<Tier, number> = {
  open: 0,
  solo: (1 << 0) | (1 << 1),                                   // 3
  advanced: (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3),         // 15
  enterprise: (1 << 0) | (1 << 1) | (1 << 2) | (1 << 3) | (1 << 4) | (1 << 5), // 63
};

function coreActive(): boolean {
  return process.env.SMITHCORE_ENABLED === '1' && isSmithCoreReady();
}

/** Host-packed input for sc_entitlements_encode: [u8 tierCode][u32 bitmask LE]. */
export function packEntitlementsInput(tierCode: number, bitmask: number): Buffer {
  const b = Buffer.allocUnsafe(5);
  b.writeUInt8(tierCode, 0);
  b.writeUInt32LE(bitmask >>> 0, 1);
  return b;
}

/** Host fallback / parity reference: [0x01][tierCode][u32 bitmask LE]. */
export function encodeEntitlementsRecordLocal(tierCode: number, bitmask: number): Buffer {
  const b = Buffer.allocUnsafe(6);
  b.writeUInt8(0x01, 0);
  b.writeUInt8(tierCode, 1);
  b.writeUInt32LE(bitmask >>> 0, 2);
  return b;
}

/** Canonical record: ROM-backed when enabled+ready, else host fallback (identical bytes). */
export function encodeEntitlementsRecord(tierCode: number, bitmask: number): Buffer {
  return coreActive()
    ? entitlementsEncode(packEntitlementsInput(tierCode, bitmask))
    : encodeEntitlementsRecordLocal(tierCode, bitmask);
}

export function entitlementsHash(tierCode: number, bitmask: number): string {
  return sha256HexGated(encodeEntitlementsRecord(tierCode, bitmask));
}
```

- [ ] **Step 2: Create `backend/src/tierResolver.ts`**

Takes role as a string (UserRole values are strings) to avoid a circular import with `auth.ts`.
```ts
import { Tier, TIER_CODE, CAPS_BY_TIER, entitlementsHash } from './entitlements';

/** Provisional: derive tier from role until a real profiles.tier / billing source
 *  exists. Single source of truth — no consumer reads tier elsewhere. UserRole
 *  values are strings ('solo','team','lead','foreman','enterprise','admin'). */
export function roleToTier(role: string): Tier {
  switch (role) {
    case 'solo': return 'solo';
    case 'team': return 'solo';
    case 'lead': return 'advanced';
    case 'foreman': return 'advanced';
    case 'enterprise': return 'enterprise';
    case 'admin': return 'enterprise';
    default: return 'open';
  }
}

export interface ResolvedEntitlements {
  tier: Tier;
  bitmask: number;
  entitlementsHash: string;
}

export function resolveEntitlements(role: string): ResolvedEntitlements {
  const tier = roleToTier(role);
  const bitmask = CAPS_BY_TIER[tier];
  return { tier, bitmask, entitlementsHash: entitlementsHash(TIER_CODE[tier], bitmask) };
}
```

- [ ] **Step 3: Create the golden generator `backend/scripts/gen-entitlements-golden.ts`**

```ts
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { Tier, TIER_CODE, CAPS_BY_TIER, encodeEntitlementsRecordLocal } from '../src/entitlements';

const tiers: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];
const vectors = tiers.map((tier) => {
  const tierCode = TIER_CODE[tier];
  const bitmask = CAPS_BY_TIER[tier];
  const record = encodeEntitlementsRecordLocal(tierCode, bitmask);
  return {
    tier, tierCode, bitmask,
    recordHex: record.toString('hex'),
    hashHex: crypto.createHash('sha256').update(record).digest('hex'),
  };
});
const json = JSON.stringify({ vectors }, null, 2) + '\n';
for (const p of [
  path.resolve(__dirname, '../../core/testdata/entitlements-golden.json'),
  path.resolve(__dirname, '../../android/app/src/androidTest/assets/entitlements-golden.json'),
]) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, json);
}
console.log(`wrote ${vectors.length} entitlements vectors`);
```

- [ ] **Step 4: Generate the fixture**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsx scripts/gen-entitlements-golden.ts`
Expected: `wrote 4 entitlements vectors`. Confirm both JSONs exist and are identical: `cd /Users/fegensprenelon/smith-net && diff core/testdata/entitlements-golden.json android/app/src/androidTest/assets/entitlements-golden.json` (no output).

- [ ] **Step 5: Create the parity test `backend/src/__tests__/entitlements-parity.test.ts`**

```ts
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import { initSmithCore, entitlementsEncode } from '../core/smithCore';
import {
  packEntitlementsInput, encodeEntitlementsRecordLocal, entitlementsHash,
  CAPS_BY_TIER, TIER_CODE,
} from '../entitlements';
import { roleToTier, resolveEntitlements } from '../tierResolver';

const goldenPath = path.resolve(__dirname, '../../../core/testdata/entitlements-golden.json');
const androidGoldenPath = path.resolve(
  __dirname, '../../../android/app/src/androidTest/assets/entitlements-golden.json');
const golden = JSON.parse(fs.readFileSync(goldenPath, 'utf8'));

beforeAll(async () => { await initSmithCore(); });

describe('M4: entitlements bitmask + hash parity', () => {
  const prevFlag = process.env.SMITHCORE_ENABLED;
  afterAll(() => {
    if (prevFlag === undefined) delete process.env.SMITHCORE_ENABLED;
    else process.env.SMITHCORE_ENABLED = prevFlag;
  });

  it('CAPS_BY_TIER matches the spec bitmasks', () => {
    expect(CAPS_BY_TIER.open).toBe(0);
    expect(CAPS_BY_TIER.solo).toBe(3);
    expect(CAPS_BY_TIER.advanced).toBe(15);
    expect(CAPS_BY_TIER.enterprise).toBe(63);
  });

  it('C core encode == golden recordHex == host fallback; hash == golden == node', () => {
    process.env.SMITHCORE_ENABLED = '1';
    try {
      for (const v of golden.vectors) {
        const c = entitlementsEncode(packEntitlementsInput(v.tierCode, v.bitmask));
        expect(`${v.tier}:${c.toString('hex')}`).toBe(`${v.tier}:${v.recordHex}`);
        expect(c.equals(encodeEntitlementsRecordLocal(v.tierCode, v.bitmask))).toBe(true);
        expect(`${v.tier}:${entitlementsHash(v.tierCode, v.bitmask)}`).toBe(`${v.tier}:${v.hashHex}`);
        expect(crypto.createHash('sha256').update(Buffer.from(v.recordHex, 'hex')).digest('hex'))
          .toBe(v.hashHex);
      }
    } finally {
      if (prevFlag === undefined) delete process.env.SMITHCORE_ENABLED;
      else process.env.SMITHCORE_ENABLED = prevFlag;
    }
  });

  it('roleToTier maps every role (+ unknown -> open)', () => {
    expect(roleToTier('solo')).toBe('solo');
    expect(roleToTier('team')).toBe('solo');
    expect(roleToTier('lead')).toBe('advanced');
    expect(roleToTier('foreman')).toBe('advanced');
    expect(roleToTier('enterprise')).toBe('enterprise');
    expect(roleToTier('admin')).toBe('enterprise');
    expect(roleToTier('nope')).toBe('open');
  });

  it('resolveEntitlements composes tier + bitmask + hash (foreman -> advanced)', () => {
    const e = resolveEntitlements('foreman');
    expect(e.tier).toBe('advanced');
    expect(e.bitmask).toBe(15);
    expect(e.entitlementsHash).toBe(entitlementsHash(TIER_CODE.advanced, 15));
  });

  it('drift guard: android golden copy is byte-identical', () => {
    expect(fs.readFileSync(goldenPath).equals(fs.readFileSync(androidGoldenPath))).toBe(true);
  });
});
```

- [ ] **Step 6: Run the parity gate + typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest entitlements-parity -v && npx tsc --noEmit -p tsconfig.json`
Expected: 5 tests PASS; no type errors.

- [ ] **Step 7: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/entitlements.ts backend/src/tierResolver.ts \
  backend/scripts/gen-entitlements-golden.ts core/testdata/entitlements-golden.json \
  android/app/src/androidTest/assets/entitlements-golden.json \
  backend/src/__tests__/entitlements-parity.test.ts
git commit -m "feat(core): entitlements bitmask + tierResolver + parity gate (M4)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: JWT claims + `/api/auth/me/entitlements` endpoint

**Files:** Modify `backend/src/auth.ts`, `backend/src/authRoutes.ts`.

- [ ] **Step 1: Add the claims to `TokenPayload` (`backend/src/auth.ts`)**

In `interface TokenPayload`, add after `type: 'access' | 'refresh';`:
```ts
  tier?: string;
  entitlementsHash?: string;
```

- [ ] **Step 2: Stamp the claims in `generateTokens` (`backend/src/auth.ts`)**

Add the import at the top of `auth.ts`:
```ts
import { resolveEntitlements } from './tierResolver';
```
In `generateTokens`, change the `accessPayload` construction to include the resolved claims (leave `refreshPayload` unchanged):
```ts
  const ent = resolveEntitlements(user.role);
  const accessPayload: TokenPayload = {
    userId: user.id,
    email: user.email,
    role: user.role,
    type: 'access',
    tier: ent.tier,
    entitlementsHash: ent.entitlementsHash,
  };
```

- [ ] **Step 3: Add the endpoint (`backend/src/authRoutes.ts`)**

Add the import near the other imports:
```ts
import { resolveEntitlements } from './tierResolver';
```
Add immediately after the existing `authRouter.get('/me', ...)` handler:
```ts
authRouter.get('/me/entitlements', authenticateToken, (req: AuthenticatedRequest, res: Response) => {
  res.json(resolveEntitlements(req.user!.role));
});
```
(Reachable at `/api/auth/me/entitlements`, since `authRouter` is mounted at `/api/auth`. `Response` and `AuthenticatedRequest` are already imported in this file.)

- [ ] **Step 4: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors. (Watch for a circular-import error: `tierResolver` imports only `entitlements`, never `auth`, so `auth -> tierResolver -> entitlements` is acyclic. If `tsc` reports a cycle, confirm `tierResolver.ts` does NOT import from `./auth`.)

- [ ] **Step 5: Confirm no auth-test regression**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest entitlements-parity 2>&1 | tail -8`
Expected: still 5 PASS. (The claim correctness is covered by `resolveEntitlements` tests in Task 2; `generateTokens`/the endpoint are thin wiring over it. No supertest/pg harness exists, so the route is verified by `tsc` + inspection; optional manual smoke once a DB is available: `curl -s localhost:3000/api/auth/me/entitlements -H "Authorization: Bearer <token>" | jq` -> `{ "tier": "...", "bitmask": N, "entitlementsHash": "..." }`.)

- [ ] **Step 6: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/auth.ts backend/src/authRoutes.ts
git commit -m "feat(core): stamp tier + entitlementsHash JWT claims + /me/entitlements (M4)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: full gate + README status

**Files:** Modify `core/README.md`.

- [ ] **Step 1: Run the full backend gate (default flag state)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity entitlements-parity auditChain 2>&1 | tail -30`
Expected: smithcore-parity, sha256-gate, ledger-hash, ledger-core-parity, entitlements-parity PASS; auditChain SKIP (pg-gated). Zero failures. If anything truly FAILS, STOP and report BLOCKED; do not edit the README.

- [ ] **Step 2: Run the full gate with the ROM path forced on**

Run: `cd /Users/fegensprenelon/smith-net/backend && SMITHCORE_ENABLED=1 npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity entitlements-parity auditChain 2>&1 | tail -30`
Expected: same counts (the flag never changes a hash).

- [ ] **Step 3: Update `core/README.md` status**

Read `core/README.md`; replace the `## Status` bullet list with:
```markdown
- M1: vector clock + SHA-256 through the ROM; backend wired + green.
- M1.5: APK size-delta CI gate.
- M2: ledger seal (canonical v2 encoding) + audit-chain checksum hash through the
  ROM; per-entry hash_version with version-aware /api/ledger/verify; golden-vector
  parity across the TS + Kotlin encoders.
- M3a: v2 ledger encoder moved into the ROM (sc_ledger_encode, ABI 2); backend
  delegates with the host encoder as readiness fallback; C output proven == the
  M2 golden vectors. Android JNI delegation deferred.
- M3b (mesh): deferred -- Android+crypto, not a ROM fit, no current consumer.
- M4: entitlements bitmask (per-tier, append-only) + canonical record via
  sc_entitlements_encode (ABI 3), hashed through the ROM; tierResolver derives
  tier from role (provisional) and stamps tier + entitlementsHash JWT claims +
  GET /api/auth/me/entitlements; per-tier golden parity. Enforcement/client/
  profiles.tier deferred.
- Next: M5 portal/iOS/Pi shells (+ the deferred Android JNI delegations).
```
Preserve the rest of the file.

- [ ] **Step 4: Commit (stage ONLY this path)**

```bash
cd /Users/fegensprenelon/smith-net
git add core/README.md
git commit -m "docs(core): mark M4 (entitlements bitmask) complete

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed during planning)

**Spec coverage:** `sc_entitlements_encode` + input format (Task 1 Steps 1-2); SC_VERSION 2->3 + backend EXPECTED_ABI sync (Task 1 Steps 2,7); core-owns-layout/host-owns-policy (Task 1 Step 1 + Task 2 `packEntitlementsInput`/`CAPS_BY_TIER`); bit registry + tier codes + CAPS_BY_TIER (Task 2 Step 1); ROM-gated encode + host fallback + `entitlementsHash` (Task 2 Step 1); `tierResolver` role->tier + resolve (Task 2 Step 2); JWT claims (Task 3 Steps 1-2); endpoint (Task 3 Step 3); golden + drift guard (Task 2 Steps 3-5); parity gate incl. flag on/off (Task 2 Step 5 + Task 4); README (Task 4). Non-goals honored: no enforcement middleware, no client, no profiles.tier, no Android JNI.

**Placeholder scan:** every code/test step is complete; commands have expected output; WASI_SDK fallback is the exact command; the DB/route manual smoke is explicitly optional with the unit-tested guarantee that covers it (resolveEntitlements). auditChain skip is expected.

**Type consistency:** `entitlementsEncode(input: Buffer): Buffer` (Task 1) used in `entitlements.ts` (Task 2) + the test. `packEntitlementsInput`/`encodeEntitlementsRecordLocal`/`entitlementsHash`/`CAPS_BY_TIER`/`TIER_CODE`/`Tier` defined in Task 2 Step 1 and consumed by tierResolver (Step 2), the generator (Step 3), and the test (Step 5). `roleToTier`/`resolveEntitlements`/`ResolvedEntitlements` defined in Task 2 Step 2, consumed by `auth.ts` + `authRoutes.ts` (Task 3) and the test. C `entitlements_encode` (Task 1 Step 1) matches the `core_internal.h` proto (Step 3) and the `sc_entitlements_encode` wrapper (Step 4). Record layout `[0x01][tierCode][u32 LE]` is identical in the C (Step 1), the host fallback (Task 2 `encodeEntitlementsRecordLocal`), and the golden generator. `roleToTier` takes a string -> no `auth` import in tierResolver -> acyclic (Task 3 Step 4 note).

**Hand-verified:** open tier -> input `[00][00000000]` -> output `01 00 00000000` = `010000000000` (6 bytes); solo `[01][03000000]` -> `01 01 03000000` = `010103000000`; the test asserts these via the committed golden recordHex.
