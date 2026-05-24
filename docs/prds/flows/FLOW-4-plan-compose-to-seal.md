# FLOW-4 — Plan compose → seal (the moat)

**Sigma step:** 5 — Wireframe Prototype PRD
**Source flow:** `docs/flows/FLOW-DIAGRAMS.md` Flow 4
**This is the moat.** Determinism MUST hold (NFR-D1 through D5).

---

## Scope

Solo+ user creates an Engagement, converts to Intent, edits IntentVersion v1 (draft), proposes, confirms (party check), synthesizes a SummaryArtifact from confirmed Intent + closed Jobs + closed TimeEntries, and seals into Ledger with SHA256 hash. Subsequent outputs (invoice, report, public link) reference the sealed artifact.

Tier impact: Free user opening this flow at any step sees N3 overlay (PLAN Compiler preview lock).

## Screens

| Step | Screen | Origin | Spec |
|---|---|---|---|
| 0 (Free user only) | E1 PlanScreen → **N3 LockedFeatureOverlay (PLAN preview variant)** with dimmed live preview behind | NET-NEW | `WIREFRAME-SPEC §2`; bg content = sample compiled plan from user's first job |
| 1 | E1 PlanScreen (Solo+) | existing | unchanged shell, see UI surfaces inside |
| 2 | E1 → engagements list | existing | unchanged |
| 3 | E1 → engagement detail → [Convert to Intent] | existing | unchanged |
| 4 | E1 → IntentVersion v1 draft edit | existing | unchanged |
| 5 | E1 → [Propose] (state → proposed) | existing | unchanged |
| 6 | E1 → [Confirm] (state → confirmed) | existing | requires confirmer to be in `parties` |
| 7 | (work happens) — D2 jobs close, F1 time closes | existing | unchanged |
| 8 | E1 → confirmed intent → [Synthesize] | existing | requires ≥1 closed Job + ≥1 closed TimeEntry |
| 9 | E1 → SummaryArtifact view (with serial) | existing | unchanged |
| 10 | E1 → artifact → [Seal in Ledger] | existing | calls server seal endpoint |
| 11 | E1 → LedgerEntry view (hash, supersession chain, [Verify hash] button) | existing | unchanged |
| 12a | E1 → sealed → [Generate Invoice] → H1 InvoiceScreen pre-populated from artifact | existing | unchanged |
| 12b | E1 → sealed → [Generate Report] → J1 ReportScreen | existing | unchanged |
| 12c | E1 → sealed → [Copy public link] → /p/:uuid (proposal) or /i/:uuid (invoice) | existing | server-rendered |

## Server contract (the moat — most-rigorous endpoints)

| Endpoint | Behavior | Validators |
|---|---|---|
| POST /api/engagements | inserts `engagements` row with `status=active`, `intent` text | (none — loose capture) |
| POST /api/engagements/:id/convert | creates Intent + IntentVersion v1 (`status=draft`), updates engagement `status=converted` | — |
| POST /api/intents | creates Intent + IntentVersion v1 directly | `intentAuthority.validateIntentCreation` (scope non-empty, parties ≥ 1) |
| POST /api/intents/:versionId/propose | transitions `draft → proposed` | only if status == draft |
| POST /api/intents/:versionId/confirm | transitions `proposed → confirmed` + sets `confirmed_at`, `confirmed_by` | `intentAuthority.validateIntentConfirmation` (status == proposed, confirmer in parties) |
| POST /api/intents/:intentId/versions | creates supersession (new IntentVersion v(n+1) with `supersedes=v(n)`) | `validateIntentVersion` (no cycles) |
| POST /api/synthesize | inserts `summary_artifacts` row with deterministic content from listed inputs | `synthesisAuthority.validateSynthesisInputs` (Intent confirmed, ≥1 closed Job, ≥1 closed TimeEntry) |
| POST /api/ledger/seal | computes SHA256 over canonicalized artifact JSON; inserts `ledger_entries` row | `ledgerAuthority.validateSealing` (artifact valid, not already sealed) |
| POST /api/ledger/amend | creates new entry with `supersedes=priorEntryId`; sets prior's `superseded_by` | `ledgerAuthority.validateAmendment` (prior not already superseded) |
| GET /api/ledger/verify/:entryId | re-computes hash, compares with stored, returns `{expected, actual, valid}` | — |

## Determinism contract (NFR-D1 through D5 — MUST hold)

| Property | How enforced | How tested |
|---|---|---|
| **Same inputs → same artifact** | `synthesizer.synthesize()` is a pure function of `(intentVersionId, jobIds[], timeEntryIds[], chatMessageIds[])`. No clock reads inside the function. | Run twice with same inputs → assert artifact byte-for-byte equality |
| **Same artifact → same hash** | `computeHash(artifact)` canonicalizes JSON (sorted keys, no whitespace, fixed numeric formatting) before SHA256 | Hash test: same artifact instance hashed twice → identical |
| **Hash regenerable** | `/api/ledger/verify/:entryId` re-runs `computeHash` on current `summary_artifacts` row | Tamper test: directly UPDATE summary_artifacts.scope_statement → verify → returns `valid=false` |
| **AI never mutates pipeline** | `intentService` validators reject any auto-generated draft from being directly confirmed; human must propose + confirm. Synthesizer rejects any artifact field not derived from listed inputs. | Code review + test: AI assist creates draft with `auto_generated=true`; subsequent confirm requires party identity (not AI) |
| **Supersession chain integrity** | `validateAmendment` rejects amend on already-superseded entries. DB trigger (planned, Step 11) prevents direct `UPDATE` on non-NULL `superseded_by`. | Concurrency test: 2 parallel amends on same prior entry → exactly 1 succeeds |

## Acceptance criteria

| AC | Description | Verification |
|---|---|---|
| AC-1 | Free user opening E1 PlanScreen sees N3 overlay within 200ms | UI test |
| AC-2 | N3 dimmed background shows the user's first job in compiled-plan-form (anonymized if no jobs exist) | UI test |
| AC-3 | Solo+ user opening E1 sees existing PlanScreen UI unchanged | UI test |
| AC-4 | Intent creation rejects empty scope_statement (`intentAuthority`) | API test → 400 |
| AC-5 | Intent confirmation rejects confirmer not in parties | API test → 403 |
| AC-6 | Synthesize rejects unconfirmed Intent | API test → 400 |
| AC-7 | Synthesize rejects 0 closed jobs OR 0 closed time entries | API test → 400 |
| AC-8 | Seal of valid artifact returns LedgerEntry with `sha256_hash` non-empty | API test |
| AC-9 | Re-running synthesize with same inputs produces byte-for-byte identical artifact + same hash | Determinism test |
| AC-10 | Tampering with summary_artifacts content → verify returns `valid=false` | Tamper test |
| AC-11 | Amend prevented on already-superseded entry | API test → 400 |
| AC-12 | Generated invoice from sealed artifact contains references to artifact `serial` | UI / DB |
| AC-13 | Public link `/p/:uuid` and `/i/:uuid` resolve correctly post-seal | E2E test |

## BDD scenarios

```gherkin
Feature: Solo user composes a Plan and seals it deterministically

Scenario: Full happy path from engagement to sealed ledger entry
  Given a Solo user on E1 PlanScreen
  When they tap "+ New Engagement" and create one with intent="Kitchen rewire"
  And they convert it to an Intent (v1 status: draft)
  And they edit scope_statement="Replace 200A panel and rewire kitchen"
  And they add party "client@acme.com"
  And they tap Propose
  Then the IntentVersion status becomes "proposed"
  When the party (or self) taps Confirm
  Then status becomes "confirmed" with confirmed_at and confirmed_by set
  Given a job linked to this Intent is closed
  And a time entry on that job is closed
  When the user taps Synthesize
  Then synthesisAuthority validates inputs OK
  And a SummaryArtifact is created with serial SA-XXXXX
  When the user taps "Seal in Ledger"
  Then ledgerAuthority validates sealing OK
  And computeHash returns a SHA256 hash
  And a LedgerEntry is inserted with that hash and actor_uuid
  When the user taps "Verify hash"
  Then GET /api/ledger/verify/:entryId returns valid=true

Scenario: Re-running synthesize is deterministic
  Given a confirmed Intent v1 and the same closed Job and TimeEntry IDs
  When the user calls Synthesize twice in succession
  Then both SummaryArtifact responses have identical content (byte-for-byte)
  And computeHash returns the same SHA256 hash for both

Scenario: Tampering with sealed artifact is detected
  Given a sealed LedgerEntry referencing an artifact
  When an attacker directly mutates summary_artifacts.scope_statement via DB access
  And the contractor calls Verify hash
  Then the response returns valid=false
  And a SECURITY.SECURITY_ALERT audit entry is created

Scenario: Free user blocked at PlanScreen
  Given a Free-tier user
  When they navigate to PLAN tab
  Then N3 LockedFeatureOverlay (PLAN preview variant) appears within 200ms
  And the dimmed background shows a sample compiled plan
  And telemetry emits gate_hit.plan_compiler_preview
```

## Edge cases

| Case | Behavior |
|---|---|
| User attempts to confirm Intent without being a party | 403; UI shows "Only listed parties can confirm" toast |
| User attempts synthesize before any job closes | 400 + UI shows specific error referencing required preconditions |
| User AI-drafts an Intent (auto_generated=true) and tries to skip directly to confirm | rejected; AI drafts must still go propose → confirm |
| Time entry referenced in synthesis is later deleted | seal blocks (synthesis fails on re-run); existing artifacts remain valid (immutability of sealed) |
| Concurrent amend on same prior entry | exactly one succeeds; other gets 409 conflict |
| Synthesis with > 1 closed Job + 1 TimeEntry per | accepted; artifact aggregates all |
| User attempts to delete a TimeEntry that's referenced by a sealed artifact | DELETE returns 409 with reference to entry serial |

## Non-goals

- Blockchain anchoring (`blockchain_ref` field exists but not wired in v1)
- AI-only confirmation (always requires human party)
- UI redesign of E1 PlanScreen (existing IntentComponents.kt unchanged)
- Real-time collaboration on Intent draft (single-author model for v1)

## Critical security & integrity tests (must pass before public launch)

| Test | Pass criteria |
|---|---|
| Determinism stability under load | 1000 parallel synthesize calls with same inputs → 1000 identical artifacts |
| Hash collision check | 100k sample artifacts → 0 SHA256 collisions |
| Tamper detection latency | Verify endpoint returns within p95 < 500ms |
| Supersession DAG integrity | 100 random amend chains → no cycles created |
| RLS / authorization | Non-party user attempting to read another user's intent → 403 |

## Linked specs

- `ARCHITECTURE.md §4` (deterministic execution pipeline diagram)
- `SCHEMA.md §6` (intents, intent_versions, summary_artifacts, ledger_entries)
- `SECURITY.md §6` (sealing properties, supersession integrity, threat model)
- `NFRS.md §1` (NFR-D1 through D5)
- `WIREFRAME-SPEC.md §2` (LockedFeatureOverlay for N3)
- `STATE-COVERAGE.md` N3 (PLAN preview lock states)
- Existing code: `backend/src/intentService.ts`, `intentAuthority.ts`, `synthesizer.ts`, `synthesisAuthority.ts`, `ledger.ts`, `ledgerAuthority.ts`
