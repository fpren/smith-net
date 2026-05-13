# Smith Net Token Optimization Plan

Companion to [smith-net-architecture-audit.md](./smith-net-architecture-audit.md) and [smith-net-agent-boundaries.md](./smith-net-agent-boundaries.md).

---

## Honest Open

Today the backend calls zero LLMs. `llmInterface.ts` (469 LOC) is a multi-provider abstraction with no callers across the 42 modules in `backend/src/`. The Android SmithAI stack (~8000 LOC under `android/app/src/main/java/com/guildofsmiths/trademesh/ai/`) is uncommitted scaffolding — code exists, tier gate defaults to Advanced, but it isn't shipped to a stable tag yet.

So this document is forward-looking. It defines the constraints LLM traffic must satisfy when it lands, not optimizations on already-burning bills. The goal is to land AI in a posture where rules handle 80 percent of the load and only the messy 20 percent reaches the model. The cost question is "what limits do we lock in before the meter starts," not "how do we cut a current bill."

---

## Principles

1. **Rules first, AI last.** A function with deterministic output never goes through an LLM. State machines, arithmetic, lookups, template rendering — all rules.
2. **AI runs only in `llmWorker`, never in a request handler.** This is enforced by the queue architecture in `smith-net-daemon-worker-queue-plan.md`. Request handlers enqueue `llm_call`; the response lands in `cache_entries` and `llm_responses`. The handler reads from cache or returns a "still thinking" state.
3. **Cache keys are content digests, not raw text.** Two messages that paraphrase the same intent should hit the same cache row. Key = `intent_type + normalized_payload_sha256`.
4. **Prompt caching at the vendor level.** Anthropic `cache_control` (or OpenRouter's equivalent) is on for every prompt with a stable system + tool prefix.
5. **Structured output, not XML.** Vendor function-calling JSON schemas. The custom `<tool_call>` XML parser in `SmithAIToolExecutor.kt` (258 LOC) is a maintenance liability and tokens-per-call tax.
6. **Confidence gate.** `CueDetector` produces a confidence score; below 0.7 we skip LLM and fall back to rules. Above 0.95 we may skip LLM and act directly via rules too.
7. **Hard daily caps per actor.** Each user has a `daily_llm_token_budget`. Exceeded -> queued for next day or 4xx with explanation. Enforced at `enqueue()` time, not at the model.

---

## Rules-First Decision Tree

Eight specific places to add a rule-based pre-check BEFORE any LLM call.

| # | Place in code | Currently | Should add rule for |
|---|---|---|---|
| 1 | `SmithAIConversationOrchestrator.kt` turn entry | Calls SubAgents which call LLM | Skip LLM when message matches a known intent regex set (start timer, stop timer, "where is X", "what time", "next job") |
| 2 | `AIRouter.kt` cue detection (788 LOC) | Has a confidence score; uses it for cache, not for skip | Skip LLM if cue confidence < 0.7 OR if matched cue is in a `rule_handled` set |
| 3 | `SubAgents.TimeKeeper` | LLM call | Replace entirely with rule: parse start/stop, compute duration, write `time_entries` |
| 4 | `SubAgents.Coordinator` | LLM call to pick a foreman | Replace with rule: available-by-skill-and-distance query |
| 5 | `SubAgents.Onboarding` | LLM-driven Q&A | Replace with a decision-tree wizard; LLM only as escape hatch when user types something off-script |
| 6 | `SubAgents.TaskValidator` | LLM check on submitted task | Pre-check with rules (required fields, value ranges, status validity); LLM only if rules pass and there's a free-text field that needs review |
| 7 | `AISupervisor.kt` (994 LOC) ambient loop | LLM every 5 min | Replace clock with event triggers; rule first decides if any LLM call is warranted at all |
| 8 | Backend `synthesizer.ts` (156 LOC) when SmithAI-driven writes arrive | No LLM today | When LLM is wired: synthesizer composes a deterministic SummaryArtifact from rules; LLM is only invoked for a free-text "explanation" field |

---

## SmithAI Critique (Android)

### `AISupervisor.kt` — 5-minute interval

- **Cost shape.** 12 calls/hour * 16 active hours = 192 calls/day per device. Even at on-device Qwen3 that's serious battery; at OpenRouter it's a line item.
- **What it actually finds.** Most ticks observe no change. The signal-to-call ratio is low.
- **Fix.** Event-triggered: subscribe to a thin event bus (`AmbientEventHub.kt` already exists) and fire only on:
  - `job_status_changed`
  - `shift_completed`
  - `invoice_created`
  - `presence_idle_60m`
  - `manual_review_requested`
- **Cap.** No more than 20 supervisor calls per device per day, regardless of events.

### 8 SubAgents — which collapse to rules

| SubAgent | Recommendation | Why |
|---|---|---|
| Translator | Keep (AI) | NL across languages is judgment |
| TimeKeeper | Replace with rules | Timestamps + arithmetic — no judgment |
| MaterialExpert | Keep (AI) | Material substitution requires domain reasoning |
| TaskValidator | Merge into SafetyOfficer | Both do shift/job validation; collapse |
| SafetyOfficer | Keep (AI) | Anomaly detection in unstructured logs |
| Coordinator | Replace with rules | Route-by-availability is SQL |
| Summarizer | Keep (AI) | End-of-day NL prose |
| Onboarding | Replace with rules | Decision tree wizard; LLM only as escape hatch |

Result: 8 -> 4 AI sub-agents + 4 rule-engine handlers. See `smith-net-agent-boundaries.md` for the per-agent spec.

### Per-message LLM pattern

- **No prompt caching.** Every call ships the full system prompt + tool list. With Anthropic `cache_control` on the system + tool prefix, the prefix becomes a cache write once per session and a cache read on every subsequent call. With 200 turns/day per device and a ~2k-token prefix, that's 400k tokens/day of redundant writes today -> ~0 with caching.
- **No batching.** Multi-turn analyses could batch (one prompt, many cases) but each call is a separate round-trip.
- **No structured output.** `<tool_call>` XML parser in `SmithAIToolExecutor.kt` requires the model to emit specific XML. Vendor JSON function-calling reduces output tokens (no closing tags, no whitespace) and improves reliability.

---

## Concrete Recommendations

| # | Place in code | Currently uses | Should use | Token savings estimate | Risk |
|---|---|---|---|---|---|
| 1 | `OpenRouterClient.kt` system prompt | Re-sent every call | Anthropic-style `cache_control` on system + tool prefix (or OpenRouter's pass-through) | 60-80 percent on stable prefix portion (typically 70 percent of total per-call prompt tokens) | Cache invalidation if prefix drifts; pin prefix versions |
| 2 | `ResponseCache.kt` (Android) | Keyed by raw user text | Key = `intent_type + normalized_payload_sha256` | Hit rate up from ~5 percent to ~30 percent | Stale cache for prompts that look the same but should diverge — mitigate via TTL and intent versioning |
| 3 | `SubAgents.TimeKeeper` | LLM call | Pure rules: parse `start|stop`, compute `now - start`, write `time_entries` | 100 percent of TimeKeeper traffic | None — TimeKeeper has no judgment branch worth saving |
| 4 | `SubAgents.Coordinator` | LLM call to pick a foreman | SQL: foremen by skill + availability + distance | 100 percent of Coordinator traffic | Need a small Postgres distance function (PostGIS or haversine) |
| 5 | `SubAgents.Onboarding` | LLM-driven Q&A | Decision-tree wizard with a defined state machine | ~90 percent (LLM only for free-text fallback) | UX feels scripted — that's the right tradeoff for setup |
| 6 | `SmithAIToolExecutor.kt` (258 LOC) | Custom XML `<tool_call>` parser | Vendor JSON function-calling | 15-25 percent reduction in output tokens per call + fewer parse failures | Requires per-vendor mapping; only some on-device models support strict schemas |
| 7 | `AISupervisor.kt` ambient | 5-min clock | Event-triggered | ~95 percent reduction in calls (192/day -> ~10/day) | Risk: missed events leave supervisor silent — mitigate with a once-per-2h tick floor |
| 8 | `AIRouter.kt` confidence gate | Confidence used for cache lookup only | Skip LLM when `confidence < 0.7` | ~20 percent skip rate | Skipped low-confidence inputs may be exactly the ones that need help — log them so we tune the gate |
| 9 | Server-side `llmInterface.ts` (future) | Unused | Always called via `llmWorker` queue; never inline | Limits worst-case blast radius (a runaway call cannot hang a request) | None — it's a discipline, not a tradeoff |
| 10 | New `cache_entries` pg table | None | Server-side LLM response cache keyed by `(prompt_id, input_sha256)` | Cross-device cache hits ~40 percent for repeated structured prompts | TTL governance; staleness on tool definition changes |

---

## Cache Architecture

Two caches, both keyed by content digest, not raw text.

### Android — `ResponseCache.kt` (already exists, 7-day TTL, 200 entries)

Change:
- **Key.** From `sha256(rawText)` to `sha256(intent_type + normalized_payload)` where `normalized_payload` strips whitespace, lowercases, and removes obvious noise tokens.
- **Bucketing.** Per-intent ring buffer instead of one 200-entry pool. A heavy "Summarizer" day shouldn't evict TimeKeeper rules answers.
- **Promotion.** A hit in the device cache is mirrored to the server cache so other devices benefit.

### Server — new `cache_entries` table (Phase 5)

```sql
CREATE TABLE cache_entries (
  cache_key   TEXT PRIMARY KEY,         -- 'llm:<prompt_id>:<input_sha256>'
  prompt_id   TEXT NOT NULL,
  input_sha   TEXT NOT NULL,
  output      JSONB NOT NULL,
  tokens_in   INT,
  tokens_out  INT,
  model       TEXT,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX cache_expires_idx ON cache_entries (expires_at);
```

`llmWorker` checks `cache_entries` before calling the vendor; populates on success; expires on TTL (default 7 days per prompt; override via `prompt_id` metadata).

---

## Vendor Prompt Caching

Anthropic's `cache_control` model (and the OpenRouter passthrough for Claude family models) lets us mark a prefix as cacheable. The cache hit is much cheaper than a cache write, and far cheaper than a fresh call.

Pattern when wiring `llmInterface.ts`:

```ts
// pseudocode for the request body sent to the vendor
{
  model: 'claude-opus-4-7',
  system: [
    { type: 'text', text: STABLE_SYSTEM_PROMPT, cache_control: { type: 'ephemeral' } },
  ],
  tools: TOOLS_LIST,  // also marked cacheable in vendor SDKs that allow it
  messages: [
    { role: 'user', content: turnSpecificContent },
  ],
}
```

Rules:
- The system prompt is versioned. A prompt_id change resets the cache. Don't edit in place.
- Tool list is part of the cached prefix. Tool churn breaks the cache; batch tool changes into releases.
- Turn content is always uncached (it's what differs per call).

---

## Structured Output

Replace the custom XML parser in `SmithAIToolExecutor.kt` (258 LOC). Each tool in `SmithAIToolRegistry.kt` becomes a JSON schema. The model emits a function call in the vendor's native format. The executor:

1. Validates against the schema (zod on the server, kotlinx.serialization on Android).
2. Routes the call to the existing handler.
3. Stores the call + response in `audit_entries`.

Concrete benefits beyond tokens:
- Parser failures drop to near zero. The XML parser already fails on edge cases (model emits markdown, model wraps in ``` blocks).
- Tool versioning becomes a schema change, not a text-format change.
- New models that don't speak the XML dialect still work.

---

## Daily Caps

Implementation when LLM goes live:

```sql
CREATE TABLE llm_budget (
  actor_id           TEXT PRIMARY KEY,
  date               DATE NOT NULL,
  tokens_in_used     INT NOT NULL DEFAULT 0,
  tokens_out_used    INT NOT NULL DEFAULT 0,
  calls_used         INT NOT NULL DEFAULT 0,
  daily_cap_calls    INT NOT NULL DEFAULT 100,
  daily_cap_tokens   INT NOT NULL DEFAULT 100000
);
```

`enqueue({kind:'llm_call', ...})` checks the budget. Over cap -> reject with a structured 403 (see `smith-net-tier-gating` skill convention) carrying `reason=daily_cap`, `retry_after=midnight_utc`.

---

## What Not To Do

- Do not call LLMs inline in route handlers. Always via `llmWorker`.
- Do not log raw prompts and outputs at INFO. Sensitive content + token bloat in logs.
- Do not invalidate the prompt cache on cosmetic edits. Version `prompt_id`; coordinate releases.
- Do not stack three sub-agents to handle one user turn just because the architecture allows it. One turn = one classification + one handler.
- Do not let the `AISupervisor` run on the same clock as `presenceWatcherDaemon`. Both ticking together amplifies cost without insight.

See `smith-net-agent-boundaries.md` for the per-agent token budgets.
