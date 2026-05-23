# Tier System — Sub-project 1: tier source + server-authoritative enforcement

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** M4 (entitlements bitmask + `CAPS_BY_TIER` + `tierResolver` + JWT `tier`/`entitlementsHash` claims).
**Position:** Sub-project 1 of the decomposed tier system (the foundation). Later sub-projects: count caps, `gate_hit_events` telemetry, trials, founder seats, billing webhooks, client UI.

---

## 0. Why this sub-project

M4 computes per-tier entitlements and stamps `tier` + `entitlementsHash` into the JWT, but nothing
*enforces* anything: tier is derived from role (provisional), there is no real tier source, and the
only gate is the role-based `requireConsoleTier`. This sub-project adds the real tier source
(`users.tier`) and the server-authoritative `requireTier` / `requireEntitlement` middleware (with the
structured 403 contract) that consumes M4 — the first piece that makes tier gating actually refuse.

The tier-gating skill is the authority for the contract (4-tier ladder, `gate_id`, structured 403,
server-authoritative, "tier gates SHOW+LOCK+CTA / role gates HIDE").

---

## 1. Scope

In scope (backend, verifiable here):
1. `users.tier` column (migration, backfilled from role).
2. `tier` threaded through `StoredUser` -> `PublicUser` (`toPublicUser`) and the `usersService` row mapping; new users default to `'open'`.
3. `resolveEntitlements` switched from role-based to **tier-based**; `generateTokens` + `GET /me/entitlements` source tier from the column / `req.user.tier`.
4. `requireTier(minTier, gateId)` + `requireEntitlement(entitlement, gateId)` middleware with the structured 403.
5. Representative wiring: `requireEntitlement('plan_compiler')` on the Phase 0 plan-compiler routes.
6. Unit tests for the middleware + tier-based resolution.

Non-goals (later sub-projects, explicitly deferred):
- Count-based caps (active jobs = 1 open, PDF sends = 5/mo open) and the numeric 403 (`limit`/`current`).
- `gate_hit_events` telemetry.
- Trials, founder seats, billing webhooks (the mechanisms that *set* `users.tier` beyond the role backfill + column default).
- All client UI (`EntitlementsRepository`, `LockedFeatureOverlay`, upgrade UX, `X-Tier-Changed`).
- Rewiring the existing role-based `requireConsoleTier` (left as-is to avoid changing console-route behavior).

---

## 2. Architecture: enforcement reads the LIVE tier

`authenticateToken` already loads the full user row every request (`userStore.getUserById` ->
`toPublicUser`). So `req.user.tier` comes from the `users.tier` column at **no extra query cost**,
and enforcement always sees the *current* tier — never a stale JWT (the access token lives 7 days).
The JWT `tier` claim (M4) stays for the client; it is now also column-sourced. Enforcement middleware
is therefore a **pure function of `req.user`** (no DB read, no async), making it unit-testable with
mock `req`/`res`/`next`.

---

## 3. Tier source

### 3.1 Migration `backend/migrations/021_users_tier.sql`
```sql
-- Sub-project 1: real tier source. Backfilled from role; the column is the
-- source of truth going forward (trials/billing/admin will set it later).
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
WHERE tier = 'open';   -- only touch un-set rows; idempotent
```
(Mirrors M4's `roleToTier`. The `WHERE tier='open'` keeps it idempotent and never downgrades a tier
that a future billing flow has already set.)

### 3.2 Types
- `StoredUser.tier: Tier` (import `Tier` from `./entitlements`).
- `PublicUser.tier: Tier`.
- `toPublicUser`: add `tier: user.tier`.
- `usersService` row -> `StoredUser` mapping: `tier: (row.tier as Tier) ?? 'open'`.
- `createUser` / register: rely on the column `DEFAULT 'open'` (no explicit insert needed); if the
  insert lists columns explicitly, add `tier` with `'open'`.

---

## 4. Resolution evolves (M4 -> tier-based)

`backend/src/tierResolver.ts`:
- `resolveEntitlements(tier: Tier): ResolvedEntitlements` — takes a tier directly (was role).
- `roleToTier(role: string): Tier` — retained, used by the migration backfill (documented as the
  seed mapping, not a per-request path).

Consumers:
- `auth.ts generateTokens(user)`: `resolveEntitlements(user.tier)`.
- `authRoutes.ts GET /me/entitlements`: `resolveEntitlements(req.user!.tier)`.
- M4 `entitlements-parity.test.ts`: the `resolveEntitlements('foreman')` assertion changes to
  `resolveEntitlements('advanced')` (tier in, not role); `roleToTier` keeps its own mapping test.

---

## 5. Enforcement middleware

`backend/src/middleware/requireEntitlement.ts` (new):

```ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { Tier, ENTITLEMENT_BITS, CAPS_BY_TIER } from '../entitlements';

const TIER_ORDER: Record<Tier, number> = { open: 0, solo: 1, advanced: 2, enterprise: 3 };

/** Lowest tier whose CAPS_BY_TIER includes the given bit. */
function lowestTierFor(bit: number): Tier { /* scan open->enterprise; return first with bit set */ }

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

The structured 403 matches the tier-gating skill's contract (`code: 'tier_gate_exceeded'`, `gate_id`,
`current_tier`, `details.target_tier`); the client maps `gate_id` to the right `LockedFeatureOverlay`
variant (client work is a later sub-project).

---

## 6. Representative wiring

In `backend/src/phase0Routes.ts`, gate the plan-compiler routes:
- `POST /synthesize` -> `requireEntitlement('plan_compiler', 'plan_compiler')`
- `POST /intents` -> same
- `POST /ledger/seal` -> same

(These are Phase 0 scaffolding, so gating them is low-risk and proves the mechanism end-to-end:
open-tier callers get the structured 403; solo+ pass. `requireConsoleTier` elsewhere is untouched.)

---

## 7. Tests (fully runnable here)

`backend/src/__tests__/tier-enforcement.test.ts` (new) — middleware as pure functions with mock
`req`/`res`/`next`:
- `requireTier`: open user vs `requireTier('solo')` -> 403 with the exact body (code, gate_id,
  current_tier:'open', details.target_tier:'solo'); solo/advanced/enterprise -> `next()`; missing
  `req.user` -> 401.
- `requireEntitlement('plan_compiler')`: open -> 403 (target_tier 'solo'); solo+ -> next.
  `requireEntitlement('smithai_on_device')`: solo -> 403 (target_tier 'advanced'); advanced+ -> next.
  `requireEntitlement('crew_multiuser')`: advanced -> 403 (target_tier 'enterprise'); enterprise -> next.
- `lowestTierFor` returns the correct target tier per entitlement bit.

Extend `entitlements-parity.test.ts`: `resolveEntitlements(tier)` is tier-based (e.g.
`resolveEntitlements('advanced')` -> bitmask 15); keep a `roleToTier` mapping test.

Deferred-verify (no DATABASE_URL here): the migration apply, `usersService` reading `row.tier`, and
`generateTokens` sourcing `user.tier`. The pure resolution + middleware (the deliverables) are fully
covered.

---

## 8. Files touched

- **backend/migrations/**: `021_users_tier.sql` (new).
- **backend/src/**: `auth.ts` (StoredUser/PublicUser/toPublicUser `tier`; generateTokens tier-sourced),
  `usersService.ts` (row mapping reads `tier`; createUser default), `tierResolver.ts`
  (`resolveEntitlements(tier)`), `authRoutes.ts` (`/me/entitlements` uses `req.user.tier`),
  `middleware/requireEntitlement.ts` (new), `phase0Routes.ts` (representative gates),
  `__tests__/tier-enforcement.test.ts` (new), `__tests__/entitlements-parity.test.ts` (tier-based).

---

## 9. Risks / open items

1. **Live-tier vs JWT claim:** enforcement uses `req.user.tier` (live DB) because the row is already
   loaded each request. The JWT `tier` claim may lag for the access-token lifetime but is not used for
   enforcement — only the client reads it. No staleness in enforcement.
2. **Backfill correctness:** the SQL `CASE` must match M4's `roleToTier` exactly (it does); a drift
   would mis-tier existing users. The `WHERE tier='open'` guard makes re-running safe.
3. **Gating Phase 0 routes changes their behavior** for open-tier callers (now 403). Intended; these
   are scaffolding routes. No production flow depends on open-tier synthesize.
4. **`usersService` mapping for legacy rows:** rows present before the migration get `'open'` via the
   column default + the `?? 'open'` guard; the backfill upgrades them by role.
5. **No DB harness here:** migration + column read are deferred-verify; the middleware + resolution are
   the verifiable core.

---

## 10. Next sub-projects (pre-shaped, not built)

Count caps (active jobs / PDF sends, numeric 403 with `limit`/`current`) -> telemetry
(`gate_hit_events`, emitted from the cap middleware) -> trials (set `users.tier`, expiry cron) ->
founder seats -> billing webhooks (the real tier setters) -> client UI (`EntitlementsRepository`,
`LockedFeatureOverlay`, `X-Tier-Changed` refresh). Each is its own spec -> plan cycle.
