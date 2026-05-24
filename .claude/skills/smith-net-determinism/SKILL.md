---
name: smith-net-determinism
description: The deterministic execution moat — Intent → SummaryArtifact → LedgerEntry pipeline is bit-for-bit reproducible, sealed via SHA256, audit-verifiable. Use when modifying intentService, synthesizer, ledger, vectorClock, BoundaryEngine, ReconciliationEngine, or anything that touches the moat. NFR-D1 through D5 must hold.
---

# Smith Net — Determinism (the moat)

This skill activates when working on the deterministic execution pipeline OR the cord-based state model OR distributed reconciliation. **Determinism is the brand.** Don't break it.

## The pipeline (the moat)

```
Engagement → Intent → IntentVersion (draft → proposed → confirmed → superseded)
   ↓
Synthesizer (validateSynthesisInputs first) → SummaryArtifact (with serial)
   ↓
Ledger.seal() → computeHash() → LedgerEntry (immutable, supersession chain)
   ↓
Outputs: Invoice, Report, Public link
```

Files: `backend/src/intentService.ts`, `intentAuthority.ts`, `synthesizer.ts`, `synthesisAuthority.ts`, `ledger.ts`, `ledgerAuthority.ts`.

## NFR-D1 through D5 (must hold)

| NFR | Property | How enforced |
|---|---|---|
| **D1** | Same compiled plan → identical execution traces across runs/devices | `synthesizer.synthesize()` is pure of `(intentVersionId, jobIds, timeEntryIds, chatMessageIds)`. No clock reads inside the function. |
| **D2** | Cord transitions are append-only | DB trigger prevents UPDATE on rows with non-NULL `superseded_by` |
| **D3** | Same SummaryArtifact byte-for-byte → same `computeHash` | Canonicalized JSON: sorted keys, no whitespace, fixed numeric formatting. Don't add fields to artifact without updating canonicalization. |
| **D4** | Plan compiled at compiler version V runs on any client supporting compiler ≥ V | Versioned compiler artifacts; semver compatibility. |
| **D5** | AI is NEVER required to advance a cord transition | `intentService` validators reject AI as confirmer; AI is observation/suggestion only. AI may draft an IntentVersion (`auto_generated=true`); a human MUST `propose` and `confirm`. |

**If you find yourself adding a clock read inside `synthesize()` or letting AI auto-confirm — STOP. You're breaking the moat.**

## The supersession chain (no cycles, no forking)

`ledger_entries.supersedes` and `superseded_by` form a DAG:
- New amend MUST point to an entry whose `superseded_by IS NULL` (`validateAmendment`)
- DB trigger blocks UPDATE on entries with non-NULL `superseded_by` (immutability)
- Concurrent amends on same prior entry → exactly one succeeds (409 conflict to the loser)

## Verification

`/api/ledger/verify/:entryId` re-computes hash from current `summary_artifacts` row and compares with stored `sha256_hash`. Returns `{expected, actual, valid}`. **Tamper detection.**

If verification fails: emit `SECURITY.SECURITY_ALERT` audit; alert ops; investigate.

## Cord-based state model (VectorClock + CordEntry)

- Every `UnifiedMessage` carries `vectorClock: { deviceId: counter }`
- `vectorClock.merge(a, b)` returns max-per-key
- `vectorClock.compare(a, b)` returns `-1 | 0 | 1` (concurrent = 0)
- Concurrent events kept ordered by `(timestamp, id)` deterministically across all clients
- **No last-write-wins.** Anywhere.

Files: `data/VectorClock.kt` (Android), `data/CordEntry.kt`, `data/CordRepository.kt`, `service/ReconciliationEngine.kt` (Android), `backend/src/vectorClock.ts`, `backend/src/reconciliationEngine.ts`.

## Reconciliation invariants

- Server: `INSERT ... ON CONFLICT (id) DO UPDATE` — idempotent
- Client: vector-clock merge on receive; sort by `compare()` result then `(timestamp, id)`
- Ephemeral channels (`ChannelPersistence.EPHEMERAL`) **never persist server-side** — even after reconciliation. No DB rows. No audit entries.

## Critical security tests (must pass before public launch)

These tests are mandatory for any change to the pipeline:

| Test | Pass criteria |
|---|---|
| Determinism stability under load | 1000 parallel `synthesize()` with same inputs → 1000 identical artifacts |
| Hash collision check | 100k sample artifacts → 0 SHA256 collisions |
| Tamper detection latency | `/api/ledger/verify` p95 < 500ms |
| Supersession DAG integrity | 100 random amend chains → no cycles created |
| RLS / authorization | Non-party user reading another's intent → 403 |

## AI safety boundaries (defense of D5)

- AI runs **on-device only** for v1 (SmithAI). No cloud round-trip for SmithAI features.
- `llmInterface.ts` (C-04) is reserved for server-side helpers (auto-quote, intent draft) where user has explicitly invoked AI. Default `LLMProvider.MOCK` until production env explicitly selects a provider.
- AI **cannot** mutate sealed artifacts or ledger entries
- AI may draft (`auto_generated=true`) but human propose+confirm required
- AI input/output never persisted to telemetry/analytics with raw user content

## When you're tempted to break determinism

If you find yourself wanting to:
- Add a default "now" timestamp inside synthesizer ❌
- Let AI auto-confirm a draft Intent ❌
- Add an "edit" path on a sealed ledger entry ❌
- Use last-write-wins on conflicting messages ❌
- Skip the supersession check on amend ❌
- Persist ephemeral channel content "just in case" ❌

**STOP.** Open a discussion. Don't quietly weaken the moat.

## Don't do

- ❌ Modify `summary_artifacts` after a `ledger_entry` references it (always amend with new artifact + new ledger entry)
- ❌ Read clock inside `synthesizer.synthesize()` (always use input IDs only)
- ❌ Allow `intentService.confirmIntent()` to skip the `confirmer in parties` check
- ❌ Persist ephemeral channel messages (server INSERT for `ChannelPersistence.EPHEMERAL` is forbidden)
- ❌ Use last-write-wins anywhere in the message bus
- ❌ Add fields to `SummaryArtifact` without updating canonicalization in `computeHash()`
- ❌ Bypass `validateAmendment` on a ledger amend
- ❌ Allow AI to call any of {`/api/intents/:id/confirm`, `/api/synthesize`, `/api/ledger/seal`} directly

## Linked specs

- `docs/architecture/ARCHITECTURE.md §4` — pipeline diagram
- `docs/database/SCHEMA.md §6` — entity model
- `docs/security/SECURITY.md §6` — sealing properties + threat model
- `docs/specs/NFRS.md §1` — NFR-D1 through D5
- `docs/prds/flows/FLOW-4-plan-compose-to-seal.md` — critical security tests
- `docs/prds/flows/FLOW-5-online-offline-sync.md` — vector-clock reconciliation
