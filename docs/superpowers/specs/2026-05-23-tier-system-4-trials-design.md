# Tier System — Sub-project 4: trials

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** Sub-project 1 (`users.tier`, `req.user.tier` live read, `usersService`), sub-project 3 (`emitGateHit` + the `tier_upgrade.trial_*` events), M4 (`Tier`, `TIER_CODE`).
**Position:** Sub-project 4 of the decomposed tier system. Earlier: tier source + enforcement, count caps, telemetry. Later: founder seats, billing webhooks, client UI.
**PRD reference:** `docs/prds/F7.1-trial-mechanics.md`, `docs/prds/flows/FLOW-6-trial-expiry.md`. **Deviation (approved 2026-05-23):** F7.1 models trials via a full `subscriptions` table + `profiles.tier`; this sub-project instead stores trials minimally against the established `users.tier` source (see section 2). The subscriptions/founder/billing model and the Enterprise trial are deferred to sub-projects 5-6.

---

## 0. Why this sub-project

Until now `users.tier` was provisional — derived from role at insert/backfill, with no real setter (sub-projects 1-3 noted this). Trials are the **first real `users.tier` setter**: a user starts a Solo or Advanced trial (no credit card), `users.tier` is raised, and an hourly daemon reverts it when the trial expires. It is also the first **server-side emitter** of `tier_upgrade.trial_started` / `tier_upgrade.trial_expired` (the seam sub-project 3 left).

---

## 1. Scope

In scope (backend):
1. `trials` table (migration 024) with a `UNIQUE(user_id, tier)` no-reuse guard.
2. `trialService.ts` — pure helpers (`TRIAL_DAYS`, `trialExpiry`, `isTrialUpgrade`) + the two transactional operations (`startTrial`, `expireDueTrials`).
3. `POST /api/me/start-trial` (new `meRoutes.ts`) with `requireVerifiedEmail`, a per-user 5/min rate limit, zod validation, the structured preconditions, `X-Tier-Changed`, and the `trial_started` emit.
4. `trialExpirerDaemon.ts` (hourly) registered in `workers/runner.ts`, with the `trial_expired` emit.
5. Unit tests for the pure pieces.

Non-goals (deferred):
- Enterprise trial (needs CC/Stripe = billing, sub-project 6). The route accepts only `solo`/`advanced`.
- The full `subscriptions` table, founder seats, `cents_per_period`, providers (sub-projects 5-6).
- FCM push on expiry (notification concern, not tier).
- Stale-JWT tier detection on the **expiry** side (setting `X-Tier-Changed` from `authenticateToken` when the JWT tier lags the DB tier) — deferred; enforcement is already correct via the live-tier read, so this only affects the client's cached entitlements after an expiry. `X-Tier-Changed` is set on the start-trial response here.
- Client UI (sub-project 7).

---

## 2. Architecture: minimal trials on `users.tier`

Enforcement (sub-projects 1-3) reads `users.tier` live every request. So a trial only needs to (a) raise `users.tier`, (b) record enough to revert and to block reuse, (c) revert on expiry. The `trials` table does exactly that; no second tier source is introduced.

**Revert target = `previous_tier`.** At trial start the user's current tier is captured as `previous_tier`; on expiry the daemon sets `users.tier = previous_tier`. This cleanly implements F7.1's "downgrade to Solo (or Open)": a fresh Open user trialing Advanced reverts to Open; a (future) paid Solo user trialing Advanced reverts to Solo. No "highest other active subscription" query is needed (that requires the subscriptions table).

**One active trial at a time + no same-tier reuse.** `UNIQUE(user_id, tier)` makes a second trial of the same tier impossible at the DB level (the expired row remains, so the slot is spent forever). A pre-insert check rejects starting a trial while another is active. Both checks live inside `startTrial`'s transaction to avoid TOCTOU.

**Expiry guard.** `expireDueTrials` reverts `users.tier` only if it still equals the trial's tier — so a future billing upgrade past the trial tier is never clobbered. (Today current always equals the trial tier; the guard is forward-compatible.)

**Daemon, not queue.** Expiry is fixed-cadence background work, matching the existing `cleanupDaemon` pattern (a `daemonLoop` in `workers/runner.ts`), not a `background_jobs` consumer. No new `BgJobKind`.

---

## 3. Migration `024_trials.sql`

```sql
-- Sub-project 4: trial records against users.tier (the single tier source).
-- UNIQUE(user_id, tier) enforces "a tier's trial can be used only once" (the
-- expired row stays, spending the slot). previous_tier is the revert target.
CREATE TABLE IF NOT EXISTS trials (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       TEXT NOT NULL REFERENCES users(id),
  tier          TEXT NOT NULL CHECK (tier IN ('solo', 'advanced')),
  previous_tier TEXT NOT NULL,
  started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  status        TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'canceled')),
  UNIQUE (user_id, tier)
);

CREATE INDEX IF NOT EXISTS idx_trials_due ON trials (status, expires_at);
```

Idempotent, matching the existing migration style.

---

## 4. `trialService.ts`

Pure helpers (fully testable here):

```ts
import { Tier, TIER_CODE } from './entitlements';

export type TrialTier = 'solo' | 'advanced';
export const TRIAL_DAYS: Record<TrialTier, number> = { solo: 14, advanced: 30 };

export function trialExpiry(tier: TrialTier, now: Date = new Date()): Date {
  return new Date(now.getTime() + TRIAL_DAYS[tier] * 24 * 60 * 60 * 1000);
}

/** A trial is allowed only for a tier strictly above the user's current tier. */
export function isTrialUpgrade(currentTier: Tier, targetTier: TrialTier): boolean {
  return TIER_CODE[targetTier] > TIER_CODE[currentTier];
}
```

Transactional operations (DB-bound; own private `requirePg`, mirroring `invoiceSendsService`):

- `startTrial(userId, currentTier, targetTier): Promise<StartTrialResult>` where
  `StartTrialResult = { ok: true; tier: TrialTier; trialEndsAt: Date } | { ok: false; code: 'trial_already_used' | 'trial_already_active' }`.
  One transaction: `SELECT 1 FROM trials WHERE user_id=$1 AND status='active'` (-> `trial_already_active`); `INSERT INTO trials (user_id, tier, previous_tier, expires_at) VALUES (...)` catching unique-violation `23505` (-> `trial_already_used`); `UPDATE users SET tier=$targetTier, updated_at=now() WHERE id=$1`; COMMIT. (The route checks `isTrialUpgrade` before calling, so `previous_tier` is always below the target.)
- `expireDueTrials(limit = 200): Promise<ExpiredTrial[]>` where `ExpiredTrial = { userId: string; tier: TrialTier; previousTier: Tier }`.
  Selects `status='active' AND expires_at <= now()` (limit N); for each, one transaction: `UPDATE users SET tier=$previous_tier, updated_at=now() WHERE id=$userId AND tier=$trialTier` (the guard), `UPDATE trials SET status='expired' WHERE id=$trialId`; COMMIT. Returns the processed rows for telemetry.

---

## 5. Start-trial route

`backend/src/schemas/me.ts` (new):
```ts
import { z } from 'zod';
export const StartTrialBody = z.object({ tier: z.enum(['solo', 'advanced']) }).strict();
export type StartTrialBody = z.infer<typeof StartTrialBody>;
```

`backend/src/meRoutes.ts` (new), mounted under the authenticated `apiRouter`:

- **Middleware chain:** `requireVerifiedEmail` (auth.ts) -> a per-user limiter `rateLimit({ windowMs: 60_000, max: 5, keyGenerator: (req) => req.user?.id ?? req.ip, skip: (req) => !req.user })` -> `validateBody(StartTrialBody)`.
- **Handler:**
  1. `current = req.user!.tier`; `target = body.tier`.
  2. If `!isTrialUpgrade(current, target)` -> 400 `{ error, code: 'already_at_or_above_tier', current_tier: current }`.
  3. `const r = await trialService.startTrial(req.user!.id, current, target)`.
  4. If `!r.ok` -> 400 `{ error, code: r.code }`.
  5. `await emitGateHit(req.user!.id, 'tier_upgrade.trial_started', target, { from_tier: current, has_cc: false })`.
  6. `res.setHeader('X-Tier-Changed', 'true')`; return 200 `{ tier: r.tier, trial_ends_at: r.trialEndsAt }`.
  7. Wrap in try/catch -> 500 + `requestLogger` (sibling convention).

`isTrialUpgrade` runs before any DB work, so an `already_at_or_above_tier` request never opens a transaction. The structured 400 codes (`already_at_or_above_tier`, `trial_already_used`, `trial_already_active`) follow the tier-gating skill's structured-error convention.

---

## 6. Expiry daemon

`backend/src/daemons/trialExpirerDaemon.ts` (new), mirroring `cleanupDaemon`:
```ts
export const TRIAL_EXPIRER_MS = 60 * 60 * 1000; // hourly

export async function trialExpirerTick(): Promise<void> {
  const expired = await expireDueTrials();
  for (const t of expired) {
    await emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.previousTier, { trial_tier: t.tier });
  }
}
```
Registered in `backend/src/workers/runner.ts` alongside the other daemons (e.g. `daemonLoop('trial_expirer', TRIAL_EXPIRER_MS, trialExpirerTick)`). `expireDueTrials` no-ops without a DB (its `requirePg` throws only when called; the daemon only runs in the worker process where a DB is present). Telemetry emits are best-effort (sub-project 3).

---

## 7. Tests (runnable here)

`backend/src/__tests__/trialService.test.ts` (new) — pure helpers + schema:
- `TRIAL_DAYS`: `{ solo: 14, advanced: 30 }`.
- `trialExpiry('solo', new Date('2026-05-01T00:00:00Z'))` -> `2026-05-15T00:00:00Z`; `trialExpiry('advanced', ...)` -> `2026-05-31T00:00:00Z`.
- `isTrialUpgrade`: `('open','solo')` true; `('open','advanced')` true; `('solo','solo')` false; `('advanced','solo')` false; `('solo','advanced')` true; `('enterprise','advanced')` false.
- `StartTrialBody`: accepts `{tier:'solo'}` / `{tier:'advanced'}`; rejects `{tier:'enterprise'}`, `{tier:'open'}`, `{}`, and unknown keys (`.strict()`).

**Deferred-verify (no `DATABASE_URL`):** migration 024; `startTrial` / `expireDueTrials` transactions (active-trial reject, unique reject, tier set, revert-with-guard); the route end-to-end (verification gate, rate limit, the four outcomes); the daemon registration. The duration math, the upgrade-direction rule, and the request schema (the deliverables that carry logic) are fully covered.

---

## 8. Files touched

- **backend/migrations/**: `024_trials.sql` (new).
- **backend/src/**: `trialService.ts` (new), `schemas/me.ts` (new), `meRoutes.ts` (new), `daemons/trialExpirerDaemon.ts` (new), `api.ts` (mount `meRouter`), `workers/runner.ts` (register the daemon), `__tests__/trialService.test.ts` (new).

---

## 9. Risks / open items

1. **Stale JWT after expiry:** when the daemon downgrades `users.tier`, the user's access-token `tier` claim lags up to 7 days. Enforcement is unaffected (reads live `users.tier`); only the client's cached entitlements lag until it refreshes. A general fix (compare token tier vs live tier in `authenticateToken`, set `X-Tier-Changed`) is deferred (section 1 non-goals). Start-trial sets the header on its own response.
2. **`previous_tier` correctness:** captured from `users.tier` at start and reverted on expiry, with the "still on trial tier" guard. Forward-compatible with paid tiers; today `previous_tier` is `open` for everyone.
3. **No concurrent trials:** the active-trial check + `UNIQUE(user_id, tier)` keep trial state simple (one active trial; each tier usable once). A user may trial Solo then later Advanced (different tiers); neither can be repeated.
4. **Daemon only runs in the worker process:** if the worker is not running, trials never expire and tiers stay elevated. This matches every other daemon (cleanup, heartbeat) — an ops concern, not a logic gap.
5. **No DB harness here:** migration, transactions, route, and daemon are deferred-verify; the helpers + schema are the verifiable core.
6. **Rate-limit keyer:** falls back to `req.ip` if `req.user` is somehow absent, but `requireVerifiedEmail` (which needs `req.user`) runs first, so the user is always present in practice.

---

## 10. Next sub-projects (pre-shaped, not built)

Founder seats (atomic `FOR UPDATE SKIP LOCKED` reserve; ties into trial start when a founder pool is selected) -> billing webhooks (Stripe/Play set `users.tier`; the real subscriptions table lands here; Enterprise trial with CC; emits `paid_converted` / `tier_downgrade.canceled`) -> client UI (`X-Tier-Changed` refresh, trial banner/counter, `LockedFeatureOverlay`). Each is its own spec -> plan cycle.
