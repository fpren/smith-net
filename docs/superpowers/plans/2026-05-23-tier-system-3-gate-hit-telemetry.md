# Tier System Sub-project 3 — gate_hit_events Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record tier-gate hits in a PII-free `gate_hit_events` sink — server auto-emits the cap-gate refusals inside `requireCap`, and an authenticated client endpoint records the boolean/UI gate hits and upgrade CTAs.

**Architecture:** A single `telemetryService` is the only writer of `gate_hit_events`; it hashes the profile id itself (`user_id_hash = SHA256(profile.id)`), screens metadata for PII keys, and emits best-effort (awaited but never throws — a telemetry failure never breaks a gated response). `requireCap` (already async) awaits the emit on its 403 path; `POST /api/telemetry/gate-hit` derives identity/tier server-side from `req.user`.

**Tech Stack:** Node + Express + TypeScript, Postgres (`pg`), Zod, Jest. Spec: `docs/superpowers/specs/2026-05-23-tier-system-3-gate-hit-telemetry-design.md`.

**Environment notes for the implementer:**
- No `DATABASE_URL` here. DB-bound code (migration, the `INSERT`, the route) is **deferred-verify**: confirm `npx tsc --noEmit --skipLibCheck` is clean and the full `npx jest` gate has no NEW failures (the same 9 DB-integration suites fail in this env with `[usersService] DATABASE_URL is required` — that is pre-existing, not your concern). The pure pieces (allowlist, hashing, PII screen, schema, the requireCap emit decision) have real runnable tests.
- **Always prefix git/build/test commands with an absolute `cd`** (`cd /Users/fegensprenelon/smith-net/backend && ...` for build/test; `cd /Users/fegensprenelon/smith-net && ...` for git). Shell CWD persists between calls; do not rely on a previous `cd`.
- **Stage only the exact files in each commit step.** The working tree has unrelated uncommitted changes — never `git add -A`/`.`/`-am`.
- No emoji anywhere (code, comments, commit messages). ASCII only.
- Commit trailer required on every commit:
  ```
  Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
  ```

---

## File Structure

| File | Responsibility |
|---|---|
| `backend/migrations/023_gate_hit_events.sql` (create) | The F5.2 sink table + 2 indexes. |
| `backend/src/telemetryService.ts` (create) | Sole writer: `ALLOWED_EVENTS`, `isAllowedEvent`, `PII_KEYS`, `hasPiiKey`, `hashUserId`, best-effort `emitGateHit`. |
| `backend/src/schemas/telemetry.ts` (create) | `GateHitBody` (event enum + scalar metadata, PII-key refine, `.strict()`). |
| `backend/src/middleware/requireCap.ts` (modify) | Await `emitGateHit` on the 403 path. |
| `backend/src/telemetryRoutes.ts` (create) | `POST /telemetry/gate-hit` (server-derived identity/tier). |
| `backend/src/api.ts` (modify) | Mount `telemetryRouter` under the authenticated `apiRouter`. |
| `backend/src/__tests__/telemetryService.test.ts` (create) | Unit tests for the service + schema. |
| `backend/src/__tests__/requireCap-telemetry.test.ts` (create) | Assert requireCap emits on 403, not on allow/unlimited. |

---

## Task 1: telemetryService + GateHitBody schema

**Files:**
- Create: `backend/src/telemetryService.ts`
- Create: `backend/src/schemas/telemetry.ts`
- Test: `backend/src/__tests__/telemetryService.test.ts`

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/telemetryService.test.ts`:

```ts
import {
  ALLOWED_EVENTS, isAllowedEvent, hasPiiKey, hashUserId, emitGateHit,
} from '../telemetryService';
import { sha256HexGated } from '../sha256Gate';
import { GateHitBody } from '../schemas/telemetry';

describe('hashUserId', () => {
  it('is the sha256 hex of the id, 64 lowercase hex chars', () => {
    const h = hashUserId('u1');
    expect(h).toBe(sha256HexGated(Buffer.from('u1', 'utf8')));
    expect(h).toMatch(/^[0-9a-f]{64}$/);
  });
  it('is deterministic, differs per id, and is never the raw id', () => {
    expect(hashUserId('u1')).toBe(hashUserId('u1'));
    expect(hashUserId('u1')).not.toBe(hashUserId('u2'));
    expect(hashUserId('u1')).not.toBe('u1');
  });
});

describe('ALLOWED_EVENTS / isAllowedEvent', () => {
  it('has the 15 F5.2 events', () => {
    expect(ALLOWED_EVENTS).toHaveLength(15);
  });
  it('accepts known events and rejects unknown', () => {
    expect(isAllowedEvent('gate_hit.active_job_cap')).toBe(true);
    expect(isAllowedEvent('gate_hit.pdf_send_cap')).toBe(true);
    expect(isAllowedEvent('tier_upgrade.cta_clicked')).toBe(true);
    expect(isAllowedEvent('gate_hit.bogus')).toBe(false);
  });
});

describe('hasPiiKey', () => {
  it('flags PII keys', () => {
    expect(hasPiiKey({ email: 'x' })).toBe(true);
    expect(hasPiiKey({ profileId: 'x' })).toBe(true);
  });
  it('passes non-PII metadata', () => {
    expect(hasPiiKey({ limit: 1, current: 1 })).toBe(false);
  });
});

describe('GateHitBody', () => {
  it('accepts a known event with scalar metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', metadata: { x: 1 } }).success).toBe(true);
  });
  it('accepts a known event with no metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.crew_invite' }).success).toBe(true);
  });
  it('rejects an unknown event', () => {
    expect(GateHitBody.safeParse({ event: 'nope' }).success).toBe(false);
  });
  it('rejects PII metadata', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', metadata: { email: 'a@b.c' } }).success).toBe(false);
  });
  it('rejects unknown top-level keys', () => {
    expect(GateHitBody.safeParse({ event: 'gate_hit.ai_tab', foo: 1 }).success).toBe(false);
  });
});

describe('emitGateHit', () => {
  it('resolves to a no-op without a DB and never throws', async () => {
    await expect(
      emitGateHit('u1', 'gate_hit.active_job_cap', 'open', { limit: 1, current: 1 }),
    ).resolves.toBeUndefined();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/telemetryService.test.ts`
Expected: FAIL — cannot find module `../telemetryService` and `../schemas/telemetry`.

- [ ] **Step 3: Create `backend/src/telemetryService.ts`** with EXACTLY:

```ts
// backend/src/telemetryService.ts
//
// The single writer of gate_hit_events (F5.2). PII-free: it hashes the profile
// id itself (user_id_hash = SHA256(profile.id)), so a raw id can never reach the
// table. Emission is best-effort -- awaited by callers but never throws -- so a
// telemetry failure cannot break a gated response.

import { pg, isPgEnabled } from './db';
import { sha256HexGated } from './sha256Gate';
import { requestLogger } from './log';

/** The F5.2 allowlist. Server-authoritative: only these events are accepted. */
export const ALLOWED_EVENTS = [
  'gate_hit.active_job_cap',
  'gate_hit.pdf_send_cap',
  'gate_hit.plan_compiler_preview',
  'gate_hit.plan_compiler_preview_dismissed',
  'gate_hit.ai_tab',
  'gate_hit.crew_invite',
  'tier_upgrade.cta_shown',
  'tier_upgrade.cta_clicked',
  'tier_upgrade.cta_dismissed',
  'tier_upgrade.trial_started',
  'tier_upgrade.trial_expired',
  'tier_upgrade.paid_converted',
  'tier_downgrade.canceled',
  'funnel.signup',
  'funnel.first_invoice_sent',
] as const;

export type GateEvent = typeof ALLOWED_EVENTS[number];

export function isAllowedEvent(e: string): e is GateEvent {
  return (ALLOWED_EVENTS as readonly string[]).includes(e);
}

/** Keys forbidden in metadata (mass-PII defense). */
export const PII_KEYS = new Set([
  'email', 'name', 'display_name', 'displayName',
  'profile_id', 'profileId', 'id', 'user_id', 'userId', 'phone',
]);

export function hasPiiKey(metadata: Record<string, unknown>): boolean {
  return Object.keys(metadata).some((k) => PII_KEYS.has(k));
}

/** SHA256(profile.id) hex. The ONLY way an id enters the table -- never raw. */
export function hashUserId(profileId: string): string {
  return sha256HexGated(Buffer.from(profileId, 'utf8'));
}

/**
 * Best-effort durable emit. Awaited by callers but NEVER throws: no-ops without
 * a DB, skips unknown events, drops PII keys, swallows query errors.
 */
export async function emitGateHit(
  profileId: string,
  event: string,
  currentTier: string,
  metadata: Record<string, unknown> = {},
): Promise<void> {
  try {
    if (!isPgEnabled() || !pg) return;
    if (!isAllowedEvent(event)) {
      requestLogger().warn({ event: 'gate_hit_unknown_event', gateEvent: event }, 'skipped unknown gate event');
      return;
    }
    const safe: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(metadata)) {
      if (!PII_KEYS.has(k)) safe[k] = v;
    }
    await pg.query(
      `INSERT INTO gate_hit_events (event, user_id_hash, current_tier, metadata)
         VALUES ($1, $2, $3, $4)`,
      [event, hashUserId(profileId), currentTier, JSON.stringify(safe)],
    );
  } catch (e) {
    requestLogger().error({ event: 'gate_hit_emit_error', err: e }, 'gate hit emit failed');
  }
}
```

- [ ] **Step 4: Create `backend/src/schemas/telemetry.ts`** with EXACTLY:

```ts
import { z } from 'zod';
import { ALLOWED_EVENTS, PII_KEYS } from '../telemetryService';

// Spread to a mutable tuple: z.enum rejects a readonly `as const` array.
const eventEnum = z.enum([...ALLOWED_EVENTS] as [string, ...string[]]);

export const GateHitBody = z.object({
  event: eventEnum,
  metadata: z
    .record(z.union([z.string(), z.number(), z.boolean()]))
    .refine((m) => Object.keys(m).every((k) => !PII_KEYS.has(k)), {
      message: 'metadata may not contain PII keys',
    })
    .optional(),
}).strict();
export type GateHitBody = z.infer<typeof GateHitBody>;
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/telemetryService.test.ts`
Expected: PASS (all cases). If `z.enum` errors on the readonly array, confirm the `[...ALLOWED_EVENTS] as [string, ...string[]]` spread is present (Step 4).

- [ ] **Step 6: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 7: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/telemetryService.ts backend/src/schemas/telemetry.ts backend/src/__tests__/telemetryService.test.ts && git commit -m "feat(tier): telemetryService + GateHitBody (gate_hit_events writer)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: requireCap auto-emit on the 403

**Files:**
- Modify: `backend/src/middleware/requireCap.ts`
- Test: `backend/src/__tests__/requireCap-telemetry.test.ts`

- [ ] **Step 1: Write the failing test** `backend/src/__tests__/requireCap-telemetry.test.ts`:

```ts
jest.mock('../telemetryService');

import { requireCap } from '../middleware/requireCap';
import { emitGateHit } from '../telemetryService';

const emitMock = emitGateHit as jest.MockedFunction<typeof emitGateHit>;

function mockRes() {
  const res: any = { statusCode: 0, body: undefined };
  res.status = (c: number) => { res.statusCode = c; return res; };
  res.json = (b: any) => { res.body = b; return res; };
  return res;
}

beforeEach(() => {
  emitMock.mockReset();
  emitMock.mockResolvedValue(undefined);
});

describe('requireCap telemetry emit', () => {
  it('emits gate_hit on the 403 (at cap)', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 1 })(req, res, next);
    expect(res.statusCode).toBe(403);
    expect(emitMock).toHaveBeenCalledTimes(1);
    expect(emitMock).toHaveBeenCalledWith('u1', 'gate_hit.active_job_cap', 'open', { limit: 1, current: 1 });
    expect(next).not.toHaveBeenCalled();
  });

  it('does not emit when under the cap', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u1', tier: 'open' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 0 })(req, res, next);
    expect(emitMock).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });

  it('does not emit for unlimited tiers', async () => {
    const next = jest.fn();
    const res = mockRes();
    const req: any = { user: { id: 'u2', tier: 'solo' } };
    await requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: async () => 99 })(req, res, next);
    expect(emitMock).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/requireCap-telemetry.test.ts`
Expected: FAIL — the "emits gate_hit on the 403" case fails (`emitMock` called 0 times) because requireCap does not emit yet.

- [ ] **Step 3: Add the emit to `backend/src/middleware/requireCap.ts`**

(a) Add this import after line 3 (`import { Tier, CapKey, CAP_LIMITS_BY_TIER } from '../entitlements';`):

```ts
import { emitGateHit } from '../telemetryService';
```

(b) In the `if (current >= limit) {` block, insert the emit as the first line, before `return res.status(403)...`:

```ts
      if (current >= limit) {
        await emitGateHit(req.user.id, `gate_hit.${cfg.gateId}`, req.user.tier, { limit, current });
        return res.status(403).json({
```

Leave the 403 body and the rest of the function unchanged.

- [ ] **Step 4: Run the new test to verify it passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/requireCap-telemetry.test.ts`
Expected: PASS (3 cases).

- [ ] **Step 5: Confirm the existing requireCap suite still passes**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest src/__tests__/requireCap.test.ts`
Expected: PASS (8 cases). (That suite does not mock telemetryService; the real `emitGateHit` no-ops without a DB, so behavior is unchanged.)

- [ ] **Step 6: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean.

- [ ] **Step 7: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/src/middleware/requireCap.ts backend/src/__tests__/requireCap-telemetry.test.ts && git commit -m "feat(tier): requireCap emits gate_hit_events on cap refusal

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Client endpoint + mount + migration

Deferred-verify (no DB/supertest): clean `tsc` + green full Jest gate (no new failures).

**Files:**
- Create: `backend/migrations/023_gate_hit_events.sql`
- Create: `backend/src/telemetryRoutes.ts`
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Create the migration `backend/migrations/023_gate_hit_events.sql`** with EXACTLY:

```sql
-- Sub-project 3: tier-gate telemetry sink (F5.2). PII-free: user_id_hash =
-- SHA256(profile.id). Append-only event log for the conversion funnel.
CREATE TABLE IF NOT EXISTS gate_hit_events (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event        TEXT NOT NULL,
  user_id_hash TEXT NOT NULL,
  current_tier TEXT NOT NULL,
  metadata     JSONB NOT NULL DEFAULT '{}',
  occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gate_hit_events_event
  ON gate_hit_events (event, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_gate_hit_events_user_hash
  ON gate_hit_events (user_id_hash, occurred_at DESC);
```

- [ ] **Step 2: Create `backend/src/telemetryRoutes.ts`** with EXACTLY:

```ts
// backend/src/telemetryRoutes.ts
//
// POST /api/telemetry/gate-hit -- client-emitted gate hits + upgrade CTAs.
// Identity (user_id_hash) and current_tier are derived server-side from
// req.user; client-supplied identity is never trusted.

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { GateHitBody } from './schemas/telemetry';
import { emitGateHit } from './telemetryService';
import { requestLogger } from './log';

export const telemetryRouter = Router();

telemetryRouter.post('/telemetry/gate-hit', validateBody(GateHitBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as GateHitBody;
    await emitGateHit(req.user!.id, body.event, req.user!.tier, body.metadata ?? {});
    res.status(204).send();
  } catch (e: any) {
    requestLogger().error({ event: 'telemetry_gate_hit_error', err: e }, 'telemetry gate-hit error');
    res.status(500).json({ error: 'Failed to record gate hit' });
  }
});
```

- [ ] **Step 3: Mount the router in `backend/src/api.ts`**

(a) Add this import after line 19 (`import { invoicesRouter } from './invoicesRoutes';`):

```ts
import { telemetryRouter } from './telemetryRoutes';
```

(b) Add this mount after line 34 (`apiRouter.use(invoicesRouter);`):

```ts
apiRouter.use(telemetryRouter);
```

- [ ] **Step 4: Typecheck**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: clean (no output).

- [ ] **Step 5: Full Jest gate (no new regressions)**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx jest`
Expected: the non-DB suites PASS (including `telemetryService`, `requireCap`, `requireCap-telemetry`); the same 9 DB-integration suites fail only with `[usersService] DATABASE_URL is required` (pre-existing). Confirm no previously-passing suite newly fails.

- [ ] **Step 6: Commit**

```bash
cd /Users/fegensprenelon/smith-net && git add backend/migrations/023_gate_hit_events.sql backend/src/telemetryRoutes.ts backend/src/api.ts && git commit -m "feat(tier): POST /api/telemetry/gate-hit + gate_hit_events migration

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Done criteria

- `telemetryService.test.ts` and `requireCap-telemetry.test.ts` pass; existing `requireCap.test.ts` still 8/8; full `npx jest` has no NEW failures.
- `npx tsc --noEmit --skipLibCheck` clean.
- requireCap emits `gate_hit.active_job_cap` / `gate_hit.pdf_send_cap` (with `{limit,current}`) on the 403 and nothing on allow/unlimited; the client endpoint records events with a server-derived `user_id_hash` and current tier; no PII reaches the table.
- Deferred-verify (run when `DATABASE_URL` present): apply migration 023; confirm a real cap refusal writes one `gate_hit_events` row; `POST /api/telemetry/gate-hit` happy path (204) + unknown-event/PII 400.

## Notes / deferred follow-ups (do not build here)

- Server-side `tier_upgrade.trial_*` / `paid_converted` / `tier_downgrade.canceled` emits — from trials (sub-project 4) and billing (sub-project 6).
- `requireTier` / `requireEntitlement` server emits — deferred; their boolean-gate events come from the client endpoint (sub-project 7 UI).
- A read/aggregation API or dashboard over `gate_hit_events` — out of scope.
- Lossless emission via the background-jobs queue — only if best-effort loss becomes a problem.
