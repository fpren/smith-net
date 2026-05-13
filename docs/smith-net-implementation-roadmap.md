# Smith Net Implementation Roadmap

The phased build order for the redesign described in:
- [smith-net-architecture-audit.md](./smith-net-architecture-audit.md)
- [smith-net-daemon-worker-queue-plan.md](./smith-net-daemon-worker-queue-plan.md)
- [smith-net-token-optimization-plan.md](./smith-net-token-optimization-plan.md)
- [smith-net-automation-map.md](./smith-net-automation-map.md)
- [smith-net-agent-boundaries.md](./smith-net-agent-boundaries.md)

Order is dictated by risk, not by enthusiasm. The highest-probability, highest-severity failure modes come first: lost in-memory state, FK drift between `userStore` and `profiles`, fire-and-forget patterns. AI and dashboards come last — they ride on top of an operational foundation that does not exist yet.

---

## Phase Overview

| # | Phase | Theme | Ship in | Depends on |
|---|---|---|---|---|
| 1 | Audit + decision freeze | Lock the constraints | 1 week | none |
| 2 | Persistence + structured logs | No state lost on restart | 3 weeks | Phase 1 |
| 3 | Queues + workers | Background-job system | 3 weeks | Phase 2 |
| 4 | Daemons + cron-like + Phase-0 decision | Watchers and Phase-0 wiring | 2 weeks | Phase 3 |
| 5 | Token-saving AI | Rules first, AI in `llmWorker` only | 4 weeks | Phase 4 |
| 6 | Operator TUI | CLI window into queues/audits | 1 week | Phase 5 |
| 7 | Web admin dashboard | Same view for non-CLI users | 2 weeks | Phase 6 |

Total elapsed: ~16 weeks, single-developer.

---

## Phase 1 — Audit + Decision Freeze

**Theme.** Lock the constraints these six docs describe. No code yet.

**Build.**
- Review these six docs with anyone who will touch the system.
- Tag the repo `audit-2026-05-13` so we can reference "before" state.

**Modify.**
- `docs/architecture/ARCHITECTURE.md` gets a one-paragraph pointer to the new audit doc.
- `CLAUDE.md` (project root) gets a "no inline LLM in routes; no inline fire-and-forget" rule.

**DB changes.** None.

**Risks.**
- Scope creep. People will want to add features into Phase 2. Hold the line: Phase 2 is operational, not feature.

**Expected benefit.** Shared understanding. Stop digging the hole.

**Tests.** None.

**Ship in 1 week.**

---

## Phase 2 — Persistence + Structured Logs

**Theme.** Eliminate in-memory state that loses on restart. Replace JSONL audit with pg `audit_entries`. Add structured logging.

**Build.**
- `backend/src/usersService.ts` — wraps a real `users` pg table; replaces `userStore` Map in `auth.ts`. Includes the same hashing + lockout logic.
- New migration `004_users_table.sql` — `users` (id, email, password_hash, role, locked_until, created_at). Backfill is empty (no current production users).
- New migration `005_audit_entries.sql` — see schema in `smith-net-daemon-worker-queue-plan.md`.
- Persist `channelRegistry` and `gatewayManager` state into pg with TTL columns; both reconstruct from DB on boot.
- Structured logger (pino or thin wrapper) replacing scattered `console.log`. Every log line has `req_id`, `actor_id`, `route`.

**Modify.**
- `auth.ts` — `userStore` Map -> calls `usersService`. Same public API.
- `jobsService.ts` — when inserting a new user-bound job, run user-create + profile-create in the same transaction. Closes weak point #1.
- `wsHandler.ts` — JWT cookie validation on the upgrade handshake. Drop query-param `userId` path. Closes weak point #4.
- `auditLog.ts` — `append()` writes to in-memory buffer + enqueues an `audit_flush` job (works once Phase 3 lands; for Phase 2 it writes directly to `audit_entries` synchronously as an interim).
- `channelRegistry.ts` and `gatewayManager.ts` — back rows in pg; rebuild map on boot.

**DB changes.** Three new tables: `users`, `audit_entries`, plus persisted channel/gateway state.

**Risks.**
- Migration to JWT-validated WS will break existing Android clients that still send query-param userId. Mitigate: dual-accept for one release; warn on use; cut over.
- `userStore` -> pg change must preserve test seed paths used in Plan 2 tests.

**Expected benefit.** Eliminates weak points #1, #4, #5, #6. Sessions and routing survive restart.

**Tests required.**
- `users` round-trip integration test (create / login / restart server / login again).
- `audit_entries` chain validation test (10 sequential writes, verify hash chain).
- WS JWT auth test (valid cookie accepted; query-param rejected).
- Channel registry restart test (create channel, restart, channel still listed).

**Ship in 3 weeks.**

---

## Phase 3 — Queues + Workers

**Theme.** Introduce the background-job system from `smith-net-daemon-worker-queue-plan.md`. Move the first three workloads (geocode, audit-flush, email) off the request path.

**Build.**
- Migration `006_background_jobs.sql` — see schema in queue plan.
- `backend/src/queue/queue.ts` — `enqueue / claimNext / complete / fail`.
- `backend/src/workers/runner.ts` — separate Node process entrypoint.
- `backend/src/workers/geocodeWorker.ts` — Plan 4's geocode logic moved here.
- `backend/src/workers/auditFlushWorker.ts` — drains `auditLog` buffer to pg.
- `backend/src/workers/emailWorker.ts` — wraps `emailService.ts` (81 LOC).
- `package.json` script: `"worker": "tsx src/workers/runner.ts"`.

**Modify.**
- `jobsService.ts` — fire-and-forget geocode -> `enqueue({kind:'geocode', dedupeKey:'geocode:'+id})`.
- `auditLog.ts` — buffer + enqueue (replaces interim sync path from Phase 2).
- `mediaHandler.ts` cleanup — out of `setInterval`, into a `cleanup` job scheduled by Phase 4's daemon. For now, leave the existing `setInterval` until daemons land.

**DB changes.** One new table: `background_jobs`.

**Risks.**
- Two processes instead of one. Deploy pipeline needs to know. Mitigate: `pm2 ecosystem` or two `systemd` units.
- Idempotency mistakes (forgetting `dedupeKey`) cause double-sends. Mitigate: code review checklist; unit test per worker that double-enqueue yields one row.

**Expected benefit.** Closes weak point #2. Geocode survives backend restart. Audit flush no longer blocks writes. Email send no longer blocks request handler.

**Tests required.**
- Enqueue + claim + complete happy path.
- Enqueue + worker dies mid-job -> `queueWatcherDaemon` (built next phase) resets after 10m. For Phase 3, test the unstick by manually running the reset query.
- Idempotency: same `dedupeKey` enqueued twice yields one row.
- Geocode retry: Nominatim returns 503 -> row moves to `failed` -> retries with backoff -> succeeds on 3rd attempt.

**Ship in 3 weeks.**

---

## Phase 4 — Daemons + Cron-Like + Phase-0 Decision

**Theme.** Long-running watchers. `scheduled_at`-based cron-like work. Decide the fate of the dead Phase-0 routes.

**Build.**
- `backend/src/daemons/heartbeatDaemon.ts`
- `backend/src/daemons/queueWatcherDaemon.ts` — unsticks stuck `running` rows after 10m
- `backend/src/daemons/cleanupDaemon.ts` — enqueues `cleanup` jobs for media, audit JSONL, dead bg_jobs
- `backend/src/daemons/presenceWatcherDaemon.ts` — marks stale presence
- `backend/src/workers/invoiceDraftWorker.ts` — wraps `invoiceGenerator.ts`
- `backend/src/workers/reportRenderWorker.ts` — wraps `reportRenderer.ts`
- `backend/src/workers/cleanupWorker.ts`
- `/api/admin/health` endpoint — reads heartbeats and oldest pending row per kind

**Modify.**
- `api.ts` (1393 LOC) — extract domain routers: `channelsRoutes.ts`, `presenceRoutes.ts`, `engagementsRoutes.ts`, `invoicesRoutes.ts`, `reportsRoutes.ts`, `phase0Routes.ts`. Each under 300 LOC.
- Phase-0 routes (`/intents`, `/synthesize`, `/ledger/*`): **decision point**. If Phase 5 will wire SmithAI Android writes through this pipeline, keep the routes. Otherwise feature-flag them off until a real caller exists. Decision documented in a short ADR.
- Server `setInterval` calls removed (media cleanup, audit cleanup) — moved into `cleanupDaemon`. The `wsHandler.ts` presence ping STAYS (it's per-connection, not system-wide).

**DB changes.** None new; reuse `background_jobs`.

**Risks.**
- Splitting `api.ts` is mechanically safe but voluminous. Do it on a branch; one router at a time; CI passes between each.
- Phase-0 decision is binary. Hedging adds maintenance cost; "let's keep it warm" is the trap to avoid.

**Expected benefit.** Closes weak points #3 (Phase-0 dead-code), #7 (`api.ts` size), #9 (audit JSONL disk fill). Operational observability via `/api/admin/health`.

**Tests required.**
- `heartbeatDaemon` writes a row every 30s; test the cadence with a fake clock.
- `queueWatcherDaemon` resets a row whose `locked_at` is 11m old.
- `cleanupDaemon` enqueues a job; `cleanupWorker` removes a JSONL file > 90d old (use a fixture file).
- Each extracted router has its own integration test file passing.

**Ship in 2 weeks.**

---

## Phase 5 — Token-Saving AI

**Theme.** Wire AI in exactly one place (the `llmWorker`). Implement the rule-first patterns from `smith-net-token-optimization-plan.md`. Collapse the 8 SubAgents to 4 AI agents + 4 rule handlers per `smith-net-agent-boundaries.md`.

**Build.**
- `backend/src/workers/llmWorker.ts` — the only place that calls `llmInterface.ts` (469 LOC).
- Migration `007_cache_entries.sql` and `008_llm_budget.sql` — per token-optimization doc.
- Vendor prompt caching wired in `llmInterface.ts` (`cache_control` for Anthropic family via OpenRouter; equivalent for other vendors).
- Server-side rule modules: `backend/src/rules/timeKeeperRules.ts`, `coordinatorRules.ts`, `taskValidatorRules.ts`, `notifyRules.ts`.
- Android: replace `TimeKeeper`, `Coordinator`, `Onboarding`, half of `TaskValidator` with rule handlers in `android/.../rules/`.
- Android: convert `SmithAIToolExecutor.kt` (258 LOC) XML parser to vendor JSON function-calling.
- Android: `AISupervisor.kt` (994 LOC) clock -> event subscription on `AmbientEventHub`.
- Per-actor daily budget enforcement in `enqueue({kind:'llm_call', ...})`.

**Modify.**
- `SubAgents.kt` (487 LOC) — delete TimeKeeper, Coordinator, Onboarding. Merge TaskValidator into SafetyOfficer. End-state has Translator, MaterialExpert, SafetyOfficer (absorbing TaskValidator), Summarizer.
- `AIRouter.kt` (788 LOC) — add confidence-gate skip (skip LLM under 0.7), repeat-input guard, free-tier short-circuit.
- `ResponseCache.kt` — key change from raw text to `intent_type + normalized_payload_sha256`; per-intent bucketed ring buffer.
- `SmithAIAuditLog.kt` — replay through new `/api/audit/append` endpoint; on-device chain becomes forward cache.

**DB changes.** Two new tables: `cache_entries`, `llm_budget`.

**Risks.**
- Vendor prompt caching contracts can change. Pin SDK versions; have a feature flag to disable caching if the vendor returns an error.
- Collapsing SubAgents requires UX validation — some flows referenced by name. Sweep the Android codebase for hard-coded sub-agent identifiers before deleting.
- Daily caps create user-visible failure modes. Document the banner copy explicitly.

**Expected benefit.** Closes weak points #8, #9, #10. Token bill stays bounded from day one of public AI release. Eliminates the `<tool_call>` XML maintenance hazard.

**Tests required.**
- `llmWorker` retry on 429 -> falls back to rules after second failure.
- Cache hit reduces second identical call to zero vendor traffic.
- Budget exceeded -> structured 403 with `reason=daily_cap`.
- TimeKeeperRules: start, stop, sum-hours, adjust — full battery of unit tests (no LLM mocks needed).
- AISupervisor event triggers fire at most 20 times per day per device.

**Ship in 4 weeks.**

---

## Phase 6 — Operator TUI

**Theme.** A small CLI that gives operators a live view into queues, daemons, and recent audit rows. No web stack needed yet.

**Build.**
- `backend/scripts/smith-tui.ts` — runs against pg, refreshes every 5s.
- Three panels: queue depth by kind, recent dead rows, recent audit (last 50).
- `backend/scripts/requeue.ts` — flip a dead row to queued.
- `backend/scripts/audit-export.ts` — pull a date range out of `audit_entries` as JSONL (for external compliance asks).

**Modify.** None.

**DB changes.** None.

**Risks.** None real; this is a read-mostly side panel.

**Expected benefit.** First-line ops without a browser. Closes the "we have no idea what's going on inside" gap.

**Tests required.**
- TUI renders against an empty DB without crashing.
- `requeue.ts` flips one dead row and the worker picks it up.

**Ship in 1 week.**

---

## Phase 7 — Web Admin Dashboard

**Theme.** Same view as the TUI, embedded in the existing desktop portal (`desktop/portal/`).

**Build.**
- New route in `desktop/portal/src/admin/`: panels for queue depth, dead rows, recent audit.
- One read-only WS subscription that pipes `background_jobs` aggregate counts every 5s.
- Single-click requeue button hitting a new `POST /api/admin/requeue` (admin-only).

**Modify.**
- `desktop/portal/src/App.tsx` — add admin route, guarded by admin role.
- `adminRoutes.ts` (113 LOC currently) — add `/health` and `/requeue` endpoints.

**DB changes.** None.

**Risks.**
- The admin role engine in `authRoutes.ts` must already gate admin routes correctly. Audit before exposing requeue.

**Expected benefit.** Operations without SSH. Especially important when more than one human handles ops.

**Tests required.**
- Admin route is 403 for non-admin users.
- Requeue button moves a dead row to queued; UI reflects within 10s.

**Ship in 2 weeks.**

---

## Cross-Reference

| Weak point (Audit doc) | Closes in Phase |
|---|---|
| 1. userStore <-> profiles FK drift | 2 |
| 2. No background-job system | 3 |
| 3. Dead Phase-0 routes | 4 |
| 4. WS legacy auth | 2 |
| 5. channelRegistry / gatewayManager in-memory | 2 |
| 6. auditLog.ts JSONL only | 2 (interim) + 3 (queue) |
| 7. api.ts 1393 LOC monolith | 4 |
| 8. llmInterface unused, no caching | 5 |
| 9. AISupervisor 5-min clock | 5 |
| 10. Two audit chains, no reconciler | 5 |

| Automation (Automation Map) | Phase |
|---|---|
| A1 geocode | 3 |
| A2 crew notify | 3 + 4 |
| A3 invoice draft | 4 |
| A4 invoice email | 3 + 4 |
| A5 payment webhook | 4 |
| A6 dead-job admin alert | 4 |
| A7 daily 06:00 summary | 4 (rules) + 5 (optional AI paragraph) |
| A8 WS reconcile | 2 + 4 |
| A9 presence stale | 4 |
| A10 rate-limit alert | 4 |
| A11 geocode terminal fail | 4 |
| A12 audit JSONL retention | 4 |
| A13 intent pipeline activation | 5 (decision in 4) |
| A14 LLM call + rule fallback | 5 |

| Agent change (Boundaries doc) | Phase |
|---|---|
| Translator keep | 5 |
| MaterialExpert keep | 5 |
| SafetyOfficer keep (absorb TaskValidator) | 5 |
| Summarizer keep | 5 |
| TimeKeeper -> rules | 5 |
| Coordinator -> rules + queue | 5 |
| Onboarding -> wizard | 5 |
| TaskValidator -> rules pre-pass | 5 |
| AISupervisor event-triggered | 5 |
| Tool layer XML -> JSON | 5 |

---

## What "Done" Looks Like

After Phase 7:
- Backend restart loses zero state. Sessions, channels, gateway, presence all persisted.
- One process for API, one for workers. `pm2` or two `systemd` units.
- `background_jobs` is the single source of work-to-do. Geocode, email, audit-flush, invoice-draft, report-render, llm-call, cleanup all flow through it.
- Three daemons running 24/7: heartbeat, queue-watcher, cleanup.
- `audit_entries` in pg with SHA chain. JSONL is a cold backup.
- AI runs only inside `llmWorker`. Per-actor daily caps. Prompt caching. Confidence gates.
- 4 AI sub-agents + 4 rule handlers. AISupervisor is event-triggered with a hard daily cap.
- Operator TUI and admin web panel show queue depth, dead rows, recent audit.
- `api.ts` is gone (split into ~6 routers).
- Phase-0 routes are either wired to a real caller or feature-flagged off.

What did NOT happen:
- We did not add Redis. We did not add Kafka. We did not add a second datastore.
- We did not multiply SubAgents. We collapsed them.
- We did not move features onto a fire-and-forget pattern. Every async path goes through the queue.
- We did not embed LLM calls in request handlers. They all run in `llmWorker`.

This is the minimum viable reliable architecture from `smith-net-architecture-audit.md`, fully realized.
