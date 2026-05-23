# SmithCore M4 — Entitlements bitmask + deterministic `entitlementsHash`

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Predecessors:** M1 (vclock+sha256 ROM), M1.5 (size gate), M2 (ledger/audit hash), M3a (ledger encoder in the ROM, ABI 2). M3b (mesh) deferred (Android+crypto, not a ROM fit, no current consumer).
**Roadmap position:** M4 of the SmithCore roadmap.

---

## 0. North star

SmithCore is ROM-like infrastructure: one deterministic `smithcore.wasm`. M4 represents each tier's
boolean entitlements as a fixed-bit, append-only **bitmask** (the Game Boy TM/HM-learnset pattern),
and computes a deterministic `entitlementsHash` over a canonical record **through the ROM**, so the
server (which stamps the hash into the JWT) and any client (which will later verify it) agree
byte-for-byte. Small, simple, efficient: one tiny new core export, one new backend module, two JWT
claims, one endpoint.

---

## 1. Scope

In scope (core + backend, fully buildable/testable here — wasi-sdk present):
1. Core export `sc_entitlements_encode` (ABI 2 -> 3) that packs the canonical entitlements record.
2. `backend/src/entitlements.ts` — the bit registry, `CAPS_BY_TIER`, tier codes, host encode + `entitlementsHash` (ROM-gated, host fallback).
3. `backend/src/tierResolver.ts` — single source of truth: `roleToTier` + `resolveEntitlements`.
4. JWT claims `tier` + `entitlementsHash` (in `generateTokens`) and `GET /api/me/entitlements`.
5. Parity gate: core encode == host fallback == committed golden vectors (one per tier); hash via ROM == node crypto; flag on/off identical.

Non-goals (explicitly deferred):
- A real `profiles.tier` column + billing/upgrade flow. **Tier is derived from role** for now (see §3); the mapping lives in exactly one place (`tierResolver`) so a real tier source swaps in later without touching consumers.
- Enforcement middleware (`requireCap`/`requireTier`) and client-side UI gating / `EntitlementsRepository`.
- Android JNI delegation for `sc_entitlements_encode` (Android can't build here; deferred like M3a's Android side). Android is not a consumer in M4.
- Numeric caps (active_jobs, pdf_sends) — those are limits, not boolean entitlements, and are not in the bitmask.

---

## 2. Entitlements model (`backend/src/entitlements.ts`, new)

### 2.1 Bit registry (append-only — never renumber or reuse a freed bit)
| bit | entitlement |
|---|---|
| 0 | `plan_compiler` |
| 1 | `cord_state_model` |
| 2 | `smithai_on_device` |
| 3 | `advanced_template` |
| 4 | `enterprise_template` |
| 5 | `crew_multiuser` |

(Numeric caps and the always-on `standard_template` / Open-only forced branding are intentionally NOT bits — they are not discriminating boolean unlocks.)

### 2.2 Tier codes (fixed, append-only) and `CAPS_BY_TIER`
| tier | code | bitmask | meaning |
|---|---|---|---|
| open | 0 | `0b000000` (0) | none |
| solo | 1 | `0b000011` (3) | plan_compiler, cord_state_model |
| advanced | 2 | `0b001111` (15) | + smithai_on_device, advanced_template |
| enterprise | 3 | `0b111111` (63) | + enterprise_template, crew_multiuser |

### 2.3 Canonical record + hash
Canonical record (6 bytes): `[u8 format=0x01][u8 tierCode][u32 bitmask LE]`.
`entitlementsHash = sha256HexGated(record)` — through the ROM when `SMITHCORE_ENABLED` + ready, else node crypto (byte-identical, proven since M1).

---

## 3. `tierResolver.ts` (new — single source of truth)

- `roleToTier(role: UserRole): Tier` — SOLO->solo, TEAM_MEMBER->solo, TEAM_LEAD->advanced, FOREMAN->advanced, ENTERPRISE->enterprise, ADMIN->enterprise.
  **Judgment call:** there is no `profiles.tier` today; `requireConsoleTier` already infers capability from role. Deriving tier from role here (a) needs no migration, (b) produces varied entitlements for existing users, and (c) is the documented temporary seam — when a real tier/billing source lands, only this function changes. The tier-gating skill warns tier != role, so this is explicitly provisional.
- `resolveEntitlements(role: UserRole): { tier: Tier; bitmask: number; entitlementsHash: string }` — `roleToTier` -> `CAPS_BY_TIER[tier]` -> encode -> hash.

---

## 4. Core: `sc_entitlements_encode` (ABI 2 -> 3)

The record is trivial, but per decision we add the export for symmetry with `sc_ledger_encode` and to be M5-iOS-ready. The core owns the canonical byte layout; the host owns the policy (which bits a tier gets).

```c
/* core/include/smithcore.h */
#define SC_VERSION 3   /* was 2 (M3a); ROM now also carries the entitlements encoder */

/* Encode the canonical entitlements record. Input (host-packed, little-endian):
 *   [u8 tierCode][u32 bitmask]
 * Output: [u8 format=0x01][u8 tierCode][u32 bitmask LE]  (6 bytes).
 * Returns out_len (6), or SC_ERR. */
i32 sc_entitlements_encode(i32 in_ptr, i32 in_len, i32 out_ptr, i32 out_cap);
```
Implementation (`core/src/entitlements.c`, new): validate `in_len == 5` and `out_cap >= 6`; write `0x01`, copy the input 5 bytes verbatim; return 6. Freestanding, no libc, bounds-checked. (Mirrors the `core/src/ledger.c` style; reuse the `i64`/`u8` typedefs already in `smithcore.h`.)

---

## 5. Host wiring (backend)

### 5.1 `backend/src/core/smithCore.ts`
- `EXPECTED_ABI` 2 -> 3.
- Add `sc_entitlements_encode(i,il,o,oc): number` to `CoreExports`.
- Add `export function entitlementsEncode(input: Buffer): Buffer` — stage `input` (5 bytes), `sc_alloc(8)`, call, slice `out_len`. Throw on `SC_ERR`. (Same shape as `ledgerEncode`.)

### 5.2 `backend/src/entitlements.ts`
- `packEntitlementsInput(tierCode, bitmask): Buffer` -> `[u8 tierCode][u32 bitmask LE]` (5 bytes).
- `encodeEntitlementsRecordLocal(tierCode, bitmask): Buffer` -> `[0x01][tierCode][u32 LE]` (host fallback / parity reference).
- `encodeEntitlementsRecord(tierCode, bitmask): Buffer` -> gated dispatcher: `coreActive() ? entitlementsEncode(packEntitlementsInput(...)) : encodeEntitlementsRecordLocal(...)` (mirror `ledgerCoreActive`).
- `entitlementsHash(tierCode, bitmask): string = sha256HexGated(encodeEntitlementsRecord(...))`.

### 5.3 `backend/src/auth.ts`
- Extend `TokenPayload` with optional `tier?: string` and `entitlementsHash?: string`.
- In `generateTokens(user)`: compute `resolveEntitlements(user.role)` and add `tier` + `entitlementsHash` to the **access** payload (refresh payload unchanged — it only needs identity). One place; `refreshAccessToken` flows through `generateTokens`.

### 5.4 Endpoint
`GET /api/me/entitlements` (authenticated, behind `authenticateToken`): returns `resolveEntitlements(req.user.role)` = `{ tier, bitmask, entitlementsHash }`. Mount in the existing authed router (e.g. `authRoutes.ts` or `api.ts`). This is the client's future verify/cache path.

---

## 6. Build

- `core/build.sh`: add `src/entitlements.c` to the clang sources.
- `cd core && ./build.sh` -> new `dist/smithcore.wasm` (ABI 3) + regenerated `.sha256` + auto-synced Android assets. M1.5 size gate reports the (tiny) delta.

---

## 7. Parity gate (fully runnable here)

`backend/src/__tests__/entitlements-parity.test.ts` (after `initSmithCore`, `SMITHCORE_ENABLED=1`):
1. `sc_version() === 3` (implicit: `initSmithCore` succeeds with `EXPECTED_ABI=3`).
2. For each tier: `CAPS_BY_TIER[tier]` equals the spec bitmask (0/3/15/63).
3. For each tier: `entitlementsEncode(packEntitlementsInput(code,mask))` == the committed golden `recordHex` == `encodeEntitlementsRecordLocal(code,mask)`; and `entitlementsHash` == golden `hashHex` == node crypto over the record.
4. `roleToTier` maps every `UserRole` to the expected tier.
5. flag on/off: `entitlementsHash` identical (the flag never changes the hash).

Golden fixture `core/testdata/entitlements-golden.json` (4 tiers), generated by a committed script (mirrors `gen-ledger-golden.ts`), written to both `core/testdata/` and `android/app/src/androidTest/assets/` with a drift-guard test (consistent with M2/M3a, ready for the deferred Android verify).

---

## 8. Files touched

- **core/**: `src/entitlements.c` (new), `include/smithcore.h` (SC_VERSION 3 + decl), `src/core_internal.h` (proto), `src/smithcore.c` (wrapper), `build.sh` (+source), `dist/smithcore.wasm` + `.sha256` (rebuilt), `README.md` (status). `android/app/src/main/assets/smithcore.wasm` (auto-resynced).
- **backend/**: `core/smithCore.ts` (ABI 3 + binding), `entitlements.ts` (new), `tierResolver.ts` (new), `auth.ts` (claims), the authed router (endpoint), `scripts/gen-entitlements-golden.ts` (new), `__tests__/entitlements-parity.test.ts` (new).
- **testdata/**: `core/testdata/entitlements-golden.json` + `android/app/src/androidTest/assets/entitlements-golden.json`.
- **docs/**: this spec.

---

## 9. Risks / open items

1. **ABI bump 2->3 atomicity:** `SC_VERSION 3` (C) + `EXPECTED_ABI 3` (smithCore.ts) + the rebuilt ABI-3 wasm must land together (same first commit), or `initSmithCore` throws on mismatch and every smithcore test errors. (Same pattern as M3a's 1->2.)
2. **roleToTier is provisional:** tier-from-role is a temporary seam, not the real subscription model. Documented in §3; isolated to `tierResolver`. The role->tier mapping (esp. FOREMAN/TEAM_LEAD -> advanced) is a guess and should be revisited when a real tier source exists.
3. **JWT size / claims:** two small string/number claims; negligible. `entitlementsHash` is derived from public tier policy (not secret) — safe to carry, but do not log full tokens (per the security skill).
4. **rebuild determinism:** the committed wasm must equal a fresh `build.sh` (CI compares to the stamp), as in M3a.
5. **No DB migration:** tier is role-derived; nothing persists. If a real `profiles.tier` is added later it is a separate migration + an `X-Tier-Changed` refresh flow (out of scope).

---

## 10. M5 direction (pre-shaped, not built)

M5 = portal / iOS / Pi shells loading the same ROM. The deferred Android JNI delegations
(`sc_ledger_encode` from M3a, `sc_entitlements_encode` here) and the mesh beacon frame (M3b) are
natural M5 work once a second host that consumes them exists, making the golden vectors enforce
real cross-host parity rather than single-host regression.
