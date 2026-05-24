# Tier System — hardening pass

**Status:** design (approved to draft 2026-05-24)
**Branch:** `experiment/smithcore-rom`
**Consumes / fixes:** sub-project 2 (PDF-send cap), sub-project 4 (trials), sub-project 6a (billing core), sub-project 3 (gate_hit_events).
**Scope (approved):** Fix 1 (close the PDF-send cap bypass), Fix 2 (one shared `recomputeUserTier` for billing + trials), Fix 4 (gate_hit_events retention). **Fix 3 (X-Tier-Changed stale-JWT signal) is NOT in this pass.**

---

## 0. Why

The shipped tier system has three concrete gaps:
1. `PATCH /api/invoices/:id/status` accepts `status='sent'`, which sets an invoice "sent" **without** going through the capped `POST /:id/send` -- a bypass of the PDF-send cap.
2. Two tier writers use different models: `billingService.applySubscriptionEvent` derives `users.tier` from active subscriptions + trials, while `trialService.expireDueTrials` reverts to a stored `previous_tier`. They can disagree (e.g. a user with a paid sub + a trial gets wrongly reverted).
3. `gate_hit_events` is append-only with no retention -- unbounded growth.

---

## 1. Fix 1 -- close the PDF-send cap bypass

`backend/src/schemas/invoices.ts`: drop `'sent'` from `SetStatusBody`'s enum. After this, a client cannot set `status='sent'` via the manual status PATCH; "sent" is reachable only through the capped `POST /api/invoices/:id/send` route (which sets the status inside `invoiceSendsService.sendInvoice`, unaffected by the schema). Other statuses remain user-settable.

```ts
export const SetStatusBody = z.object({
  status: z.enum(['draft', 'issued', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled']),
}).strict();
```

(Note: clients that previously PATCHed status to `'sent'` must call `/send` instead -- that is the point. `'viewed'` is left settable; tightening it is a separate, non-cap concern.)

**Test:** `SetStatusBody` rejects `{ status: 'sent' }`; still accepts `{ status: 'issued' }` / `{ status: 'paid' }`; rejects unknown + extra keys.

---

## 2. Fix 2 -- one tier authority: `recomputeUserTier`

The "highest tier across active subscriptions + active trials, written to `users.tier`" logic lives once and both writers call it inside their transaction.

`backend/src/billingService.ts` (new export; keeps `highestTier` here, so no import cycle -- `trialService` imports from `billingService`, which does not import `trialService`):

```ts
import type { PoolClient } from 'pg';

/** Set users.tier to the highest tier across the user's active subscriptions
 *  AND active trials. The single tier authority. Runs in the caller's tx. */
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

- `applySubscriptionEvent`: replace its inline recompute (the UNION query + `highestTier` + `UPDATE users`) with `after = await recomputeUserTier(client, event.userId)`. `before` (read earlier under `FOR UPDATE`) and the post-commit `tierTransitionEvent` telemetry are unchanged.
- `trialService.expireDueTrials`: per due trial, in one transaction now does **mark-expired then recompute** (derive-from-active, the approved behavior change):
  ```ts
  await client.query('BEGIN');
  await client.query(`UPDATE trials SET status = 'expired' WHERE id = $1`, [row.id]);
  const newTier = await recomputeUserTier(client, row.user_id);
  await client.query('COMMIT');
  ```
  The trial is marked expired *first*, so the recompute excludes it and the tier falls to the next-best active source (another paid sub/trial) or `open`. The old `SET tier=previous_tier WHERE tier=trial_tier` guard is removed; `recomputeUserTier` subsumes it. `trials.previous_tier` stays in the schema (history) but is no longer the revert source.
- `ExpiredTrial` changes `previousTier` -> `newTier` (the recomputed tier). `trialExpirerDaemon` emits `emitGateHit(t.userId, 'tier_upgrade.trial_expired', t.newTier, { trial_tier: t.tier })`.

**Test (`billingService.test.ts`, mock pg client -- no DB):** `recomputeUserTier` with a fake client whose `SELECT` returns `[{tier:'solo'},{tier:'advanced'}]` -> resolves `'advanced'` and issues `UPDATE users SET tier='advanced' WHERE id='u1'`; empty rows -> `'open'`.

---

## 3. Fix 4 -- gate_hit_events retention

`backend/src/daemons/cleanupDaemon.ts` (already retains bg_jobs/heartbeats/audit on its 24h cadence): add a 90-day purge.

```ts
const GATE_HIT_RETENTION_DAYS = 90;
// ... inside tick(), after the existing purges:
const gateHits = await pg.query(
  `DELETE FROM gate_hit_events
     WHERE occurred_at < NOW() - ($1::int * INTERVAL '1 day')`,
  [GATE_HIT_RETENTION_DAYS],
);
if ((gateHits.rowCount ?? 0) > 0) {
  requestLogger().info({ event: 'gate_hit_events_purged', count: gateHits.rowCount }, 'purged old gate_hit_events');
}
```

---

## 4. Files touched

- `backend/src/schemas/invoices.ts` (Fix 1)
- `backend/src/billingService.ts` (Fix 2: `recomputeUserTier`; refactor `applySubscriptionEvent`)
- `backend/src/trialService.ts` (Fix 2: `expireDueTrials` recompute; `ExpiredTrial.newTier`)
- `backend/src/daemons/trialExpirerDaemon.ts` (Fix 2: emit with `newTier`)
- `backend/src/daemons/cleanupDaemon.ts` (Fix 4)
- `backend/src/__tests__/billingService.test.ts` (Fix 2 `recomputeUserTier` mock-client tests)
- `backend/src/__tests__/setStatusBody.test.ts` (new, Fix 1 schema test)

---

## 5. Tests / verification

- Runnable here: `SetStatusBody` (rejects `'sent'`), `recomputeUserTier` (mock-client logic), plus the existing `highestTier` suite.
- Deferred-verify (no `DATABASE_URL`): `applySubscriptionEvent` + `expireDueTrials` with the shared recompute; the daemon purge. tsc clean + green non-DB gate (no new regressions).

---

## 6. Risks

1. **Fix 1 breaks any client that PATCHes status to `'sent'`** (now a 400). Intended -- such clients must use `/send`. No server flow depends on the manual `'sent'` PATCH.
2. **Fix 2 behavior change** (approved): trial expiry derives from active sources instead of reverting to `previous_tier`. More correct under paid-sub + trial overlap; converges billing + trials. `previous_tier` becomes vestigial (kept for record).
3. **No DB here:** the transactional paths + daemon are deferred-verify; the pure/mock-client logic is the verifiable core.
