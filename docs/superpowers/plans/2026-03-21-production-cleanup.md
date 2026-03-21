# Production-Ready Cleanup Sprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up Phase 1+2 code into app startup, replace Synthesizer mock data with real Supabase queries, and remove deprecated Plan files.

**Architecture:** Three independent cleanup tasks: (A) add initialization calls in TradeMeshApplication.kt, (B) replace mock data in synthesizer.ts with real queries + fix serial counter with DB sequence, (C) remove Plan routes from api.ts and delete deprecated files.

**Tech Stack:** Kotlin (Android), TypeScript (backend), Supabase (PostgreSQL)

**Spec:** `docs/superpowers/specs/2026-03-21-production-cleanup-design.md`

---

## File Structure

### Modified Files
- `android/app/src/main/java/com/guildofsmiths/trademesh/TradeMeshApplication.kt` — add IntentRepository + MessageBus init
- `backend/src/synthesizer.ts` — replace mock data with real Supabase queries, make generateSerial async
- `backend/src/api.ts` — remove Plan routes and unused imports

### Created Files
- `supabase/migrations/004_artifact_serial_sequence.sql` — DB sequence for artifact serials

### Deleted Files
- `backend/src/planAuthority.ts`
- `backend/src/planSynthesis.ts`
- `backend/src/autoPlanCreator.ts`
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/PlanRepository.kt`
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanTypes.kt`
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanComponents.kt`

---

## Task 1: Wire Up App Initialization (Android)

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/TradeMeshApplication.kt`

- [ ] **Step 1: Add imports**

Add these imports at the top of `TradeMeshApplication.kt`:

```kotlin
import com.guildofsmiths.trademesh.data.IntentRepository
```

- [ ] **Step 2: Add IntentRepository and MessageBus initialization**

In `onCreate()`, after the `BoundaryEngine.initializeChannelMembership()` call (line 63), add:

```kotlin
        // Initialize Intent repository (Phase 2 — scope declaration + versioning)
        IntentRepository.init(getSharedPreferences("intent_prefs", MODE_PRIVATE))

        // Initialize unified Message Bus (Phase 1 — dedup, vector clocks, reconciliation)
        BoundaryEngine.initMessageBus(this, "http://192.168.8.163:3000")
```

- [ ] **Step 3: Verify Android compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/TradeMeshApplication.kt
git commit -m "feat: wire up IntentRepository and MessageBus initialization in app startup

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Artifact Serial Sequence (Supabase)

**Files:**
- Create: `supabase/migrations/004_artifact_serial_sequence.sql`

- [ ] **Step 1: Create migration file**

```sql
-- Persistent sequence for Summary Artifact serial numbers
-- Replaces in-memory counter that reset on server restart
CREATE SEQUENCE IF NOT EXISTS artifact_serial_seq START 1;
```

- [ ] **Step 2: Commit**

```bash
git add supabase/migrations/004_artifact_serial_sequence.sql
git commit -m "feat: add Supabase sequence for persistent artifact serial numbers

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Replace Synthesizer Mock Data (Backend)

**Files:**
- Modify: `backend/src/synthesizer.ts`

- [ ] **Step 1: Replace generateSerial with async DB-backed version**

Replace lines 6-14 (the in-memory counter and `generateSerial` function) with:

```typescript
async function generateSerial(): Promise<string> {
  const { data, error } = await supabase.rpc('nextval', { seq_name: 'artifact_serial_seq' });

  // Fallback to timestamp-based serial if sequence not available
  let seq: number;
  if (error || data === null) {
    seq = Date.now() % 100000;
  } else {
    seq = Number(data);
  }

  const date = new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `ART-${y}-${m}-${String(seq).padStart(4, '0')}`;
}
```

Note: Supabase JS client doesn't expose raw `SELECT nextval()` directly. Use `supabase.rpc()` if a function is defined, otherwise use a raw query approach. If `rpc` doesn't work with PostgreSQL built-in functions, use this alternative:

```typescript
async function generateSerial(): Promise<string> {
  const { data, error } = await supabase
    .from('summary_artifacts')
    .select('serial')
    .order('created_at', { ascending: false })
    .limit(1);

  let seq = 1;
  if (!error && data && data.length > 0) {
    const lastSerial = data[0].serial; // e.g., "ART-2026-03-0005"
    const lastNum = parseInt(lastSerial.split('-').pop() || '0', 10);
    seq = lastNum + 1;
  }

  const date = new Date();
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  return `ART-${y}-${m}-${String(seq).padStart(4, '0')}`;
}
```

- [ ] **Step 2: Replace mock data assembly with real Supabase queries**

Replace lines 25-31 (the mock block) with:

```typescript
  // Query real job data
  const { data: jobRows } = await supabase
    .from('jobs')
    .select('id, title, description, status')
    .in('id', jobIds);

  const workPerformed = (jobRows || []).map(j =>
    `${j.title || 'Untitled job'}: ${j.status || 'completed'}${j.description ? ' — ' + j.description : ''}`
  );

  // Query real time entry data
  const { data: timeRows } = await supabase
    .from('time_entries')
    .select('id, user_id, duration_minutes, job_id')
    .in('id', timeEntryIds);

  const totalMinutes = (timeRows || []).reduce((sum, t) => sum + (t.duration_minutes || 0), 0);
  const totalHours = Math.round((totalMinutes / 60) * 100) / 100;

  const laborRecorded = (timeRows || []).map(t =>
    `${t.user_id?.substring(0, 8) || 'unknown'}: ${t.duration_minutes || 0} min on job ${t.job_id?.substring(0, 8) || 'unlinked'}`
  );

  // Query materials by job IDs
  const { data: materialRows } = await supabase
    .from('materials')
    .select('name, quantity, unit_cost, job_id')
    .in('job_id', jobIds);

  const materialsUsed = (materialRows || []).map(m =>
    `${m.name}: ${m.quantity} × $${m.unit_cost || 0}`
  );

  const materialCost = (materialRows || []).reduce((sum, m) =>
    sum + ((m.quantity || 0) * (m.unit_cost || 0)), 0
  );

  // Query approved chat messages
  const contextualNotes: string[] = [];
  if (approvedChatMessageIds.length > 0) {
    const { data: chatRows } = await supabase
      .from('message_bus_messages')
      .select('content, sender_name')
      .in('id', approvedChatMessageIds);

    for (const msg of chatRows || []) {
      contextualNotes.push(`${msg.sender_name}: ${msg.content}`);
    }
  }

  // Calculate total cost: labor ($55/hr default) + materials
  const laborCost = totalHours * 55;
  const totalCost = Math.round((laborCost + materialCost) * 100) / 100;
```

- [ ] **Step 3: Update the serial assignment to await**

Change line 35 from:
```typescript
    serial: generateSerial(),
```
to:
```typescript
    serial: await generateSerial(),
```

- [ ] **Step 4: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | grep synthesizer || echo "No errors in synthesizer.ts"`
Expected: No errors in synthesizer.ts

- [ ] **Step 5: Commit**

```bash
git add backend/src/synthesizer.ts
git commit -m "feat: replace Synthesizer mock data with real Supabase queries for jobs, time, materials

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Remove Plan Routes from api.ts (Backend)

**Files:**
- Modify: `backend/src/api.ts`

- [ ] **Step 1: Read api.ts and identify all Plan route blocks**

Read the full file. The Plan routes to remove are at these approximate lines:
- `apiRouter.post('/plans', ...)` — line 595
- `apiRouter.get('/plans', ...)` — line 623
- `apiRouter.get('/plans/:id', ...)` — line 628
- `apiRouter.patch('/plans/:id', ...)` — line 634
- `apiRouter.post('/plans/:planId/proposals', ...)` — line 641
- `apiRouter.post('/proposals/:id/confirm', ...)` — line 670
- `apiRouter.post('/plans/:id/synthesize', ...)` — line 687
- `apiRouter.post('/summaries/:id/confirm', ...)` — line 743
- `apiRouter.post('/plans/:id/generate-output', ...)` — line 760
- `apiRouter.post('/plans/:id/archive', ...)` — line 868
- `apiRouter.get('/archive/plans/:id', ...)` — line 905
- `apiRouter.get('/archive/plans/:id/export', ...)` — line 933
- `apiRouter.post('/small-project/create-auto-plan', ...)` — line 1215
- `apiRouter.post('/plan-authority/validate-creation', ...)` — line 1332
- `apiRouter.post('/plan-authority/validate-finalization', ...)` — line 1398
- `apiRouter.post('/plan-authority/validate-output', ...)` — line 1458

**Keep** the Engagement routes:
- `apiRouter.post('/engagements', ...)` — line 551
- `apiRouter.get('/engagements', ...)` — line 578
- `apiRouter.get('/engagements/:id', ...)` — line 583

**Keep** all non-Plan routes (channels, messages, media, reports, system, small-project Intent routes, etc.)

- [ ] **Step 2: Remove Plan route blocks**

Delete all the Plan route handler blocks identified above. Each block spans from the `apiRouter.post/get(...)` line to its closing `});`.

- [ ] **Step 3: Remove unused imports**

Remove these imports from the top of api.ts:
```typescript
import { planSynthesisService } from './planSynthesis';
import { planAuthority } from './planAuthority';
import { autoPlanCreator } from './autoPlanCreator';
```

Also remove any Plan-related type imports that are no longer used after route removal:
```typescript
// Remove from the import block if no longer referenced:
Plan, Proposal, PlanSummary, CreatePlanRequest, CreateProposalRequest,
ConfirmProposalRequest, CreatePlanSummaryRequest, ConfirmSummaryRequest
```

Check each one — if still referenced by remaining code, keep it.

- [ ] **Step 4: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l`
Expected: Same or fewer errors than before (pre-existing errors OK, no new ones)

- [ ] **Step 5: Commit**

```bash
git add backend/src/api.ts
git commit -m "refactor: remove deprecated Plan routes from api.ts — replaced by Intent/Synthesizer/Ledger

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Delete Deprecated Backend Files

**Files:**
- Delete: `backend/src/planAuthority.ts`
- Delete: `backend/src/planSynthesis.ts`
- Delete: `backend/src/autoPlanCreator.ts`

- [ ] **Step 1: Verify no remaining imports**

Run: `grep -rn "planAuthority\|planSynthesis\|autoPlanCreator" /Users/fegensprenelon/smith-net/backend/src/ --include="*.ts" | grep -v "planAuthority.ts\|planSynthesis.ts\|autoPlanCreator.ts"`
Expected: No results (imports were removed in Task 4)

- [ ] **Step 2: Delete the files**

```bash
rm backend/src/planAuthority.ts backend/src/planSynthesis.ts backend/src/autoPlanCreator.ts
```

- [ ] **Step 3: Verify backend still compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l`
Expected: Same or fewer errors

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: delete deprecated planAuthority, planSynthesis, autoPlanCreator

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Delete Deprecated Android Files

**Files:**
- Delete: `android/app/src/main/java/com/guildofsmiths/trademesh/data/PlanRepository.kt`
- Delete: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanTypes.kt`
- Delete: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanComponents.kt`

- [ ] **Step 1: Verify no remaining imports**

Run: `grep -rn "PlanRepository\|PlanTypes\|PlanComponents\|import.*PlanProposal\|import.*PlanSummary\|import.*PlanFilter\|import.*PlanOutputType" /Users/fegensprenelon/smith-net/android/ --include="*.kt" | grep -v "PlanRepository.kt\|PlanTypes.kt\|PlanComponents.kt\|node_modules"`

If any files still import from these, remove those import lines first.

- [ ] **Step 2: Delete the files**

```bash
rm android/app/src/main/java/com/guildofsmiths/trademesh/data/PlanRepository.kt
rm android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanTypes.kt
rm android/app/src/main/java/com/guildofsmiths/trademesh/ui/plan/PlanComponents.kt
```

- [ ] **Step 3: Verify Android compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: delete deprecated PlanRepository, PlanTypes, PlanComponents

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Mark Deprecated Types (Backend)

**Files:**
- Modify: `backend/src/types.ts`

- [ ] **Step 1: Add deprecation comments to Plan types**

Find these type definitions in `types.ts` and add `/** @deprecated */` JSDoc comments above each:

```typescript
/** @deprecated Use Intent instead */
export interface Plan { ... }

/** @deprecated Use IntentStatus instead */
export type PlanPhase = ...

/** @deprecated Use SummaryArtifact instead */
export interface PlanSummary { ... }

/** @deprecated Use IntentVersion instead */
export interface Proposal { ... }

/** @deprecated Use LedgerEntry.sha256Hash instead */
export interface PlanSerial { ... }

/** @deprecated Use LedgerEntry instead */
export interface PlanOutput { ... }
```

Do NOT delete these types — other code may still reference them. Just mark them.

- [ ] **Step 2: Verify backend compiles**

- [ ] **Step 3: Commit**

```bash
git add backend/src/types.ts
git commit -m "refactor: mark Plan-related types as deprecated in favor of Intent/Synthesizer/Ledger

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Integration Verification

- [ ] **Step 1: Full backend compilation check**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck 2>&1 | grep "error TS" | wc -l
```

Expected: Fewer errors than before cleanup (Plan-related errors should be gone)

- [ ] **Step 2: Full Android compilation check**

```bash
cd /Users/fegensprenelon/smith-net/android && JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME) ./gradlew compileDebugKotlin 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no orphaned imports**

```bash
grep -rn "planAuthority\|planSynthesis\|autoPlanCreator\|PlanRepository\|PlanTypes\|PlanComponents" /Users/fegensprenelon/smith-net/backend/src/ /Users/fegensprenelon/smith-net/android/app/src/ --include="*.ts" --include="*.kt" | grep -v "node_modules\|\.gradle" | head -10
```

Expected: No results (or only the @deprecated comments in types.ts)

- [ ] **Step 4: Milestone commit**

```bash
git add -A
git commit -m "milestone: production cleanup sprint complete — new code wired, mocks replaced, Plan files removed

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Summary

| Task | What It Does |
|------|-------------|
| 1 | Wire IntentRepository + MessageBus init in TradeMeshApplication |
| 2 | Add Supabase sequence for artifact serial persistence |
| 3 | Replace Synthesizer mock data with real job/time/material queries |
| 4 | Remove Plan routes from api.ts |
| 5 | Delete deprecated backend Plan files |
| 6 | Delete deprecated Android Plan files |
| 7 | Mark remaining Plan types as @deprecated |
| 8 | Full integration verification |
