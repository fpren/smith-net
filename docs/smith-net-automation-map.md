# Smith Net Automation Map

Companion to [smith-net-daemon-worker-queue-plan.md](./smith-net-daemon-worker-queue-plan.md).

This document maps every recurring or event-driven behavior we expect Smith Net to perform automatically, and locates each one in the rules-first / workers / queues / daemons / AI-last architecture.

---

## Reading Guide

Each automation lists:
- **Event** — what fact in the system changes
- **Fires it** — the producer module (existing or planned)
- **Worker / handler** — which queue worker or rule module consumes the event
- **Rules vs AI** — is the body of work deterministic (rules) or judgmental (AI)?
- **Where it lives** — rule engine | cron-like (`scheduled_at`) | queue | daemon | AI worker
- **Logs land** — `audit_entries` | `background_jobs` | structured log file

---

## Automations

### A1. New job created -> geocode

| Field | Value |
|---|---|
| Event | `INSERT` into `jobs` with a non-null `address` and null `lat/lng` |
| Fires it | `jobsService.ts` (currently fire-and-forget; change to `enqueue()`) |
| Worker | `geocodeWorker` consumes `kind=geocode` |
| Rules vs AI | Rules (Nominatim HTTP) |
| Where it lives | Queue |
| Logs land | `background_jobs` row; `audit_entries` on success/failure |
| Dedupe key | `geocode:<job_id>` |

### A2. Job status -> `in_progress` -> notify assigned crew

| Field | Value |
|---|---|
| Event | `UPDATE jobs SET status='in_progress'` (via `jobsRoutes.ts` state machine) |
| Fires it | `jobsService.ts` status transition |
| Worker | Rule module `notifyRules.ts` decides recipients; enqueues `kind=ws_push` and/or `kind=email` |
| Rules vs AI | Rules only (recipient list is `jobs.assigned_crew`) |
| Where it lives | Rule engine -> queue |
| Logs land | `audit_entries` (action=`crew_notified`) |
| Notes | WS push is best-effort via `wsHandler.ts`; email is the durable channel |

### A3. Shift completed (`in_progress` -> `complete`) -> draft invoice

| Field | Value |
|---|---|
| Event | `UPDATE shifts SET status='complete'` |
| Fires it | `jobsService.ts` (or shift module) |
| Worker | `invoiceDraftWorker` consumes `kind=invoice_draft`; calls `invoiceGenerator.ts` (301 LOC) |
| Rules vs AI | Rules (line items are queries; totals are arithmetic) |
| Where it lives | Queue |
| Logs land | `audit_entries` (action=`invoice_drafted`) |
| Dedupe key | `invoice_draft:<job_id>:<shift_id>` |

### A4. Invoice ready -> email send

| Field | Value |
|---|---|
| Event | `INSERT` into `invoices` with `status='ready'` |
| Fires it | `invoiceDraftWorker` chains an enqueue at end of run |
| Worker | `emailWorker` (uses `emailService.ts` 81 LOC) |
| Rules vs AI | Rules — subject and body are templates. No LLM. |
| Where it lives | Queue |
| Logs land | `audit_entries` (action=`invoice_emailed`) + delivery receipt in `background_jobs.payload` |
| Dedupe key | `email:invoice_ready:<invoice_id>` |

### A5. Payment received (webhook) -> mark paid + notify foreman

| Field | Value |
|---|---|
| Event | HTTP POST on `/api/webhooks/payment/<provider>` (route to add) |
| Fires it | Payment provider |
| Worker | Inline handler validates signature, updates `invoices.status='paid'`, enqueues `kind=ws_push` for foreman |
| Rules vs AI | Rules only |
| Where it lives | Webhook handler (request path) + queue for notification |
| Logs land | `audit_entries` (action=`payment_received`) |
| Notes | The DB write is inline because the webhook source needs a synchronous 2xx; the notification is async |

### A6. Background job failed permanently -> admin alert

| Field | Value |
|---|---|
| Event | `background_jobs.state` transitions to `dead` |
| Fires it | `queue.ts` `fail()` when `attempts >= max_attempts` |
| Worker | `queueWatcherDaemon` enqueues `kind=admin_alert` per dead row |
| Rules vs AI | Rules (template alert: which kind, which payload, which last_error) |
| Where it lives | Daemon -> queue |
| Logs land | `audit_entries` (action=`bg_job_dead`) |
| Dedupe key | `admin_alert:bg_dead:<bg_job_id>` |

### A7. Daily 06:00 local -> summary email for foremen with active jobs

| Field | Value |
|---|---|
| Event | Wall-clock 06:00 in foreman's timezone |
| Fires it | `cleanupDaemon` (or a dedicated `scheduleSweeperDaemon`) scans for foremen with `active_jobs > 0` and inserts a `background_jobs` row with `scheduled_at=06:00` |
| Worker | `summaryEmailWorker` (kind=`summary_email`) renders a daily roll-up via `reportAssembler.ts` (319 LOC) |
| Rules vs AI | Mostly rules. Optional: a `Summarizer` AI call produces one paragraph at the top. That AI call is per-foreman-per-day, cached by `(foreman_id, date)`. |
| Where it lives | Cron-like (`scheduled_at`) -> queue, with optional AI worker |
| Logs land | `audit_entries` (action=`summary_emailed`) |
| Dedupe key | `summary_email:<foreman_id>:<yyyymmdd>` |

### A8. Offline data sync on WebSocket connect

| Field | Value |
|---|---|
| Event | WS open with a `last_seen_seq` from the client |
| Fires it | `wsHandler.ts` (after JWT auth refactor) calls `reconciliationEngine.ts` (108 LOC) |
| Worker | Inline within `reconciliationEngine.ts`; pushes deltas back over the WS |
| Rules vs AI | Rules (vector clock comparison, ledger replay) |
| Where it lives | Request path (the WS open) -> orchestrator |
| Logs land | `audit_entries` (action=`reconciled`, payload=`{deltas_count}`) |

### A9. WebSocket disconnect > 5 min -> mark presence stale

| Field | Value |
|---|---|
| Event | Wall clock + last-seen heartbeat |
| Fires it | `presenceWatcherDaemon` (new) |
| Worker | Daemon directly updates `presence.state='stale'` |
| Rules vs AI | Rules |
| Where it lives | Daemon |
| Logs land | structured log only (high-frequency event; not auditable) |

### A10. Rate limit triggered -> log + alert

| Field | Value |
|---|---|
| Event | `apiLimiter` or `authLimiter` middleware rejects |
| Fires it | Express middleware in `server.ts` |
| Worker | Inline: writes to structured log; enqueues `kind=admin_alert` once per minute per (route, actor) |
| Rules vs AI | Rules |
| Where it lives | Request path + queue (de-duped) |
| Logs land | structured log (every hit); `audit_entries` only on `admin_alert` enqueue |
| Dedupe key | `admin_alert:rate_limit:<route>:<actor_id>:<yyyymmddhhmm>` |

### A11. Geocode permanently failed (3 retries) -> notify foreman

| Field | Value |
|---|---|
| Event | `geocodeWorker` exhausted retries; `background_jobs.state='dead'` |
| Fires it | `queueWatcherDaemon` recognizes `kind=geocode` dead row |
| Worker | Enqueues `kind=ws_push` to foreman with "address could not be located, please review" |
| Rules vs AI | Rules |
| Where it lives | Daemon -> queue |
| Logs land | `audit_entries` (action=`geocode_failed_terminal`) |
| Dedupe key | `ws_push:geocode_failed:<job_id>` |

### A12. Audit JSONL retention -> archive + truncate at 90 days

| Field | Value |
|---|---|
| Event | `cleanupDaemon` runs hourly; checks JSONL files older than 90 days |
| Fires it | Daemon |
| Worker | `cleanupWorker` consumes `kind=cleanup` with payload `{target:'audit_jsonl'}`; tars files, moves to cold storage path, deletes originals |
| Rules vs AI | Rules |
| Where it lives | Daemon -> queue |
| Logs land | `audit_entries` (action=`audit_archived`, payload=`{from, to, file_count, sha256}`) |
| Dedupe key | `cleanup:audit_jsonl:<yyyymmdd>` |

### A13. SmithAI device call ready to execute write tool -> enqueue intent

| Field | Value |
|---|---|
| Event | `SmithAIToolExecutor.kt` receives an approved write-tool call (`create_job`, `add_time_entry`, `update_job_stage`, `send_message`) |
| Fires it | Android SmithAI tool layer (currently routes directly to endpoints) |
| Worker | Backend `/api/intents` endpoint -> `intentService.ts` -> `synthesizer.ts` -> `ledger.ts` |
| Rules vs AI | Rules in the synthesis path; the LLM that produced the tool call is upstream |
| Where it lives | Orchestrator (Phase-0 pipeline) — currently dead routes; this is the activation |
| Logs land | `audit_entries` (action=`intent_recorded`) + ledger row |
| Notes | This is the wiring that turns the Phase-0 routes from dead code into live code. Highest-leverage automation. |

### A14. Failed LLM call -> retry once then fall back to rules

| Field | Value |
|---|---|
| Event | `llmWorker` gets 429 / 5xx / timeout |
| Fires it | `llmWorker` |
| Worker | Retry once with backoff; on second failure, mark `kind=llm_call` dead and call the corresponding `RuleBasedFallback` handler |
| Rules vs AI | AI first, rules fallback |
| Where it lives | Queue + AI worker, with rule fallback |
| Logs land | `audit_entries` (action=`llm_fallback_used`, payload=`{prompt_id, reason}`) |

---

## Summary Table

| # | Trigger | Worker / handler | Rules / AI | Lives in |
|---|---|---|---|---|
| A1 | New job INSERT | `geocodeWorker` | Rules | Queue |
| A2 | Job -> `in_progress` | `notifyRules` -> ws_push/email | Rules | Rule + queue |
| A3 | Shift complete | `invoiceDraftWorker` | Rules | Queue |
| A4 | Invoice ready | `emailWorker` | Rules | Queue |
| A5 | Payment webhook | inline + ws_push | Rules | Request + queue |
| A6 | bg_job -> dead | `queueWatcherDaemon` -> admin_alert | Rules | Daemon + queue |
| A7 | Daily 06:00 | `summaryEmailWorker` (+ optional Summarizer AI) | Rules + optional AI | Cron-like + queue |
| A8 | WS connect | `reconciliationEngine` | Rules | Request path |
| A9 | WS idle > 5m | `presenceWatcherDaemon` | Rules | Daemon |
| A10 | Rate limit hit | middleware + queue | Rules | Request + queue |
| A11 | Geocode dead | `queueWatcherDaemon` -> ws_push | Rules | Daemon + queue |
| A12 | Audit JSONL > 90d | `cleanupDaemon` + `cleanupWorker` | Rules | Daemon + queue |
| A13 | Approved write tool call | `intentService` -> `synthesizer` -> `ledger` | Rules | Orchestrator |
| A14 | LLM call failure | `llmWorker` + `RuleBasedFallback` | AI then rules | Queue |

---

## Smaller Automations Not Worth Their Own Section

- **JWT expiry sweep.** `auth.ts` cleans expired tokens via `setInterval`. Move into `cleanupDaemon`.
- **Media cleanup.** Currently `setInterval` in `server.ts` line 329. Move into `cleanupDaemon` enqueueing `kind=cleanup` with payload `{target:'media'}`.
- **Presence ping.** Currently `setInterval` in `wsHandler.ts` line 37. Stays in WS handler — it's per-connection, not system-wide.
- **Pricing tier audit.** When a profile's tier changes, enqueue `kind=audit_flush` with the diff. Currently no log of tier transitions.

---

## Where AI Is Allowed In This Map

Of 14 automations: only **A7** (one optional paragraph in the daily summary) and **A14** (the upstream LLM call that produced a tool intent) involve AI. The other 12 are pure rules.

This is the target distribution. If the count of AI-bearing automations grows past 4 of 14 in the next year, audit again — the cost curve gets non-linear past that point.

---

## Telemetry Per Automation

Every automation writes one `audit_entries` row OR one `background_jobs` row OR both. The shape:

```
audit_entries.action  = '<automation_id>:<outcome>'  -- e.g. 'A4:invoice_emailed'
audit_entries.payload = { actor_id, target_id, duration_ms, details }
```

This convention makes "show me everything that fired in the last hour" a one-line query and makes `automation_id` discoverable by anyone reading the table.

See `smith-net-implementation-roadmap.md` for which Phase each automation lands in.
