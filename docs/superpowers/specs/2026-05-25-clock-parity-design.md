# Clock Parity (portal mirrors the APK time clock) -- Design

> Status: design approved 2026-05-25.

**Goal:** Bring the portal's clock up to parity with the Android APK time clock: a
dedicated `/console/time` screen with the live timer, an 8-hour daily-summary bar,
and a TODAY'S ENTRIES log; clock-in/out **dialogs** (entry type + job tag on
clock-in; reason on clock-out); and a richer header. Available to all tiers.

---

## 1. Context

The portal clock today is a header-only widget (`desktop/portal/src/console/
components/header/ShiftClock.tsx` + `useShiftToggle.ts` + `useDayShiftTotal.ts`):
a single context button that silently toggles a bare shift (`POST /api/shifts/start`
/ `end`), a live elapsed timer on-clock, and a day-total number off-clock. The
backend `shifts` table is minimal (`backend/src/crewPositionService.ts`,
`shiftsRoutes.ts`): `id, user_id, started_at, ended_at, source`.

The APK clock (the reference) is far richer -- `android/app/src/main/java/com/
guildofsmiths/trademesh/ui/timetracking/TimeTrackingScreen.kt` +
`TimeTrackingViewModel.kt` + `TimeTrackingTypes.kt`:
- A **clock-in dialog**: pick an **entry type** (REGULAR / OVERTIME / BREAK /
  TRAVEL / ON_CALL), then optionally **tag a job** -- from the job board OR a
  free-text job name.
- A **clock-out dialog**: pick a **reason** (LUNCH / JOB_DONE / END_DAY / BREAK /
  OTHER + custom text), stored as a note.
- A **daily summary bar**: an 8-hour visual (`[blocks] HH:MM / 8:00`) at half-hour
  resolution; time past 8h spills over in red; recomputed live each second.
- A **TODAY'S ENTRIES list**: every entry today with time range, type, job tag,
  clock-out reason, and duration (`HH:mm - HH:mm` / `HH:mm - NOW`), live-ticking.
- A live header timer with "Started 09:15 - REGULAR @ Job".

This design mirrors that in the portal, scoped to what the web platform and the
locked tier rules allow.

**Decisions locked in brainstorming (2026-05-25):**
- Clock UI home = a **dedicated `/console/time` screen** (closest APK mirror).
- The entry-type/job selection appears **only when the user clicks the clock-in
  box** (a dialog on click), not as always-visible chrome. Same for clock-out
  (reason dialog). The timer still starts **optimistically** after the dialog is
  confirmed (the behavior the user asked for earlier).
- **Job tagging "just like the APK"** = free-text for everyone, PLUS a job-board
  picker for tiers that can fetch the board (foreman/enterprise).
- `/console/time` is available to **all tiers** (clock-in/out is basic presence;
  not foreman-gated like jobs/crew).
- **All 5 entry types** and the full clock-out reason set.
- The entries log is **read-only in v1** -- editing/deleting time entries is
  Enterprise per the tier scope ([[project_smithai_tier_scope]]).
- Backend: **extend the existing `shifts` table** (the day-total already reads it),
  not a parallel table.

---

## 2. Scope

### In scope (v1)
- `shifts` model extension: `entry_type`, `job_id`, `job_title`, `clock_out_reason`.
- `POST /api/shifts/start` + `/end` accept the new fields (zod-validated); `GET
  /api/shifts/today` + `/current` serialize them.
- A `/console/time` route + screen: live timer + entry/job detail, the 8-hour
  daily-summary bar, and the read-only TODAY'S ENTRIES log. All tiers.
- A **ClockInDialog** (entry type + job tag: board picker when available + free
  text) and a **ClockOutDialog** (reason set + custom for OTHER).
- Header: show `ENTRY_TYPE @ Job` while on-clock; clock-in/out box opens the
  dialogs.

### Out of scope (v1)
- Edit / delete / swipe-to-delete entries (Enterprise; [[project_smithai_tier_scope]]).
- GPS / geofence / beacon source tagging (no web equivalent in v1).
- Weekly summary + archive/history screen (APK has these; defer).
- AISupervisor daily-log hook on clock-out (Phase-5; [[project_smithai_tier_scope]]).
- Break auto-tracking / on-call escalation logic.
- Mesh / offline-queue for shifts (online-only in v1).

---

## 3. Data model -- `backend/migrations/028_shifts_time_entry_fields.sql`

Idempotent, house style (`ADD COLUMN IF NOT EXISTS`, `DO $$ ... END $$` for the
CHECK). All columns nullable/defaulted so existing rows and the current bare
clock-in keep working:
```sql
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS entry_type       TEXT NOT NULL DEFAULT 'regular';
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_id           UUID;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_title        TEXT;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS clock_out_reason TEXT;

-- job_id references the job board when picked from it; NULL for free-text-only
-- tags. ON DELETE SET NULL so deleting a job never orphans/breaks a shift row.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE constraint_name = 'shifts_job_id_fkey') THEN
    ALTER TABLE shifts
      ADD CONSTRAINT shifts_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.constraint_column_usage
                 WHERE constraint_name = 'shifts_entry_type_check') THEN
    ALTER TABLE shifts ADD CONSTRAINT shifts_entry_type_check
      CHECK (entry_type IN ('regular','overtime','break','travel','on_call'));
  END IF;
END $$;
```
Lowercase enum values match the existing `source` convention. Applied manually:
`cd backend && psql "$DATABASE_URL" -f migrations/028_shifts_time_entry_fields.sql`.

---

## 4. Backend -- service + endpoints

`crewPositionService` (`backend/src/crewPositionService.ts`):
- `Shift` interface gains `entry_type: string; job_id: string | null; job_title: string | null; clock_out_reason: string | null`.
- `startShift(userId, source, opts?: { entryType?: string; jobId?: string; jobTitle?: string })` -- INSERT the new columns (entry_type defaults to `'regular'`). The existing partial unique index (one open shift) still raises 23505 -> 409.
- `endShift(userId, reason?: string)` -- set `clock_out_reason = $reason` alongside `ended_at = NOW()`.
- `getShiftsSince` / `getCurrentShift` SELECT the new columns.

`shiftsRoutes.ts` (all `authenticateToken`-only, scoped to `req.user!.id`; add zod
`validateBody` per the security rule):
- `POST /api/shifts/start` body `{ source?, entryType?, jobId?, jobTitle? }` --
  `StartShiftBody` zod schema `.strict()`: `entryType` in the 5 values (default
  `'regular'`), `jobId` optional uuid, `jobTitle` optional `string().max(200)`,
  `source` in the existing set.
- `POST /api/shifts/end` body `{ reason? }` -- `EndShiftBody` `.strict()`,
  `reason` optional `string().max(500)`.
- `serializeShift` adds `entryType, jobId, jobTitle, clockOutReason` (camelCase).

---

## 5. Frontend

### Route + screen
- `desktop/portal/src/App.tsx` -- add `<Route path="time" element={<TimeRoute />} />`
  under `/console` (RequireAuth only -- **NOT** `RequireForemanTier`).
- `console/routes/TimeRoute.tsx` -> renders `<TimeScreen />`.
- `console/components/time/TimeScreen.tsx` -- the mirror:
```
TIME CLOCK
  00:42:17   ON CLOCK   REGULAR  @ Kitchen Reno
  [ clock out ]
  [########--------]  03:30 / 8:00      (slots turn red past 8:00)
  TODAY'S ENTRIES
   09:15 - 10:30  REGULAR  @Kitchen   1:15
   10:45 -  NOW   REGULAR  @Kitchen   0:42   (NOW in green, live)
```
  Live-ticks each second while on-clock (timer + daily bar + the active entry's
  running duration). Console/monospace aesthetic, no emoji.

### Components
- `console/components/time/ClockInDialog.tsx` -- modal: step 1 entry type (5
  options); step 2 optional job tag = a **board picker** (list from `jobsClient`
  when reachable) + an "or enter job name" free-text field. Confirm -> calls the
  optimistic start with `{ entryType, jobId?, jobTitle? }`. Cancel closes.
- `console/components/time/ClockOutDialog.tsx` -- modal: reason (LUNCH / JOB_DONE /
  END_DAY / BREAK / OTHER); OTHER reveals a "specify reason" text field. Confirm ->
  optimistic end with `{ reason }`.
- `console/components/time/DailySummaryBar.tsx` -- 16 half-hour slots (8h); each
  slot full / half / empty by minutes worked; slots past 8h render in the
  console "warn/error" color (overtime). Pure function of today's entries +
  (on-clock) the live current elapsed; tested in isolation.
- `console/components/time/TodayEntriesList.tsx` -- read-only list of today's
  shifts: `HH:mm - HH:mm` (or `HH:mm - NOW`), entry type, `@job`, `- reason`,
  duration `H:MM`. No delete in v1.

### Data layer
- Extend the shifts client used by `useShiftToggle` (`console/components/header/
  useShiftToggle.ts` -> its `POST /start|/end` caller): `start(opts)` /
  `end(reason?)`.
- `useShiftToggle` carries the optimistic flow: dialog confirm -> `setLocal`
  optimistic (now incl. entryType/job) -> POST -> refresh on success / rollback +
  toast on failure (the existing pattern, extended).
- `console/components/time/useTodayEntries.ts` -- `GET /api/shifts/today` mapped to
  the new serialized shape (mirrors `useDayShiftTotal`'s fetch; the screen needs
  the full rows, not just the summed seconds).

### Header
- `ShiftClock.tsx` -- on-clock, show `ENTRY_TYPE @ Job` next to the timer
  (from `useCurrentShift`, which gains the new fields). The clock-in box opens
  `ClockInDialog`; the clock-out box opens `ClockOutDialog`. Off-clock day-total
  mirror behavior unchanged.

---

## 6. Tier behavior

- `/console/time` is reachable by every authenticated user (RequireAuth only).
- The `ClockInDialog` job-**board picker** depends on `GET /api/jobs`, which is
  foreman-gated. Solo gets a 403 there -> the dialog **hides the picker and shows
  only the free-text field** (graceful, no error surfaced). Foreman/enterprise get
  picker + free-text. (`jobTitle` always stored; `job_id` only when board-picked.)
- The entries log is **read-only** for all tiers in v1; edit/delete is a future
  Enterprise slice.

---

## 7. Determinism / security / isolation

- Shifts are presence data, not ledger/Intent artifacts -- the determinism
  pipeline does not apply. No clock reads inside any sealing path.
- `validateBody` zod (`.strict()`) on both POSTs (security rule); identity from
  `req.user!.id` only (no `X-User-Id`); parameterized pg queries.
- Per-profile isolation: every shift read/write stays scoped to `req.user!.id`
  (unchanged). `job_id` FK is `ON DELETE SET NULL` so a deleted job can't break or
  leak across a shift row.
- No emoji (ASCII / console glyphs only).

---

## 8. Decomposition (build order -- one spec, subagent-driven like N-1)

```
T1  Backend: migration 028 + service + start/end params + serializer (+ tests)
T2  Clock-in/out dialog components + optimistic wiring through useShiftToggle (+ tests)
T3  /console/time route + screen + TodayEntriesList (read-only) + useTodayEntries (+ tests, all-tier)
T4  DailySummaryBar (8h, half-hour slots, overtime) component (+ tests)
T5  Header integration: entry-type/job display + dialogs from the header box
```

---

## 9. Testing / acceptance

- **Backend**: start with `entryType/jobId/jobTitle` persists + serializes
  (camelCase); end with `reason` persists; invalid `entryType` -> 400; a second
  open shift -> 409; isolation (another user can't read/affect). (jest, DB-gated.)
- **Dialogs**: ClockInDialog renders 5 types; shows free-text always and the board
  picker only when jobs are available; confirm calls start with the right args.
  ClockOutDialog reveals the custom field for OTHER; confirm passes the reason.
- **DailySummaryBar**: pure-function tests -- 0h all-empty; 3.5h half-filled
  boundary; >8h overtime slots flagged. (vitest.)
- **TimeScreen / TodayEntriesList**: renders timer + entries (NOW for the active
  one); reachable by a solo user (not redirected).
- **Gates**: portal `npm run test:run` + `tsc` + build green; backend `tsc` +
  `jest` green.

---

## 10. Open questions

None. Clock UI home (dedicated `/console/time`), the dialog-on-click interaction,
job tagging (free-text + tier-aware board picker), all-tier access, the 5 entry
types + reason set, read-only log (edit/delete = Enterprise), and the
extend-`shifts` backend approach are all decided above.
