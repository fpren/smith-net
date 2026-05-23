# Tier System Sub-project 1 — tier source + enforcement — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real `users.tier` source and `requireTier`/`requireEntitlement` enforcement middleware (with the structured 403 contract) that consumes M4's entitlements, so tier gating refuses server-side.

**Architecture:** `users.tier` (backfilled from role) flows `StoredUser -> PublicUser` via the already-per-request `toPublicUser`, so enforcement reads the live tier off `req.user` (no extra query, no JWT staleness). `resolveEntitlements` switches role-based -> tier-based; `generateTokens` + `/me/entitlements` source tier from the column. New pure middleware checks `req.user.tier` against M4's `CAPS_BY_TIER` and returns the structured 403.

**Tech Stack:** TypeScript (Node/Express, raw `pg`, Jest), the M4 `entitlements.ts` (`Tier`, `CAPS_BY_TIER`, `ENTITLEMENT_BITS`, `TIER_CODE`), SQL migration.

**Spec:** `docs/superpowers/specs/2026-05-23-tier-system-1-source-and-enforcement-design.md`

**Conventions:** No emoji. Run backend tooling from `backend/`. Stage only the listed paths per commit; never `git add -A`/`.`/`-am`. Always `cd /Users/fegensprenelon/smith-net` (absolute) before git — the shell CWD persists.

---

## File Structure

Create:
- `backend/migrations/021_users_tier.sql` — `users.tier` column + role backfill.
- `backend/src/middleware/requireEntitlement.ts` — `requireTier` + `requireEntitlement` + `lowestTierFor`.
- `backend/src/__tests__/tier-enforcement.test.ts` — middleware unit tests.

Modify:
- `backend/src/auth.ts` — `StoredUser.tier`, `PublicUser.tier`, `toPublicUser`, `generateTokens` (tier-sourced).
- `backend/src/usersService.ts` — `UserRow.tier`, `rowToUser`, `colMap`.
- `backend/src/tierResolver.ts` — `resolveEntitlements(tier)` (was role).
- `backend/src/authRoutes.ts` — `/me/entitlements` uses `req.user.tier`.
- `backend/src/phase0Routes.ts` — representative `plan_compiler` gates.
- `backend/src/__tests__/entitlements-parity.test.ts` — `resolveEntitlements` is tier-based.

---

## Task 1: `users.tier` column + user-model threading

**Files:** Create `backend/migrations/021_users_tier.sql`; modify `backend/src/auth.ts`, `backend/src/usersService.ts`.

- [ ] **Step 1: Write the migration `backend/migrations/021_users_tier.sql`**

```sql
-- Tier sub-project 1: real tier source. Backfilled from role; the column is the
-- source of truth going forward (trials/billing/admin set it later).
ALTER TABLE users ADD COLUMN IF NOT EXISTS tier TEXT NOT NULL DEFAULT 'open';

UPDATE users SET tier = CASE role
  WHEN 'solo' THEN 'solo'
  WHEN 'team' THEN 'solo'
  WHEN 'lead' THEN 'advanced'
  WHEN 'foreman' THEN 'advanced'
  WHEN 'enterprise' THEN 'enterprise'
  WHEN 'admin' THEN 'enterprise'
  ELSE 'open'
END
WHERE tier = 'open';
```

- [ ] **Step 2: Add `tier` to the user types in `backend/src/auth.ts`**

`auth.ts` already has `import type { Tier } from './entitlements';` (added in M4). Make these edits:
- In `interface StoredUser`, add after `role: UserRole;`:
```ts
  tier: Tier;
```
- In `interface PublicUser`, add after `role: UserRole;`:
```ts
  tier: Tier;
```
- In `toPublicUser`, add `tier: user.tier,` to the returned object (after `role: user.role,`).

- [ ] **Step 3: Thread `tier` through `backend/src/usersService.ts`**

- Add the `Tier` import. Find the existing `import { ... StoredUser ... } from './auth';` (or wherever StoredUser is imported) and add a type import:
```ts
import type { Tier } from './entitlements';
```
- In `interface UserRow`, add after `role: string;`:
```ts
  tier: string;
```
- In `rowToUser`, add after `role: r.role as UserRole,`:
```ts
    tier: (r.tier as Tier) ?? 'open',
```
- In `updateUser`'s `colMap` (typed `Record<keyof StoredUser, string>`), add an entry (TypeScript requires it once `StoredUser` has `tier`):
```ts
      tier: 'tier',
```
(`createUser` needs no change: its `INSERT ... RETURNING *` omits `tier`, so the column `DEFAULT 'open'` applies and `rowToUser` maps it.)

- [ ] **Step 4: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors. (If `tsc` flags another `StoredUser`/`PublicUser` object literal missing `tier`, add `tier: 'open'` / `tier: user.tier` there — but the only constructors are `rowToUser` and `toPublicUser`, both handled above.)

- [ ] **Step 5: Confirm the non-DB suites still pass**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest smithcore-parity entitlements-parity sha256-gate ledger-hash 2>&1 | tail -10`
Expected: all PASS (these don't touch the users table; this confirms no compile/wiring breakage).

- [ ] **Step 6: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/migrations/021_users_tier.sql backend/src/auth.ts backend/src/usersService.ts
git commit -m "feat(tier): users.tier column + thread tier through user model (tier-1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: tier-based resolution + JWT/endpoint sourcing

**Files:** Modify `backend/src/tierResolver.ts`, `backend/src/auth.ts`, `backend/src/authRoutes.ts`, `backend/src/__tests__/entitlements-parity.test.ts`.

- [ ] **Step 1: Update the parity test to drive the tier-based signature (`entitlements-parity.test.ts`)**

Find the test `resolveEntitlements composes tier + bitmask + hash (foreman -> advanced)` and replace it with the tier-based version:
```ts
  it('resolveEntitlements composes tier + bitmask + hash (tier-based)', () => {
    const e = resolveEntitlements('advanced');
    expect(e.tier).toBe('advanced');
    expect(e.bitmask).toBe(15);
    expect(e.entitlementsHash).toBe(entitlementsHash(TIER_CODE.advanced, 15));
  });
```
Leave the separate `roleToTier maps every role` test unchanged (roleToTier still exists).

- [ ] **Step 2: Run it to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest entitlements-parity -t "tier-based" -v`
Expected: FAIL — `resolveEntitlements('advanced')` currently treats the arg as a role, so `roleToTier('advanced')` returns `'open'` (default) and `e.tier` is `'open'`, not `'advanced'`.

- [ ] **Step 3: Make `resolveEntitlements` tier-based (`backend/src/tierResolver.ts`)**

Replace `resolveEntitlements` with:
```ts
export function resolveEntitlements(tier: Tier): ResolvedEntitlements {
  const bitmask = CAPS_BY_TIER[tier];
  return { tier, bitmask, entitlementsHash: entitlementsHash(TIER_CODE[tier], bitmask) };
}
```
Add `Tier` to the existing import from `./entitlements` if not already imported there (the file already imports `Tier, TIER_CODE, CAPS_BY_TIER, entitlementsHash`). Keep `roleToTier` exactly as-is (now used only for the migration backfill mapping + its own test).

- [ ] **Step 4: Update the consumers**

- `backend/src/auth.ts` `generateTokens`: change `const ent = resolveEntitlements(user.role);` to `const ent = resolveEntitlements(user.tier);`.
- `backend/src/authRoutes.ts` `GET /me/entitlements`: change `resolveEntitlements(req.user!.role)` to `resolveEntitlements(req.user!.tier)`.

- [ ] **Step 5: Run the test + typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest entitlements-parity -v && npx tsc --noEmit -p tsconfig.json`
Expected: all entitlements-parity tests PASS (incl. the new tier-based one); no type errors.

- [ ] **Step 6: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/tierResolver.ts backend/src/auth.ts backend/src/authRoutes.ts \
  backend/src/__tests__/entitlements-parity.test.ts
git commit -m "feat(tier): resolveEntitlements is tier-based; JWT + endpoint source users.tier (tier-1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: enforcement middleware + structured 403

**Files:** Create `backend/src/middleware/requireEntitlement.ts`, `backend/src/__tests__/tier-enforcement.test.ts`.

- [ ] **Step 1: Write the failing test `backend/src/__tests__/tier-enforcement.test.ts`**

```ts
import { requireTier, requireEntitlement } from '../middleware/requireEntitlement';

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}
function run(mw: any, user: any) {
  const req: any = { user };
  const res = mockRes();
  let nexted = false;
  mw(req, res, () => { nexted = true; });
  return { res, nexted };
}

describe('requireTier', () => {
  it('401 when no user', () => {
    const { res, nexted } = run(requireTier('solo', 'plan_compiler'), undefined);
    expect(res.statusCode).toBe(401);
    expect(nexted).toBe(false);
  });
  it('403 structured when below min', () => {
    const { res, nexted } = run(requireTier('solo', 'plan_compiler'), { tier: 'open' });
    expect(nexted).toBe(false);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier gate: plan_compiler',
      code: 'tier_gate_exceeded',
      gate_id: 'plan_compiler',
      current_tier: 'open',
      details: { target_tier: 'solo' },
    });
  });
  it('next() at or above min', () => {
    expect(run(requireTier('solo', 'g'), { tier: 'solo' }).nexted).toBe(true);
    expect(run(requireTier('solo', 'g'), { tier: 'enterprise' }).nexted).toBe(true);
  });
});

describe('requireEntitlement', () => {
  it('401 when no user', () => {
    expect(run(requireEntitlement('plan_compiler', 'g'), undefined).res.statusCode).toBe(401);
  });
  it('plan_compiler: open 403 (target solo), solo+ next', () => {
    const mw = requireEntitlement('plan_compiler', 'plan_compiler');
    const r = run(mw, { tier: 'open' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('solo');
    expect(run(mw, { tier: 'solo' }).nexted).toBe(true);
    expect(run(mw, { tier: 'enterprise' }).nexted).toBe(true);
  });
  it('smithai_on_device: solo 403 (target advanced), advanced+ next', () => {
    const mw = requireEntitlement('smithai_on_device', 'ai_tab');
    const r = run(mw, { tier: 'solo' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('advanced');
    expect(run(mw, { tier: 'advanced' }).nexted).toBe(true);
  });
  it('crew_multiuser: advanced 403 (target enterprise), enterprise next', () => {
    const mw = requireEntitlement('crew_multiuser', 'crew_invite');
    const r = run(mw, { tier: 'advanced' });
    expect(r.res.statusCode).toBe(403);
    expect(r.res.body.details.target_tier).toBe('enterprise');
    expect(run(mw, { tier: 'enterprise' }).nexted).toBe(true);
  });
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest tier-enforcement -v`
Expected: FAIL — `Cannot find module '../middleware/requireEntitlement'`.

- [ ] **Step 3: Write `backend/src/middleware/requireEntitlement.ts`**

```ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { Tier, ENTITLEMENT_BITS, CAPS_BY_TIER } from '../entitlements';

const TIER_ORDER: Record<Tier, number> = { open: 0, solo: 1, advanced: 2, enterprise: 3 };
const TIER_ASC: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];

/** Lowest tier whose CAPS_BY_TIER includes the given bit. */
export function lowestTierFor(bit: number): Tier {
  for (const t of TIER_ASC) {
    if ((CAPS_BY_TIER[t] & (1 << bit)) !== 0) return t;
  }
  return 'enterprise'; // unreachable for a registered entitlement bit
}

/** Refuse unless the caller's tier is >= minTier. Structured 403 on refusal. */
export function requireTier(minTier: Tier, gateId: string) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    if (TIER_ORDER[req.user.tier] < TIER_ORDER[minTier]) {
      return res.status(403).json({
        error: `Tier gate: ${gateId}`,
        code: 'tier_gate_exceeded',
        gate_id: gateId,
        current_tier: req.user.tier,
        details: { target_tier: minTier },
      });
    }
    next();
  };
}

/** Refuse unless the caller's tier includes the named entitlement bit. */
export function requireEntitlement(entitlement: keyof typeof ENTITLEMENT_BITS, gateId: string) {
  const bit = ENTITLEMENT_BITS[entitlement];
  const target = lowestTierFor(bit);
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    if ((CAPS_BY_TIER[req.user.tier] & (1 << bit)) === 0) {
      return res.status(403).json({
        error: `Tier gate: ${gateId}`,
        code: 'tier_gate_exceeded',
        gate_id: gateId,
        current_tier: req.user.tier,
        details: { target_tier: target },
      });
    }
    next();
  };
}
```

- [ ] **Step 4: Run the test + typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest tier-enforcement -v && npx tsc --noEmit -p tsconfig.json`
Expected: all tier-enforcement tests PASS; no type errors.

- [ ] **Step 5: Commit (stage ONLY these paths)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/middleware/requireEntitlement.ts backend/src/__tests__/tier-enforcement.test.ts
git commit -m "feat(tier): requireTier/requireEntitlement middleware + structured 403 (tier-1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: representative wiring + full gate

**Files:** Modify `backend/src/phase0Routes.ts`.

- [ ] **Step 1: Gate the Phase 0 plan-compiler routes (`backend/src/phase0Routes.ts`)**

Add the import near the other imports:
```ts
import { requireEntitlement } from './middleware/requireEntitlement';
```
Add `requireEntitlement('plan_compiler', 'plan_compiler')` as middleware (between the path and the async handler) on these three routes. Read each current route first; insert the middleware argument:
- `phase0Router.post('/synthesize', requireEntitlement('plan_compiler', 'plan_compiler'), async (req, res) => { ... })`
- `phase0Router.post('/intents', requireEntitlement('plan_compiler', 'plan_compiler'), async (req, res) => { ... })`
- `phase0Router.post('/ledger/seal', requireEntitlement('plan_compiler', 'plan_compiler'), async (req, res) => { ... })`

(These run after the app-level `authenticateToken` in `api.ts`, so `req.user.tier` is populated. Open-tier callers now get the structured 403; solo+ pass. `requireConsoleTier` elsewhere is untouched.)

- [ ] **Step 2: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit -p tsconfig.json`
Expected: no errors.

- [ ] **Step 3: Full backend gate (both flag states)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity entitlements-parity tier-enforcement auditChain 2>&1 | tail -30`
Then: `cd /Users/fegensprenelon/smith-net/backend && SMITHCORE_ENABLED=1 npx jest smithcore-parity sha256-gate ledger-hash ledger-core-parity entitlements-parity tier-enforcement auditChain 2>&1 | tail -30`
Expected (both): smithcore-parity, sha256-gate, ledger-hash, ledger-core-parity, entitlements-parity, tier-enforcement PASS; auditChain SKIP (pg-gated). Zero failures. If anything truly FAILS, STOP and report BLOCKED.

- [ ] **Step 4: Commit (stage ONLY this path)**

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/phase0Routes.ts
git commit -m "feat(tier): gate Phase 0 plan-compiler routes with requireEntitlement (tier-1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review (completed during planning)

**Spec coverage:** users.tier column + backfill (Task 1 Step 1); tier on StoredUser/PublicUser/toPublicUser + usersService mapping (Task 1 Steps 2-3); resolveEntitlements tier-based + generateTokens/endpoint sourcing (Task 2); requireTier/requireEntitlement + structured 403 (Task 3); representative plan_compiler wiring (Task 4 Step 1); middleware unit tests (Task 3 Step 1); tier-based resolution test (Task 2 Step 1). Non-goals honored: no count caps, no telemetry, no trials/founder/billing, no client, requireConsoleTier untouched.

**Placeholder scan:** every code/test step is complete; commands have expected output; the only conditional ("if tsc flags another constructor") names the exact fix and notes the only two constructors are already handled. Migration apply is deferred-verify (no DATABASE_URL) — the middleware + resolution (the deliverables) are unit-tested without a DB.

**Type consistency:** `Tier` (from entitlements.ts) used in auth.ts (StoredUser/PublicUser), usersService.ts (rowToUser cast), tierResolver.ts (resolveEntitlements param), requireEntitlement.ts. `resolveEntitlements(tier: Tier)` (Task 2) consumed by generateTokens (`user.tier`) + endpoint (`req.user.tier`) — both `Tier`-typed via Task 1. `requireTier`/`requireEntitlement`/`lowestTierFor` defined in Task 3 and consumed in Task 4 + the test. `ENTITLEMENT_BITS`/`CAPS_BY_TIER`/`TIER_CODE` are the M4 exports. `req.user.tier` exists because Task 1 adds `PublicUser.tier` and `AuthenticatedRequest.user?: PublicUser`. The structured 403 body shape is identical in the middleware (Task 3 Step 3) and its test (Task 3 Step 1).

**Hand-verified:** `lowestTierFor(ENTITLEMENT_BITS.plan_compiler=0)` -> open has bit? `CAPS_BY_TIER.open=0` no; solo=3 has bit0 yes -> 'solo'. `smithai_on_device=2` -> solo=3 (0b11) no; advanced=15 (0b1111) yes -> 'advanced'. `crew_multiuser=5` -> advanced=15 no; enterprise=63 (0b111111) yes -> 'enterprise'. Matches the test expectations.
