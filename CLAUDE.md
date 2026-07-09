# Smith Net — Claude Code Project Instructions

This file is loaded into every Claude Code session in this repo. Read it before suggesting backend changes.

---

## Architecture Constraints (locked 2026-05-13)

Decided in the audit at `docs/smith-net-architecture-audit.md` and the roadmap at `docs/smith-net-implementation-roadmap.md`. Treat these as hard rules until a follow-up audit replaces them.

### Rule 1 — No inline LLM calls in route handlers

**Don't** call `llmInterface.ts`, OpenRouter, Anthropic, or any model vendor directly from a request handler in `backend/src/`. There is exactly one approved entry point: `llmWorker` (Phase 5, not yet built).

**Why.** Inline LLM calls hold the request thread for seconds-to-minutes, leak vendor errors as 500s, bypass per-actor budget caps, and skip vendor prompt caching. Every audit weak point in the LLM area (#8, #9, #10) traces back to "the call was inline."

**How to comply.** A route that needs LLM output enqueues a `background_jobs` row of `kind = 'llm_call'` and returns a job id; the client polls or subscribes for the result. The route does not `await` the LLM.

### Rule 2 — No inline fire-and-forget

**Don't** start async work that the response doesn't await (`void someAsync()`, `setImmediate`, `setTimeout(..., 0)`, `.then()` with no error path returning into the request scope) inside a route handler.

**Why.** Fire-and-forget patterns survive only as long as the process. Backend restart loses the work. The Plan 4 geocoder is the prototype of what this rule wants: it works today, but the audit explicitly calls it out as the next thing to move into the queue (Phase 3, A1).

**How to comply.** Persist intent first (insert the row, return 200), then enqueue a `background_jobs` row to do the side effect. The worker handles retry, dedupe, and crash recovery.

### Exceptions

- Per-connection work in `wsHandler.ts` (presence pings, message fanout) is not "fire-and-forget" — it's tied to a live WebSocket and dies with the socket. Fine.
- Synchronous CPU-bound work in a route (auth hash, JSON parse, bcrypt) is not async fire-and-forget. Fine.
- Tests can call worker code directly; the rule is about production routes.

---

## What was decided in the 2026-05-13 audit

| Decision | Reference |
|---|---|
| Background jobs go through a single `background_jobs` pg table, not Redis | `smith-net-daemon-worker-queue-plan.md` |
| 8 SubAgents collapse to 4 AI agents + 4 rule handlers | `smith-net-agent-boundaries.md` |
| 14 automations (A1-A14) enumerated, each mapped to a phase | `smith-net-automation-map.md` |
| Token-saving rules: confidence gate, response cache by intent-hash, per-actor daily cap, vendor prompt caching | `smith-net-token-optimization-plan.md` |
| 7-phase build order; AI lands in Phase 5, not earlier | `smith-net-implementation-roadmap.md` |

Phase 1 ("audit + decision freeze") closes when this file lands and the repo is tagged `audit-2026-05-13`.

---

## Project conventions

- **No emoji.** Anywhere — UI, code, commit messages, generated docs. Use ASCII tokens like `[>]`, `[x]`, `[+]`, `[-]`, `->`.
- **Design System v2 (Crew Soft / North Cobalt)** governs the portal: light-first with a user dark toggle, sn-* tokens only. The v1 light-only/monospace lock is repealed (spec: docs/superpowers/specs/2026-07-08-design-system-v2-design.md).
- **Per-profile data isolation.** Anything synced from Hetzner must be scoped per-profile; cross-profile leakage means smithnet is broken.
- **Two architecture vocabularies coexist.** Code uses `Intent` / `SummaryArtifact` / `Ledger`. Marketing/product uses `PLAN Compiler` / `Smith Mesh` / `SmithAI`. Don't conflate them in code.
