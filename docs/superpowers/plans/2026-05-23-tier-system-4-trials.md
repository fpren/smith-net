# Tier System Sub-project 4 — Trials Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a verified user start a Solo (14d) or Advanced (30d) trial with no credit card — raising `users.tier` — and have an hourly daemon auto-revert the tier when the trial expires, emitting `tier_upgrade.trial_*` telemetry.

**Architecture:** A minimal `trials` table records each trial against the existing `users.tier` source (no second source, no subscriptions/billing model). `trialService` owns the logic: pure helpers (durations, upgrade-direction) plus two transactional ops (`startTrial` sets the tier + inserts the row atomically; `expireDueTrials` reverts the tier with a "still on trial tier" guard). A `/api/me/start-trial` route and a `trialExpirerDaemon` (mirroring `cleanupDaemon`) wire it up.

**Tech Stack:** Node + Express + TypeScript, Postgres (`pg`), Zod, express-rate-limit, Jest. Spec: `docs/superpowers/specs/2026-05-23-tier-system-4-trials-design.md`.

**Environment notes for the implementer:**
- No `DATABASE_URL` here. The transactions, route, daemon, and migration are **deferred-verify**: confirm `npx tsc --noEmit --skipLibCheck` is clean and the full `npx jest` gate has no NEW failures (the same ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required` — pre-existing, not your concern). The pure helpers + schema have real runnable tests.
- **Always prefix every command with an absolute `cd`.** Use `cd /Users/fegensprenelon/smith-net/backend && ...` for build/test, and a SEPARATE `cd /Users/fegensprenelon/smith-net && ...` line for git (do not chain a backend build with a git command — the cd breaks git paths).
- **Stage only the exact files in each commit step.** Never `git add -A`/`.`/`-am`; the working tree has unrelated changes.
- No emoji anywhere (code, comments, commit messages). ASCII only.
- Commit trailer required on every commit:
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/migrations/024_trials.sql` (create) | `trials` table + `UNIQUE(user_id, tier)` no-reuse + due index. |
| `backend/src/trialService.ts` (create) | `TRIAL_DAYS`, `trialExpiry`, `isTrialUpgrade` (pure); `startTrial`, `expireDueTrials` (transactional). |
| `backend/src/schemas/me.ts` (create) | `StartTrialBody` (tier enum solo/advanced, `.strict()`). |
| `backend/src/meRoutes.ts` (create) | `POST /me/start-trial` (verified-email, per-user 5/min, preconditions, X-Tier-Changed, trial_started emit). |
| `backend/src/api.ts` (modify) | Mount `meRouter`. |
| `backend/src/daemons/trialExpirerDaemon.ts` (create) | Hourly tick: `expireDueTrials` + `trial_expired` emit. |
| `backend/src/workers/runner.ts` (modify) | Register the daemon. |
| `backend/src/__tests__/trialService.test.ts` (create) | Unit tests for the pure helpers + schema. |

---

## Task 1: migration + trialService + schema

**Files:**
- Create: `backend/migrations/024_trials.sql`
- Create: `backend/src/trialService.ts`
- Create: `backend/src/schemas/me.ts`
- Test: `backend/src/__tests__/trialService.test.ts`

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/trialService.test.ts`:

```ts
import { TRIAL_DAYS, trialExpiry, isTrialUpgrade } from '../trialService';
import { StartTrialBody } from '../schemas/me';

describe('TRIAL_DAYS', () => {
  it('is 14 for solo, 30 for advanced', () => {
    expect(TRIAL_DAYS).toEqual({ solo: 14, advanced: 30 });
  });
});

describe('trialExpiry', () => {
  it('adds the tier duration in days', () => {
    const now = new Date('2026-05-01T00:00:00.000Z');
    expect(trialExpiry('solo', now).toISOString()).toBe('2026-05-15T00:00:00.000Z');
    expect(trialExpiry('advanced', now).toISOString()).toBe('2026-05-31T00:00:00.000Z');
  });
});

describe('isTrialUpgrade', () => {
  it('allows only a tier strictly above the current tier', () => {
    expect(isTrialUpgrade('open', 'solo')).toBe(true);
    expect(isTrialUpgrade('open', 'advanced')).toBe(true);
    expect(isTrialUpgrade('solo', 'advanced')).toBe(true);
    expect(isTrialUpgrade('solo', 'solo')).toBe(false);
    expect(isTrialUpgrade('advanced', 'solo')).toBe(false);
    expect(isTrialUpgrade('enterprise', 'advanced')).toBe(false);
  });
});

describe('StartTrialBody', () => {
  it('accepts solo and advanced', () => {
    expect(StartTrialBody.safeParse({ tier: 'solo' }).success).toBe(true);
    expect(StartTrialBody.safeParse({ tier: 'advanced' }).success).toBe(true);
  });
  it('rejects enterprise, open, missing, and unknown keys', () => {
    expect(StartTrialBody.safeParse({ tier: 'enterprise' }).success).toBe(false);
    expect(StartTrialBody.safeParse({ tier: 'open' }).success).toBe(false);
    expect(StartTrialBody.safeParse({}).success).toBe(false);
    expect(StartTrialBody.safeParse({ tier: 'solo', foo: 1 }).success).toBe(false);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/trialService.test.ts`
Expected: FAIL — cannot find module `../trialService` and `../schemas/me`.

- [ ] **Step 3: Create the migration `backend/migrations/024_trials.sql`** with EXACTLY:

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

- [ ] **Step 4: Create `backend/src/schemas/me.ts`** with EXACTLY:

```ts
import { z } from 'zod';

export const StartTrialBody = z.object({ tier: z.enum(['solo', 'advanced']) }).strict();
export type StartTrialBody = z.infer<typeof StartTrialBody>;
```

- [ ] **Step 5: Create `backend/src/trialService.ts`** with EXACTLY:

```ts
// backend/src/trialService.ts
//
// Sub-project 4: trials against users.tier (the single tier source). Pure
// helpers (durations, upgrade direction) + two transactional ops. startTrial
// raises users.tier and records the trial atomically; expireDueTrials reverts
// the tier to previous_tier with a "still on trial tier" guard.

import { pg, isPgEnabled } from './db';
import { Tier, TIER_CODE } from './entitlements';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[TrialService] Postgres client not initialized');
  return pg;
}

export type TrialTier = 'solo' | 'advanced';
export const TRIAL_DAYS: Record<TrialTier, number> = { solo: 14, advanced: 30 };

export function trialExpiry(tier: TrialTier, now: Date = new Date()): Date {
  return new Date(now.getTime() + TRIAL_DAYS[tier] * 24 * 60 * 60 * 1000);
}

/** A trial is allowed only for a tier strictly above the user's current tier. */
export function isTrialUpgrade(currentTier: Tier, targetTier: TrialTier): boolean {
  return TIER_CODE[targetTier] > TIER_CODE[currentTier];
}

export type StartTrialResult =
  | { ok: true; tier: TrialTier; trialEndsAt: Date }
  | { ok: false; code: 'trial_already_used' | 'trial_already_active' };

/**
 * Atomically: reject if an active trial exists; insert the trial row (the
 * UNIQUE(user_id, tier) constraint makes same-tier reuse a 23505 -> reject);
 * raise users.tier. Either everything lands or nothing does. The caller has
 * already checked isTrialUpgrade, so previous_tier (= currentTier) is below the
 * target.
 */
export async function startTrial(
  userId: string,
  currentTier: Tier,
  targetTier: TrialTier,
): Promise<StartTrialResult> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const active = await client.query(
      `SELECT 1 FROM trials WHERE user_id = $1 AND status = 'active' LIMIT 1`,
      [userId],
    );
    if ((active.rowCount ?? 0) > 0) {
      await client.query('ROLLBACK');
      return { ok: false, code: 'trial_already_active' };
    }
    const expiresAt = trialExpiry(targetTier);
    try {
      await client.query(
        `INSERT INTO trials (user_id, tier, previous_tier, expires_at)
           VALUES ($1, $2, $3, $4)`,
        [userId, targetTier, currentTier, expiresAt],
      );
    } catch (e: any) {
      if (e.code === '23505') {
        await client.query('ROLLBACK');
        return { ok: false, code: 'trial_already_used' };
      }
      throw e;
    }
    await client.query(
      `UPDATE users SET tier = $1, updated_at = now() WHERE id = $2`,
      [targetTier, userId],
    );
    await client.query('COMMIT');
    return { ok: true, tier: targetTier, trialEndsAt: expiresAt };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

export interface ExpiredTrial {
  userId: string;
  tier: TrialTier;
  previousTier: Tier;
}

/**
 * Revert each due trial: set users.tier back to previous_tier ONLY if the user
 * is still on the trial tier (guard against a later upgrade), mark the row
 * expired. Each row is its own transaction. Returns the processed rows for
 * telemetry.
 */
export async function expireDueTrials(limit = 200): Promise<ExpiredTrial[]> {
  const db = requirePg();
  const due = await db.query<{ id: string; user_id: string; tier: string; previous_tier: string }>(
    `SELECT id, user_id, tier, previous_tier FROM trials
       WHERE status = 'active' AND expires_at <= now()
       ORDER BY expires_at ASC
       LIMIT $1`,
    [limit],
  );
  const processed: ExpiredTrial[] = [];
  for (const row of due.rows) {
    const client = await db.connect();
    try {
      await client.query('BEGIN');
      await client.query(
        `UPDATE users SET tier = $1, updated_at = now() WHERE id = $2 AND tier = $3`,
        [row.previous_tier, row.user_id, row.tier],
      );
      await client.query(`UPDATE trials SET status = 'expired' WHERE id = $1`, [row.id]);
      await client.query('COMMIT');
      processed.push({
        userId: row.user_id,
        tier: row.tier as TrialTier,
        previousTier: row.previous_tier as Tier,
      });
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }
  return processed;
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/trialService.test.ts`
Expected: PASS (all cases).

- [ ] **Step 7: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 8: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/migrations/024_trials.sql backend/src/trialService.ts backend/src/schemas/me.ts backend/src/__tests__/trialService.test.ts && git commit -m "feat(tier): trials table + trialService (start/expire, users.tier)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: start-trial route + mount

Deferred-verify (no DB): clean `tsc` + green full Jest gate (no new failures).

**Files:**
- Create: `backend/src/meRoutes.ts`
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Create `backend/src/meRoutes.ts`** with EXACTLY:

```ts
// backend/src/meRoutes.ts
//
// POST /api/me/start-trial -- start a Solo or Advanced trial (no credit card).
// Raises users.tier; the trialExpirer daemon reverts it at expiry. Enterprise
// trials require billing (deferred). Email-verified + per-user 5/min rate limit.

import { Router, Response } from 'express';
import rateLimit from 'express-rate-limit';
import { AuthenticatedRequest, requireVerifiedEmail } from './auth';
import { validateBody } from './middleware/validate';
import { StartTrialBody } from './schemas/me';
import * as trialService from './trialService';
import { emitGateHit } from './telemetryService';
import { requestLogger } from './log';

export const meRouter = Router();

const startTrialLimiter = rateLimit({
  windowMs: 60_000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => (req as AuthenticatedRequest).user?.id ?? req.ip ?? 'unknown',
  skip: (req) => !(req as AuthenticatedRequest).user,
});

meRouter.post(
  '/me/start-trial',
  requireVerifiedEmail,
  startTrialLimiter,
  validateBody(StartTrialBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const current = req.user!.tier;
      const target = (req.body as StartTrialBody).tier;
      if (!trialService.isTrialUpgrade(current, target)) {
        return res.status(400).json({
          error: `Cannot trial ${target} from ${current}`,
          code: 'already_at_or_above_tier',
          current_tier: current,
        });
      }
      const r = await trialService.startTrial(req.user!.id, current, target);
      if (!r.ok) {
        return res.status(400).json({ error: `Trial not started: ${r.code}`, code: r.code });
      }
      await emitGateHit(req.user!.id, 'tier_upgrade.trial_started', target, {
        from_tier: current,
        has_cc: false,
      });
      res.setHeader('X-Tier-Changed', 'true');
      res.status(200).json({ tier: r.tier, trial_ends_at: r.trialEndsAt });
    } catch (e: any) {
      requestLogger().error({ event: 'start_trial_error', err: e }, 'start trial error');
      res.status(500).json({ error: 'Failed to start trial' });
    }
  },
);
```

- [ ] **Step 2: Mount the router in `backend/src/api.ts`**

(a) Add the import immediately after the existing `import { telemetryRouter } from './telemetryRoutes';` line:

```ts
import { meRouter } from './meRoutes';
```

(b) Add the mount immediately after the existing `apiRouter.use(telemetryRouter);` line:

```ts
apiRouter.use(meRouter);
```

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output). If `keyGenerator`/`req.ip` produces a type error, confirm the `?? 'unknown'` fallback is present (it guarantees a `string` return).

- [ ] **Step 4: Full Jest gate (no new regressions)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: the non-DB suites PASS (including `trialService`); only the known ~9 DB-integration suites fail with `[usersService] DATABASE_URL is required`. Confirm no previously-passing suite newly fails.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/meRoutes.ts backend/src/api.ts && git commit -m "feat(tier): POST /api/me/start-trial (verified, rate-limited, X-Tier-Changed)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: expiry daemon + runner registration

Deferred-verify (no DB): clean `tsc` + green full Jest gate.

**Files:**
- Create: `backend/src/daemons/trialExpirerDaemon.ts`
- Modify: `backend/src/workers/runner.ts`

- [ ] **Step 1: Create `backend/src/daemons/trialExpirerDaemon.ts`** with EXACTLY:

```ts
// backend/src/daemons/trialExpirerDaemon.ts
//
// Sub-project 4: hourly trial expiry. Reverts users.tier to each due trial's
// previous_tier and emits tier_upgrade.trial_expired. Mirrors cleanupDaemon
// (exports INTERVAL_MS + tick; registered in workers/runner.ts).

import { isPgEnabled, pg } from '../db';
import { expireDueTrials } from '../trialService';
import { emitGateHit } from '../telemetryService';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60 * 60 * 1000; // hourly

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const expired = await expireDueTrials();
  for (const t of expired) {
    await emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.previousTier, { trial_tier: t.tier });
  }
  if (expired.length > 0) {
    requestLogger().info({ event: 'trials_expired', count: expired.length }, 'expired due trials');
  }
}
```

- [ ] **Step 2: Register the daemon in `backend/src/workers/runner.ts`**

(a) Add the import immediately after line 19 (`import { tick as presenceWatcherTick, INTERVAL_MS as PRESENCE_WATCHER_MS } from '../daemons/presenceWatcherDaemon';`):

```ts
import { tick as trialExpirerTick, INTERVAL_MS as TRIAL_EXPIRER_MS } from '../daemons/trialExpirerDaemon';
```

(b) Add the registration immediately after line 73 (`void daemonLoop('presence_watcher', PRESENCE_WATCHER_MS, presenceWatcherTick);`), inside `main()`:

```ts
  void daemonLoop('trial_expirer', TRIAL_EXPIRER_MS, trialExpirerTick);
```

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 4: Full Jest gate**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: non-DB suites PASS; only the known ~9 DB-integration suites fail (`DATABASE_URL`). No new regression.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/daemons/trialExpirerDaemon.ts backend/src/workers/runner.ts && git commit -m "feat(tier): hourly trialExpirer daemon (revert tier + emit trial_expired)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `trialService.test.ts` passes; full `npx jest` has no NEW failures; `npx tsc --noEmit --skipLibCheck` clean.
- A verified user can start a Solo/Advanced trial (tier raised, `X-Tier-Changed` set, `trial_started` emitted); re-trialing the same tier -> `trial_already_used`; a second concurrent trial -> `trial_already_active`; trialing at/below current tier -> `already_at_or_above_tier`; the hourly daemon reverts expired trials to `previous_tier` and emits `trial_expired`.
- Deferred-verify (run when `DATABASE_URL` present): apply migration 024; exercise the four start-trial outcomes + the verified-email gate + the 5/min limit; confirm `expireDueTrials` reverts only when still on the trial tier; confirm the daemon runs hourly in the worker process.

## Notes / deferred follow-ups (do not build here)

- Enterprise trial (CC/Stripe) — billing, sub-project 6.
- The full `subscriptions` table + founder seats — sub-projects 5-6.
- Stale-JWT tier detection on expiry (set `X-Tier-Changed` from `authenticateToken` when the JWT tier lags live `users.tier`) — enforcement is already correct via the live read; this only refreshes the client's cached entitlements.
- FCM push on trial expiry — notification concern.
- `expireDueTrials` aborts the batch on a per-row error (next hourly tick retries the rest); revisit with per-row skip if a poison row is ever observed.
