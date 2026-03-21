# Production-Ready Cleanup Sprint

**Date:** 2026-03-21
**Status:** Draft
**Scope:** Wire up new Phase 1+2 code, replace Synthesizer mock data with real queries, remove deprecated Plan files

---

## Problem Statement

Phase 1 (Messaging Unification) and Phase 2 (Core Flow Redesign) code is implemented but not wired into the app startup. The Synthesizer uses mock data instead of real job/time queries. Deprecated Plan files remain in the codebase creating confusion.

## Part A: Wire Up New Code

### TradeMeshApplication.kt

Add to `onCreate()` initialization sequence:

1. `IntentRepository.init(sharedPrefs)` — initialize with SharedPreferences for serial counter persistence
2. `BoundaryEngine.initMessageBus(this, "http://192.168.8.163:3000")` — use the same backend URL already hardcoded in `registerMeshService()`

No backend URL configuration refactoring — matches existing pattern used by ChatManager and GatewayClient.

## Part B: Replace Synthesizer Mock Data

### Real Queries

Replace the 6 mock lines in `backend/src/synthesizer.ts` with Supabase queries:

- **workPerformed**: Query `jobs` table by job IDs → extract `title`, `description`, `status`
- **laborRecorded**: Query `time_entries` table by time entry IDs → extract `user_id`, `duration_minutes`, `job_id`
- **materialsUsed**: Query `materials` table by job IDs → extract `name`, `quantity`, `unit_cost`
- **contextualNotes**: Query `message_bus_messages` by approved chat message IDs → extract `content`, `sender_name`
- **totalHours**: Sum actual `duration_minutes` from time entries, convert to hours
- **totalCost**: Sum `(quantity * unit_cost)` from materials + `(totalHours * hourly_rate)` for labor

### Serial Counter Fix

Add to Supabase migration:
```sql
CREATE SEQUENCE IF NOT EXISTS artifact_serial_seq START 1;
```

Change `generateSerial()` from in-memory counter to async Supabase `nextval('artifact_serial_seq')` query.

## Part C: Remove Deprecated Files

### Backend — Remove Plan Routes from api.ts

Remove these routes (replaced by Intent/Synthesizer/Ledger routes):
- `POST /plans` → replaced by `POST /intents`
- `GET /plans`, `GET /plans/:id`, `PATCH /plans/:id` → removed
- `POST /plans/:planId/proposals` → replaced by Intent propose
- `POST /proposals/:id/confirm` → replaced by Intent confirm
- `POST /plans/:id/synthesize` → replaced by `POST /synthesize`
- All Plan-specific summary, finalization, and output routes → removed

Keep Engagement routes (`POST /engagements`, `GET /engagements`, `GET /engagements/:id`).

Remove unused imports for `planAuthority`, `planSynthesis`, `autoPlanCreator` and Plan-related types.

### Backend — Delete Files
- `backend/src/planAuthority.ts`
- `backend/src/planSynthesis.ts`
- `backend/src/autoPlanCreator.ts`

### Android — Delete Files
- `android/.../data/PlanRepository.kt`
- `android/.../ui/plan/PlanTypes.kt`
- `android/.../ui/plan/PlanComponents.kt`

### Types Cleanup
Mark existing Plan types in `backend/src/types.ts` with `@deprecated` JSDoc comments. Do not remove yet — minimizes cascade.

## Testing

- Backend compiles with no new errors after cleanup
- Android compiles after deleting Plan files
- `POST /intents` endpoint still works after Plan route removal
- `POST /synthesize` returns real data from jobs/time_entries tables (when records exist)
- Artifact serial numbers persist across server restarts
