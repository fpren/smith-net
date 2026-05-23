# Tier System — Sub-project 2: count-based caps

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** Sub-project 1 (`users.tier` source, `req.user.tier` live read, `requireTier`/`requireEntitlement` + structured 403) and M4 (`CAPS_BY_TIER`, `Tier`).
**Position:** Sub-project 2 of the decomposed tier system. Earlier: tier source + enforcement. Later: `gate_hit_events` telemetry, trials, founder seats, billing webhooks, client UI.

---

## 0. Why this sub-project

Sub-project 1 added boolean tier gates (does this tier *have* the feature). It did not add **numeric caps** (how *much* a tier may do). The tier-gating skill's cap matrix specifies two count caps for the Open tier:

| Capability | Open | Solo | Adv | Ent |
|---|---|---|---|---|
| Active jobs | 1 | unlim | unlim | unlim |
| PDF sends/mo | 5 | unlim | unlim | unlim |

This sub-project adds the server-authoritative `requireCap` mechanism (numeric 403 with `limit`/`current`) and wires both caps. A free user creating a 2nd active job, or sending a 6th PDF in a calendar month, is refused server-side — the upgrade moment.

The tier-gating skill is the contract authority: same structured 403 (`code:'tier_gate_exceeded'`) as sub-project 1, plus numeric `limit`/`current`; `gate_id` from the enumerated set (`active_job_cap`, `pdf_send_cap`); friction at the moment of value (no proactive "you have 1 left" warnings).

---

## 1. Scope

In scope (backend):
1. Numeric cap registry `CAP_LIMITS_BY_TIER` in `entitlements.ts` (colocated with `CAPS_BY_TIER`); `null` = unlimited.
2. `requireCap` middleware (`middleware/requireCap.ts`) with an **injected counter function**, fail-closed, structured numeric 403.
3. Active-jobs counter (`jobsService.countActive`) + wiring `requireCap('active_jobs')` on `POST /api/jobs`.
4. PDF-send infrastructure (minimal): `invoice_sends` events table (migration 022), `invoiceSendsService` (count + record), and a new `POST /api/invoices/:id/send` route gated by `requireCap('pdf_sends_per_month')`.
5. Unit tests for `requireCap` + `lowestUnlimitedTierFor` (pure, mock req/res/next + injected counter).

Non-goals (explicitly deferred):
- `gate_hit_events` telemetry (sub-project 3). `requireCap` leaves the seam but emits nothing — keeps it sync/pure-testable here.
- Email / PDF generation / delivery on send. The send route marks `status='sent'` and records the send event only.
- Branded-PDF-at-send (`invoices.sent_with_branding`) — a separate tier feature (Open = forced branding), its own follow-up.
- Trials / founder seats / billing — the mechanisms that change `users.tier`.
- All client UI (`LockedFeatureOverlay` cap variants, cap-hit UX).
- Rewiring the legacy role-based `requireConsoleTier` (left in place; `requireCap` is added alongside it on `POST /api/jobs`).

---

## 2. Architecture: injected counter keeps the gate pure

Unlike `requireTier`/`requireEntitlement` (pure functions of `req.user`), a cap must read **current usage**, which is a DB query (async). To keep the middleware unit-testable without a database (no `DATABASE_URL` in this environment — same constraint as sub-project 1), `requireCap` takes the counter as a parameter:

```ts
count: (userId: string) => Promise<number>
```

The middleware itself contains only the tier-limit lookup, the `current >= limit` decision, and the 403 shape — all testable with a mocked counter. Each call site supplies its own query (`jobsService.countActive`, `invoiceSendsService.countSendsThisMonth`), so the gate is decoupled from any specific table.

**Unlimited short-circuit:** for any tier whose cap is `null`, the middleware `return next()` **before** calling the counter. Paying users (solo+) never pay the count query cost and never hit its failure modes — only Open-tier callers are counted.

**Fail-closed:** if the counter rejects (DB error), the middleware calls `next(err)` so the request fails (handled by the error middleware) rather than silently allowing an over-cap action.

`req.user.tier` is the live DB tier (loaded each request by `authenticateToken`, per sub-project 1) — caps reflect the current tier, never a stale JWT.

---

## 3. Cap registry

`backend/src/entitlements.ts` (append; do not disturb `CAPS_BY_TIER`):

```ts
export type CapKey = 'active_jobs' | 'pdf_sends_per_month';

/** Numeric per-tier caps. null = unlimited. Mirrors the tier-gating skill cap matrix. */
export const CAP_LIMITS_BY_TIER: Record<Tier, Record<CapKey, number | null>> = {
  open:       { active_jobs: 1,    pdf_sends_per_month: 5 },
  solo:       { active_jobs: null, pdf_sends_per_month: null },
  advanced:   { active_jobs: null, pdf_sends_per_month: null },
  enterprise: { active_jobs: null, pdf_sends_per_month: null },
};
```

---

## 4. `requireCap` middleware

`backend/src/middleware/requireCap.ts` (new):

```ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { Tier, CapKey, CAP_LIMITS_BY_TIER } from '../entitlements';

const TIER_ASC: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];

/** Lowest tier whose cap is unlimited (null) for the given key. */
export function lowestUnlimitedTierFor(capKey: CapKey): Tier {
  for (const t of TIER_ASC) {
    if (CAP_LIMITS_BY_TIER[t][capKey] === null) return t;
  }
  return 'enterprise'; // unreachable: enterprise is unlimited for every cap
}

interface CapConfig {
  capKey: CapKey;
  gateId: string;                                  // 'active_job_cap' | 'pdf_send_cap'
  count: (userId: string) => Promise<number>;      // current usage for this user
}

export function requireCap(cfg: CapConfig) {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    const limit = CAP_LIMITS_BY_TIER[req.user.tier][cfg.capKey];
    if (limit === null) return next();             // unlimited: no count, no DB hit
    try {
      const current = await cfg.count(req.user.id);
      if (current >= limit) {
        return res.status(403).json({
          error: `Tier cap reached: ${cfg.gateId}`,
          code: 'tier_gate_exceeded',
          gate_id: cfg.gateId,
          current_tier: req.user.tier,
          limit,
          current,
          details: { target_tier: lowestUnlimitedTierFor(cfg.capKey) },
        });
      }
      next();
    } catch (err) {
      next(err);                                   // fail-closed
    }
  };
}
```

The 403 matches the tier-gating skill's cap shape (`code:'tier_gate_exceeded'`, `gate_id`, `current_tier`, `limit`, `current`, `details.target_tier`). The client maps `gate_id` to the right `LockedFeatureOverlay` cap variant (client work is a later sub-project).

---

## 5. Counters

### 5.1 Active jobs — `jobsService.ts`

```ts
/** Active = not in a terminal state. Used by the active_jobs cap. */
export async function countActive(foremanId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM jobs
       WHERE foreman_id = $1 AND status NOT IN ('complete', 'cancelled')`,
    [foremanId],
  );
  return rows[0].c;
}
```

(`'complete'`/`'cancelled'` are the two terminal states in the existing `jobsService` status machine.)

### 5.2 PDF sends this month — `invoiceSendsService.ts` (new)

```ts
import { requirePg } from './db';

/** First-of-month in UTC, so the window is deterministic regardless of server TZ. */
function utcMonthStart(now = new Date()): Date {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
}

/** Count send events by this user in the current calendar month (UTC). */
export async function countSendsThisMonth(userId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM invoice_sends
       WHERE sent_by = $1 AND sent_at >= $2`,
    [userId, utcMonthStart()],
  );
  return rows[0].c;
}

/** Record one send action. Caller sets invoice status='sent' in the same tx. */
export async function recordSend(invoiceId: string, sentBy: string): Promise<void> {
  const db = requirePg();
  await db.query(
    `INSERT INTO invoice_sends (invoice_id, sent_by) VALUES ($1, $2)`,
    [invoiceId, sentBy],
  );
}
```

The send route performs the status update + `recordSend` in a single transaction (see §7).

---

## 6. Migration `022_invoice_sends.sql`

```sql
-- Sub-project 2: per-action PDF send log. One row per send; the pdf_sends_per_month
-- cap counts rows in the current calendar month. Source of truth for "sends" (the
-- invoices table gets no sent_at column -- MAX(invoice_sends.sent_at) is last-sent).
CREATE TABLE IF NOT EXISTS invoice_sends (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  sent_by    TEXT NOT NULL REFERENCES users(id),
  sent_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoice_sends_sender_month
  ON invoice_sends (sent_by, sent_at);
```

Idempotent (`IF NOT EXISTS`), consistent with the existing migration style.

---

## 7. Send endpoint

`backend/src/invoicesRoutes.ts` — `POST /api/invoices/:id/send`:

- **Schema:** `SendInvoiceBody = z.object({}).strict()` (no inputs in this minimal version; `.strict()` rejects unknown fields per the security skill). Place with the other invoice schemas.
- **Middleware order:** `validateBody(SendInvoiceBody)` -> `requireCap({ capKey:'pdf_sends_per_month', gateId:'pdf_send_cap', count: invoiceSendsService.countSendsThisMonth })`. (`authenticateToken` is already applied to the parent `apiRouter`.)
- **Handler:**
  1. Load the invoice by `:id`. If missing -> 404.
  2. **Ownership/scope:** mirror the existing `PATCH /api/invoices/:id` check exactly (do not invent a new rule) — read that handler and apply the same `created_by`/`organization_id` scoping; on mismatch return the same status that handler uses (404/403).
  3. In a single transaction: `UPDATE invoices SET status='sent', updated_at=now() WHERE id=$1` and `invoiceSendsService.recordSend(id, req.user.id)`.
  4. Return 200 with the send result (e.g. `{ ok: true, invoice_id, sent_at }`).

`requireCap` counts **before** the insert, so the Nth send (where N = limit) is the last allowed: at `current = limit - 1` it passes and becomes `limit`; the next attempt sees `current = limit` and is refused. Open tier therefore gets exactly `limit` sends per month.

---

## 8. Wire the active-jobs cap

`backend/src/jobsRoutes.ts` — `POST /api/jobs` currently: `authenticateToken, requireConsoleTier, validateBody(CreateJobBody)`. Add the cap after `validateBody`:

```ts
requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: jobsService.countActive })
```

`req.user.id` is the value used as `foreman_id` at create, so the count matches what the insert will add to. `requireConsoleTier` is untouched.

---

## 9. Tests (runnable here)

`backend/src/__tests__/requireCap.test.ts` (new) — middleware as a function with mock `req`/`res`/`next` and an injected counter (no DB):

- **Under limit:** open tier, `active_jobs` limit 1, counter -> 0 => `next()` called once with no error; no 403.
- **At limit (active jobs):** open, counter -> 1 => 403 with exact body: `code:'tier_gate_exceeded'`, `gate_id:'active_job_cap'`, `current_tier:'open'`, `limit:1`, `current:1`, `details.target_tier:'solo'`.
- **At limit (pdf sends):** open, `pdf_sends_per_month` limit 5, counter -> 5 => 403 with `gate_id:'pdf_send_cap'`, `limit:5`, `current:5`, `target_tier:'solo'`.
- **Unlimited tier short-circuit:** solo tier => `next()` called AND the counter is **not invoked** (assert the mock counter received 0 calls).
- **Missing user:** no `req.user` => 401.
- **Counter throws:** counter rejects => `next` called with an error argument (fail-closed); no 200, no `next()` with no args.
- **`lowestUnlimitedTierFor`:** returns `'solo'` for both `active_jobs` and `pdf_sends_per_month`.

Optionally assert `CAP_LIMITS_BY_TIER` values directly (open = {1, 5}; solo/adv/ent all null) to lock the matrix.

**Deferred-verify (no `DATABASE_URL` / no supertest here):** migration 022 apply; `jobsService.countActive` and `invoiceSendsService.countSendsThisMonth` SQL; the `POST /:id/send` route end-to-end; the `POST /api/jobs` cap integration. Same deferral posture as sub-project 1 — the verifiable deliverable is the pure cap mechanism + config; the DB/route pieces are reviewed by reading and run when a database is present.

---

## 10. Files touched

- **backend/migrations/**: `022_invoice_sends.sql` (new).
- **backend/src/**: `entitlements.ts` (`CapKey`, `CAP_LIMITS_BY_TIER`), `middleware/requireCap.ts` (new), `jobsService.ts` (`countActive`), `jobsRoutes.ts` (wire active-jobs cap), `invoiceSendsService.ts` (new: `countSendsThisMonth`, `recordSend`, `utcMonthStart`), `invoicesRoutes.ts` (`POST /:id/send` + `SendInvoiceBody`), `__tests__/requireCap.test.ts` (new).

---

## 11. Risks / open items

1. **Send route ownership:** must mirror the existing `PATCH /:id` scope exactly; a looser check would let a user send another tenant's invoice. The plan instructs the implementer to read and copy that handler's check rather than invent one (security skill: cross-tenant isolation).
2. **Re-send semantics:** each send press inserts a new `invoice_sends` row, so re-sending the same invoice counts toward the cap (intended — prevents bypass by re-send).
3. **Month boundary:** the window uses UTC first-of-month computed in JS; deterministic across server timezones. Caps reset at UTC month rollover.
4. **No DB harness here:** the migration, counters, and route are deferred-verify; the middleware + config + `lowestUnlimitedTierFor` are the verifiable core.
5. **No telemetry yet:** cap hits are not recorded anywhere this sub-project. Sub-project 3 adds `gate_hit_events` (server emit inside `requireCap`). Until then there is no funnel data on cap hits — acceptable for the mechanism milestone.
6. **`requireConsoleTier` still on `POST /api/jobs`:** the legacy role gate runs before the cap; both must pass. No behavior change for roles that already passed it.

---

## 12. Next sub-projects (pre-shaped, not built)

`gate_hit_events` telemetry (emit inside `requireCap` + `requireTier`/`requireEntitlement`, `user_id_hash` = SHA256(profile id), no PII) -> trials -> founder seats -> billing webhooks (the real tier setters) -> client UI (cap variants of `LockedFeatureOverlay`, `X-Tier-Changed` refresh). Each is its own spec -> plan cycle.
