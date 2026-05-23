# Tier System — Sub-project 3: gate_hit_events telemetry

**Status:** design (approved to draft 2026-05-23)
**Branch:** `experiment/smithcore-rom`
**Consumes:** Sub-project 2 (`requireCap`, the cap `gate_id`s), sub-project 1 (`req.user.tier` live read), M2 (`sha256HexGated`).
**Position:** Sub-project 3 of the decomposed tier system. Earlier: tier source + enforcement, count caps. Later: trials, founder seats, billing webhooks, client UI.
**PRD authority:** `docs/prds/F5.2-telemetry-sink.md` (schema + `ALLOWED_EVENTS`), `docs/ops/SUCCESS-METRICS.md` (event taxonomy). Conventions: `smith-net-tier-gating` (no PII, `user_id_hash = SHA256(profile.id)`, server emit inside `requireCap`, clients emit via `POST /api/telemetry/gate-hit`), `smith-net-security` (server-authoritative, zod `.strict()`, no PII in `gate_hit_events.metadata`), CLAUDE.md Rule 2 (no inline fire-and-forget).

---

## 0. Why this sub-project

Sub-project 2 added the cap refusals (`tier_gate_exceeded` 403) but records nothing — there is no funnel data on which gate a user hit, when, or at what tier. This sub-project adds the `gate_hit_events` sink: server auto-emits the cap-gate hits inside `requireCap`, and a client endpoint records the boolean/UI gate hits and upgrade-CTA events. Every row is PII-free (`user_id_hash` only), so the conversion funnel can be analyzed without exposing identities.

The seam was deliberately left in sub-project 2: `requireCap` emits nothing today. This fills it.

---

## 1. Scope

In scope (backend):
1. `gate_hit_events` table (migration 023, the exact F5.2 schema + 2 indexes).
2. `telemetryService.ts` — the single writer: `ALLOWED_EVENTS`, `hashUserId`, `hasPiiKey`, and `emitGateHit` (best-effort, never throws).
3. Server auto-emit inside `requireCap` on the 403 path (`gate_hit.active_job_cap`, `gate_hit.pdf_send_cap`).
4. `POST /api/telemetry/gate-hit` (authenticated) + `GateHitBody` schema — the client emit path for boolean/UI gate hits and `tier_upgrade.*` CTAs.
5. Unit tests for the pure pieces.

Non-goals (deferred):
- Server auto-emit inside `requireTier` / `requireEntitlement` (they stay synchronous and untouched; their boolean-gate events are client UI moments emitted via the endpoint — see section 2). 
- The `tier_upgrade.trial_*`, `tier_upgrade.paid_converted`, `tier_downgrade.canceled` *server-side* emits (those fire from trials/billing — later sub-projects). The event names are in `ALLOWED_EVENTS` so the client may emit the CTA variants now.
- Any analytics read/query API, dashboard, or aggregation.
- Client UI (sub-project 7).
- A background-jobs queue for emission (not needed — see section 2).

---

## 2. Architecture: best-effort awaited emit, PII enforced at one writer

**Why not auto-emit in the boolean gates.** `requireTier`/`requireEntitlement` are synchronous and their tests call them without `await`. Making them emit would force them async (breaking that contract and rewriting `tier-enforcement.test.ts`), and CLAUDE Rule 2 forbids fire-and-forget. The taxonomy frames the boolean-gate events (`gate_hit.plan_compiler_preview`, `gate_hit.ai_tab`, `gate_hit.crew_invite`) as client UI moments, so they are emitted from the client endpoint. Only `requireCap` (already async) auto-emits, which is exactly what the tier-gating skill says.

**Emission mechanism.** A gate hit is a low-frequency refusal. `emitGateHit` does one indexed INSERT, which `requireCap` **awaits** before sending the 403 — awaited (not fire-and-forget, Rule-2 compliant) and durable (the row lands before the response). It is **best-effort**: `emitGateHit` swallows its own errors (invalid event, missing DB, query failure) and never throws, so a telemetry problem can never convert a clean 403 into a 500 or block the gate. No background-jobs queue: awaiting a single insert is already durable and well under F5.2's <50ms; a queue would add a DB write of its own with no benefit at this volume (noted as a future lever if volume ever demands it).

**PII enforced at the writer.** `telemetryService` is the only module that writes `gate_hit_events`. It hashes the profile id itself (`user_id_hash = SHA256(profile.id)`) — callers pass `req.user.id`, never a hash, so a raw id cannot reach the table. `current_tier` comes from `req.user.tier` (server), not the client. `metadata` is screened against a PII key denylist. The client endpoint never trusts client-supplied identity.

---

## 3. Schema — migration `023_gate_hit_events.sql`

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

Idempotent, matching the existing migration style.

---

## 4. `telemetryService.ts`

```ts
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
 * a DB, skips unknown events, drops PII keys, swallows query errors. A telemetry
 * failure must never break the gated response.
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

---

## 5. `requireCap` auto-emit

In `backend/src/middleware/requireCap.ts`, on the refusal path (inside the `current >= limit` block), emit before responding:

```ts
import { emitGateHit } from '../telemetryService';
// ...
if (current >= limit) {
  await emitGateHit(req.user.id, `gate_hit.${cfg.gateId}`, req.user.tier, { limit, current });
  return res.status(403).json({ /* ...unchanged numeric 403 body... */ });
}
```

`gate_hit.${cfg.gateId}` produces `gate_hit.active_job_cap` / `gate_hit.pdf_send_cap` (both in `ALLOWED_EVENTS`). `emitGateHit` never throws and no-ops without a DB, so the existing `requireCap.test.ts` cases pass unchanged. The unlimited short-circuit and the allow path emit nothing.

---

## 6. Client endpoint

`backend/src/schemas/telemetry.ts` (new):

```ts
import { z } from 'zod';
import { ALLOWED_EVENTS, PII_KEYS } from '../telemetryService';

export const GateHitBody = z.object({
  event: z.enum(ALLOWED_EVENTS),
  metadata: z.record(z.union([z.string(), z.number(), z.boolean()]))
    .refine((m) => Object.keys(m).every((k) => !PII_KEYS.has(k)), { message: 'metadata may not contain PII keys' })
    .optional(),
}).strict();
export type GateHitBody = z.infer<typeof GateHitBody>;
```

`backend/src/telemetryRoutes.ts` (new), mounted under the authenticated `apiRouter`:

```ts
telemetryRouter.post('/telemetry/gate-hit', validateBody(GateHitBody), async (req, res) => {
  const body = req.body as GateHitBody;
  await emitGateHit(req.user!.id, body.event, req.user!.tier, body.metadata ?? {});
  res.status(204).send();
});
```

The handler derives `user_id_hash` (inside `emitGateHit`) and `current_tier` from `req.user` — client identity is never trusted. `z.enum(ALLOWED_EVENTS)` gives clients a clean 400 on an unknown event; the `.refine` rejects PII metadata with a 400. The awaited emit + 204 is Rule-2 compliant (persisted, not fire-and-forget). Mount in `api.ts` after `authenticateToken`, alongside the other sub-routers (`apiRouter.use(telemetryRouter)`, matching the `invoicesRouter` full-path convention).

---

## 7. Tests (runnable here)

`backend/src/__tests__/telemetryService.test.ts` (new):
- `hashUserId('u1')`: 64 lowercase hex chars; equals `sha256HexGated(Buffer.from('u1'))`; deterministic; differs for a different id; never equals the raw id.
- `isAllowedEvent`: true for `gate_hit.active_job_cap` / `gate_hit.pdf_send_cap` / `tier_upgrade.cta_clicked`; false for `gate_hit.bogus`. `ALLOWED_EVENTS` length is 15.
- `hasPiiKey`: true for `{ email: 'x' }` / `{ profileId: 'x' }`; false for `{ limit: 1, current: 1 }`.
- `GateHitBody`: parses `{ event:'gate_hit.ai_tab', metadata:{ x:1 } }`; rejects `{ event:'nope' }`; rejects `{ event:'gate_hit.ai_tab', metadata:{ email:'a@b.c' } }`; rejects unknown top-level keys (`.strict()`).
- `emitGateHit('u1','gate_hit.active_job_cap','open',{limit:1,current:1})` resolves to `undefined` without a DB (no throw).

`backend/src/__tests__/requireCap-telemetry.test.ts` (new) — `jest.mock('../telemetryService')`:
- At cap (open, count 1, `active_job_cap`): after awaiting the middleware, `emitGateHit` called exactly once with `('u1', 'gate_hit.active_job_cap', 'open', { limit: 1, current: 1 })`; response still 403.
- Under cap (count 0): `emitGateHit` not called; `next()` called.
- Unlimited tier (solo): `emitGateHit` not called; `next()` called.

**Deferred-verify (no `DATABASE_URL` / no supertest):** migration 023 apply; the real `INSERT`; the `POST /api/telemetry/gate-hit` route end-to-end. The allowlist, hashing, PII screening, schema, and the requireCap emit decision (the deliverables) are fully covered.

---

## 8. Files touched

- **backend/migrations/**: `023_gate_hit_events.sql` (new).
- **backend/src/**: `telemetryService.ts` (new), `middleware/requireCap.ts` (emit on 403), `schemas/telemetry.ts` (new), `telemetryRoutes.ts` (new), `api.ts` (mount telemetryRouter), `__tests__/telemetryService.test.ts` (new), `__tests__/requireCap-telemetry.test.ts` (new).

---

## 9. Risks / open items

1. **Best-effort vs lost events:** `emitGateHit` swallows errors, so a DB outage drops telemetry rather than failing the request. Intended — gating correctness outranks funnel completeness. (If lossless telemetry is later required, route through the background-jobs queue.)
2. **PII at the writer:** the denylist is key-based; a PII *value* under an innocuous key (e.g. `{ note: 'a@b.c' }`) would still be stored. Acceptable: server-emitted metadata is fixed (`{limit,current}`), and the client schema constrains metadata to scalars with a key denylist. Free-text values are the client's responsibility; the funnel events defined here carry none.
3. **Event/gate_id coupling:** `gate_hit.${gateId}` relies on the cap gate_ids (`active_job_cap`,`pdf_send_cap`) matching the `ALLOWED_EVENTS` suffixes. They do today; `emitGateHit` tolerantly skips any mismatch rather than writing a junk event.
4. **No DB harness here:** migration, INSERT, and route are deferred-verify; the allowlist/hash/PII/schema and the emit-decision are the verifiable core.
5. **`current_tier` is the live tier** (`req.user.tier`), consistent with enforcement — the recorded tier is the one that was actually gated.

---

## 10. Next sub-projects (pre-shaped, not built)

Trials (set `users.tier`, expiry cron; emits `tier_upgrade.trial_*`) -> founder seats -> billing webhooks (the real tier setters; emit `tier_upgrade.paid_converted` / `tier_downgrade.canceled`) -> client UI (`LockedFeatureOverlay`, the `tier_upgrade.cta_*` emits via this endpoint, `X-Tier-Changed` refresh). Each is its own spec -> plan cycle.
