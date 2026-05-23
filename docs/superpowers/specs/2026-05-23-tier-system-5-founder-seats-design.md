# Tier System — Sub-project 5: founder seats

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** sub-project 1 (`users`, `req.user`), the queue's `FOR UPDATE SKIP LOCKED` concurrency pattern (`queue.ts`), the daemon pattern (`cleanupDaemon`/`trialExpirer`).
**Position:** Sub-project 5 of the decomposed tier system. Earlier: tier source + enforcement, count caps, telemetry, trials. Later: billing webhooks, client UI.
**PRD reference:** `docs/prds/F5.1-founder-seats-service.md`. **Deviations (approved 2026-05-23):** `held_by`/`claimed_by` reference `users(id)` (not F5.1's `profiles(id)`) for consistency with the trials table and `req.user.id` — the rows are paired so the value is identical; the WS `founder_seats_changed` push and the `claim()` (hold->claimed after payment) are deferred (see section 1).

---

## 0. Why this sub-project

Founder pricing is a real, atomic scarcity mechanic: a fixed pool of seats (Solo 1000 / Advanced 100 / Enterprise 10), each reservable as a 10-minute hold when a user taps the founder CTA, auto-released if not converted. "Never fake scarcity" (tier-gating skill): the seats are pre-minted rows, and the counter reflects true availability. This sub-project builds the server inventory + atomic reserve + release sweep + a counts API. The permanent `claim` (after payment) lands with billing; the live WS counter lands with the client UI.

---

## 1. Scope

In scope (backend):
1. `founder_seats` table (migration 025, F5.1 schema) pre-minted with 1000/100/10 seats.
2. `founderSeatService.ts` — pool config + `reserve` (atomic 10-min hold), `releaseExpiredHolds`, `getAllCounts`.
3. `GET /api/founder-seats` (counts) + `POST /api/founder-seats/reserve` (hold) routes.
4. `founderSeatsExpirerDaemon.ts` (60s) registered in `workers/runner.ts`.
5. Unit tests for the pure pieces.

Non-goals (deferred):
- WS live push `founder_seats_changed` (sub-project 7 client UI consumes it). The `GET` counts endpoint feeds the counter on demand.
- `claim(seatId, userId)` (hold -> claimed) — fires on payment success (billing, sub-project 6). Until then a hold simply expires and releases.
- Founder pricing amounts / bonus application, and the `subscriptions.founder_seat_id` FK (billing, sub-project 6).
- Public (unauthenticated) availability — both routes are authenticated for now.

---

## 2. Architecture: pre-minted rows + self-healing holds

**Pre-minted rows, not a counter.** The migration inserts one row per seat (1110 total), so scarcity is real and a seat is a lockable unit. `reserve` grabs exactly one row under `FOR UPDATE SKIP LOCKED` (the same concurrency primitive as `queue.claimNext`), so concurrent taps never double-allocate and never block each other.

**Self-healing holds.** An expired hold (`status='held' AND held_until <= now()`) is treated as available by both `reserve` (it can grab such a row) and `getAllCounts` (it counts as remaining). So correctness never depends on the release daemon's timing — the daemon (`releaseExpiredHolds`) is housekeeping that flips expired holds back to `available` (and is where the future WS push will fire). This is the same "best-effort daemon, correct-without-it" posture as elsewhere.

**One hold per user per pool.** Before grabbing a new seat, `reserve` returns the caller's existing un-expired hold for that pool, so a double-tap cannot burn two seats.

**Standalone — no tier-model conflict.** Founder seats do not read or write `users.tier`; they are pure inventory. The eventual link (a claimed seat -> founder pricing on a subscription) is billing's concern.

---

## 3. Migration `025_founder_seats.sql`

```sql
-- Sub-project 5: pre-minted founder-pricing scarcity pools (F5.1). One row per
-- seat; reserve() grabs one under FOR UPDATE SKIP LOCKED. held_by/claimed_by ->
-- users(id) (paired with profiles; consistent with the trials table).
CREATE TABLE IF NOT EXISTS founder_seats (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  bonus_id    TEXT NOT NULL CHECK (bonus_id IN (
                'solo_founder_pricing_lock',
                'advanced_lifetime_template_library',
                'enterprise_founder_annual_pricing')),
  seat_number INTEGER NOT NULL,
  total_seats INTEGER NOT NULL,
  status      TEXT NOT NULL DEFAULT 'available'
                CHECK (status IN ('available', 'held', 'claimed', 'released')),
  held_by     TEXT REFERENCES users(id),
  held_until  TIMESTAMPTZ,
  claimed_by  TEXT REFERENCES users(id),
  claimed_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (bonus_id, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_founder_seats_status ON founder_seats (bonus_id, status);
CREATE INDEX IF NOT EXISTS idx_founder_seats_held_until
  ON founder_seats (held_until) WHERE status = 'held';

-- Pre-mint each pool once (idempotent: skip if the pool already exists).
INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'solo_founder_pricing_lock', g, 1000 FROM generate_series(1, 1000) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'solo_founder_pricing_lock');

INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'advanced_lifetime_template_library', g, 100 FROM generate_series(1, 100) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'advanced_lifetime_template_library');

INSERT INTO founder_seats (bonus_id, seat_number, total_seats)
SELECT 'enterprise_founder_annual_pricing', g, 10 FROM generate_series(1, 10) AS g
WHERE NOT EXISTS (SELECT 1 FROM founder_seats WHERE bonus_id = 'enterprise_founder_annual_pricing');
```

---

## 4. `founderSeatService.ts`

Pure (tested here):
```ts
export const FOUNDER_BONUS_IDS = [
  'solo_founder_pricing_lock',
  'advanced_lifetime_template_library',
  'enterprise_founder_annual_pricing',
] as const;
export type FounderBonusId = typeof FOUNDER_BONUS_IDS[number];

export const FOUNDER_POOLS: Record<FounderBonusId, number> = {
  solo_founder_pricing_lock: 1000,
  advanced_lifetime_template_library: 100,
  enterprise_founder_annual_pricing: 10,
};

export const HOLD_MINUTES = 10;
```

Transactional / DB (own private `requirePg`):
- `reserve(bonusId, userId): Promise<Reservation | null>` where `Reservation = { seatId: string; bonusId: FounderBonusId; heldUntil: Date }`.
  One transaction on a dedicated client: (a) `SELECT id, held_until FROM founder_seats WHERE bonus_id=$1 AND status='held' AND held_by=$2 AND held_until > now() LIMIT 1` -> if found, COMMIT and return it; (b) else `SELECT id FROM founder_seats WHERE bonus_id=$1 AND (status='available' OR (status='held' AND held_until <= now())) ORDER BY seat_number LIMIT 1 FOR UPDATE SKIP LOCKED` -> if none, COMMIT and return `null` (exhausted); (c) `UPDATE founder_seats SET status='held', held_by=$2, held_until=$3 WHERE id=$seat`; COMMIT; return the reservation. ROLLBACK on error; `release()` in finally.
- `releaseExpiredHolds(): Promise<number>` — `UPDATE founder_seats SET status='available', held_by=NULL, held_until=NULL WHERE status='held' AND held_until <= now()`; returns `rowCount`.
- `getAllCounts(): Promise<Record<FounderBonusId, { remaining: number; total: number }>>` — one grouped query: `SELECT bonus_id, COUNT(*) FILTER (WHERE status='available' OR (status='held' AND held_until <= now())) AS remaining, COUNT(*) AS total FROM founder_seats GROUP BY bonus_id`; map rows into the record, defaulting any missing pool to `{ remaining: 0, total: FOUNDER_POOLS[id] }`.

---

## 5. Routes

`backend/src/schemas/founderSeats.ts` (new):
```ts
import { z } from 'zod';
import { FOUNDER_BONUS_IDS } from '../founderSeatService';
// Spread to a mutable tuple: z.enum rejects a readonly `as const` array.
export const ReserveFounderSeatBody = z.object({
  bonusId: z.enum([...FOUNDER_BONUS_IDS] as [string, ...string[]]),
}).strict();
export type ReserveFounderSeatBody = z.infer<typeof ReserveFounderSeatBody>;
```

`backend/src/founderSeatsRoutes.ts` (new), mounted under the authenticated `apiRouter`:
- `GET /founder-seats` -> `200 { counts: getAllCounts() }`.
- `POST /founder-seats/reserve` -> `validateBody(ReserveFounderSeatBody)` -> `reserve(body.bonusId, req.user!.id)`; on a `Reservation` -> `200 { seat_id, bonus_id, held_until }`; on `null` -> `409 { error, code: 'founder_seats_exhausted', bonus_id }`. Both wrapped in try/catch -> 500 + `requestLogger` (sibling convention). Identity from `req.user`, never the body.

---

## 6. Release daemon

`backend/src/daemons/founderSeatsExpirerDaemon.ts` (new), mirroring `cleanupDaemon`:
```ts
export const INTERVAL_MS = 60 * 1000; // 60s

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const released = await releaseExpiredHolds();
  if (released > 0) {
    requestLogger().info({ event: 'founder_holds_released', count: released }, 'released expired founder holds');
  }
}
```
Registered in `backend/src/workers/runner.ts` via `daemonLoop('founder_seats_expirer', FOUNDER_SEATS_EXPIRER_MS, founderSeatsExpirerTick)`.

---

## 7. Tests (runnable here)

`backend/src/__tests__/founderSeatService.test.ts` (new) — pure config + schema:
- `FOUNDER_POOLS` equals `{ solo_founder_pricing_lock: 1000, advanced_lifetime_template_library: 100, enterprise_founder_annual_pricing: 10 }`.
- `FOUNDER_BONUS_IDS` has length 3; `HOLD_MINUTES` is 10.
- `ReserveFounderSeatBody`: accepts each of the 3 bonus ids; rejects `{ bonusId: 'nope' }`, `{}`, and unknown top-level keys (`.strict()`).

**Deferred-verify (no `DATABASE_URL`):** migration 025 incl. the pre-mint counts (1000/100/10); `reserve` (existing-hold reuse, SKIP-LOCKED grab, exhaustion -> null, expired-hold reuse); `releaseExpiredHolds`; `getAllCounts`; the two routes (200 / 409 / counts); the daemon registration. The pool config + schema (the deliverables carrying logic) are fully covered.

---

## 8. Files touched

- **backend/migrations/**: `025_founder_seats.sql` (new).
- **backend/src/**: `founderSeatService.ts` (new), `schemas/founderSeats.ts` (new), `founderSeatsRoutes.ts` (new), `api.ts` (mount `founderSeatsRouter`), `daemons/founderSeatsExpirerDaemon.ts` (new), `workers/runner.ts` (register the daemon), `__tests__/founderSeatService.test.ts` (new).

---

## 9. Risks / open items

1. **Holds without a claim path:** until billing (sub-project 6) adds `claim`, every hold eventually expires and releases — no seat is permanently consumed. Intended for this milestone; the scarcity counter still reflects active holds.
2. **Self-healing correctness:** `reserve` and `getAllCounts` both treat expired holds as available, so the 60s daemon is housekeeping, not a correctness dependency. A seat briefly double-counted between an expiry and the next reserve cannot be over-allocated because `reserve`'s `FOR UPDATE SKIP LOCKED` + `UPDATE` is atomic.
3. **`held_by`/`claimed_by` -> `users(id)`:** a deviation from F5.1's `profiles(id)`; identical value (paired rows), consistent with the trials table.
4. **No WS push yet:** the client must fetch `GET /api/founder-seats` to update its counter; live push is sub-project 7.
5. **No DB harness here:** migration, service queries, routes, and daemon are deferred-verify; the pool config + schema are the verifiable core.
6. **Pre-mint idempotency:** the `WHERE NOT EXISTS` guard makes re-running the migration safe and never duplicates a pool.

---

## 10. Next sub-projects (pre-shaped, not built)

Billing webhooks (Stripe/Play set `users.tier`; the real `subscriptions` table; `founderSeatService.claim` on payment success linking `subscriptions.founder_seat_id`; Enterprise trial with CC; emits `paid_converted` / `tier_downgrade.canceled`) -> client UI (`founder_seats_changed` WS consumer + live counter, `LockedFeatureOverlay`, `X-Tier-Changed` refresh, trial banner). Each is its own spec -> plan cycle.
