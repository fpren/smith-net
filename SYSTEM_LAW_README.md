# CORE SYSTEM LAW — AUTHORITATIVE REFERENCE (v3)
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
→ PLAN (front page)
→ PROPOSAL CREATED
→ PROPOSAL CONFIRMED
→ JOBS GENERATED
→ JOBS EXECUTED
→ TIME RUNS DURING WORK
→ JOBS COMPLETE
→ RETURN TO PLAN
→ SUMMARY CREATED
→ SUMMARY CONFIRMED
→ REPORT + INVOICE GENERATED
→ ARCHIVE STORES FINAL TRUTH
```

This flow **cannot be reordered**. Breaking the order breaks system integrity.

### SMALL PROJECT FLOW (APPROVED ALTERNATE)
```
APP OPENS
→ JOBS (manual job entry)
→ JOB EXECUTED
→ TIME RUNS DURING WORK
→ JOB COMPLETE
→ PLAN (auto-created)
→ SUMMARY CREATED
→ SUMMARY CONFIRMED
→ REPORT and/or INVOICE GENERATED
→ ARCHIVE STORES FINAL TRUTH
```

**Key Rule:** PLAN is not skipped. PLAN is deferred. PLAN is auto-instantiated at finalization using collected facts.

---

## SECTION 2 — CORE CONTAINERS AND THEIR MEANING

### PLAN
**Responsibility:** Command center and synthesis layer
- Creates proposals, confirms intent, assembles facts, locks truth
- Evolves: Draft → Proposal → Confirmed → Finalized → Locked Snapshot
- **Never captures raw work, never edits jobs, never edits time**
- Once locked, **cannot be edited** - corrections require new Plan version

### JOBS
**Responsibility:** Work scope and execution state
- Defines WHAT is being done
- May be generated from proposal OR manually entered
- Once completed, **immutable** - corrections require new jobs

### TIME
**Responsibility:** Labor facts
- Defines WHEN and HOW LONG work occurred
- Records clock-in/out, breaks, adjustment records
- **Append-only** - closed entries never edited
- Attached clock-out notes are **contextual only**

### ARCHIVE
**Responsibility:** Canonical ledger
- Stores **finalized truth and nothing else**
- Contains closed jobs, time, chat, plans, reports, invoices
- **Immutable, append-only, no deletion**
- Read-only forever

### REPORT / INVOICE OUTPUT
**Responsibility:** Publications from locked snapshots
- Render locked Plan snapshots into documents
- **Do not calculate, do not fix errors, do not change data**
- Errors require new Plan version and new outputs

### SETTINGS
**Responsibility:** System configuration
- Identity, permissions, BLE mesh toggle, AI config, archive viewing
- **Never participates in payroll, planning, or reporting logic**

### CONNECTIVITY (BLE / ONLINE)
**Responsibility:** Infrastructure transport
- BLE Mesh: peer discovery, local relay, offline operation
- Online/Gateway: cloud sync, backup, report delivery
- **Never owns data, never changes data** - only moves data

---

## SECTION 3 — CONTEXTUAL EXTENSIONS (APPROVED, NON-DESTRUCTIVE)

### CLOCK-OUT NOTES AND AI SUMMARIZATION
- At clock-out: worker may write note OR request AI clarity
- Notes attach to time entries as **context only**
- May be summarized by Plan, optionally included in reports
- **Do not modify labor facts**

### CHAT (BLE + ONLINE) AS CONTEXTUAL EVIDENCE
- Messages archived immutably regardless of transport
- **Never changes jobs, time, or plan state**
- Plan may read chat during finalization for contextual summaries
- **Never auto-included** - requires human confirmation

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
- Finalize plans
- Generate invoices independently

### BREAK REQUESTS AND APPROVAL FLOW
```
Worker requests break
→ AI may assist phrasing
→ Foreman reviews and approves
→ TIME records break only after approval
```

---

## SECTION 4 — PLAN AUTHORITY VALIDATION SYSTEM

The **Plan Authority** enforces system law through binary validation:

### PRECONDITIONS (ALL MUST BE TRUE)
1. All referenced Jobs exist and are closed
2. All referenced Time entries exist and are closed
3. No referenced Job or Time entry is mutable
4. All clock-out notes are captured and immutable
5. Any chat or AI context is read-only
6. Every referenced record has canonical serial
7. Every serial has cryptographic hash
8. Every hash written to blockchain ledger
9. Ledger transactions verifiable

### PLAN CONTENT REQUIREMENTS
- Clear intent statement
- Explicit scope (Job IDs + Time IDs)
- Summary derived from facts only
- Actor attribution by UUID only
- Canonical serial generation
- SHA-256 cryptographic commitment

### VALIDATION RESPONSE
**VALIDATED — READY FOR FINALIZATION** or **REJECTED — SYSTEM LAW VIOLATION [rule]**

---

## SECTION 5 — API ENDPOINTS BY SYSTEM LAW

### PLAN AUTHORITY (VALIDATION)
```
POST /plan-authority/validate-creation     — Validate plan creation
POST /plan-authority/validate-finalization — Validate finalization
POST /plan-authority/validate-output       — Validate output auth
```

### SMALL PROJECT FLOW
```
POST /small-project/validate-eligibility   — Check small project eligibility
POST /small-project/create-auto-plan      — Auto-create Plan from facts
POST /small-project/transition-phase      — Move through phases
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
POST /reports/assemble                    — Assemble from snapshot
POST /reports/render                      — Render to format
POST /reports/download                    — Prepare download
POST /reports/share                       — Share with recipients
POST /reports/generate                    — Full pipeline
```

---

## SECTION 6 — CORE FEATURES VS ADD-ON FEATURES

### CORE FEATURES (CANNOT BE REMOVED OR MERGED)
- ✅ Plan (explicit or implicit)
- ✅ Jobs (scope execution)
- ✅ Time (labor ledger)
- ✅ Archive (immutable history)
- ✅ Report / Invoice Output (publication)
- ✅ Settings (system configuration)
- ✅ Connectivity (BLE / Online transport)

### APPROVED CONTEXTUAL ADD-ONS
- ✅ Clock-out notes
- ✅ AI-assisted summaries
- ✅ Chat-derived context
- ✅ Break coordination
- ✅ Foreman dashboards

### OPTIONAL ADD-ONS
- ⭕ Payroll rules
- ⭕ Notifications
- ⭕ Analytics
- ⭕ External accounting integrations
- ⭕ Advanced exports

---

## SECTION 7 — FINAL SYSTEM LAW

**Plan may be explicit or implicit.**  
**Plan may be early or deferred.**  
**Plan is never optional.**

**Jobs execute work.**  
**Time records labor.**  
**Chat provides context.**  
**Plan explains results.**  
**Reports publish proof.**  
**Archive remembers forever.**

**AI assists.**  
**Humans decide.**  
**Facts do not change.**

---

## IMPLEMENTATION STATUS

### ✅ COMPLETED
- Plan Authority validation system
- Serial generation and cryptographic commitment
- Standard and small project flows
- Report generation pipeline
- Archive system with immutability
- Settings configuration (non-executive)
- System law enforcement endpoints

### 🔄 ARCHITECTURAL COMPONENTS
- Report Assembler (deterministic mapping)
- Report Renderer (PDF/HTML/XLSX)
- Report Output (export/download/share)
- Auto Plan Creator (small project flow)

### 📋 REMAINING INTEGRATION TASKS
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
- Skip Plan creation
- Edit immutable records
- Break flow order
- Allow AI to make decisions
- Change archived data

Will result in **immediate rejection** with violation identifier.

**System law is absolute. No appeals. No overrides.**

---

*This document defines the system. The code implements the law.*
