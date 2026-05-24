# SmithAI Enterprise Tier — Backlog

**Created:** 2026-05-03
**Status:** Backlog (not started)
**Depends on:** Enterprise tier infrastructure (entitlements endpoint, server-authoritative tier check)

## Context

SmithAI v1 shipped 2026-05-03 (commit on branch `feat/relay-hetzner-postgres`) with **add-only** write tools at the Advanced tier:

- `create_job`, `send_message`, `add_time_entry`, `update_job_stage`

Edit/delete of existing records and crew awareness were intentionally deferred to the Enterprise tier. This file tracks that backlog so it doesn't get lost.

## Scope

### 1. Mutate-existing tools

- `update_time_entry(entryId, fields)` — change clock-in/out, duration, notes on an existing entry
- `delete_time_entry(entryId)` — remove an entry
- `update_job_fields(jobId, fields)` — edit title, client, address, etc. of an existing job (not just stage)
- `delete_job(jobId)` — soft-delete a job (already exists in JobBoardViewModel as `deleteJob`)

### 2. Crew / team awareness

- `query_crew_status()` — who's clocked in, on break, on which job
- `query_crew_messages(memberId)` — recent messages from a crew member
- `assign_to_crew(jobId, memberId)` — assign a crew member to a job

## Why these are Enterprise-only

**Mutation tools** touch records that feed the deterministic Intent → SummaryArtifact → Ledger pipeline (the moat per smith-net-determinism). They need:

- **Before/after diff in approval card** — single confirmation isn't enough; user must see the original value
- **Original-row preservation** — audit log keeps the pre-mutation hash so disputes can reconstruct history
- **Re-derivation of downstream artifacts** — Ledger entries depending on the mutated row must be rebuilt
- **Stronger tier check** — edits to payroll-relevant data shouldn't be a stub

**Crew tools** require:

- Per smith-net-security: solo users currently get NO crew data (hard isolation). Enterprise lifts this.
- Server-authoritative entitlements (no client stub)
- Mesh-state queries that respect cord-based state model
- New audit category for cross-user reads

## Files that will change (rough)

- `android/.../ai/SmithAIToolRegistry.kt` — add tool defs in a new `enterprise:` list
- `android/.../ai/SmithAIToolExecutor.kt` — handler branches; before/after capture for mutations
- `android/.../ai/SmithAITierGate.kt` — wire to real entitlements endpoint
- `android/.../ai/SmithAIToolBridge.kt` — add `updateTimeEntry`, `deleteTimeEntry`, `crewStatus` callbacks
- `android/.../ui/timetracking/TimeTrackingViewModel.kt` — register the new bridge callbacks
- `android/.../ui/ConversationScreen.kt` — `ToolCallApprovalCard` variant with diff rendering
- `backend/src/...` — entitlements endpoint (currently doesn't exist on Android)

## Out of scope (still)

- Voice / wake word (v2)
- Always-on push from SmithAI ("notify me when X happens")
- Streaming token render (v1.1, separate from Enterprise)
- Work-mode picker (v1.5)

## Decision log

- **2026-05-03** — User confirmed: edit/delete of existing time entries goes to Enterprise, not v1.1. Crew awareness already in same bucket per the original spec. Filed during follow-up conversation about SmithAI v1 ship.
