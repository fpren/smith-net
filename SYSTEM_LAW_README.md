# CORE SYSTEM LAW — AUTHORITATIVE REFERENCE (v4)
==========================================

## PURPOSE

This document defines the non-changeable core feature flow, container responsibilities,
immutability rules, and add-on boundaries for the system.

This document also defines approved alternate entry paths (small-project execution)
that do NOT violate system integrity.

This is **system law**. It cannot be appealed. It cannot be overridden.

---

## SECTION 1 — CORE USER FLOW (NON-CHANGEABLE)

### STANDARD FLOW
```
APP OPENS
→ INTENT CREATED (scope + proposal)
→ INTENT CONFIRMED
→ JOBS GENERATED FROM INTENT
→ JOBS EXECUTED
→ TIME RUNS DURING WORK
→ JOBS COMPLETE
→ SYNTHESIZER ASSEMBLES FACTS (Intent + Jobs + Time → Summary Artifact)
→ LEDGER SEALS ARTIFACT (cryptographic commitment)
→ ARCHIVE STORES SEALED TRUTH
→ REPORTS / INVOICES GENERATED FROM LEDGER
```

This flow **cannot be reordered**. Breaking the order breaks system integrity.

### SMALL PROJECT FLOW (APPROVED ALTERNATE)
```
APP OPENS
→ JOBS (manual entry)
→ JOBS EXECUTED
→ TIME RUNS DURING WORK
→ JOBS COMPLETE
→ SYNTHESIZER ASSEMBLES FACTS
→ INTENT AUTO-GENERATED (retroactive, from Synthesizer's fact assembly)
→ INTENT CONFIRMED
→ LEDGER SEALS ARTIFACT
→ ARCHIVE STORES SEALED TRUTH
→ REPORTS / INVOICES GENERATED FROM LEDGER
```

**Key Rule:** Intent is not skipped. Intent is deferred. Intent is auto-generated retroactively by the Synthesizer from collected facts, then confirmed by a human before sealing.

---

## SECTION 2 — CORE CONTAINERS AND THEIR MEANING

### INTENT
**Responsibility:** Scope declaration and agreement
- Declares what is to be done and by whom
- States: Draft → Proposed → Confirmed → Superseded
- **Never assembles facts, generates summaries, locks truth, or produces reports**
- **Never references Time entries** — Intent is about what, never how long
- Once confirmed, **cannot be edited** — change orders create new Intent versions

### SYNTHESIZER
**Responsibility:** Fact assembly
- Stateless processor — runs, produces output, done
- Input: Intent version + closed Jobs + closed Time entries + clock-out notes + approved chat
- Output: Summary Artifact (immutable, with serial number)
- **Does NOT seal, commit, or archive**
- **Does NOT modify any input record**

### LEDGER
**Responsibility:** Truth-sealing
- Append-only, no deletions, no edits
- Each entry: Artifact serial + SHA-256 hash + blockchain commitment + timestamp + actor UUID
- Corrections: new entry referencing prior (amendment chain)
- **Reports and Invoices read exclusively from Ledger**
- Sealed entries are permanent — they cannot be reversed

### JOBS
**Responsibility:** Work scope and execution state
- Defines WHAT is being done
- May be generated from Intent OR manually entered
- Once completed, **immutable** — corrections require new jobs

### TIME
**Responsibility:** Labor facts
- Defines WHEN and HOW LONG work occurred
- Records clock-in/out, breaks, adjustment records
- **Append-only** — closed entries never edited
- Attached clock-out notes are **contextual only**

### ARCHIVE
**Responsibility:** Canonical store of sealed truth
- Stores **Ledger-sealed artifacts and nothing else**
- Contains closed jobs, time, chat, sealed artifacts, reports, invoices
- **Immutable, append-only, no deletion**
- Read-only forever

### REPORT / INVOICE OUTPUT
**Responsibility:** Publications from Ledger entries
- Render Ledger-sealed artifacts into documents
- **Do not calculate, do not fix errors, do not change data**
- Errors require Ledger amendment and new outputs

### SETTINGS
**Responsibility:** System configuration
- Identity, permissions, BLE mesh toggle, AI config, archive viewing
- **Never participates in payroll, synthesis, or reporting logic**

### CONNECTIVITY (BLE / ONLINE)
**Responsibility:** Infrastructure transport
- BLE Mesh: peer discovery, local relay, offline operation
- Online/Gateway: cloud sync, backup, report delivery
- **Never owns data, never changes data** — only moves data

---

## SECTION 3 — CONTEXTUAL EXTENSIONS (APPROVED, NON-DESTRUCTIVE)

### CLOCK-OUT NOTES AND AI SUMMARIZATION
- At clock-out: worker may write note OR request AI clarity
- Notes attach to time entries as **context only**
- May be consumed by Synthesizer, optionally included in artifacts
- **Do not modify labor facts**

### CHAT (BLE + ONLINE) AS CONTEXTUAL EVIDENCE
- Messages archived immutably regardless of transport
- **Never changes jobs, time, or intent state**
- Synthesizer may read approved chat during fact assembly for contextual summaries
- **Never auto-included** — requires human confirmation before Synthesizer consumes it

### AI ASSISTANT (REAL-TIME COORDINATION)
**AI May Assist:**
- Phrase break requests
- Assist status updates
- Broadcast foreman-approved messages
- Summarize discussions

**AI May NOT:**
- Approve breaks
- Start/stop time
- Edit jobs
- Confirm intent
- Trigger synthesis
- Seal the Ledger
- Generate invoices independently

### BREAK REQUESTS AND APPROVAL FLOW
```
Worker requests break
→ AI may assist phrasing
→ Foreman reviews and approves
→ TIME records break only after approval
```

---

## SECTION 4 — VALIDATION AUTHORITIES

### INTENT AUTHORITY
Enforces rules for Intent creation, confirmation, and versioning:
- Intent creation: scope must be non-empty, actor UUID required
- Intent confirmation: **human action only** — AI cannot confirm
- Version chain: each new version references prior version serial
- **VALIDATED — INTENT READY** or **REJECTED — INTENT AUTHORITY VIOLATION [rule]**

### SYNTHESIS AUTHORITY
Enforces rules for Synthesizer inputs and artifact output:
- All referenced Jobs must exist and be closed (immutable)
- All referenced Time entries must exist and be closed (immutable)
- All clock-out notes captured and immutable
- Any chat context must be read-only and human-approved
- Every referenced record has canonical serial
- Every serial has cryptographic hash
- Artifact serial generated on successful output
- **VALIDATED — ARTIFACT READY FOR SEALING** or **REJECTED — SYNTHESIS AUTHORITY VIOLATION [rule]**

### LEDGER AUTHORITY
Enforces rules for sealing and amendment:
- Sealing: Artifact serial + SHA-256 hash required, blockchain commitment written
- Ledger transaction must be verifiable
- Amendments: new entry must reference prior entry serial (amendment chain must be valid)
- No deletions, no edits to existing entries — ever
- **SEALED** or **REJECTED — LEDGER AUTHORITY VIOLATION [rule]**

---

## SECTION 5 — API ENDPOINTS BY SYSTEM LAW

### INTENT AUTHORITY (VALIDATION + LIFECYCLE)
```
POST /intent-authority/validate-creation
POST /intents
POST /intents/:intentId/versions/:versionId/propose
POST /intents/:intentId/versions/:versionId/confirm
POST /intents/:intentId/versions
```

### SYNTHESIS AUTHORITY (VALIDATION + EXECUTION)
```
POST /synthesis-authority/validate-inputs
POST /synthesize
GET  /artifacts/:id
```

### LEDGER (SEALING + AMENDMENT)
```
POST /ledger/seal
POST /ledger/amend
GET  /ledger/:id
```

### SMALL PROJECT FLOW
```
POST /small-project/synthesize-and-generate-intent
POST /small-project/confirm-and-seal
```

### SYSTEM LAW ENFORCEMENT
```
GET  /system/flow-status                   — Get current flow state
POST /system/validate-action               — Check action against law
```

### SETTINGS (CONFIGURATION ONLY)
```
GET  /settings                            — Get system settings
PATCH /settings                           — Update configuration
GET  /settings/connectivity               — Get connectivity status
```

### REPORT GENERATION PIPELINE
```
POST /reports/assemble                    — Assemble from Ledger entry
POST /reports/render                      — Render to format
POST /reports/download                    — Prepare download
POST /reports/share                       — Share with recipients
POST /reports/generate                    — Full pipeline
```

---

## SECTION 6 — CORE FEATURES VS ADD-ON FEATURES

### CORE FEATURES (CANNOT BE REMOVED OR MERGED)
- Intent (scope declaration, explicit or retroactive)
- Synthesizer (fact assembly, stateless)
- Ledger (truth-sealing, append-only)
- Jobs (scope execution)
- Time (labor ledger)
- Archive (immutable history)
- Report / Invoice Output (publication from Ledger)
- Settings (system configuration)
- Connectivity (BLE / Online transport)

### APPROVED CONTEXTUAL ADD-ONS
- Clock-out notes
- AI-assisted summaries
- Chat-derived context
- Break coordination
- Foreman dashboards

### OPTIONAL ADD-ONS
- Payroll rules
- Notifications
- Analytics
- External accounting integrations
- Advanced exports

---

## SECTION 7 — FINAL SYSTEM LAW

**Intent declares scope.**
**Synthesizer assembles facts.**
**Ledger seals truth.**
**Jobs execute work.**
**Time records labor.**
**Chat provides context.**
**Reports publish proof.**
**Archive remembers forever.**

**AI assists.**
**Humans decide.**
**Facts do not change.**

---

## IMPLEMENTATION STATUS

### COMPLETED
- Intent Authority validation system
- Synthesis Authority with stateless fact assembly
- Ledger sealing with SHA-256 and blockchain commitment
- Serial generation and cryptographic commitment
- Standard and small project flows
- Report generation pipeline (reads from Ledger only)
- Archive system with immutability
- Settings configuration (non-executive)
- System law enforcement endpoints

### ARCHITECTURAL COMPONENTS
- Synthesizer (deterministic fact assembly)
- Report Renderer (PDF/HTML/XLSX from Ledger entries)
- Report Output (export/download/share)
- Auto Intent Generator (small project flow, retroactive)

### REMAINING INTEGRATION TASKS
- Connect to actual jobboard component
- Connect to actual time tracking component
- Implement real database persistence
- Add blockchain ledger integration
- Implement actual AI summarization
- Add foreman dashboard UI
- Test end-to-end flows

---

## VIOLATION CONSEQUENCES

Any attempt to:
- Skip or bypass Intent creation
- Edit immutable records
- Break flow order
- Allow AI to confirm Intent, trigger Synthesis, or seal the Ledger
- Change archived or Ledger-sealed data
- Generate reports from any source other than the Ledger

Will result in **immediate rejection** with violation identifier.

**System law is absolute. No appeals. No overrides.**

---

*This document defines the system. The code implements the law.*
