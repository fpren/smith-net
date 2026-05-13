# Smith Net Architecture Audit

Status: forward-looking blueprint. Generated 2026-05-13.
Companion docs:
- [smith-net-daemon-worker-queue-plan.md](./smith-net-daemon-worker-queue-plan.md)
- [smith-net-token-optimization-plan.md](./smith-net-token-optimization-plan.md)
- [smith-net-automation-map.md](./smith-net-automation-map.md)
- [smith-net-agent-boundaries.md](./smith-net-agent-boundaries.md)
- [smith-net-implementation-roadmap.md](./smith-net-implementation-roadmap.md)

---

## Summary

Smith Net is a ~10k LOC Node/Express backend (42 modules under `backend/src/`) plus a Compose Android app with ~8000 LOC of uncommitted SmithAI scaffolding. The backend is feature-broad but operationally thin: there are no background workers, no queues, no daemons, and no cron. Critical state (auth user store, channel registry, gateway sessions, WebSocket connections) lives in process memory and is lost on restart. The full Intent / SummaryArtifact / Ledger pipeline is wired in code under `api.ts` (1393 LOC) but has zero UI callers, making it dead-code risk. The LLM abstraction (`llmInterface.ts`, 469 LOC) is fully scaffolded and is not invoked by any route — so the user's brief about "reducing token usage" is forward-looking, not retroactive: nothing on the server burns tokens today, but the design must avoid that trap before SmithAI Android (which does call LLMs) is shipped end-to-end. This audit names ten concrete weak points, maps the missing operational components, and defines the smallest reliable architecture that closes the gaps without overbuilding.

---

## Current State

### Routes / surface

| Router | File | LOC | Notes |
|---|---|---|---|
| auth | `authRoutes.ts` | 382 | JWT 7d/30d, role engine, email verification, lockout |
| admin | `adminRoutes.ts` | 113 | Admin-only ops |
| jobs | `jobsRoutes.ts` | 156 | Plan 2 CRUD + assign + status state machine |
| profiles | `profilesRoutes.ts` | 30 | Plan 3 / Plan 4 search + crew roster |
| api (catchall) | `api.ts` | 1393 | ~50 endpoints: channels, presence, gateway, engagements, invoices, reports, intents, ledger, proposals, wages |
| media | `mediaHandler.ts` | 232 | Multipart upload + cleanup |

### Services backed by `pg`

18 modules import the real Postgres driver — `auth.ts`, `intentService.ts`, `synthesizer.ts`, `ledger.ts`, `messageBus.ts`, `messageStore.ts`, `jobsService.ts`, `reconciliationEngine.ts`, `identityResolver.ts`, `presenceManager.ts`, `vectorClock.ts`, `intentAuthority.ts`, `synthesisAuthority.ts`, `ledgerAuthority.ts`, `archiveService.ts`, plus a few more. Two migrations exist: `002_full_schema.sql`, `003_jobs_expansion.sql`.

### Stores that LOSE on restart

| Module | What it holds | Persistence | Impact |
|---|---|---|---|
| `auth.ts` | `userStore` map | None — in-memory | All sessions invalid; FK drift vs `profiles` |
| `channelRegistry.ts` | channel membership, 378 LOC | None | Cross-device channels reset |
| `gatewayManager.ts` | gateway routing, 178 LOC | None | Routing table cold |
| `wsHandler.ts` | WS connections + presence, 497 LOC | None | Presence flicker; offline queue rebuild |

### Scaffolded-but-unused

- `llmInterface.ts` (469 LOC) — multi-provider LLM abstraction. Zero callers.
- Phase-0 routes (`/intents`, `/synthesize`, `/ledger/*`, 11 mounts) — wired with multi-authority validation (`intentAuthority`, `synthesisAuthority`, `ledgerAuthority`) and SHA256 sealing. No frontend or Android caller exists yet.
- Heavy domain modules with no automated tests: `autoQuoteEngine.ts` (318), `electricianTools.ts` (463), `payrollDocuments.ts` (323), `invoiceGenerator.ts` (301), `reportAssembler.ts` (319), `reportRenderer.ts` (455), `outputGenerator.ts` (298).

### AI surface (Android, uncommitted)

- Orchestrator: `SmithAIConversationOrchestrator.kt`
- Router: `AIRouter.kt` (788 LOC) with cue detection, battery gates, rule/LLM fallback, response cache
- Supervisor: `AISupervisor.kt` (994 LOC) — runs every ~5 min, Advanced+ tier
- Sub-agents: `SubAgents.kt` (487 LOC) — 8 of them (Translator, TimeKeeper, MaterialExpert, TaskValidator, SafetyOfficer, Coordinator, Summarizer, Onboarding)
- Tool layer: `SmithAIToolRegistry.kt` (7 tools), `SmithAIToolExecutor.kt` (258 LOC, custom XML `<tool_call>` parser)
- Backends: `OpenRouterClient.kt`, `LlamaInference.kt`, `SmithAIBackendRouter.kt`
- Cache: `ResponseCache.kt` (7-day TTL, 200-entry JSON)
- Audit: `SmithAIAuditLog.kt` (SHA256-chained JSONL) — SEPARATE from backend's `auditLog.ts`
- Battery, offline queue, tier gate: `BatteryGate.kt`, `OfflineQueueManager.kt`, `SmithAITierGate.kt`

### Audit + scheduled work

- `auditLog.ts` (352 LOC) writes JSONL files daily with SHA256 checksums. NOT in DB.
- The only "scheduled" code is `setInterval` in `server.ts` (media cleanup hourly, audit cleanup daily) and `wsHandler.ts` (presence ping). No cron, no `node-cron`, no Redis Queue, no BullMQ, no pg-boss.

---

## Top 10 Weak Points

Each row: what + why it bites + when it bites + fix + when to fix.

### 1. `userStore` is in-memory; `jobs.foreman_id` FK references `profiles(id)` [closed in slice 1, commit 91b835b]

- **What.** `auth.ts` mints user ids in a Map. Production has no path that copies that id into `profiles`. Plan 2 tests use a helper to work around it.
- **Why.** First time a real foreman tries to create a job, INSERT fails on FK; or worse, succeeds with a stub profile that has no name, splitting identity across two tables.
- **When.** First production user creates a job. Or: any backend restart invalidates every active JWT because the `userStore` is empty.
- **Fix.** Persist users in pg as part of authentication; collapse `userStore` into a `users` table; trigger or service-layer insert that creates a matching `profiles` row in the same transaction.
- **When to fix.** Roadmap Phase 2. Blocking before any external beta.

### 2. No background-job system at all

- **What.** No queue table, no worker process, no retry, no dead-letter.
- **Why.** Plan 4 makes geocoding fire-and-forget after job INSERT. Email send, invoice generation, audit flushes — all inline or scattered.
- **When.** Whenever Nominatim is slow, or the server restarts during a request, or an SMTP send hangs the request loop.
- **Fix.** Single pg-backed `background_jobs` table (see `smith-net-daemon-worker-queue-plan.md`). Two workers (geocode, audit-flush) and three daemons (heartbeat, queue-watcher, cleanup) on day one.
- **When to fix.** Roadmap Phase 3. Geocode is the canary.

### 3. Dead Phase-0 routes (Intent / SummaryArtifact / Ledger)

- **What.** 11 mounts in `api.ts` for `/intents`, `/synthesize`, `/ledger/*`. Multi-authority validation, SHA256 sealing — all real code. Zero callers.
- **Why.** Code rots without callers. Schema drift accumulates. Operational cost (test maintenance, mental load) is real.
- **When.** Every refactor wave; every schema migration that misses these tables.
- **Fix.** Either (a) wire one real client path (Android SmithAI write-tools should go through `intentService`) or (b) move these into a feature-flagged module and skip from the default route set until the client lands.
- **When to fix.** Roadmap Phase 4 (decision point at end of Phase 3).

### 4. `wsHandler.ts` uses legacy `userId + userName` query auth [closed in slice 3, commit 1ac4275]

- **What.** 497 LOC handler accepts identity via WS connect params. New REST stack is JWT + httpOnly cookie.
- **Why.** Inconsistent auth = trivial spoofing. A second client can claim any `userId`.
- **When.** First time someone reads `wsHandler.ts` source (now public if repo leaks) or runs a packet inspector.
- **Fix.** Validate the JWT from the upgrade handshake cookie; reject otherwise. Drop the query-param path.
- **When to fix.** Roadmap Phase 2 (security-affecting).

### 5. `channelRegistry` + `gatewayManager` in-memory [closed in slice 4, commit 1a7d8b5]

- **What.** 378 + 178 LOC of routing state held in process maps.
- **Why.** Restart wipes membership; reconnect storms; mesh routing forgets peers.
- **When.** Every deploy. Every crash. Every OOM.
- **Fix.** Persist channel membership and gateway sessions in pg with a TTL column; rebuild on boot from DB rather than waiting for clients to re-announce.
- **When to fix.** Roadmap Phase 2.

### 6. `auditLog.ts` writes JSONL files, not DB rows [closed in slice 2, commit 85685aa]

- **What.** Daily file with SHA256 checksums. Cleanup via `setInterval` every 24h.
- **Why.** Search is grep. Querying for "all writes by foreman X in March" is `find | xargs grep`. SHA chain spans files, not records — re-org is destructive.
- **When.** First compliance question; first time anyone asks "who changed this row."
- **Fix.** Add `audit_entries` pg table (id, ts, actor, action, target, payload, prev_hash, hash). Keep the JSONL as an immutable backup; query against the table.
- **When to fix.** Roadmap Phase 2.

### 7. `api.ts` is 1393 LOC of 50 endpoints in one file

- **What.** Channels, presence, gateway, engagements, invoices, reports, intents, ledger, proposals, wages.
- **Why.** Implicit coupling, no domain boundary, change in one feature triggers full retest.
- **When.** Every Plan that touches more than one of those domains (most of them).
- **Fix.** Split into `channelsRoutes.ts`, `presenceRoutes.ts`, `engagementsRoutes.ts`, `invoicesRoutes.ts`, `reportsRoutes.ts`, `phase0Routes.ts` (intents/synthesize/ledger). Keep `proposals` public bits in their own public router.
- **When to fix.** Roadmap Phase 4 (low-risk refactor with high mental dividend).

### 8. Scaffolded `llmInterface.ts` has no callers, no rate limit, no cache key contract

- **What.** 469 LOC multi-provider client. No usage in the route layer.
- **Why.** First time anyone wires it up, every concern (caching, budgets, prompt versioning, retries) is unsolved.
- **When.** The moment SmithAI Android starts proxying through the backend, or the moment server-side synthesis turns AI on.
- **Fix.** Before any caller is added, define: cache key contract (intent-type + summary digest, not raw text), rate-limit per-actor, retry budget, prompt-cache integration, structured-output schema. See `smith-net-token-optimization-plan.md`.
- **When to fix.** Roadmap Phase 5 (the LLM phase).

### 9. SmithAI `AISupervisor` runs every ~5 minutes

- **What.** Proactive insight loop in `AISupervisor.kt` (994 LOC).
- **Why.** That's ~288 calls/day per active device just for ambient. Even with on-device Qwen3 this drains battery; with OpenRouter it's a real bill.
- **When.** First Advanced-tier user with the app open all day.
- **Fix.** Event-triggered: fire on `job_status_changed`, `shift_completed`, `invoice_created`, `presence_idle_60m` — not a wall-clock timer. Add a per-day hard cap.
- **When to fix.** Roadmap Phase 5.

### 10. Two audit chains (backend JSONL + SmithAI Android SHA chain) and no reconciliation

- **What.** `auditLog.ts` server-side, `SmithAIAuditLog.kt` device-side, both SHA256-chained, no cross-checking.
- **Why.** Disagreements are silent. Determinism claim of NFR-D1..D5 weakens.
- **When.** First time an Enterprise customer asks for a unified audit export.
- **Fix.** When backend audit moves into pg (#6), have the Android queue replay through a single `/api/audit/append` endpoint; backend acts as authority; on-device chain becomes a forward cache only.
- **When to fix.** Roadmap Phase 6.

---

## Component Fit Map

What component shape (rule engine, daemon, worker, etc.) maps to which Smith Net concern. Built specifically to this codebase — not a generic ops checklist.

| Component | Why Smith Net needs it | Where it lives (existing or new) | Talks AI? |
|---|---|---|---|
| **Rule engine** | Routing, gating, status transitions, classification of simple intents | New `backend/src/rules/` — small pure-function modules; one per concern (`jobRules.ts`, `messageRules.ts`, `invoiceRules.ts`) | No |
| **Queue** | Geocode, email, invoice draft, report render, audit flush, LLM call | New pg table `background_jobs` + adapter `backend/src/queue/queue.ts` | No |
| **Worker** | Pulls from queue; runs one job kind; idempotent | New `backend/src/workers/*.ts` (one file per kind) | Only for `llmWorker` |
| **Daemon** | Long-running watcher; promotes / cleans / scans | New `backend/src/daemons/*.ts`: `heartbeatDaemon`, `queueWatcherDaemon`, `cleanupDaemon` | No |
| **Script** | One-shot ops: backfill, migration helpers, audit exports | New `backend/scripts/*.ts` (run via `tsx`) | No |
| **AI agent (server)** | Judgment, classification, NL summarization for ambiguous inputs | Future `backend/src/ai/agents/*` — wraps `llmInterface.ts` | Yes |
| **AI agent (device)** | On-device judgment + ambient supervisor | Existing Android `ai/` directory | Yes |
| **Orchestrator** | Workflow order for multi-step actions (intent -> synthesize -> ledger) | Existing `intentService.ts` + `synthesizer.ts` + `ledger.ts`; needs a thin coordinator `phase0Orchestrator.ts` | No (until a step calls AI) |
| **Scheduler / cron** | Daily summary, archive, recurring invoice cycles | A single tick daemon that reads `scheduled_at` rows in `background_jobs` — no extra cron service | No |
| **Cache** | LLM responses, geocode results, profile lookups | Existing Android `ResponseCache.kt`; new server-side pg-backed `cache_entries` | No |
| **Logger** | Structured per-request log lines | Already minimal (`console.log` and audit); add `pino` or a thin wrapper | No |
| **Monitor / health** | Live counters: queue depth, oldest pending job, error rate | New `/api/admin/health` (already a route stub) + heartbeat row in `background_jobs` | No |
| **Supervisor (proactive AI)** | Event-triggered insight generation on device | Existing `AISupervisor.kt` — change trigger model | Yes |
| **TUI / ops UI** | Operator window into queues, daemons, audit | Future small CLI on top of `pg` (Phase 6) | No |
| **Web dashboard** | Same view for non-CLI ops | Future minimal admin page in desktop portal | No |
| **Rule-based fallback** | When AI confidence < threshold or model unavailable | Existing Android `RuleBasedFallback.kt`; add server-side mirror for synthesis | No |
| **Offline sync engine** | Mesh + device replay on reconnect | Existing `reconciliationEngine.ts` (108 LOC) + `OfflineQueueManager.kt` | No |

---

## Premortem

8 failure scenarios that this codebase is set up to hit. Sorted by descending probability * severity.

| # | Scenario | Root cause | Early warning | Severity (1-5) | Probability (1-5) | Prevention | Recovery | Component responsible |
|---|---|---|---|---|---|---|---|---|
| 1 | First production foreman gets 500 on create-job | `userStore` <-> `profiles` FK drift | Plan 2 had to add a test helper to insert profile rows manually | 5 | 5 | Persist `users` in pg; insert `profiles` row in same tx | Backfill from logs; insert missing profiles | `auth.ts`, new `usersService.ts` |
| 2 | Backend restart loses all logged-in sessions | `userStore` is a Map | First deploy after JWT issuance | 4 | 5 | Persist users in pg; JWT validates against pg user, not Map | Force re-login | `auth.ts` |
| 3 | Job stays unpinned forever after Nominatim outage | Plan 4 geocode is fire-and-forget, no retry | Map view shows pin missing | 3 | 4 | Move geocode into `background_jobs` queue; retry with backoff | Manual re-enqueue script | `geocodeWorker.ts` (new) |
| 4 | WebSocket spoof: client claims any `userId` | `wsHandler.ts` query-param auth | Anyone reading source | 5 | 3 | JWT cookie validation on upgrade | Rotate JWT secret; invalidate sessions | `wsHandler.ts` |
| 5 | Channel routing broken for 10 min after deploy | `channelRegistry` in-memory | Mesh tests fail post-deploy | 3 | 4 | Persist channel membership; rebuild on boot | Force client re-announce | `channelRegistry.ts` |
| 6 | First time SmithAI ships, monthly bill 10x expected | Supervisor 5-min interval, no prompt cache, no batching | OpenRouter dashboard cost graph | 4 | 4 | Event-triggered supervisor; prompt caching; daily cap | Kill switch on `AISupervisor.enabled` | `AISupervisor.kt`, `OpenRouterClient.kt` |
| 7 | Audit query during compliance review takes hours | JSONL daily files, grep-only search | First compliance ask | 3 | 3 | Move audit to pg; keep JSONL as cold backup | Build pg audit table from JSONL | `auditLog.ts` |
| 8 | Phase-0 ledger table schema drifts unnoticed | Routes wired but unused | Random failure on first real call | 3 | 3 | One smoke test per route in CI; or feature-flag the routes off until a client wires up | Replay synthesis; rebuild ledger | `intentService.ts`, `synthesizer.ts`, `ledger.ts` |
| 9 | Audit JSONL files fill disk | Daily file growth + retention only on `setInterval` | Disk metric near full | 3 | 2 | Move retention to a worker; alert at 70 percent | Rotate / archive | `auditLog.ts`, new `cleanupDaemon` |
| 10 | SmithAI XML tool-call parser breaks on a model that emits markdown | Custom XML parser in `SmithAIToolExecutor.kt` | First model upgrade | 3 | 3 | Switch to vendor function-calling JSON schemas | Hotfix parser tolerances | `SmithAIToolExecutor.kt` |
| 11 | `api.ts` merge conflicts block two parallel plans | 1393 LOC single file | Plan 3 + Plan 4 already collided once | 2 | 4 | Split into domain routers | Manual merge | `api.ts` |
| 12 | Reconciliation engine silently drops a vector clock entry on slow client | `reconciliationEngine.ts` (108 LOC) and no replay test under mesh latency | Cross-device ledger divergence | 5 | 2 | Add chaos test; persist vector clock per-cord in pg | Replay from intent stream | `reconciliationEngine.ts`, `vectorClock.ts` |

---

## Do Not Build This Way

Anti-patterns this audit explicitly rejects. Each one is either already half-present in the repo or a tempting next step.

1. **Do not add Redis or a second datastore for queues.** Postgres can hold a `background_jobs` table with `FOR UPDATE SKIP LOCKED` semantics. A second runtime doubles ops surface for zero feature gain at this scale.
2. **Do not run workers in the same process as the API.** Even if it's the same Node binary started with a different entrypoint, keep the worker loop in `backend/src/workers/runner.ts` invoked as a separate process. Otherwise an API request and a worker share an event loop and back-pressure stops mattering.
3. **Do not put AI in the request path for status changes or simple validations.** A job status transition is a state machine; an invoice subject line is a template. Rules first; LLM only for ambiguous text or judgment.
4. **Do not expand the 8 SmithAI SubAgents.** Several (TimeKeeper, Coordinator, Onboarding) are rule-shaped and should shrink. See `smith-net-agent-boundaries.md`.
5. **Do not keep two SHA-chained audit logs (server JSONL + Android JSONL) without a reconciler.** Pick one authority. Backend pg is the right choice; device queue replays into it.
6. **Do not let `api.ts` grow past 1500 LOC.** Split before Plan 5.
7. **Do not turn on the Phase-0 routes (Intent / SummaryArtifact / Ledger) without a real client path.** Either wire them or feature-flag off; do not maintain dead routes "just in case."

---

## Minimum Viable Reliable Architecture

The smallest stack that delivers automation + low token usage + crash recovery + background processing + structured logs + queue/worker separation + AI boundaries.

```
                            +---------------------+
                            |   Android Console   |
                            |   (SmithAI device)  |
                            +----------+----------+
                                       |
                              JWT cookie (REST + WS)
                                       |
+-----------+        +-----------------v------------------+        +-----------------+
|   pg DB   |<-------+       Express API process          +------->+   Mesh peers    |
|           |        |    server.ts + domain routers      |        |   (BLE/WiFiD)   |
|  users    |        |    rules first, AI last            |        +-----------------+
|  profiles |        +---------+--------------+-----------+
|  jobs     |                  |              |
|  channels |                  |              | enqueue(kind,payload)
|  bg_jobs  |<-----------------+              v
|  audit    |        +------------------------+-----------+
|  cache    |        |   Worker process: workers/runner   |
|           |        |   FOR UPDATE SKIP LOCKED loop      |
|           |        |   geocode | audit-flush | email |  |
|           |        |   invoice-draft | llm | cleanup    |
|           |        +------------------------+-----------+
|           |                  ^              |
|           |                  | scheduled_at | logs
|           |        +---------+--------------+-----------+
+-----------+        |   Daemons: heartbeat, queueWatcher,|
                     |   cleanup, presenceWatcher         |
                     +------------------------------------+
```

Components present from day one:
- **One queue table** (`background_jobs`) — not Redis, not Kafka.
- **One worker process** — separate Node entrypoint reading the queue.
- **Three daemons** — heartbeat, queue-watcher, cleanup. All in the worker process.
- **Rule modules** — small pure-function files under `backend/src/rules/`.
- **Audit in pg** — `audit_entries` table; JSONL retained as cold backup.
- **AI as a worker kind** — `llmWorker` consumes `background_jobs` of kind `llm_call`; never inline in request path.
- **Structured logs** — single logger, JSON lines, with `req_id` and `actor_id` propagation.

Things deliberately NOT in MVRA:
- Redis, Kafka, RabbitMQ.
- Separate scheduler service. The queue's `scheduled_at` column plus a watcher daemon covers cron.
- Microservices. One API process + one worker process is enough through Tier 3.
- An ORM. Stay with raw `pg`.
- Server-side AI in the request path. Workers only.

See `smith-net-implementation-roadmap.md` for the 7-phase build order.
