# Tier System Sub-project 5 — Founder Seats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pre-minted founder-pricing scarcity pools (Solo 1000 / Advanced 100 / Enterprise 10) with an atomic 10-minute reserve (hold), a counts API, and a 60s release sweep.

**Architecture:** One pre-minted row per seat; `reserve` grabs one under `FOR UPDATE SKIP LOCKED` (no double-allocation, no contention) and holds it 10 minutes. Holds are self-healing — `reserve` and `getAllCounts` both treat an expired hold as available — so the release daemon is housekeeping, not a correctness dependency. WS push and the `claim` path are deferred (sub-projects 7 and 6).

**Tech Stack:** Node + Express + TypeScript, Postgres (`pg`), Zod, Jest. Spec: `docs/superpowers/specs/2026-05-23-tier-system-5-founder-seats-design.md`.

**Environment notes for the implementer:**
- No `DATABASE_URL` here. Migration, service queries, routes, and daemon are **deferred-verify**: confirm `npx tsc --noEmit --skipLibCheck` is clean and the full `npx jest` gate has no NEW failures (the same ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required` — pre-existing). The pool config + schema have real runnable tests.
- **Always prefix every command with an absolute `cd`.** `cd /Users/fegensprenelon/smith-net/backend && ...` for build/test; a SEPARATE `cd /Users/fegensprenelon/smith-net && ...` line for git (do not chain a backend build with a git command).
- **Stage only the exact files in each commit step.** Never `git add -A`/`.`/`-am`; the working tree has unrelated changes.
- No emoji anywhere. ASCII only.
- Commit trailer required on every commit:
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/migrations/025_founder_seats.sql` (create) | `founder_seats` table + indexes + idempotent pre-mint (1000/100/10). |
| `backend/src/founderSeatService.ts` (create) | `FOUNDER_POOLS`/`FOUNDER_BONUS_IDS`/`HOLD_MINUTES` (pure); `reserve`, `releaseExpiredHolds`, `getAllCounts`. |
| `backend/src/schemas/founderSeats.ts` (create) | `ReserveFounderSeatBody` (bonusId enum, `.strict()`). |
| `backend/src/founderSeatsRoutes.ts` (create) | `GET /founder-seats`, `POST /founder-seats/reserve`. |
| `backend/src/api.ts` (modify) | Mount `founderSeatsRouter`. |
| `backend/src/daemons/founderSeatsExpirerDaemon.ts` (create) | 60s tick: `releaseExpiredHolds`. |
| `backend/src/workers/runner.ts` (modify) | Register the daemon. |
| `backend/src/__tests__/founderSeatService.test.ts` (create) | Unit tests for pool config + schema. |

---

## Task 1: migration + founderSeatService + schema

**Files:**
- Create: `backend/migrations/025_founder_seats.sql`
- Create: `backend/src/founderSeatService.ts`
- Create: `backend/src/schemas/founderSeats.ts`
- Test: `backend/src/__tests__/founderSeatService.test.ts`

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/founderSeatService.test.ts`:

```ts
import { FOUNDER_POOLS, FOUNDER_BONUS_IDS, HOLD_MINUTES } from '../founderSeatService';
import { ReserveFounderSeatBody } from '../schemas/founderSeats';

describe('FOUNDER_POOLS', () => {
  it('is 1000 / 100 / 10 per pool', () => {
    expect(FOUNDER_POOLS).toEqual({
      solo_founder_pricing_lock: 1000,
      advanced_lifetime_template_library: 100,
      enterprise_founder_annual_pricing: 10,
    });
  });
});

describe('FOUNDER_BONUS_IDS / HOLD_MINUTES', () => {
  it('has 3 bonus ids and a 10-minute hold', () => {
    expect(FOUNDER_BONUS_IDS).toHaveLength(3);
    expect(HOLD_MINUTES).toBe(10);
  });
});

describe('ReserveFounderSeatBody', () => {
  it('accepts each known bonus id', () => {
    for (const id of FOUNDER_BONUS_IDS) {
      expect(ReserveFounderSeatBody.safeParse({ bonusId: id }).success).toBe(true);
    }
  });
  it('rejects unknown bonus id, missing, and unknown keys', () => {
    expect(ReserveFounderSeatBody.safeParse({ bonusId: 'nope' }).success).toBe(false);
    expect(ReserveFounderSeatBody.safeParse({}).success).toBe(false);
    expect(ReserveFounderSeatBody.safeParse({ bonusId: 'solo_founder_pricing_lock', foo: 1 }).success).toBe(false);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/founderSeatService.test.ts`
Expected: FAIL — cannot find module `../founderSeatService` and `../schemas/founderSeats`.

- [ ] **Step 3: Create the migration `backend/migrations/025_founder_seats.sql`** with EXACTLY:

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

- [ ] **Step 4: Create `backend/src/founderSeatService.ts`** with EXACTLY:

```ts
// backend/src/founderSeatService.ts
//
// Sub-project 5: founder-pricing scarcity pools (F5.1). Pre-minted rows; reserve
// grabs one under FOR UPDATE SKIP LOCKED for a 10-min hold. Holds are
// self-healing: reserve and getAllCounts both treat an expired hold as
// available, so the release daemon is housekeeping, not a correctness need.

import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[FounderSeatService] Postgres client not initialized');
  return pg;
}

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

export interface Reservation {
  seatId: string;
  bonusId: FounderBonusId;
  heldUntil: Date;
}

export interface SeatCount {
  remaining: number;
  total: number;
}

/**
 * Atomically hold one seat for 10 minutes. Returns the caller's existing
 * un-expired hold for this pool if any (a double-tap never burns two seats),
 * else grabs one available-or-expired-held seat via FOR UPDATE SKIP LOCKED.
 * null when the pool is exhausted.
 */
export async function reserve(bonusId: FounderBonusId, userId: string): Promise<Reservation | null> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const existing = await client.query<{ id: string; held_until: string }>(
      `SELECT id, held_until FROM founder_seats
         WHERE bonus_id = $1 AND status = 'held' AND held_by = $2 AND held_until > now()
         LIMIT 1`,
      [bonusId, userId],
    );
    if (existing.rows.length > 0) {
      await client.query('COMMIT');
      return { seatId: existing.rows[0].id, bonusId, heldUntil: new Date(existing.rows[0].held_until) };
    }
    const pick = await client.query<{ id: string }>(
      `SELECT id FROM founder_seats
         WHERE bonus_id = $1 AND (status = 'available' OR (status = 'held' AND held_until <= now()))
         ORDER BY seat_number
         LIMIT 1
         FOR UPDATE SKIP LOCKED`,
      [bonusId],
    );
    if (pick.rows.length === 0) {
      await client.query('COMMIT');
      return null;
    }
    const seatId = pick.rows[0].id;
    const heldUntil = new Date(Date.now() + HOLD_MINUTES * 60 * 1000);
    await client.query(
      `UPDATE founder_seats SET status = 'held', held_by = $2, held_until = $3 WHERE id = $1`,
      [seatId, userId, heldUntil],
    );
    await client.query('COMMIT');
    return { seatId, bonusId, heldUntil };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

/** Flip expired holds back to available. Returns the number released. */
export async function releaseExpiredHolds(): Promise<number> {
  const db = requirePg();
  const r = await db.query(
    `UPDATE founder_seats SET status = 'available', held_by = NULL, held_until = NULL
       WHERE status = 'held' AND held_until <= now()`,
  );
  return r.rowCount ?? 0;
}

/** Remaining (available + expired holds) and total per pool. */
export async function getAllCounts(): Promise<Record<FounderBonusId, SeatCount>> {
  const db = requirePg();
  const r = await db.query<{ bonus_id: string; remaining: number; total: number }>(
    `SELECT bonus_id,
            COUNT(*) FILTER (WHERE status = 'available' OR (status = 'held' AND held_until <= now()))::int AS remaining,
            COUNT(*)::int AS total
       FROM founder_seats
      GROUP BY bonus_id`,
  );
  const out = {} as Record<FounderBonusId, SeatCount>;
  for (const id of FOUNDER_BONUS_IDS) {
    out[id] = { remaining: 0, total: FOUNDER_POOLS[id] };
  }
  for (const row of r.rows) {
    if ((FOUNDER_BONUS_IDS as readonly string[]).includes(row.bonus_id)) {
      out[row.bonus_id as FounderBonusId] = { remaining: row.remaining, total: row.total };
    }
  }
  return out;
}
```

- [ ] **Step 5: Create `backend/src/schemas/founderSeats.ts`** with EXACTLY:

```ts
import { z } from 'zod';
import { FOUNDER_BONUS_IDS } from '../founderSeatService';

// Spread to a mutable tuple: z.enum rejects a readonly `as const` array.
export const ReserveFounderSeatBody = z.object({
  bonusId: z.enum([...FOUNDER_BONUS_IDS] as [string, ...string[]]),
}).strict();
export type ReserveFounderSeatBody = z.infer<typeof ReserveFounderSeatBody>;
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/founderSeatService.test.ts`
Expected: PASS (all cases).

- [ ] **Step 7: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 8: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/migrations/025_founder_seats.sql backend/src/founderSeatService.ts backend/src/schemas/founderSeats.ts backend/src/__tests__/founderSeatService.test.ts && git commit -m "feat(tier): founder_seats table + founderSeatService (reserve/release/counts)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: routes + mount

Deferred-verify (no DB): clean `tsc` + green full Jest gate (no new failures).

**Files:**
- Create: `backend/src/founderSeatsRoutes.ts`
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Create `backend/src/founderSeatsRoutes.ts`** with EXACTLY:

```ts
// backend/src/founderSeatsRoutes.ts
//
// Founder-pricing scarcity pools. GET counts (drives the "X OF Y SPOTS"
// counter) + POST reserve (10-min hold). Identity from req.user, never body.

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { ReserveFounderSeatBody } from './schemas/founderSeats';
import * as founderSeatService from './founderSeatService';
import type { FounderBonusId } from './founderSeatService';
import { requestLogger } from './log';

export const founderSeatsRouter = Router();

founderSeatsRouter.get('/founder-seats', async (_req: AuthenticatedRequest, res: Response) => {
  try {
    res.json({ counts: await founderSeatService.getAllCounts() });
  } catch (e: any) {
    requestLogger().error({ event: 'founder_seats_counts_error', err: e }, 'founder seats counts error');
    res.status(500).json({ error: 'Failed to load founder seats' });
  }
});

founderSeatsRouter.post(
  '/founder-seats/reserve',
  validateBody(ReserveFounderSeatBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const bonusId = (req.body as ReserveFounderSeatBody).bonusId as FounderBonusId;
      const r = await founderSeatService.reserve(bonusId, req.user!.id);
      if (!r) {
        return res.status(409).json({
          error: 'Founder seats exhausted',
          code: 'founder_seats_exhausted',
          bonus_id: bonusId,
        });
      }
      res.status(200).json({ seat_id: r.seatId, bonus_id: r.bonusId, held_until: r.heldUntil });
    } catch (e: any) {
      requestLogger().error({ event: 'founder_seats_reserve_error', err: e }, 'founder seats reserve error');
      res.status(500).json({ error: 'Failed to reserve founder seat' });
    }
  },
);
```

- [ ] **Step 2: Mount the router in `backend/src/api.ts`**

(a) Add the import immediately after the existing `import { meRouter } from './meRoutes';` line:

```ts
import { founderSeatsRouter } from './founderSeatsRoutes';
```

(b) Add the mount immediately after the existing `apiRouter.use(meRouter);` line:

```ts
apiRouter.use(founderSeatsRouter);
```

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 4: Full Jest gate (no new regressions)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: non-DB suites PASS (including `founderSeatService`); only the known ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required`. No previously-passing suite newly fails.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/founderSeatsRoutes.ts backend/src/api.ts && git commit -m "feat(tier): GET /api/founder-seats + POST /api/founder-seats/reserve

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: release daemon + runner registration

Deferred-verify (no DB): clean `tsc` + green full Jest gate.

**Files:**
- Create: `backend/src/daemons/founderSeatsExpirerDaemon.ts`
- Modify: `backend/src/workers/runner.ts`

- [ ] **Step 1: Create `backend/src/daemons/founderSeatsExpirerDaemon.ts`** with EXACTLY:

```ts
// backend/src/daemons/founderSeatsExpirerDaemon.ts
//
// Sub-project 5: release expired founder-seat holds (10-min holds) back to
// available. Housekeeping -- reserve()/getAllCounts() already treat expired
// holds as available, so correctness does not depend on this cadence.

import { isPgEnabled, pg } from '../db';
import { releaseExpiredHolds } from '../founderSeatService';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60 * 1000; // 60s

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const released = await releaseExpiredHolds();
  if (released > 0) {
    requestLogger().info({ event: 'founder_holds_released', count: released }, 'released expired founder holds');
  }
}
```

- [ ] **Step 2: Register the daemon in `backend/src/workers/runner.ts`**

(a) Add the import immediately after the existing `import { tick as trialExpirerTick, INTERVAL_MS as TRIAL_EXPIRER_MS } from '../daemons/trialExpirerDaemon';` line:

```ts
import { tick as founderSeatsExpirerTick, INTERVAL_MS as FOUNDER_SEATS_EXPIRER_MS } from '../daemons/founderSeatsExpirerDaemon';
```

(b) Add the registration immediately after the existing `void daemonLoop('trial_expirer', TRIAL_EXPIRER_MS, trialExpirerTick);` line, inside `main()`:

```ts
  void daemonLoop('founder_seats_expirer', FOUNDER_SEATS_EXPIRER_MS, founderSeatsExpirerTick);
```

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 4: Full Jest gate**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: non-DB suites PASS; only the known ~9 DB-integration suites fail. No new regression.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/daemons/founderSeatsExpirerDaemon.ts backend/src/workers/runner.ts && git commit -m "feat(tier): 60s founderSeatsExpirer daemon (release expired holds)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `founderSeatService.test.ts` passes; full `npx jest` has no NEW failures; `npx tsc --noEmit --skipLibCheck` clean.
- A user can `GET /api/founder-seats` (per-pool remaining/total) and `POST /api/founder-seats/reserve` (10-min hold, or 409 `founder_seats_exhausted`); a double-tap returns the same hold; expired holds are reusable and swept by the 60s daemon.
- Deferred-verify (run when `DATABASE_URL` present): apply migration 025 and confirm 1000/100/10 rows pre-minted; exercise reserve (grab, existing-hold reuse, exhaustion -> null, expired-hold reuse under concurrency), `releaseExpiredHolds`, `getAllCounts`, and the two routes.

## Notes / deferred follow-ups (do not build here)

- WS push `founder_seats_changed` (a new `wsHandler` broadcast method fired from reserve/release) — sub-project 7 client UI consumer.
- `claim(seatId, userId)` (hold -> claimed) on payment success + `subscriptions.founder_seat_id` link — billing, sub-project 6.
- Founder pricing amounts / bonus application — billing.
- Public (unauthenticated) availability for a marketing page — both routes are authenticated for now.
