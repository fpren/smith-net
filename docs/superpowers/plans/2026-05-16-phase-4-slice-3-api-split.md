# Phase 4 Slice 3 — api.ts split into domain routers

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Split `backend/src/api.ts` (1393 LOC, ~50 endpoints) into focused domain routers. Each under ~300 LOC. Behavior unchanged.

**Architecture:** `api.ts` becomes a thin aggregator. It still exports `apiRouter` (so all existing test imports + `server.ts` stay untouched), applies `authenticateToken` once at the parent level, and mounts each domain router via `apiRouter.use(...)`. The public proposal router (`proposalPublicRouter`) also moves out of `api.ts` and gets re-exported.

**Tech Stack:** TypeScript, Express. No new dependencies.

**Scope guardrails:**
- Routes move verbatim. No new behavior, no signature changes, no test rewrites.
- `apiRouter` stays exported from `api.ts`. `proposalPublicRouter` stays exported from `api.ts`. Tests + server.ts unchanged.
- `authenticateToken` stays applied at the `apiRouter` level (`apiRouter.use(authenticateToken)`). Domain routers do NOT re-apply it.
- Imports get pruned in `api.ts` as routes leave; each new router imports only what it needs.

---

## Endpoint inventory

From `grep -nE "apiRouter\.|proposalPublicRouter\." backend/src/api.ts`:

| Domain | Lines | Endpoints |
|---|---|---|
| channels | 55–318 | POST/GET/PATCH/DELETE /channels, access flow, messages, message inject |
| sync | 393–406 | GET /sync |
| presence | 408–468 | GET/POST /presence, GET /presence/online |
| gateway | 470–557 | gateway status/relays/inject |
| engagements | 558–597 | POST/GET /engagements, GET /engagements/:id |
| reports (basic) | 598–608 | GET /reports, GET /reports/:id |
| invoices | 610–637 | GET /invoices, GET /invoices/:id, PATCH /invoices/:id/status |
| settings | 639–757 | GET/PATCH /settings, GET /settings/connectivity |
| reports (advanced) | 758–976 | assemble/render/download/share/generate |
| health/metrics | 979–1046 | GET /health, GET /metrics, POST /refresh-subscriptions |
| phase-0 (intents/synth/ledger) | 1048–1202 | intents, synthesize, ledger, small-project |
| proposals | 1203–1356 | POST/POST /revoke; proposalPublicRouter:1317 |
| invoice-links | 1357–1376 | POST /invoice-links |
| wages | 1377–1393 | GET /wages |

## Target file structure

7 new files, all under 300 LOC:

| File | Sources from api.ts | Approx LOC |
|---|---|---|
| `channelsRoutes.ts` | channels (55–318) + sync (393–406) | ~280 |
| `presenceGatewayRoutes.ts` | presence (408–468) + gateway (470–557) + health/metrics/refresh-subscriptions (979–1046) | ~220 |
| `engagementsInvoicesRoutes.ts` | engagements (558–597) + invoices (610–637) + invoice-links (1357–1376) + wages (1377–1393) | ~100 |
| `reportsRoutes.ts` | reports basic (598–608) + reports advanced (758–976) | ~230 |
| `settingsRoutes.ts` | settings (639–757) | ~120 |
| `phase0Routes.ts` | intents + synthesize + ledger + small-project (1048–1202) | ~155 |
| `proposalsRoutes.ts` | proposals (1203–1316) + proposalPublicRouter (1317–1356) | ~160 |

After the split, `api.ts` becomes:
```typescript
import { Router } from 'express';
import { authenticateToken } from './auth';
import { channelsRouter } from './channelsRoutes';
import { presenceGatewayRouter } from './presenceGatewayRoutes';
import { engagementsInvoicesRouter } from './engagementsInvoicesRoutes';
import { reportsRouter } from './reportsRoutes';
import { settingsRouter } from './settingsRoutes';
import { phase0Router } from './phase0Routes';
import { proposalsRouter, proposalPublicRouter as _publicRouter } from './proposalsRoutes';

export const apiRouter = Router();
apiRouter.use(authenticateToken);

apiRouter.use(channelsRouter);
apiRouter.use(presenceGatewayRouter);
apiRouter.use(engagementsInvoicesRouter);
apiRouter.use(reportsRouter);
apiRouter.use(settingsRouter);
apiRouter.use(phase0Router);
apiRouter.use(proposalsRouter);

export const proposalPublicRouter = _publicRouter;
```

~25 LOC. server.ts continues to do `app.use('/api', apiRouter)` and `app.use('/p', proposalPublicRouter)` — no change.

---

## Process: one task per domain router

For each task:
1. **Create the new file.** Copy the route handlers verbatim from `api.ts` into a fresh `Router()`. Import only what those handlers need.
2. **Modify `api.ts`.** Add the import + `apiRouter.use(newRouter)`. Delete the routes that were copied out. Remove imports that are now unused.
3. **Run full backend test sweep + tsc.** Must stay green.
4. **Commit.**

Sweeping the test suite between every task is the safety net. Anything that breaks is one task's worth of work to fix.

---

## Task 0: Baseline

- [ ] Confirm 188 tests pass:

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```

---

## Tasks 1-7: extract each domain router

Each follows the same recipe:

```
1. Read backend/src/api.ts to confirm current line numbers (they shift after each task).
2. Create backend/src/<domain>Routes.ts with:
   - imports the handlers need
   - export const <domain>Router = Router();
   - paste route handlers verbatim
3. Modify backend/src/api.ts:
   - import the new router
   - apiRouter.use(<domain>Router) (insert at the position the routes used to occupy, or at the bottom)
   - delete the now-extracted route handlers
   - drop imports that became unused
4. cd backend && npx tsc --noEmit  -> zero errors
5. DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test  -> 188/188
6. git add backend/src/{api,<domain>Routes}.ts && git commit
```

Order (smallest first to surface issues early):

### T1: `engagementsInvoicesRoutes.ts`
Extract: engagements + invoices + invoice-links + wages.
Imports needed (subset of api.ts):
- `Router, Request, Response` from express
- `engagement-related` imports
- `invoiceLinkService` from './invoiceLinks'
- `wageDataService` from './wageData'
- `Engagement, CreateEngagementRequest` from './types'
- `AuthenticatedRequest` from './auth'

Commit: `refactor(api): extract engagementsInvoicesRoutes (engagements, invoices, invoice-links, wages)`

### T2: `settingsRoutes.ts`
Extract: GET/PATCH /settings + GET /settings/connectivity. Includes any closures/helpers used only by those endpoints.

Commit: `refactor(api): extract settingsRoutes`

### T3: `phase0Routes.ts`
Extract: intent-authority/validate-creation, intents, synthesis-authority/validate-inputs, synthesize, artifacts, ledger, small-project endpoints. Keep them all together because the spec acknowledges they may be feature-flagged off or removed in a separate Phase 4 slice.

Commit: `refactor(api): extract phase0Routes (intents, synthesize, ledger, small-project)`

### T4: `proposalsRoutes.ts`
Extract: proposals create + revoke + `proposalPublicRouter` (the public-facing one for `/p/:uuid`). Export both: `proposalsRouter` (mounted by api.ts) and `proposalPublicRouter` (re-exported from api.ts so server.ts still imports it from './api').

The aggregator in api.ts re-exports `proposalPublicRouter`:
```typescript
export { proposalPublicRouter } from './proposalsRoutes';
```

Commit: `refactor(api): extract proposalsRoutes (auth + public proposal endpoints)`

### T5: `presenceGatewayRoutes.ts`
Extract: presence (3 endpoints) + gateway (4 endpoints) + /health + /metrics + /refresh-subscriptions. These are the operational/mesh endpoints — they share imports (presenceManager, gatewayManager, wsHandler, channelRegistry).

Commit: `refactor(api): extract presenceGatewayRoutes (presence, gateway, health, metrics, refresh-subscriptions)`

### T6: `reportsRoutes.ts`
Extract: GET /reports + GET /reports/:id + POST /reports/assemble + /render + /download + /share + /generate. Imports: `reportAssembler`, `reportRenderer`, `reportOutput`, `messageBus`, etc.

Commit: `refactor(api): extract reportsRoutes (basic CRUD + assemble/render/download/share/generate)`

### T7: `channelsRoutes.ts`
Extract: all channel endpoints + /sync (it shares the channelRegistry/messageStore dependency).

Commit: `refactor(api): extract channelsRoutes (channels + sync)`

After T7, api.ts should be ~25 LOC.

---

## Task 8: Reduce api.ts to the aggregator + closeout

**Files:**
- Modify: `backend/src/api.ts`

After Tasks 1-7, `api.ts` should already be mostly an aggregator. This task:
1. Triple-check the file has no leftover route handlers
2. Remove dead imports (anything not used by the aggregator itself)
3. Confirm the public router is re-exported

Final api.ts content:
```typescript
/**
 * Phase 4 Slice 3: aggregator. Each domain lives in its own router file;
 * api.ts just composes them and applies authenticateToken once at the parent.
 *
 * server.ts mounts `apiRouter` at /api and `proposalPublicRouter` at /p.
 */

import { Router } from 'express';
import { authenticateToken } from './auth';
import { channelsRouter } from './channelsRoutes';
import { presenceGatewayRouter } from './presenceGatewayRoutes';
import { engagementsInvoicesRouter } from './engagementsInvoicesRoutes';
import { reportsRouter } from './reportsRoutes';
import { settingsRouter } from './settingsRoutes';
import { phase0Router } from './phase0Routes';
import { proposalsRouter } from './proposalsRoutes';

export const apiRouter = Router();
apiRouter.use(authenticateToken);

apiRouter.use(channelsRouter);
apiRouter.use(presenceGatewayRouter);
apiRouter.use(engagementsInvoicesRouter);
apiRouter.use(reportsRouter);
apiRouter.use(settingsRouter);
apiRouter.use(phase0Router);
apiRouter.use(proposalsRouter);

export { proposalPublicRouter } from './proposalsRoutes';
```

- [ ] Run final sweep: `npm test`, expected 188/188
- [ ] `npx tsc --noEmit` clean
- [ ] Commit + tag:

```bash
git add backend/src/api.ts
git commit -m "$(cat <<'EOF'
refactor(api): api.ts is now a 25-LOC aggregator (Phase 4 Slice 3)

api.ts went from 1393 LOC of mixed routes to a thin composition layer.
Seven new domain routers, each under 300 LOC, each importing only what
its handlers need:

- channelsRoutes.ts        — channels + sync
- presenceGatewayRoutes.ts — presence + gateway + health/metrics
- engagementsInvoicesRoutes.ts — engagements, invoices, invoice-links, wages
- reportsRoutes.ts         — basic + assemble/render/download/share/generate
- settingsRoutes.ts        — settings + connectivity
- phase0Routes.ts          — intents/synthesize/ledger/small-project
- proposalsRoutes.ts       — proposals + public proposal endpoint

apiRouter still exported from api.ts; server.ts and all tests unchanged.
authenticateToken applied once at the parent. proposalPublicRouter
re-exported so /p/:uuid mount stays at server.ts.

Behavior unchanged. Backend tests: 28 suites / 188 passed.
EOF
)"
git tag -a phase-4-slice-3 -m "Phase 4 Slice 3 — api.ts split into domain routers"
```

---

## Done criteria

- 7 new files in `backend/src/` (each <300 LOC)
- `api.ts` < 50 LOC, aggregator only
- `tsc --noEmit` clean
- `npm test` still 28 suites / 188 tests
- `phase-4-slice-3` tag exists

## Self-review checklist

- [ ] No route handler appears in api.ts after Task 8
- [ ] No domain router calls `authenticateToken` (parent applies it)
- [ ] `proposalPublicRouter` still exported from api.ts (server.ts imports it from there)
- [ ] All test imports of `apiRouter` still resolve unchanged
- [ ] No `apiRouter` reference outside `api.ts` itself
