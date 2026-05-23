# Tier System Sub-project 2 — Count Caps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add server-authoritative numeric caps (active jobs = 1, PDF sends = 5/mo for Open tier) with a structured numeric 403, wired onto job-create and a new invoice-send endpoint.

**Architecture:** A pure `requireCap` middleware takes an injected `count(userId)=>Promise<number>` so it is unit-testable without a DB; per-tier limits live in `entitlements.ts` (`null` = unlimited). Unlimited tiers short-circuit before any query. The PDF-send cap is backed by a new per-action `invoice_sends` table written atomically inside an org-fenced service method.

**Tech Stack:** Node + Express + TypeScript, Postgres (`pg`), Zod, Jest. Spec: `docs/superpowers/specs/2026-05-23-tier-system-2-count-caps-design.md`.

**Environment notes for the implementer:**
- No `DATABASE_URL` here, so DB-bound code (counters, migration, send route, job-create wiring) is **deferred-verify**: verify it by `npx tsc --noEmit --skipLibCheck` compiling clean and the full `npx jest` gate staying green. The pure pieces (`requireCap`, `lowestUnlimitedTierFor`, `utcMonthStart`, the cap matrix) have real runnable tests.
- **Always prefix git/build/test commands with `cd /Users/fegensprenelon/smith-net/backend`** (shell CWD persists between calls; do not rely on a previous `cd`).
- **Stage only the exact files listed in each commit step.** The working tree has unrelated uncommitted changes — never `git add -A`/`.`/`-am`.
- No emoji anywhere (code, comments, commit messages).
- Commit trailer required on every commit:
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/src/entitlements.ts` (modify) | Add `CapKey` + `CAP_LIMITS_BY_TIER` numeric cap registry (colocated with `CAPS_BY_TIER`). |
| `backend/src/middleware/requireCap.ts` (create) | Pure cap gate + `lowestUnlimitedTierFor`. Injected counter; unlimited short-circuit; fail-closed; numeric 403. |
| `backend/src/__tests__/requireCap.test.ts` (create) | Unit tests for the gate + helper + matrix. |
| `backend/src/jobsService.ts` (modify) | `countActive(foremanId)` counter. |
| `backend/src/jobsRoutes.ts` (modify) | Wire `requireCap('active_jobs')` onto `POST /api/jobs`. |
| `backend/migrations/022_invoice_sends.sql` (create) | Per-action send log table + index. |
| `backend/src/invoiceSendsService.ts` (create) | `utcMonthStart`, `countSendsThisMonth`, atomic org-fenced `sendInvoice`. |
| `backend/src/__tests__/invoiceSends.test.ts` (create) | Unit tests for `utcMonthStart`. |
| `backend/src/schemas/invoices.ts` (modify) | `SendInvoiceBody` (empty `.strict()`). |
| `backend/src/invoicesRoutes.ts` (modify) | `POST /api/invoices/:id/send`, gated by `requireCap('pdf_sends_per_month')`. |

---

## Task 1: Cap registry + `requireCap` middleware

**Files:**
- Modify: `backend/src/entitlements.ts` (append after the `CAPS_BY_TIER` block, currently ending line 24)
- Create: `backend/src/middleware/requireCap.ts`
- Test: `backend/src/__tests__/requireCap.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/requireCap.test.ts`:

```ts
import { requireCap, lowestUnlimitedTierFor } from '../middleware/requireCap';
import { CAP_LIMITS_BY_TIER } from '../entitlements';

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}

describe('requireCap', () => {
  it('calls next() when under the cap (open, active_jobs=1, current 0)', async () => {
    const count = jest.fn().mockResolvedValue(0);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(count).toHaveBeenCalledWith('u1');
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith();
    expect(res.statusCode).toBe(0);
  });

  it('refuses with the numeric 403 at the active_jobs cap', async () => {
    const count = jest.fn().mockResolvedValue(1);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier cap reached: active_job_cap',
      code: 'tier_gate_exceeded',
      gate_id: 'active_job_cap',
      current_tier: 'open',
      limit: 1,
      current: 1,
      details: { target_tier: 'solo' },
    });
    expect(next).not.toHaveBeenCalled();
  });

  it('refuses with the numeric 403 at the pdf_sends cap', async () => {
    const count = jest.fn().mockResolvedValue(5);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u9', tier: 'open' } };
    await requireCap({ capKey: 'pdf_sends_per_month', gateId: 'pdf_send_cap', count })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({
      error: 'Tier cap reached: pdf_send_cap',
      code: 'tier_gate_exceeded',
      gate_id: 'pdf_send_cap',
      current_tier: 'open',
      limit: 5,
      current: 5,
      details: { target_tier: 'solo' },
    });
    expect(next).not.toHaveBeenCalled();
  });

  it('short-circuits unlimited tiers without invoking the counter', async () => {
    const count = jest.fn();
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u2', tier: 'solo' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(count).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
    expect(res.statusCode).toBe(0);
  });

  it('returns 401 when req.user is missing', async () => {
    const count = jest.fn();
    const next = jest.fn();
    const res = mockRes();
    const req: any = {};
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(res.statusCode).toBe(401);
    expect(res.body).toEqual({ error: 'Authentication required' });
    expect(count).not.toHaveBeenCalled();
    expect(next).not.toHaveBeenCalled();
  });

  it('fails closed: counter error goes to next(err), not an allow', async () => {
    const boom = new Error('db down');
    const count = jest.fn().mockRejectedValue(boom);
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count })(req, res, next);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith(boom);
    expect(res.statusCode).toBe(0);
  });
});

describe('lowestUnlimitedTierFor', () => {
  it('returns solo for both count caps', () => {
    expect(lowestUnlimitedTierFor('active_jobs')).toBe('solo');
    expect(lowestUnlimitedTierFor('pdf_sends_per_month')).toBe('solo');
  });
});

describe('CAP_LIMITS_BY_TIER', () => {
  it('matches the tier-gating cap matrix', () => {
    expect(CAP_LIMITS_BY_TIER.open).toEqual({ active_jobs: 1, pdf_sends_per_month: 5 });
    expect(CAP_LIMITS_BY_TIER.solo).toEqual({ active_jobs: null, pdf_sends_per_month: null });
    expect(CAP_LIMITS_BY_TIER.advanced).toEqual({ active_jobs: null, pdf_sends_per_month: null });
    expect(CAP_LIMITS_BY_TIER.enterprise).toEqual({ active_jobs: null, pdf_sends_per_month: null });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/requireCap.test.ts`
Expected: FAIL — cannot find module `../middleware/requireCap` (and `CAP_LIMITS_BY_TIER` not exported).

- [ ] **Step 3: Add the cap registry to `entitlements.ts`**

In `backend/src/entitlements.ts`, immediately after the `CAPS_BY_TIER` declaration (which ends at line 24, before `function coreActive()`), insert:

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

- [ ] **Step 4: Create the middleware**

Create `backend/src/middleware/requireCap.ts`:

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

export interface CapConfig {
  capKey: CapKey;
  gateId: string;                              // 'active_job_cap' | 'pdf_send_cap'
  count: (userId: string) => Promise<number>;  // current usage for this user
}

/**
 * Refuse when the caller's current usage has reached their tier's numeric cap.
 * Unlimited tiers short-circuit before any count query. Fail-closed: a counter
 * that rejects is forwarded to next(err) so the request does not proceed.
 */
export function requireCap(cfg: CapConfig) {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    const limit = CAP_LIMITS_BY_TIER[req.user.tier][cfg.capKey];
    if (limit === null) return next();         // unlimited: no count, no DB hit
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
      next(err);                               // fail-closed
    }
  };
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/requireCap.test.ts`
Expected: PASS (all cases green).

- [ ] **Step 6: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/entitlements.ts backend/src/middleware/requireCap.ts backend/src/__tests__/requireCap.test.ts && git commit -m "feat(tier): requireCap middleware + numeric cap registry

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Active-jobs counter + wire `POST /api/jobs`

DB-bound, so **deferred-verify**: verification is a clean `tsc` and a green full Jest gate (the cap mechanism itself is already covered by Task 1).

**Files:**
- Modify: `backend/src/jobsService.ts` (add `countActive` near the other read functions, after `listByForeman` ~line 124)
- Modify: `backend/src/jobsRoutes.ts` (import `requireCap`; add to the `POST '/'` chain at line 63)

- [ ] **Step 1: Add the counter to `jobsService.ts`**

In `backend/src/jobsService.ts`, after `listByForeman` (ends ~line 124), add:

```ts
/** Count active (non-terminal) jobs for a foreman. Used by the active_jobs cap. */
export async function countActive(foremanId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM jobs
       WHERE foreman_id = $1 AND status NOT IN ('complete', 'cancelled')`,
    [foremanId]
  );
  return rows[0].c;
}
```

(`requirePg` is the existing private helper at jobsService.ts:80 — do not import one.)

- [ ] **Step 2: Wire the cap onto job creation**

In `backend/src/jobsRoutes.ts`, add the import after line 9 (`import { validateBody } from './middleware/validate';`):

```ts
import { requireCap } from './middleware/requireCap';
```

Then change the `POST '/'` route signature (line 63) from:

```ts
jobsRouter.post('/', validateBody(CreateJobBody), async (req: AuthenticatedRequest, res: Response) => {
```

to:

```ts
jobsRouter.post(
  '/',
  validateBody(CreateJobBody),
  requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: jobsService.countActive }),
  async (req: AuthenticatedRequest, res: Response) => {
```

Leave the handler body and the closing `});` unchanged. `requireConsoleTier` (applied via `jobsRouter.use` on line 15) stays; both gates must pass. `req.user.id` is the value passed as `foremanId` at create, so the count matches what the insert adds to.

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: no output (clean).

- [ ] **Step 4: Full Jest gate (no regressions)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: PASS — all suites green, including the new `requireCap` suite.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/jobsService.ts backend/src/jobsRoutes.ts && git commit -m "feat(tier): enforce active-jobs cap on POST /api/jobs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: `invoice_sends` migration + `invoiceSendsService`

`utcMonthStart` is pure and tested here; the SQL (migration, `countSendsThisMonth`, `sendInvoice`) is deferred-verify (compiles clean, no DB to run against).

**Files:**
- Create: `backend/migrations/022_invoice_sends.sql`
- Create: `backend/src/invoiceSendsService.ts`
- Test: `backend/src/__tests__/invoiceSends.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/invoiceSends.test.ts`:

```ts
import { utcMonthStart } from '../invoiceSendsService';

describe('utcMonthStart', () => {
  it('returns the first instant of the month in UTC', () => {
    expect(utcMonthStart(new Date('2026-05-23T18:45:30.123Z')).toISOString())
      .toBe('2026-05-01T00:00:00.000Z');
  });

  it('is stable at month end and month start (UTC)', () => {
    expect(utcMonthStart(new Date('2026-01-31T23:59:59.999Z')).toISOString())
      .toBe('2026-01-01T00:00:00.000Z');
    expect(utcMonthStart(new Date('2026-12-01T00:00:00.000Z')).toISOString())
      .toBe('2026-12-01T00:00:00.000Z');
  });

  it('uses UTC fields near the UTC-midnight boundary', () => {
    expect(utcMonthStart(new Date('2026-03-01T00:30:00.000Z')).toISOString())
      .toBe('2026-03-01T00:00:00.000Z');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/invoiceSends.test.ts`
Expected: FAIL — cannot find module `../invoiceSendsService`.

- [ ] **Step 3: Create the migration**

Create `backend/migrations/022_invoice_sends.sql`:

```sql
-- Sub-project 2: per-action PDF send log. One row per send; the
-- pdf_sends_per_month cap counts rows in the current calendar month. Source of
-- truth for "sends" (the invoices table gets no sent_at column -- last-sent is
-- MAX(invoice_sends.sent_at)).
CREATE TABLE IF NOT EXISTS invoice_sends (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  sent_by    TEXT NOT NULL REFERENCES users(id),
  sent_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_invoice_sends_sender_month
  ON invoice_sends (sent_by, sent_at);
```

- [ ] **Step 4: Create the service**

Create `backend/src/invoiceSendsService.ts`:

```ts
// backend/src/invoiceSendsService.ts
//
// The "send" domain for invoices: the monthly send counter (pdf_sends_per_month
// cap) and the atomic, org-fenced send mutation. invoice_sends is append-only:
// one row per send action.

import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[InvoiceSendsService] Postgres client not initialized');
  return pg;
}

/** First-of-month in UTC, so the cap window is deterministic regardless of server TZ. */
export function utcMonthStart(now: Date = new Date()): Date {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1));
}

/** Count send actions by this user in the current calendar month (UTC). */
export async function countSendsThisMonth(userId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM invoice_sends
       WHERE sent_by = $1 AND sent_at >= $2`,
    [userId, utcMonthStart()]
  );
  return rows[0].c;
}

export interface SendResult {
  invoiceId: string;
  sentAt: Date;
}

/**
 * Atomically mark an invoice 'sent' (org-fenced, mirroring invoicesService) and
 * append one invoice_sends row. Returns null if no invoice matches the
 * org + id + not-deleted fence (route -> 404). Either both writes land or neither.
 */
export async function sendInvoice(
  invoiceId: string,
  organizationId: string,
  sentBy: string
): Promise<SendResult | null> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const upd = await client.query(
      `UPDATE invoices SET status = 'sent', updated_at = now()
         WHERE id = $1 AND organization_id = $2 AND is_deleted = false
         RETURNING id`,
      [invoiceId, organizationId]
    );
    if (upd.rowCount === 0) {
      await client.query('ROLLBACK');
      return null;
    }
    const ins = await client.query(
      `INSERT INTO invoice_sends (invoice_id, sent_by) VALUES ($1, $2) RETURNING sent_at`,
      [invoiceId, sentBy]
    );
    await client.query('COMMIT');
    return { invoiceId, sentAt: new Date(ins.rows[0].sent_at) };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/invoiceSends.test.ts`
Expected: PASS.

- [ ] **Step 6: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: no output (clean).

- [ ] **Step 7: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/migrations/022_invoice_sends.sql backend/src/invoiceSendsService.ts backend/src/__tests__/invoiceSends.test.ts && git commit -m "feat(tier): invoice_sends table + send service (count + atomic send)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Send endpoint + `SendInvoiceBody`

Deferred-verify (no DB/supertest): clean `tsc` + green full Jest gate.

**Files:**
- Modify: `backend/src/schemas/invoices.ts` (add `SendInvoiceBody` at end of file)
- Modify: `backend/src/invoicesRoutes.ts` (imports + new route)

- [ ] **Step 1: Add the schema**

In `backend/src/schemas/invoices.ts`, append:

```ts
export const SendInvoiceBody = z.object({}).strict();
export type SendInvoiceBody = z.infer<typeof SendInvoiceBody>;
```

- [ ] **Step 2: Add the send route**

In `backend/src/invoicesRoutes.ts`:

(a) Add `SendInvoiceBody` to the schema import (lines 11-14):

```ts
import {
  CreateInvoiceBody, UpdateInvoiceBody, SetStatusBody,
  AddLineItemBody, UpdateLineItemBody, SendInvoiceBody,
} from './schemas/invoices';
```

(b) Add two imports after line 16 (`import { requestLogger } from './log';`):

```ts
import * as invoiceSendsService from './invoiceSendsService';
import { requireCap } from './middleware/requireCap';
```

(c) Insert this route immediately after the `PATCH /invoices/:id/status` handler (after line 114, before the `DELETE /invoices/:id` route). It mirrors the existing org fence (`org(req)` + the service's `organization_id` scope):

```ts
invoicesRouter.post(
  '/invoices/:id/send',
  validateBody(SendInvoiceBody),
  requireCap({
    capKey: 'pdf_sends_per_month',
    gateId: 'pdf_send_cap',
    count: invoiceSendsService.countSendsThisMonth,
  }),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const o = org(req);
      if (!o) return res.status(401).json({ error: 'user missing organization_id' });
      const result = await invoiceSendsService.sendInvoice(req.params.id, o, req.user!.id);
      if (!result) return res.status(404).json({ error: 'Invoice not found' });
      res.json({ ok: true, invoiceId: result.invoiceId, sentAt: result.sentAt });
    } catch (e: any) {
      requestLogger().error({ event: 'invoice_send_error', err: e }, 'invoice send error');
      res.status(500).json({ error: 'Failed to send invoice' });
    }
  }
);
```

- [ ] **Step 3: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: no output (clean).

- [ ] **Step 4: Full Jest gate**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: PASS — all suites green.

- [ ] **Step 5: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/schemas/invoices.ts backend/src/invoicesRoutes.ts && git commit -m "feat(tier): POST /api/invoices/:id/send gated by pdf-sends cap

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `requireCap.test.ts` and `invoiceSends.test.ts` pass; full `npx jest` gate green (no regressions).
- `npx tsc --noEmit --skipLibCheck` clean.
- Open tier refused on a 2nd active job and a 6th monthly send with the numeric `tier_gate_exceeded` 403; solo+ never counted.
- Deferred-verify (run when `DATABASE_URL` present): apply migration 022; exercise `POST /api/jobs` cap, `POST /api/invoices/:id/send` happy path + over-cap + cross-org 404.

## Notes / deferred follow-ups (do not build here)

- `gate_hit_events` telemetry inside `requireCap` — sub-project 3.
- Real email/PDF generation + `sent_with_branding` stamp on send — separate feature.
- Audit entry on send (no `INVOICE_SENT` AuditAction exists yet) — add with telemetry work.
