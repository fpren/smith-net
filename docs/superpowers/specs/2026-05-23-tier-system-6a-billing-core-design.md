# Tier System — Sub-project 6a: provider-agnostic billing core

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** sub-project 1 (`users.tier`, `usersService`), sub-project 3 (`emitGateHit` + `tier_upgrade.paid_converted` / `tier_downgrade.canceled`), sub-project 4 (`trials` table — read for tier recompute), sub-project 5 (`founder_seats` — `claim`), M4 (`Tier`, `TIER_CODE`).
**Position:** Sub-project 6 (billing) was split after a feasibility read. **6a (this spec) = the provider-agnostic billing domain core**, fully buildable/testable here. **6b (deferred) = the Stripe + Play webhook adapters** (need `stripe`/`google-auth-library`, an `express.raw()` body refactor, webhook secrets, and the client checkout sender from sub-project 7).
**PRD reference:** `docs/prds/F3.1` (Stripe), `F3.2` (Play), `docs/database/SCHEMA.md` (subscriptions). **Deviations (approved 2026-05-23):** `subscriptions.user_id -> users(id)` (not the PRD's `profile_id`; same value, consistent with prior sub-projects); tier is **derived inline** from active subscriptions + trials on each event (not F3.1's "downgrade via cron"); `provider_webhook_events` dedup is deferred to 6b (the unique upsert already makes `applySubscriptionEvent` idempotent).

---

## 0. Why this sub-project

Trials (sub-project 4) and founder seats (sub-project 5) were built toward a permanent paid tier. Billing is that permanent `users.tier` setter. The feasibility read showed the webhook *transport* (Stripe/Play signature verification) is blocked here on uninstalled SDKs, a raw-body refactor, external secrets, and a non-existent client checkout sender. The *domain core* — the `subscriptions` table, the founder-seat `claim`, and the normalized `applySubscriptionEvent` that sets `users.tier` and emits conversion telemetry — is independent of the provider and fully testable. This sub-project builds that core; the adapters (6b) call it.

---

## 1. Scope

In scope (backend, no external SDK):
1. `subscriptions` table (migration 026) with an idempotent `UNIQUE(provider, provider_subscription_id)`.
2. `billingService.ts` — pure `highestTier` + `tierTransitionEvent` + types; transactional `applySubscriptionEvent`.
3. `founderSeatService.claim(seatId, userId)` (sub-project 5's deferred claim).
4. Unit tests for the pure helpers.

Non-goals (deferred to 6b / later):
- The Stripe webhook endpoint + `stripe.webhooks.constructEvent` signature verification + `npm install stripe`.
- The Play Billing RTDN endpoint + Pub/Sub JWT verification + `google-auth-library`.
- The `express.raw()` body-parser carve-out for `/webhooks/*`.
- The `provider_webhook_events` table + `markEventSeen` dedup (the unique upsert makes `applySubscriptionEvent` idempotent already; per-event dedup pairs with the adapters).
- Enterprise trial with CC (Stripe Checkout) and the client checkout sender (sub-project 7).
- Any HTTP route or daemon (6a has neither).

---

## 2. Architecture: derived tier, idempotent upsert, decoupled founder claim

**Tier is derived, not imperatively set.** On each event, `applySubscriptionEvent` upserts the subscription, then recomputes `users.tier` as the **highest tier across the user's access-granting subscriptions (`trialing`/`active`/`past_due`) AND active (non-expired) trials**. This single rule handles new subscription, upgrade, cancel, expire, and multiple concurrent subscriptions uniformly, and — by including active trials in the recompute — it **never clobbers a running trial** (the two tier sources are unified at this one authority). On a cancel/expire event, the subscription's status flips and the recompute naturally drops the tier (to the next-best active source, else `open`).

**Idempotent via upsert.** `UNIQUE(provider, provider_subscription_id)` + `ON CONFLICT ... DO UPDATE` means re-delivering the same provider event re-computes the same state — safe to replay. So 6a needs no separate dedup table; `provider_webhook_events` is a 6b transport concern.

**Founder claim is decoupled.** `founderSeatService.claim` is a standalone atomic primitive (a single guarded `UPDATE`). The adapter (6b) calls `claim` on payment success and passes the boolean result + seat id into `applySubscriptionEvent`, which only *records* `founder_seat_id` / `founder_price_locked` on the subscription row. No cross-transaction coupling; each operation is atomic on its own.

**No provider knowledge.** `applySubscriptionEvent` takes a normalized `SubscriptionEvent`; the `provider` is just a stored field. Stripe and Play adapters (6b) normalize their payloads into this shape after verifying signatures.

---

## 3. Migration `026_subscriptions.sql`

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

---

## 4. `billingService.ts`

Pure (tested here):
```ts
import { Tier, TIER_CODE } from './entitlements';

export type SubscriptionStatus = 'trialing' | 'active' | 'past_due' | 'canceled' | 'expired';
export type SubscriptionProvider = 'stripe' | 'play_billing' | 'manual';

/** A subscription always carries a paid tier; matches the migration CHECK. */
export type PaidTier = Exclude<Tier, 'open'>;

/** Highest tier among the given access-granting tiers; 'open' if none. */
export function highestTier(tiers: Tier[]): Tier {
  let best: Tier = 'open';
  for (const t of tiers) if (TIER_CODE[t] > TIER_CODE[best]) best = t;
  return best;
}

/** Telemetry event implied by a tier change (null if unchanged). */
export function tierTransitionEvent(
  before: Tier, after: Tier,
): 'tier_upgrade.paid_converted' | 'tier_downgrade.canceled' | null {
  if (TIER_CODE[after] > TIER_CODE[before]) return 'tier_upgrade.paid_converted';
  if (TIER_CODE[after] < TIER_CODE[before]) return 'tier_downgrade.canceled';
  return null;
}

export interface SubscriptionEvent {
  userId: string;
  provider: SubscriptionProvider;
  providerSubscriptionId: string;
  tier: PaidTier;
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

Transactional (DB; own private `requirePg`):
- `applySubscriptionEvent(event): Promise<{ tier: Tier; changed: boolean }>`. One transaction:
  1. `SELECT tier FROM users WHERE id=$1 FOR UPDATE` (capture `before`; throw if the user is missing).
  2. Upsert the subscription `ON CONFLICT (provider, provider_subscription_id) DO UPDATE` (status/tier/periods/cancel_at_period_end/cents; `founder_seat_id = COALESCE(EXCLUDED, existing)`; `founder_price_locked = existing OR EXCLUDED`; `updated_at=now()`).
  3. Recompute: `after = highestTier(` tiers from `SELECT tier FROM subscriptions WHERE user_id=$1 AND status IN ('trialing','active','past_due')` `UNION ALL` `SELECT tier FROM trials WHERE user_id=$1 AND status='active' AND expires_at > now()` `)`.
  4. `UPDATE users SET tier=$after, updated_at=now() WHERE id=$1`; COMMIT.
  Post-commit (best-effort): `const evt = tierTransitionEvent(before, after); if (evt) await emitGateHit(userId, evt, after, { from_tier: before, to_tier: after, provider })`. Return `{ tier: after, changed: before !== after }`. ROLLBACK on error; `release()` in finally.

---

## 5. `founderSeatService.claim`

Add to `backend/src/founderSeatService.ts`:
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
A single guarded `UPDATE` is atomic; no transaction needed. The `WHERE held_by = $2` guard means only the holder can claim, and an expired-but-not-yet-released hold by the same user still claims (honoring a slow checkout). Returns `false` if the seat was reassigned or never held by this user (the subscription still gets its tier, just without founder pricing).

---

## 6. Tests (runnable here)

`backend/src/__tests__/billingService.test.ts` (new) — pure helpers:
- `highestTier([])` -> `'open'`; `['solo']` -> `'solo'`; `['solo','advanced','enterprise']` -> `'enterprise'`; `['advanced','solo']` -> `'advanced'` (order independence); `['open','open']` -> `'open'`.
- `tierTransitionEvent`: `('open','solo')` -> `'tier_upgrade.paid_converted'`; `('open','enterprise')` -> upgrade; `('advanced','open')` -> `'tier_downgrade.canceled'`; `('enterprise','solo')` -> downgrade; `('solo','solo')` -> `null`; `('open','open')` -> `null`.

**Deferred-verify (no `DATABASE_URL`):** migration 026; `applySubscriptionEvent` (upsert idempotency on replay, the dual-source subscriptions+trials recompute, trial-not-clobbered, tier set, telemetry on change); `founderSeatService.claim` (holder-only guard, returns false when reassigned). The tier-ranking + transition logic (the deliverables carrying decisions) are fully covered.

---

## 7. Files touched

- **backend/migrations/**: `026_subscriptions.sql` (new).
- **backend/src/**: `billingService.ts` (new), `founderSeatService.ts` (add `claim`), `__tests__/billingService.test.ts` (new).

---

## 8. Risks / open items

1. **No live caller in 6a:** `applySubscriptionEvent` and `claim` have no HTTP caller until the 6b adapters exist (accepted — this is the foundational domain core trials/founder-seats were built toward; it is fully unit-tested at the logic boundary). Each is exercised by tests + reading.
2. **Two tier sources unified here:** the recompute reads both `subscriptions` and `trials`, so `applySubscriptionEvent` is the most authoritative tier writer. `trialService.expireDueTrials` still reverts on its own guard; if both fire near-simultaneously the last writer wins, but both derive from the same active-source rule, so the result converges. A future single `recomputeUserTier(userId)` shared by both services would formalize this (follow-up).
3. **Idempotency via upsert, not an events table:** safe for replayed *subscription* events. The per-webhook-event dedup (`provider_webhook_events`) lands with the adapters (6b) where the raw provider event id exists.
4. **Inline downgrade on cancel/expire** (deviation from F3.1's cron): the provider sends the status-change event; the recompute drops the tier then. `cancel_at_period_end=true` with `status='active'` keeps the tier until the provider sends the end event — correct.
5. **No DB harness here:** migration, `applySubscriptionEvent`, and `claim` are deferred-verify; the pure helpers are the verifiable core.

---

## 9. Next sub-projects (pre-shaped, not built)

6b — provider adapters: `npm install stripe` + `google-auth-library`; `express.raw()` carve-out for `/webhooks/*`; `POST /webhooks/stripe` (verify `Stripe-Signature` via `constructEvent`, normalize -> `claim` + `applySubscriptionEvent`) and `POST /webhooks/play-billing` (Pub/Sub JWT); `provider_webhook_events` + `markEventSeen` dedup; Enterprise trial with CC. Then 7 — client UI (Stripe Checkout sender, `X-Tier-Changed` refresh, founder counter + WS consumer, `LockedFeatureOverlay`). Each is its own spec -> plan cycle.
