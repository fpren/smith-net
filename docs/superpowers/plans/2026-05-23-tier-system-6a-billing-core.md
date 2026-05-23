# Tier System Sub-project 6a — Billing Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the provider-agnostic billing domain core — the `subscriptions` table, `founderSeatService.claim()`, and `applySubscriptionEvent` (sets `users.tier` from active subscriptions + trials, claims handled separately, emits conversion telemetry). No provider SDK, no routes, no daemon.

**Architecture:** Tier is derived, not imperatively set: each event upserts the subscription (idempotent via `UNIQUE(provider, provider_subscription_id)`) then recomputes `users.tier` as the highest tier across the user's active subscriptions AND active trials — unifying the two tier sources and never clobbering a trial. The founder-seat claim is a standalone atomic primitive the adapter (6b) calls separately.

**Tech Stack:** Node + Express + TypeScript, Postgres (`pg`), Jest. Spec: `docs/superpowers/specs/2026-05-23-tier-system-6a-billing-core-design.md`.

**Environment notes for the implementer:**
- No `DATABASE_URL` here. The migration, `applySubscriptionEvent`, and `claim` are **deferred-verify**: confirm `npx tsc --noEmit --skipLibCheck` is clean and the full `npx jest` gate has no NEW failures (the same ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required` — pre-existing). The pure helpers have real runnable tests.
- **Always prefix every command with an absolute `cd`.** `cd /Users/fegensprenelon/smith-net/backend && ...` for build/test; a SEPARATE `cd /Users/fegensprenelon/smith-net && ...` line for git.
- **Stage only the exact files in each commit step.** Never `git add -A`/`.`/`-am`.
- No emoji anywhere. ASCII only.
- Commit trailer required on every commit:
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/migrations/026_subscriptions.sql` (create) | The real subscriptions table (idempotent upsert key). |
| `backend/src/billingService.ts` (create T1, extend T2) | Pure `highestTier`/`tierTransitionEvent`/types (T1); `applySubscriptionEvent` (T2). |
| `backend/src/founderSeatService.ts` (modify T2) | Add `claim(seatId, userId)`. |
| `backend/src/__tests__/billingService.test.ts` (create) | Unit tests for the pure helpers. |

---

## Task 1: migration + billingService pure helpers

**Files:**
- Create: `backend/migrations/026_subscriptions.sql`
- Create: `backend/src/billingService.ts`
- Test: `backend/src/__tests__/billingService.test.ts`

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/billingService.test.ts`:

```ts
import { highestTier, tierTransitionEvent } from '../billingService';

describe('highestTier', () => {
  it('returns open for an empty list', () => {
    expect(highestTier([])).toBe('open');
  });
  it('returns the single tier', () => {
    expect(highestTier(['solo'])).toBe('solo');
  });
  it('returns the maximum tier regardless of order', () => {
    expect(highestTier(['solo', 'advanced', 'enterprise'])).toBe('enterprise');
    expect(highestTier(['advanced', 'solo'])).toBe('advanced');
    expect(highestTier(['enterprise', 'open', 'solo'])).toBe('enterprise');
  });
  it('returns open when all are open', () => {
    expect(highestTier(['open', 'open'])).toBe('open');
  });
});

describe('tierTransitionEvent', () => {
  it('returns paid_converted on an upgrade', () => {
    expect(tierTransitionEvent('open', 'solo')).toBe('tier_upgrade.paid_converted');
    expect(tierTransitionEvent('open', 'enterprise')).toBe('tier_upgrade.paid_converted');
    expect(tierTransitionEvent('solo', 'advanced')).toBe('tier_upgrade.paid_converted');
  });
  it('returns canceled on a downgrade', () => {
    expect(tierTransitionEvent('advanced', 'open')).toBe('tier_downgrade.canceled');
    expect(tierTransitionEvent('enterprise', 'solo')).toBe('tier_downgrade.canceled');
  });
  it('returns null when unchanged', () => {
    expect(tierTransitionEvent('solo', 'solo')).toBeNull();
    expect(tierTransitionEvent('open', 'open')).toBeNull();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/billingService.test.ts`
Expected: FAIL — cannot find module `../billingService`.

- [ ] **Step 3: Create the migration `backend/migrations/026_subscriptions.sql`** with EXACTLY:

```sql
-- Sub-project 6a: the real subscriptions table (permanent tier source). Provider
-- adapters (6b) normalize webhooks into applySubscriptionEvent. user_id ->
-- users(id) (consistent with prior tier sub-projects). UNIQUE(provider,
-- provider_subscription_id) makes the upsert idempotent for replayed events.
CREATE TABLE IF NOT EXISTS subscriptions (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                  TEXT NOT NULL REFERENCES users(id),
  tier                     TEXT NOT NULL CHECK (tier IN ('solo', 'advanced', 'enterprise')),
  cadence                  TEXT NOT NULL DEFAULT 'monthly' CHECK (cadence IN ('monthly', 'annual')),
  provider                 TEXT NOT NULL CHECK (provider IN ('stripe', 'play_billing', 'manual')),
  provider_subscription_id TEXT NOT NULL,
  status                   TEXT NOT NULL CHECK (status IN ('trialing', 'active', 'past_due', 'canceled', 'expired')),
  current_period_start     TIMESTAMPTZ,
  current_period_end       TIMESTAMPTZ,
  cancel_at_period_end     BOOLEAN NOT NULL DEFAULT false,
  founder_seat_id          UUID REFERENCES founder_seats(id),
  founder_price_locked     BOOLEAN NOT NULL DEFAULT false,
  cents_per_period         INTEGER NOT NULL DEFAULT 0,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider, provider_subscription_id)
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_user ON subscriptions (user_id, status);
```

- [ ] **Step 4: Create `backend/src/billingService.ts`** with EXACTLY:

```ts
// backend/src/billingService.ts
//
// Sub-project 6a: provider-agnostic billing core. Pure tier-derivation helpers
// + (added in Task 2) the transactional applySubscriptionEvent. No provider SDK;
// the Stripe/Play adapters (6b) normalize webhooks into applySubscriptionEvent.

import { Tier, TIER_CODE } from './entitlements';

export type SubscriptionStatus = 'trialing' | 'active' | 'past_due' | 'canceled' | 'expired';
export type SubscriptionProvider = 'stripe' | 'play_billing' | 'manual';

/** Highest tier among the given access-granting tiers; 'open' if none. */
export function highestTier(tiers: Tier[]): Tier {
  let best: Tier = 'open';
  for (const t of tiers) if (TIER_CODE[t] > TIER_CODE[best]) best = t;
  return best;
}

/** Telemetry event implied by a tier change (null if unchanged). */
export function tierTransitionEvent(
  before: Tier,
  after: Tier,
): 'tier_upgrade.paid_converted' | 'tier_downgrade.canceled' | null {
  if (TIER_CODE[after] > TIER_CODE[before]) return 'tier_upgrade.paid_converted';
  if (TIER_CODE[after] < TIER_CODE[before]) return 'tier_downgrade.canceled';
  return null;
}

export interface SubscriptionEvent {
  userId: string;
  provider: SubscriptionProvider;
  providerSubscriptionId: string;
  tier: Tier;
  status: SubscriptionStatus;
  cadence?: 'monthly' | 'annual';
  currentPeriodStart?: Date | null;
  currentPeriodEnd?: Date | null;
  cancelAtPeriodEnd?: boolean;
  centsPerPeriod?: number;
  founderSeatId?: string | null;
  founderPriceLocked?: boolean;
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/billingService.test.ts`
Expected: PASS (all cases).

- [ ] **Step 6: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 7: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/migrations/026_subscriptions.sql backend/src/billingService.ts backend/src/__tests__/billingService.test.ts && git commit -m "feat(tier): subscriptions table + billingService tier-derivation helpers

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: founderSeatService.claim + billingService.applySubscriptionEvent

Deferred-verify (no DB): clean `tsc` + green full Jest gate (no new failures). Both additions are DB-bound; their logic is covered by reading + Task 1's helper tests.

**Files:**
- Modify: `backend/src/founderSeatService.ts` (add `claim`)
- Modify: `backend/src/billingService.ts` (add imports + `applySubscriptionEvent`)

- [ ] **Step 1: Add `claim` to `backend/src/founderSeatService.ts`**

Insert this function immediately BEFORE the existing `releaseExpiredHolds` function (its doc comment is `/** Flip expired holds back to available. Returns the number released. */`):

```ts
/** Claim a held seat after payment. True if the seat was held by this user. */
export async function claim(seatId: string, userId: string): Promise<boolean> {
  const db = requirePg();
  const r = await db.query(
    `UPDATE founder_seats SET status = 'claimed', claimed_by = $2, claimed_at = now()
       WHERE id = $1 AND status = 'held' AND held_by = $2`,
    [seatId, userId],
  );
  return (r.rowCount ?? 0) > 0;
}
```

(Uses the existing private `requirePg` already in the file. A single guarded UPDATE is atomic — no transaction needed.)

- [ ] **Step 2: Add imports to `backend/src/billingService.ts`**

Change the import block at the top. The current first import line is `import { Tier, TIER_CODE } from './entitlements';`. Add two imports immediately after it:

```ts
import { pg, isPgEnabled } from './db';
import { emitGateHit } from './telemetryService';
```

- [ ] **Step 3: Append `requirePg` + `applySubscriptionEvent` to `backend/src/billingService.ts`**

Add at the END of the file:

```ts
function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[BillingService] Postgres client not initialized');
  return pg;
}

/**
 * Idempotently apply a normalized subscription event: upsert the subscription
 * (ON CONFLICT provider+provider_subscription_id), recompute users.tier as the
 * highest tier across the user's active subscriptions AND active trials (so a
 * running trial is never clobbered), and emit conversion telemetry on a change.
 */
export async function applySubscriptionEvent(
  event: SubscriptionEvent,
): Promise<{ tier: Tier; changed: boolean }> {
  const db = requirePg();
  const client = await db.connect();
  let before: Tier = 'open';
  let after: Tier = 'open';
  try {
    await client.query('BEGIN');
    const u = await client.query<{ tier: string }>(
      `SELECT tier FROM users WHERE id = $1 FOR UPDATE`,
      [event.userId],
    );
    if (u.rowCount === 0) {
      throw new Error(`[BillingService] user not found: ${event.userId}`);
    }
    before = u.rows[0].tier as Tier;

    await client.query(
      `INSERT INTO subscriptions
         (user_id, tier, cadence, provider, provider_subscription_id, status,
          current_period_start, current_period_end, cancel_at_period_end,
          founder_seat_id, founder_price_locked, cents_per_period)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       ON CONFLICT (provider, provider_subscription_id) DO UPDATE SET
         tier = EXCLUDED.tier,
         cadence = EXCLUDED.cadence,
         status = EXCLUDED.status,
         current_period_start = EXCLUDED.current_period_start,
         current_period_end = EXCLUDED.current_period_end,
         cancel_at_period_end = EXCLUDED.cancel_at_period_end,
         founder_seat_id = COALESCE(EXCLUDED.founder_seat_id, subscriptions.founder_seat_id),
         founder_price_locked = subscriptions.founder_price_locked OR EXCLUDED.founder_price_locked,
         cents_per_period = EXCLUDED.cents_per_period,
         updated_at = now()`,
      [
        event.userId,
        event.tier,
        event.cadence ?? 'monthly',
        event.provider,
        event.providerSubscriptionId,
        event.status,
        event.currentPeriodStart ?? null,
        event.currentPeriodEnd ?? null,
        event.cancelAtPeriodEnd ?? false,
        event.founderSeatId ?? null,
        event.founderPriceLocked ?? false,
        event.centsPerPeriod ?? 0,
      ],
    );

    const tiers = await client.query<{ tier: string }>(
      `SELECT tier FROM subscriptions
         WHERE user_id = $1 AND status IN ('trialing', 'active', 'past_due')
       UNION ALL
       SELECT tier FROM trials
         WHERE user_id = $1 AND status = 'active' AND expires_at > now()`,
      [event.userId],
    );
    after = highestTier(tiers.rows.map((r) => r.tier as Tier));

    await client.query(`UPDATE users SET tier = $1, updated_at = now() WHERE id = $2`, [after, event.userId]);
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }

  const evt = tierTransitionEvent(before, after);
  if (evt) {
    await emitGateHit(event.userId, evt, after, {
      from_tier: before,
      to_tier: after,
      provider: event.provider,
    });
  }
  return { tier: after, changed: before !== after };
}
```

(The user-not-found path throws; the single outer `catch` issues the `ROLLBACK` — no double rollback.)

- [ ] **Step 4: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output). Fix any errors your edits caused; re-run until clean.

- [ ] **Step 5: Full Jest gate (no new regressions)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: the non-DB suites PASS (including `billingService`, `founderSeatService`); only the known ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required`. No previously-passing suite newly fails.

- [ ] **Step 6: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/founderSeatService.ts backend/src/billingService.ts && git commit -m "feat(tier): founderSeatService.claim + applySubscriptionEvent (derive users.tier)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `billingService.test.ts` passes; full `npx jest` has no NEW failures; `npx tsc --noEmit --skipLibCheck` clean.
- `applySubscriptionEvent` upserts a subscription idempotently and sets `users.tier` to the highest tier across the user's active subscriptions + active trials (never clobbering a trial), emitting `paid_converted`/`tier_downgrade.canceled` only on a change; `founderSeatService.claim` claims a seat only for its holder.
- Deferred-verify (run when `DATABASE_URL` present): apply migration 026; exercise `applySubscriptionEvent` (new sub, replay idempotency, upgrade, cancel->downgrade, trial-not-clobbered, telemetry); `claim` (holder-only, returns false when reassigned).

## Notes / deferred follow-ups (do not build here)

- 6b provider adapters: `npm install stripe` + `google-auth-library`; `express.raw()` carve-out for `/webhooks/*`; `POST /webhooks/stripe` + `POST /webhooks/play-billing`; `provider_webhook_events` + `markEventSeen` dedup; Enterprise trial with CC.
- A shared `recomputeUserTier(userId)` used by BOTH `billingService` and `trialService.expireDueTrials` to formalize the single-source-of-truth recompute (currently billing reads trials, trials reverts on its own guard).
- Client checkout sender + `X-Tier-Changed` refresh + founder counter UI — sub-project 7.
