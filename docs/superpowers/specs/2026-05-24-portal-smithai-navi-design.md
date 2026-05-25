# Portal SmithAI "navi" -- Design (build deferred to Phase 5)

> Scope/design only. Implementation is gated on Phase 5 (the approved
> `llmWorker` LLM entry point, which does not exist yet). Status: design
> approved 2026-05-24.

**Goal:** A portal-side SmithAI "navi" -- a conversational AI companion that
answers about the user's jobs/clients/time/messages and summarizes invoices,
suggestion-only, Advanced-tier-gated -- architected to match the in-progress
Android SmithAI and to run through the approved asynchronous LLM path (never
inline). This document is the design; building waits for Phase 5.

---

## 1. Context

The Android app has an in-progress SmithAI (untracked `SmithAI*.kt` files): a
single-turn tool-calling chat loop, Advanced-tier gated, with 8 tools (4
read-only `query_*`, 4 add-only writes requiring approve/deny), a per-user
`ContextBuilder` (~1400-token prompt), an on-device-first inference router
(llama.cpp Qwen3-1.7B -> cloud via the user's own key -> offline), and an
append-only SHA-256-chained audit log for write tools. A separate `AISupervisor`
does proactive insights (auto/semi-auto/off).

The portal has **no AI today**. The backend's `llmInterface.ts` (vendor-neutral
`LLMManager`) exists but is a dead import; `background_jobs` has an `llm_call`
kind scaffolded but **no `llmWorker`** consumes it. Two locked rules govern this:
- **CLAUDE.md Rule 1:** no inline LLM calls in route handlers -- the only approved
  entry point is `llmWorker` (Phase 5).
- **AI is deferred to Phase 5** (architecture audit). SmithAI is the **Advanced**
  tier hero feature; crew-awareness is **Enterprise** (`crew_aware_smithai`).
- Determinism (NFR-D5): AI is suggestion/observation only -- it may draft, but a
  human must propose+confirm; AI never seals/mutates ledger artifacts or advances
  a cord transition.
- Per-profile/crew isolation: solo users must never see crew data.

This design brings the navi to the portal **through the approved async worker
path**, mirroring the Android tool/tier model, and explicitly defers the build.

---

## 2. Architecture (Path A -- async, never inline)

```
NaviPanel (portal)
  -- POST /api/ai/chat (Advanced-gated, validateBody) --> enqueue background_jobs { kind:'llm_call' } -> { jobId }
                                                               |
  llmWorker (Phase-5 PREREQUISITE) claims the job:
    ContextBuilder (server-side, per-profile scoped)
      -> llmInterface (vendor-neutral; per-actor budget cap; response cache by intent-hash; vendor prompt caching)
      -> parse at most one tool call -> execute a READ tool server-side (scoped to the actor) -> compose the answer
                                                               |
  portal receives the result via GET /api/ai/chat/:jobId (poll) OR a WebSocket push -> renders in the panel
```

The route **only enqueues**; it never awaits the LLM (Rule 1). Tools execute
**inside the worker against the Hetzner API/DB**, not in the browser (the portal
has no in-memory repositories like Android does).

---

## 3. v1 scope (read-only assistant)

- **NaviPanel:** a slide-over companion panel opened from a header/dashboard
  affordance -- NOT a bottom-nav tab (preserves the 3-tab mobile cleanup).
  Advanced users get the live panel; Open/Solo users see a `LockedFeatureOverlay`
  (SHOW + LOCK + upgrade CTA, per the tier rules; gate_id `ai_tab`).
- **Capabilities:** chat + the 4 **read** tools (`query_jobs`, `query_client`,
  `query_time_entries`, `query_messages`) + **"Summarize with SmithAI"** -- a
  one-click action on the invoice-detail screen AND an in-chat ability.
- **Suggestion-only; no writes** in v1.

---

## 4. Tiering, isolation, determinism

- Route enforces `requireTier('advanced')` + `requireCap({ gate_id: 'ai_tab' })`;
  gate-hit telemetry stores `user_id_hash` only (no PII).
- **Crew-aware context = Enterprise (`crew_aware_smithai`), deferred to SP-3.** In
  v1 every read tool scopes its query to the actor via `visibilityScope`/
  `buildJobVisibilityClause` -- solo never sees crew data.
- **Suggestion-only:** the navi never seals/mutates ledger artifacts and never
  advances a cord transition (NFR-D5). v1 is read-only regardless.
- **Audit:** server-side append-only audit entries for AI invocations (and, in
  SP-2, write approve/deny) -- the DB equivalent of Android's SHA-256-chained
  JSONL, via the F11.1 audit log.
- **Budget/caching** live in the worker (per-actor daily cap, response cache by
  intent-hash, vendor prompt caching) -- the token-optimization plan.

---

## 5. Components / files (when built)

### Portal
- `console/ai/aiClient.ts` -- `POST /api/ai/chat` (enqueue, returns jobId) +
  result via `GET /api/ai/chat/:jobId` polling and/or WS subscription.
- `console/ai/NaviPanel.tsx` -- the gated slide-over chat companion; renders the
  `LockedFeatureOverlay` for non-Advanced.
- `console/ai/naviStore.ts` -- conversation state (messages, pending jobId).
- Invoice detail -- a "Summarize with SmithAI" action that opens the navi with a
  summarize intent for that invoice.
- A header/dashboard affordance to open the navi.

### Backend
- `llmWorker.ts` -- **SP-0 prerequisite**: consume `background_jobs` `llm_call`,
  route via `llmInterface`, enforce budget caps + response cache, deliver results.
- `POST /api/ai/chat` (Advanced-gated, `validateBody`, enqueues `llm_call`) +
  `GET /api/ai/chat/:jobId` (result/status).
- Server-side `ContextBuilder` + scoped read-tool executors + audit writes.

---

## 6. Decomposition (build order, once Phase 5 is greenlit)

```
SP-0  llmWorker (PREREQUISITE, backend)  consume llm_call; llmInterface routing; budget caps; response cache; result delivery
SP-1  navi v1 (read-only)                NaviPanel + aiClient + POST/GET routes + server ContextBuilder + 4 read tools + invoice summary + Advanced gate + audit
SP-2  add-only writes                    create_job / send_message / add_time_entry / update_job_stage with approve/deny (human confirms -> satisfies D5) + write audit
SP-3  crew-aware (Enterprise)            crew context + crew chat; crew_aware_smithai gate; preserves solo crew-isolation
```

Each is its own spec -> plan -> implementation cycle when AI is unfrozen. SP-0 is
the gate for all of them.

---

## 7. Out of scope

- **The actual build** -- Phase-5-gated; this document is a design only.
- On-device / in-browser inference (WebLLM/WASM) -- a future option, not v1.
- Write tools (SP-2) and crew-awareness (SP-3).
- The proactive `AISupervisor` layer (insights/inbox) -- a separate future design.
- Browser-direct-to-vendor inference -- rejected (bypasses budget caps + leaks
  keys; conflicts with Rule 1 + the token-optimization plan).

---

## 8. Open questions

None for the design. Inference path (worker-routed Path A), v1 capability scope
(read-only chat + invoice summary), UI surface (slide-over panel, not a nav tab),
tier gating (Advanced; crew = Enterprise/SP-3), and the SP-0 worker prerequisite
are all decided above. Build timing is explicitly deferred to Phase 5.
