# Smith Net Agent Boundaries

Companion to [smith-net-token-optimization-plan.md](./smith-net-token-optimization-plan.md).

This document reviews the 8 SubAgents in the Android SmithAI stack and recommends which to keep as AI agents, which to merge, and which to replace with rules outright. It then specifies the surviving agents in detail.

---

## Honest Assessment

Eight SubAgents is too many for a v1 product. The split was conceived when "more specialized agents" sounded like an architectural virtue. In practice each agent costs:

- a system prompt slot (cache write on first use)
- a tool-list slot (cache invalidation on tool changes)
- routing complexity in `AIRouter.kt` (788 LOC)
- ambiguity at orchestration time ("which agent handles this turn?")
- duplicated context-building in `SmithAIContextBuilder.kt`

The bar for "this needs to be an AI agent" is: **the work requires judgment, ambiguity resolution, NL understanding, or open-ended classification — and rules cannot do it correctly even with a structured input.**

Three of the eight clearly fail that bar (TimeKeeper, Coordinator, Onboarding). One overlaps with another (TaskValidator with SafetyOfficer). Four are appropriate AI agents.

Target end-state: **4 AI sub-agents + 4 rule-engine handlers + 1 queue worker (for AI calls).**

---

## The 8 SubAgents — Per-Agent Recommendation

Reference: `SubAgents.kt` (487 LOC).

| # | SubAgent | Recommendation | One-line reason |
|---|---|---|---|
| 1 | Translator | **KEEP** (AI) | Cross-language NL is judgment |
| 2 | TimeKeeper | **REPLACE WITH RULES** | Timestamps + arithmetic — no judgment |
| 3 | MaterialExpert | **KEEP** (AI) | Material substitution + quantity reasoning |
| 4 | TaskValidator | **MERGE INTO SafetyOfficer** | Both validate unstructured shift/job data |
| 5 | SafetyOfficer | **KEEP** (AI, absorbs TaskValidator) | Anomaly detection in unstructured logs |
| 6 | Coordinator | **REPLACE WITH RULES + queue** | Route-by-availability is SQL |
| 7 | Summarizer | **KEEP** (AI) | End-of-day NL prose |
| 8 | Onboarding | **REPLACE WITH RULES (decision tree)** | Scripted Q&A; LLM only as escape hatch |

Net: 8 -> 4 AI + 4 rule modules.

---

## The 4 Retained AI Agents

For each: purpose / allowed inputs / allowed outputs / when to call / when NOT to call / required tools / token-saving rules / fallback if AI fails / daily token budget per device.

### Agent 1 — Translator

| Field | Spec |
|---|---|
| **Purpose** | Translate a message between two languages when both sender and recipient profiles declare different `preferred_language` values |
| **Allowed inputs** | `{source_text: string, source_lang: string, target_lang: string, glossary: string[]}` |
| **Allowed outputs** | `{translated_text: string, confidence: number}` |
| **When to call** | A message is sent in language A; recipient profile's `preferred_language = B`; cache miss |
| **When NOT to call** | Source language == target language. Cache hit. Recipient hasn't opened the thread in 7+ days (queue the translation for when they do, don't burn tokens now). |
| **Required tools** | None (pure transformation; no side effects) |
| **Token-saving rules** | Cache key = `sha256(source_text + source_lang + target_lang)`. Glossary lookups happen pre-call as rules (if the whole message is glossary terms, skip LLM). |
| **Fallback if AI fails** | Show the original text with a "translation unavailable" banner. The user can tap to retry. |
| **Daily token budget** | 25k tokens per device per day. Hard cap. |

### Agent 2 — MaterialExpert

| Field | Spec |
|---|---|
| **Purpose** | Suggest material costs / quantities / substitutions for a job estimate |
| **Allowed inputs** | `{job_type: string, scope_description: string, region: string}` |
| **Allowed outputs** | JSON list of `{material_name, quantity, unit, est_cost, justification}` items |
| **When to call** | Foreman explicitly asks for material suggestions OR `autoQuoteEngine.ts` (318 LOC) needs an unrecognized SKU |
| **When NOT to call** | The scope contains only SKUs already in `wageData.ts` / pricing tables — those go through `electricianTools.ts` (463 LOC) instead. |
| **Required tools** | `query_pricing_tables` (read-only) |
| **Token-saving rules** | Pre-filter by `electricianTools` lookup; LLM only handles the gaps. Cache by `(job_type, scope_sha256, region)`. |
| **Fallback if AI fails** | Return the deterministic `autoQuoteEngine` output with a "manual review recommended" flag. |
| **Daily token budget** | 40k tokens per device per day. |

### Agent 3 — SafetyOfficer (absorbs TaskValidator)

| Field | Spec |
|---|---|
| **Purpose** | Read unstructured shift / job text and flag anomalies (missing PPE notes, near-miss language, code-violation patterns). Validate that completed tasks match their declared scope. |
| **Allowed inputs** | `{shift_log: string, job_scope: string, region_codes: string[]}` |
| **Allowed outputs** | `{anomalies: [{code, severity, evidence_span}], task_validation: 'pass' | 'review' | 'fail'}` |
| **When to call** | Shift completed AND `shift_log.length > 200 chars` AND no obvious red-flag regex match (those bypass to immediate `fail`) |
| **When NOT to call** | Short logs (< 200 chars — too noisy to judge). Logs already flagged by a regex rule. |
| **Required tools** | `query_codes` (region-specific code lookup) |
| **Token-saving rules** | Rule pre-filter catches the obvious cases. LLM only on ambiguous prose. Cache by `(shift_log_sha256, job_scope_sha256)`. |
| **Fallback if AI fails** | Mark `task_validation='review'`; queue a human review notification. Default to safe. |
| **Daily token budget** | 30k tokens per device per day. |

### Agent 4 — Summarizer

| Field | Spec |
|---|---|
| **Purpose** | End-of-day prose summary of a foreman's day (jobs touched, hours, anomalies, payment events). |
| **Allowed inputs** | `{foreman_id, date, structured_events: [...]}` |
| **Allowed outputs** | `{summary_prose: string, suggested_actions: string[]}` |
| **When to call** | Once per foreman per day at 06:00 local (automation A7). Optionally on-demand when foreman opens the daily roll-up. |
| **When NOT to call** | Foreman had zero events that day. The `reportAssembler` deterministic output suffices for low-event days. |
| **Required tools** | None |
| **Token-saving rules** | Aggressive prompt caching: the system prompt is identical day-over-day. Per-foreman context is small (one day of events). Cache key = `(foreman_id, date)`. Same call shouldn't fire twice. |
| **Fallback if AI fails** | Deterministic `reportAssembler.ts` (319 LOC) output without the prose paragraph. |
| **Daily token budget** | 10k tokens per device per day (just one call). |

---

## The 4 Replaced-By-Rules Handlers

### Handler 1 — TimeKeeperRules (replaces TimeKeeper SubAgent)

```ts
// backend/src/rules/timeKeeperRules.ts (or android-side mirror)
export function startTimer(actorId: string, jobId: string): TimeEntry { ... }
export function stopTimer(actorId: string, jobId: string): { entry: TimeEntry; durationMin: number } { ... }
export function adjustEntry(entryId: string, newStart: Date, newEnd: Date): TimeEntry { ... }
```

- No judgment branch worth saving.
- Input vocabulary is closed: `start | stop | adjust`.
- Arithmetic and DB writes only.
- 100 percent of TimeKeeper traffic moves here. Zero LLM calls.

### Handler 2 — CoordinatorRules (replaces Coordinator SubAgent)

```sql
-- candidates for "who picks up this job?"
SELECT p.id, p.name, p.skills, ST_Distance(p.location, $1::geography) AS dist_m
  FROM profiles p
 WHERE $2 = ANY(p.skills)
   AND p.id NOT IN (SELECT foreman_id FROM jobs WHERE status IN ('assigned','in_progress'))
 ORDER BY dist_m
 LIMIT 5;
```

Combined with a small policy module (preferred-foreman, blocked-pairing rules), this is a function, not a judgment. If anything edge-case appears (a free-text "but only Tuesday" request), it goes to the conversation orchestrator and falls back to the foreman picking manually.

### Handler 3 — OnboardingWizard (replaces Onboarding SubAgent)

A state machine in `android/.../onboarding/` with explicit states:
- `pickRole` -> `confirmTrade` -> `addProfile` -> `inviteCrew` -> `done`

Each state has 2-4 buttons and one optional free-text field. The free-text field only escapes to LLM if the user types a question the wizard cannot route (rare). Even then, the LLM call is one-shot and capped at 500 tokens.

### Handler 4 — TaskValidatorRules (the rules-pass before SafetyOfficer)

```ts
export function validateTask(task: Task): 'pass' | 'review' | 'fail' {
  if (!task.completed_at) return 'fail';
  if (!task.actor_id) return 'fail';
  if (task.hours > 24) return 'fail';
  if (!task.scope_match) return 'review';  // SafetyOfficer takes it from here
  return 'pass';
}
```

Two-thirds of TaskValidator calls are caught by these rules. Only `'review'` cases reach the (merged) SafetyOfficer.

---

## AISupervisor — Keep With Event Triggers

`AISupervisor.kt` (994 LOC) is the right idea (proactive ambient insight) implemented with the wrong cadence (5-minute clock).

| Aspect | Current | Should be |
|---|---|---|
| Trigger | `setInterval`-style every ~5 min | Event-bus subscription on `AmbientEventHub.kt` |
| Events | n/a | `job_status_changed`, `shift_completed`, `invoice_created`, `payment_received`, `presence_idle_60m`, `manual_review_requested` |
| Floor | n/a | At most once per 30 minutes regardless of events (debounce) |
| Cap | n/a | At most 20 supervisor calls per device per day |
| Tier | Advanced+ default | Advanced+ enforced; defer Enterprise-only features (crew awareness, edits) per `project_smithai_tier_scope.md` |
| Daily token budget | unbounded | 30k tokens per device per day |

This change alone is the single largest token reduction in the SmithAI stack: from ~192 ambient calls/day to ~10-20.

---

## Tools Layer — Switch From XML to Structured Output

`SmithAIToolExecutor.kt` (258 LOC) parses custom `<tool_call>` XML emitted by the model. `SmithAIToolRegistry.kt` defines 7 tools (4 read: `query_jobs`, `query_client`, `query_time_entries`, `query_messages`; 3 write: `create_job`, `send_message`, `add_time_entry`, `update_job_stage` — note that's actually 4 writes).

Recommendation:
- Convert each tool to a JSON schema.
- Use vendor function-calling.
- Writes still go through the pending-approval inbox (unchanged).
- Reads return data directly to the agent.

Benefits already detailed in `smith-net-token-optimization-plan.md` (item 6).

---

## Per-Agent Token Budgets (Summary)

| Agent | Tokens / device / day |
|---|---|
| Translator | 25 000 |
| MaterialExpert | 40 000 |
| SafetyOfficer | 30 000 |
| Summarizer | 10 000 |
| AISupervisor | 30 000 |
| **Total** | **135 000** |

Hard cap per device per day. When exceeded:
- Agent returns its `Fallback if AI fails` output.
- Audit row written with `action='budget_exceeded'`.
- Foreman sees a banner: "Smart features paused until 00:00 UTC."

Server-side enforcement in `enqueue()` for `kind=llm_call`. Device-side enforcement in `AIRouter.kt` as a second line of defense.

---

## When NOT To Call Any Agent

A short rule set applied in `AIRouter.kt` before any agent dispatch:

1. **Offline.** No AI when the device is offline AND no on-device model is ready. Queue the intent for later via `OfflineQueueManager.kt`.
2. **Low battery + on charger=false.** Below 20 percent battery on cell power, AI is off. `BatteryGate.kt` already exists; tighten the threshold from current.
3. **Confidence < 0.7.** Cue detector says "I don't know what this is" — rules can't help either; ask the user to clarify, don't burn an LLM call on noise.
4. **Repeat input within 60 seconds.** A double-submit yields the same cache hit, but the rule guards against cache misses on near-paraphrases.
5. **Free-tier user.** Per `smith-net-tier-gating`, AI features start at Advanced. Free / Solo see the rule outputs only.

---

## Reconciler Between The Two Audit Chains

Per the architecture audit (weak point #10), the backend JSONL audit and the Android `SmithAIAuditLog.kt` SHA chain need a single authority. When backend audit moves into pg `audit_entries` (Phase 2), the Android chain becomes a forward cache:

- Every Android audit entry queues via `OfflineQueueManager` for replay through a new `/api/audit/append` endpoint.
- Backend validates the chain (`prev_hash` linkage) and writes the canonical row.
- Android's local chain remains intact for offline display and forensic export, but the authoritative chain is in pg.

This unifies the two audit trails without losing offline capability.

See `smith-net-implementation-roadmap.md` for which Phase each agent change lands in.
