# Tier System Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Close three gaps in the shipped tier system: the PDF-send cap bypass via PATCH status, the two-different tier writers, and unbounded gate_hit_events growth.

**Architecture:** Fix 1 narrows a zod enum. Fix 2 extracts one `recomputeUserTier(client, userId)` authority that both `applySubscriptionEvent` and `expireDueTrials` call inside their transactions (trial expiry switches from revert-to-previous to derive-from-active). Fix 4 adds a 90-day purge to the existing cleanup daemon.

**Tech Stack:** Node + Express + TS + Postgres (`pg`) + Jest. Spec: `docs/superpowers/specs/2026-05-24-tier-hardening-design.md`.

**Environment notes:**
- No `DATABASE_URL`. The transactional refactors + daemon purge are **deferred-verify** (tsc clean + green non-DB gate, ~same 9 DB-integration suites still fail on `DATABASE_URL`). The pure/mock-client pieces have runnable tests.
- Prefix every command with an absolute `cd` (`cd /Users/fegensprenelon/smith-net/backend && ...` for build/test; a separate `cd /Users/fegensprenelon/smith-net && ...` for git).
- Stage only the files listed per commit; never `git add -A`/`.`/`-am`.
- No emoji anywhere. Commit trailer: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`

---

## Task 1: Fix 1 -- close the PDF-send cap bypass

**Files:** Modify `backend/src/schemas/invoices.ts`; Test `backend/src/__tests__/setStatusBody.test.ts` (new).

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/setStatusBody.test.ts`:

```ts
import { SetStatusBody } from '../schemas/invoices';

describe('SetStatusBody', () => {
  it('rejects sent (must go through the capped /send route)', () => {
    expect(SetStatusBody.safeParse({ status: 'sent' }).success).toBe(false);
  });
  it('accepts the user-settable statuses', () => {
    for (const status of ['draft', 'issued', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']) {
      expect(SetStatusBody.safeParse({ status }).success).toBe(true);
    }
  });
  it('rejects unknown status and extra keys', () => {
    expect(SetStatusBody.safeParse({ status: 'nope' }).success).toBe(false);
    expect(SetStatusBody.safeParse({ status: 'paid', foo: 1 }).success).toBe(false);
  });
});
```

- [ ] **Step 2: Run -> FAIL** (`'sent'` currently passes): `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/setStatusBody.test.ts`

- [ ] **Step 3: Remove `'sent'` from `SetStatusBody`** in `backend/src/schemas/invoices.ts`:

Change the `SetStatusBody` enum from
```ts
  status: z.enum(['draft', 'issued', 'sent', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']),
```
to
```ts
  status: z.enum(['draft', 'issued', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']),
```
(Leave the rest of `SetStatusBody` and all other schemas unchanged. `invoiceSendsService.sendInvoice` sets `status='sent'` directly and is unaffected.)

- [ ] **Step 4: Run -> PASS.** Then `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck` (clean).

- [ ] **Step 5: Commit**
```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/schemas/invoices.ts backend/src/__tests__/setStatusBody.test.ts && git commit -m "fix(tier): remove 'sent' from PATCH status -- close PDF-send cap bypass

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Fix 2 -- one shared `recomputeUserTier`

**Files:** Modify `backend/src/billingService.ts`, `backend/src/trialService.ts`, `backend/src/daemons/trialExpirerDaemon.ts`; Test `backend/src/__tests__/billingService.test.ts`.

- [ ] **Step 1: Write the failing test** -- append to `backend/src/__tests__/billingService.test.ts`:

```ts
import { recomputeUserTier } from '../billingService';

function mockClient(tierRows: { tier: string }[]) {
  const calls: { sql: string; params?: unknown[] }[] = [];
  const query = async (sql: string, params?: unknown[]) => {
    calls.push({ sql, params });
    return { rows: /SELECT tier/.test(sql) ? tierRows : [] };
  };
  return { query, calls };
}

describe('recomputeUserTier', () => {
  it('writes the highest active tier and returns it', async () => {
    const c = mockClient([{ tier: 'solo' }, { tier: 'advanced' }]);
    const t = await recomputeUserTier(c as never, 'u1');
    expect(t).toBe('advanced');
    const update = c.calls.find((x) => /UPDATE users/.test(x.sql));
    expect(update?.params).toEqual(['advanced', 'u1']);
  });
  it('falls back to open when there is no active source', async () => {
    const c = mockClient([]);
    expect(await recomputeUserTier(c as never, 'u1')).toBe('open');
  });
});
```

- [ ] **Step 2: Run -> FAIL** (`recomputeUserTier` not exported): `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/billingService.test.ts`

- [ ] **Step 3: Add `recomputeUserTier` to `backend/src/billingService.ts`.**
Add `import type { PoolClient } from 'pg';` near the top imports. Then add this exported function (e.g. directly above `applySubscriptionEvent`):

```ts
/**
 * The single tier authority: set users.tier to the highest tier across the
 * user's active subscriptions AND active trials. Runs in the caller's tx.
 */
export async function recomputeUserTier(client: PoolClient, userId: string): Promise<Tier> {
  const rows = await client.query<{ tier: string }>(
    `SELECT tier FROM subscriptions WHERE user_id = $1 AND status IN ('trialing', 'active', 'past_due')
     UNION ALL
     SELECT tier FROM trials WHERE user_id = $1 AND status = 'active' AND expires_at > now()`,
    [userId],
  );
  const next = highestTier(rows.rows.map((r) => r.tier as Tier));
  await client.query(`UPDATE users SET tier = $1, updated_at = now() WHERE id = $2`, [next, userId]);
  return next;
}
```

- [ ] **Step 4: Refactor `applySubscriptionEvent`** in the same file to use it. Replace the inline recompute block:
```ts
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
```
with:
```ts
    after = await recomputeUserTier(client, event.userId);
```
(Everything else in `applySubscriptionEvent` -- the `before` read, the upsert, the COMMIT, the post-commit `tierTransitionEvent` emit -- stays.)

- [ ] **Step 5: Run -> PASS** (`npx jest src/__tests__/billingService.test.ts`). The earlier `highestTier`/`tierTransitionEvent` cases still pass.

- [ ] **Step 6: Switch `trialService.expireDueTrials` to the shared recompute** in `backend/src/trialService.ts`:
  (a) Add `import { recomputeUserTier } from './billingService';` to the imports.
  (b) Change the `ExpiredTrial` interface:
```ts
export interface ExpiredTrial {
  userId: string;
  tier: TrialTier;
  newTier: Tier;
}
```
  (c) In `expireDueTrials`, drop `previous_tier` from the `due` SELECT and its row type (now unused), and replace the per-row transaction body:
```ts
  const due = await db.query<{ id: string; user_id: string; tier: string }>(
    `SELECT id, user_id, tier FROM trials
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
      await client.query(`UPDATE trials SET status = 'expired' WHERE id = $1`, [row.id]);
      const newTier = await recomputeUserTier(client, row.user_id);
      await client.query('COMMIT');
      processed.push({ userId: row.user_id, tier: row.tier as TrialTier, newTier });
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }
  return processed;
```
  (The trial is marked expired before the recompute, so the recompute excludes it. `trials.previous_tier` is still written at start but no longer read here.)

- [ ] **Step 7: Update the daemon emit** in `backend/src/daemons/trialExpirerDaemon.ts`: change
```ts
    await emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.previousTier, { trial_tier: t.tier });
```
to
```ts
    await emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.newTier, { trial_tier: t.tier });
```

- [ ] **Step 8: Typecheck + gate.** `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck` (clean), then `npx jest` (non-DB suites pass incl. billingService; only the known ~9 DB suites fail on `DATABASE_URL`; no new regression).

- [ ] **Step 9: Commit**
```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/billingService.ts backend/src/trialService.ts backend/src/daemons/trialExpirerDaemon.ts backend/src/__tests__/billingService.test.ts && git commit -m "refactor(tier): shared recomputeUserTier authority for billing + trial expiry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Fix 4 -- gate_hit_events retention

**Files:** Modify `backend/src/daemons/cleanupDaemon.ts`. Deferred-verify (tsc + gate).

- [ ] **Step 1: Add the retention constant** near the existing `DEAD_JOB_RETENTION_DAYS` in `backend/src/daemons/cleanupDaemon.ts`:
```ts
const GATE_HIT_RETENTION_DAYS = 90;
```

- [ ] **Step 2: Add the purge inside `tick()`**, after the stale-heartbeats purge block and before the `auditLog.cleanupOldEntries()` try/catch:
```ts
  const gateHits = await pg.query(
    `DELETE FROM gate_hit_events
      WHERE occurred_at < NOW() - ($1::int * INTERVAL '1 day')`,
    [GATE_HIT_RETENTION_DAYS],
  );
  if ((gateHits.rowCount ?? 0) > 0) {
    requestLogger().info(
      { event: 'gate_hit_events_purged', count: gateHits.rowCount },
      'purged gate_hit_events older than retention',
    );
  }
```

- [ ] **Step 3: Typecheck + gate.** `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck` clean; `npx jest` no new regression.

- [ ] **Step 4: Commit**
```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/daemons/cleanupDaemon.ts && git commit -m "feat(tier): 90-day gate_hit_events retention in cleanup daemon

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `setStatusBody.test.ts` + `billingService.test.ts` (incl. `recomputeUserTier`) pass; full `npx jest` no new failures; `tsc --noEmit --skipLibCheck` clean.
- `'sent'` rejected by PATCH status; billing + trial-expiry both derive `users.tier` via `recomputeUserTier`; cleanup daemon purges gate_hit_events > 90 days.
- Deferred-verify (with `DATABASE_URL`): exercise applySubscriptionEvent + expireDueTrials (trial expiry drops to next-best active source), and the daemon purge.
