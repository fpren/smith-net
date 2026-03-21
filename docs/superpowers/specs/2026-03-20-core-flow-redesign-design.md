# Core Flow Redesign: Plan Decomposition + Messaging Unification

**Date:** 2026-03-20
**Status:** Draft
**Scope:** System Law core flow restructuring + unified messaging transport

---

## Problem Statement

The Plan container in the current System Law is overloaded. It serves as scope definition, proposal workflow, fact synthesis, truth-sealing, and snapshot mechanism simultaneously. This makes it difficult to reason about, test in isolation, and evolve independently.

Additionally, BLE mesh and IP chat operate as two separate messaging systems rather than one system with two transports. This causes:
- Sync failures: duplicates, missing messages, ordering problems
- Fragmented user experience: users must think about which path they're on
- Unreliable chat context for downstream consumers (reports, summaries)
- Broken handoff when switching between mesh and online

## Solution Overview

**Phase 1:** Unify BLE mesh and IP chat into a single Message Bus with transport abstraction and reconciliation.

**Phase 2:** Decompose Plan into three focused containers — Intent, Synthesizer, Ledger — each with independent versioning and clear boundaries.

---

## Phase 1: Messaging Unification

### Architecture

```
+--------------------------------------+
|         Message Bus (single API)      |
|  - send(message)                      |
|  - subscribe(channel)                 |
|  - getHistory(channel, range)         |
|                                       |
|  Every message gets:                  |
|  - UUID (generated at creation)       |
|  - Vector clock (causal ordering)     |
|  - Channel ID                         |
|  - Payload                            |
|  - Transport metadata (which path)    |
+------------------+-------------------+
                   |
          +--------+--------+
          |                 |
    +-----v-----+    +-----v-----+
    |    BLE    |    |    IP     |
    |   Mesh    |    |   Chat    |
    | Transport |    | Transport |
    +-----+-----+    +-----+-----+
          |                 |
    +-----v-----------------v-----+
    |    Reconciliation Engine    |
    |  - Dedup by UUID            |
    |  - Order by vector clock    |
    |  - Merge on reconnect       |
    +-----------------------------+
```

### Design Decisions

**Single message identity:** Every message gets a UUID at creation time, regardless of transport. Deduplication is trivial — same UUID = same message.

**Vector clocks for ordering:** Instead of timestamps (which drift across devices), each device maintains a vector clock — one counter per device UUID. This gives causal ordering — "message B was definitely a reply to message A" — without relying on synchronized time.

**Transport-agnostic storage:** Messages are stored locally first (Room database on Android for offline resilience), then synced to Supabase when online. Transport metadata (BLE vs IP) is recorded but does not affect the message's identity or ordering.

**Reconciliation on reconnect:** When a device transitions from mesh-only to online (or vice versa):
1. Compare local message set vs remote by UUID
2. Push missing messages in both directions
3. Resolve ordering via vector clock merge
4. No duplicates, no lost messages

**Handoff protocol:** When switching transports mid-conversation:
1. Message Bus detects transport change
2. Queues any in-flight messages
3. Re-sends via new transport with same UUIDs
4. Receiving side deduplicates naturally

---

## Phase 2: Core Flow Redesign

### New Core Flow

The System Law's core flow changes from:

```
Plan -> Jobs -> Time -> Archive -> Reports/Invoices
```

To:

```
Intent -> Jobs -> Time -> Synthesizer -> Ledger -> Archive -> Reports/Invoices
```

### Standard Flow

```
APP OPENS
-> INTENT CREATED (scope + proposal)
-> INTENT CONFIRMED
-> JOBS GENERATED FROM INTENT
-> JOBS EXECUTED
-> TIME RUNS DURING WORK
-> JOBS COMPLETE
-> SYNTHESIZER ASSEMBLES FACTS (Intent + Jobs + Time -> Summary Artifact)
-> LEDGER SEALS ARTIFACT (cryptographic commitment)
-> ARCHIVE STORES SEALED TRUTH
-> REPORTS / INVOICES GENERATED FROM LEDGER
```

### Small Project Flow

```
APP OPENS
-> JOBS (manual entry)
-> JOBS EXECUTED
-> TIME RUNS DURING WORK
-> JOBS COMPLETE
-> SYNTHESIZER ASSEMBLES FACTS
-> INTENT AUTO-GENERATED (retroactive, from Synthesizer's fact assembly)
-> INTENT CONFIRMED
-> LEDGER SEALS ARTIFACT
-> ARCHIVE STORES SEALED TRUTH
-> REPORTS / INVOICES GENERATED FROM LEDGER
```

Intent is auto-generated before Ledger seals, preserving the rule that Intent is never optional — but it is lightweight, just a retroactive scope statement derived from the work performed.

### Correction Flow

**Mid-project scope change:**
1. New Intent version created referencing prior version
2. Prior Intent moves to Superseded state
3. New/modified Jobs linked to new Intent version
4. Work continues under new Intent

**Post-seal correction:**
1. New Synthesizer run with corrected inputs
2. New Summary Artifact produced (new serial)
3. New Ledger entry referencing prior entry as superseded
4. Reports regenerated from new Ledger entry
5. Both entries remain in Archive (full audit trail)

---

## Container Definitions

### INTENT

**Responsibility:** Scope declaration and agreement — what will be done, and who agreed to it.

- **States:** Draft -> Proposed -> Confirmed -> Superseded
- **Contains:** scope statement, job list (intended), parties involved, confirmation timestamps
- **Versioning:** change orders create new Intent versions. Previous versions move to Superseded, remain readable.
- **Does NOT** assemble facts, generate summaries, lock truth, or produce reports
- **Does NOT** reference Time entries — Intent is about what, never how long

### SYNTHESIZER

**Responsibility:** Fact assembly — reads closed Jobs + Time + Intent and produces an immutable Summary Artifact.

- Stateless processor, not a persistent container. It runs, produces output, done.
- **Input:** Intent version + closed Job IDs + closed Time entry IDs + clock-out notes + chat context (if human-approved)
- **Output:** Summary Artifact — a self-contained document with clear intent statement, work performed, labor recorded, materials used, contextual notes
- Each run produces a new immutable artifact with its own serial number
- Re-running with different inputs produces a new artifact, not an edit
- **Does NOT** seal, commit, or archive — it only assembles

### LEDGER

**Responsibility:** Truth-sealing — takes Summary Artifacts and makes them canonical.

- Append-only, no deletions, no edits
- Each entry: Summary Artifact serial + SHA-256 hash + blockchain commitment (append-only ledger — specific blockchain/service TBD based on infrastructure decisions) + timestamp + actor UUID
- Corrections: new Ledger entry referencing the prior entry it supersedes (chain of amendments)
- Reports and Invoices read exclusively from Ledger entries
- **Does NOT** assemble facts or validate scope — it only seals what Synthesizer produced

### Unchanged Containers

- **JOBS:** unchanged — work scope and execution state
- **TIME:** unchanged — append-only labor facts
- **ARCHIVE:** unchanged — stores finalized Ledger entries + all supporting records
- **SETTINGS:** unchanged — system configuration
- **CONNECTIVITY:** unchanged — infrastructure transport (now via Message Bus)

---

## Validation & Authority System

The current Plan Authority is replaced by three focused validators.

### Intent Authority

Validates Intent creation and confirmation:
- All referenced Jobs exist (standard flow) or will be auto-linked (small project flow)
- Parties identified by UUID
- Confirmation requires human action — AI cannot confirm Intent
- Version chain integrity: superseding Intent must reference prior version

### Synthesis Authority

Validates Synthesizer input and output:
- All referenced Jobs are closed and immutable
- All referenced Time entries are closed and immutable
- All clock-out notes captured and immutable
- Any chat/AI context is read-only and human-approved for inclusion
- Intent version is Confirmed (or auto-generated and confirmed for small projects)
- Output artifact contains: clear scope, explicit Job IDs + Time IDs, summary derived from facts only, actor attribution by UUID

### Ledger Authority

Validates sealing:
- Summary Artifact has valid serial number
- Artifact passes SHA-256 commitment
- Blockchain ledger write succeeds
- If amendment: prior Ledger entry referenced and chain is valid
- Once sealed: no field may change — ever

### API Endpoints

```
# Intent Authority
POST /intent-authority/validate-creation
POST /intent-authority/validate-confirmation
POST /intent-authority/validate-version

# Synthesis Authority
POST /synthesis-authority/validate-inputs
POST /synthesis-authority/validate-artifact

# Ledger Authority
POST /ledger-authority/validate-sealing
POST /ledger-authority/validate-amendment

# Small Project Flow
POST /small-project/validate-eligibility
POST /small-project/synthesize-and-generate-intent
POST /small-project/confirm-and-seal          # Orchestration endpoint: calls Intent Authority then Ledger Authority sequentially, not atomic

# System Law Enforcement (unchanged pattern)
GET  /system/flow-status
POST /system/validate-action
```

---

## AI Boundaries (Unchanged Spirit, New Containers)

**AI May:**
- Draft Intent scope statements for human review
- Assist Synthesizer with natural-language summaries within artifacts
- Suggest corrections or flag inconsistencies

**AI May NOT:**
- Confirm Intent
- Trigger Synthesizer
- Seal Ledger entries
- Approve amendments

---

## Migration Path

### Backend Changes

**Remove:**
- `planAuthority.ts` — replaced by three focused validators
- `planSynthesis.ts` — replaced by Synthesizer service
- `autoPlanCreator.ts` — replaced by auto-Intent generator

**Create:**
- `messageBus.ts` — unified message API with transport abstraction
- `reconciliationEngine.ts` — dedup, vector clock ordering, merge
- `intentAuthority.ts` — Intent validation
- `synthesisAuthority.ts` — Synthesizer input/output validation
- `ledgerAuthority.ts` — sealing validation
- `synthesizer.ts` — fact assembly, artifact production
- `ledger.ts` — append-only truth sealing
- `intentService.ts` — Intent CRUD, versioning, auto-generation

**Modify:**
- `reportAssembler.ts` — read from Ledger instead of Plan
- `reportRenderer.ts` — render from Ledger artifacts
- `invoiceGenerator.ts` — source data from Ledger
- `outputGenerator.ts` — update pipeline to Ledger-based flow
- `server.ts` — new routes for Intent/Synthesis/Ledger authorities
- `wsHandler.ts` — route through Message Bus instead of direct transport
- `types.ts` — new types for Intent, SummaryArtifact, LedgerEntry, VectorClock

### Database Changes (Supabase)

**New tables:**
- `intents` — id, version, scope, status, parties, timestamps
- `intent_versions` — version chain with supersedes references
- `summary_artifacts` — id, serial, intent_version_id, content, job_ids, time_ids
- `ledger_entries` — id, artifact_serial, sha256, blockchain_ref, supersedes_id
- `message_bus_messages` — unified message store with UUID, vector_clock, channel_id, transport_type

**Migrate:**
- Existing `messages` table data into `message_bus_messages`
- Any existing Plan data into `intents` + `intent_versions`

**Drop (after migration verified):**
- Plan-specific columns/tables no longer needed

### Android Changes

- `PlanRepository.kt` becomes `IntentRepository.kt`
- Plan UI screens become Intent screens (simpler — just scope + confirmation)
- New finalization flow UI: trigger Synthesizer, review artifact, confirm seal
- Message layer refactored to use Message Bus API instead of separate BLE/IP paths
- Reconciliation runs on transport change events

### Execution Order

**Phase 1 (Messaging):**
1. Build Message Bus + Reconciliation Engine (backend)
2. Migrate message storage to unified table
3. Refactor Android message layer to use Message Bus
4. Verify: send via BLE, receive via IP, no dupes, correct order

**Phase 2 (Core Flow):** Depends on Phase 1 — Synthesizer consumes chat context via Message Bus, so unified messaging must be operational first.
1. Build Intent service + authority
2. Build Synthesizer + authority
3. Build Ledger + authority
4. Migrate Plan data to Intent
5. Update Report/Invoice pipeline to read from Ledger
6. Update Android UI
7. Update System Law document
8. Verify: standard flow end-to-end, small project flow end-to-end, correction flow

---

## Testing & Verification

### Phase 1 Tests (Messaging)

**Transport reliability:**
- Send message via BLE only, go online, message appears in IP history with same UUID
- Send message via IP, go offline, message available in local BLE mesh store
- Send same message on both transports simultaneously, single message in store (dedup)

**Ordering:**
- Device A sends msg1, Device B replies msg2 while offline, reconciliation preserves causal order
- Three devices in mesh, messages arrive out of order, vector clock resolves to correct sequence

**Handoff:**
- Mid-conversation transport switch, no lost messages, no duplicates
- Rapid toggling between BLE/IP, message integrity maintained

### Phase 2 Tests (Core Flow)

**Standard flow end-to-end:**
- Create Intent, confirm, generate Jobs, record Time, close all, Synthesize, Seal, verify Archive entry, generate Report from Ledger

**Small project flow:**
- Create Jobs directly, execute, close, Synthesize, auto-Intent generated, confirm, Seal, verify audit trail

**Correction flow:**
- Sealed Ledger entry, new Synthesizer run, new Ledger entry with supersedes reference, both entries in Archive, Report reflects latest

**Authority enforcement:**
- Attempt to seal with open Jobs — rejected
- Attempt to confirm Intent via AI — rejected
- Attempt to edit sealed Ledger entry — rejected
- Attempt to finalize without confirmed Intent — rejected

**Versioning independence:**
- New Intent version does not invalidate existing Summary Artifacts
- New Summary Artifact does not auto-seal (requires explicit Ledger action)
- Ledger amendment creates new entry, prior entry unchanged

**Data integrity:**
- Every Ledger entry has valid SHA-256
- Every sealed artifact traceable: Ledger -> Artifact -> Intent version + Job IDs + Time IDs
- Archive contains complete chain for any given project

---

## System Law Updates Required

The following sections of `SYSTEM_LAW_README.md` require rewriting:
- Section 1: Core User Flow — replace Plan with Intent/Synthesizer/Ledger sequence
- Section 2: Core Containers — replace Plan definition with Intent, Synthesizer, Ledger definitions
- Section 4: Plan Authority Validation — split into three authority sections
- Section 5: API Endpoints — replace plan-authority endpoints with new authority endpoints
- Section 6: Core Features — update feature list to reflect new containers
- Section 7: Final System Law — update maxims to reflect new terminology
