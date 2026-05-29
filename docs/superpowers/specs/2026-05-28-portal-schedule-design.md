# Portal — Scheduling UI (Slice 4) — Design

**Date:** 2026-05-28
**Status:** Approved (List + Calendar with tab toggle; Sun-first; stage-colored chips; point events; list as default)
**Parent plan:** `~/.claude/plans/quizzical-napping-balloon.md` (Slice 4 of 5 — the Plans roadmap slice)

---

## 1. Goal

Give the foreman a single surface to see all scheduled jobs and to schedule the unscheduled ones. Two view modes — a vertical date-grouped **List** and a month-grid **Calendar** — toggled via a tab and persisted in `localStorage`. Both consume the existing `jobs.scheduledAt` field; the click-to-reschedule path reuses the existing `EditJobModal`.

## 2. Why now

After Slices 1-3, the foreman can manage clients, advance job stages, and capture materials/expenses — but the only way to see what's on the calendar is to scroll the jobs list and read each `scheduledAt`. The dashboard's `DispatchCard` lists unscheduled jobs read-only. Slice 4 turns "where are my jobs in time" into a first-class view, and makes scheduling an unscheduled job a single click from that view.

Importantly, this is the **lightest** slice in the arc: no backend changes, no migrations, no audit actions. All data already exists; this is pure presentation + interaction.

## 3. Architecture summary

- New route `/console/schedule` (foreman+) registered alongside `/console/clients`, `/console/jobs`, etc.
- Wrapped in `RequireForemanTier` (Slice 1 pattern).
- Mounted as `[Schedule]` in foreman nav group (between `[Jobs]` and `[Clients]`).
- Mounted as `[Schedule]` in `BottomTabBar` foreman group.
- Reads `useJobsStore.jobs` via the existing `useJobsPolling('list')` hook.
- Derives view-ready buckets via a pure hook `useScheduleData(jobs, now)`.
- Two view components: `ScheduleListView`, `ScheduleCalendarView`.
- One shared component: `UnscheduledSection` (used by both views).
- One tab toggle: `ScheduleTabs` — persists choice in `localStorage.smithnet-schedule-view`.
- Click-to-edit via the existing `EditJobModal` instance (mounted at the route level).

## 4. Data shape

### 4.1 `useScheduleData(jobs, now)` -> derived buckets

```typescript
interface ScheduleData {
  unscheduled: Job[];                     // active stage AND scheduledAt === null
  today: Job[];                           // scheduledAt is calendar-today (local TZ)
  tomorrow: Job[];
  thisWeek: Job[];                        // 2..7 days out from today, exclusive
  later: Job[];                           // > 7 days from today
  past: Job[];                            // < today (separate bucket so List view can collapse)
  byMonthKey: Record<string, Job[]>;      // 'YYYY-MM' -> jobs in that month
  byDateKey: Record<string, Job[]>;       // 'YYYY-MM-DD' -> jobs on that date
}
```

- "Active" = `stage !== 'closed'` (so a closed unscheduled job doesn't keep nagging). Same definition the `DispatchCard` uses today (`(j.status === 'planned' || j.status === 'in_progress') && !j.scheduledAt`) — but updated to use `stage` per Slice 2. The plan task will update `DispatchCard` to match.
- All date math uses the **browser's local time zone**. `now` is passed in to keep the hook pure (tests inject a fixed date).
- "Today" means the calendar day of `now`; "tomorrow" the next calendar day; "this week" the 5 calendar days after tomorrow.
- `byDateKey` uses `YYYY-MM-DD` local-TZ strings — same encoding `expenseDate` uses (Slice 3 lesson).

### 4.2 No backend calls beyond the existing `useJobsPolling`

The existing 15-second polling refreshes `useJobsStore` automatically. The schedule views don't need a separate fetch.

## 5. Route + nav

### 5.1 `routes/ScheduleRoute.tsx`

```tsx
export function ScheduleRoute() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const data = useScheduleData(jobs, new Date());
  const [view, setView] = useState<'list' | 'calendar'>(() => readPersistedView());
  const [selectedJob, setSelectedJob] = useState<Job | null>(null);
  const setView$ = (v) => { writePersistedView(v); setView(v); };
  return (
    <div className="font-mono">
      <header className="flex justify-between mb-3">
        <h1 className="text-console-text text-lg">Schedule</h1>
        <ScheduleTabs view={view} onChange={setView$} />
      </header>
      {view === 'list'
        ? <ScheduleListView data={data} onPick={setSelectedJob} />
        : <ScheduleCalendarView data={data} onPick={setSelectedJob} />}
      <EditJobModal
        open={selectedJob !== null}
        job={selectedJob ?? undefined}
        onClose={() => setSelectedJob(null)}
      />
    </div>
  );
}
```

Persisted view helpers live in the same file or in `useScheduleData.ts`:
```typescript
function readPersistedView(): 'list' | 'calendar' {
  try { const v = localStorage.getItem('smithnet-schedule-view'); return v === 'calendar' ? 'calendar' : 'list'; }
  catch { return 'list'; }
}
function writePersistedView(v: 'list' | 'calendar') {
  try { localStorage.setItem('smithnet-schedule-view', v); } catch { /* SSR / private mode */ }
}
```

### 5.2 Route registration

Wherever the route table lives (likely `App.tsx` or `ConsoleShell.tsx`):
```tsx
<Route path="/console/schedule" element={<RequireForemanTier><ScheduleRoute /></RequireForemanTier>} />
```

### 5.3 Nav items

- `AppHeader.tsx` — add `<NavButton to="/console/schedule">[Schedule]</NavButton>` in the foreman nav group between `[Jobs]` and `[Clients]`.
- `BottomTabBar.tsx` — add the same in the foreman group.

## 6. Components

### 6.1 `ScheduleTabs.tsx`

```tsx
export function ScheduleTabs({ view, onChange }: { view: 'list' | 'calendar'; onChange: (v: 'list' | 'calendar') => void }) {
  return (
    <div className="flex gap-1 text-xs">
      <TabBtn active={view === 'list'} onClick={() => onChange('list')}>[LIST]</TabBtn>
      <TabBtn active={view === 'calendar'} onClick={() => onChange('calendar')}>[CALENDAR]</TabBtn>
    </div>
  );
}
```
Tab is selected → `text-console-accent`; unselected → `text-console-text-muted`.

### 6.2 `UnscheduledSection.tsx`

Renders the `data.unscheduled` array. Each row: title + `[+ schedule]` button. Click → calls `onPick(job)`. Empty array → null (caller decides whether to render at all).

Header: `NEEDS SCHEDULING (N)` with a `text-console-warn` count chip.

Shared between List view (top section) and Calendar view (banner above the grid; collapsed by default with an expand toggle when N > 0).

### 6.3 `ScheduleListView.tsx`

Renders, in order, only non-empty sections:

1. `<UnscheduledSection data={data.unscheduled} onPick={onPick} />`
2. **PAST** (collapsed by default with `[show N past]` toggle; jobs whose `scheduledAt` is < today)
3. **TODAY** — sorted ascending by `scheduledAt`; row format: `HH:mm  title  -- client?` (HH:mm in local TZ, 24-hr)
4. **TOMORROW** — same row format
5. **THIS WEEK** — row format: `Ddd DD  HH:mm  title` (e.g. `Thu 28  09:00  Lobby reno`)
6. **LATER** — row format: `MMM D  --  title` (no time; date-precision only, e.g. `Jun 3  --  Walkthrough`)

Click a row -> `onPick(job)`. Section headers in `text-console-text-muted text-xs uppercase tracking-wider`.

Empty schedule (no scheduled, no unscheduled): `No jobs.` text-muted.

### 6.4 `ScheduleCalendarView.tsx`

- **Header bar**: `[<<] [today] [>>]` controls + `MMMM YYYY` label.
- **Day grid**: 7 columns (Sun first), week rows spanning the visible month. The grid includes leading days from the previous month and trailing days from the next month, rendered with `text-console-text-muted` to indicate they're outside the focused month.
- **Day cell**: `<div className="border border-console-border min-h-[80px] p-1 cursor-pointer">`
  - Top: day number; today gets `text-console-accent font-bold`.
  - Body: up to 2 chips per day. Each chip: small block of `<bg color from stage>` with white text, truncated title.
  - `+N more` link below the chips when overflow; click opens a drawer (or simple inline expansion) listing all that day's jobs.
- **Chip color by stage** (mirrors `JobStageBar.kt` semantics):
  - `lead` -> `bg-console-text-muted/50`
  - `proposal` -> `bg-console-accent/60`
  - `approved` -> `bg-console-accent`
  - `in_progress` -> `bg-console-warn`
  - `review` -> `bg-console-accent`
  - `invoice` -> `bg-console-success` (if defined; else `bg-console-accent`)
  - `closed` -> `bg-console-text-muted/30`
- **Click a chip** -> `onPick(job)`.
- **Unscheduled banner** at the top: collapsed by default. `! N jobs not scheduled - [show]`. Expanded → renders `UnscheduledSection`.
- **Mobile responsiveness** (< 480px): chips collapse to colored dots showing count only. Day number remains. Tapping a day cell with chips opens an inline expansion of that day's jobs (no separate drawer needed).

#### 6.4.1 Month navigation state

- `currentMonth` (Date, day = 1, time = 00:00 local).
- `[<<]` -> month - 1.
- `[>>]` -> month + 1.
- `[today]` -> set to current month containing `now`.
- `useScheduleData` filters into the grid via `byDateKey`; the view doesn't need a separate fetch when navigating.

### 6.5 Shared: empty state across both views

If `data` has zero jobs in every bucket (including unscheduled), the route shows a centered message: `No jobs on the schedule. [Create a job]` linking to `/console/jobs`.

## 7. Click-to-edit flow

Single `EditJobModal` instance mounted at the route level. Clicking any job in any view sets the route's `selectedJob` state. The modal's existing inputs handle the rescheduling — no new modal needed.

If the click came from the "Needs scheduling" section, the modal opens with the existing job; the user fills in `scheduledAt`; on save, `useJobsStore.upsertJob()` propagates the change to both views immediately. The unscheduled section disappears (the job moved into a date bucket).

## 8. Tests

### 8.1 Unit (the pure derivation hook)

`hooks/__tests__/useScheduleData.test.ts`:
- Given a fixed `now`, jobs spread across past/today/tomorrow/this-week/later/unscheduled all sort into the right buckets.
- An `unscheduled` job at stage `closed` does NOT appear in `unscheduled`.
- An `unscheduled` job at stage `lead` (or any non-closed stage) DOES appear.
- `byDateKey` / `byMonthKey` use local-TZ `YYYY-MM-DD` keys.

### 8.2 Component

- `ScheduleListView.test.tsx` — given a `data` prop with one job in each bucket, all 5 non-empty sections render with the correct format strings. Empty sections hidden. Click a row -> `onPick` called.
- `ScheduleCalendarView.test.tsx` — given a fixed month, renders the right week rows, prev/next nav advances the month, today button restores. Chip color matches stage. Click chip -> `onPick`.
- `ScheduleTabs.test.tsx` — toggle triggers onChange; localStorage is written and read back.
- `UnscheduledSection.test.tsx` — N jobs render as N rows with `[+ schedule]` buttons; click -> `onPick`.

### 8.3 Route

- `ScheduleRoute.test.tsx` — RequireForemanTier honored (Solo seeded test asserts the locked state, foreman seeded test asserts the schedule renders). Switching view via the tab swaps the rendered component. Clicking a job in either view opens the `EditJobModal`.

### 8.4 Update `DispatchCard` to use `stage` and re-test

`DispatchCard` currently filters on `status` (`planned`/`in_progress`). Update to filter on `stage !== 'closed' && !scheduledAt` for consistency with the new route. Existing `DispatchCard.test.tsx` (if present) must continue to pass after the change. If not present, no new test needed — `DispatchCard` is on the dashboard, indirectly tested by the existing dashboard tests.

## 9. Tier / security / determinism

- **Tier**: `RequireForemanTier` at the route + `requireConsoleTier` already on the backend `/api/jobs` endpoint (no new endpoints).
- **Identity**: `req.user!.id` (server-side; client just reads from `useJobsStore`).
- **No new mutations** — Slice 4 is read + click-into-existing-edit. Audit + validation handled by the existing `PATCH /api/jobs/:id` from prior slices.
- **Determinism**: untouched.
- **No inline LLM, no fire-and-forget** (CLAUDE.md rules 1 and 2 — automatically satisfied since this is a presentation slice).
- **No emoji** (project rule), monospace + console palette (project rule).
- **Mobile-friendly** per `feedback_portal_mobile_friendly` memory.

## 10. Decisions called out

- **No new backend.** `scheduledAt` already exists on every job, settable via the existing `EditJobModal`. Slice 4 wires a new view over existing data.
- **List as the default view.** Most foreman use cases ("what's on the docket this week") are list-shaped; the calendar is a "month-at-a-glance" affordance. `localStorage` remembers their choice across sessions.
- **Stage-aware chip color** on the calendar (NOT status). Stage is the pipeline indicator added in Slice 2; status is the older flag. Reinforces the pipeline language.
- **Sun-first calendar week** (US convention). Project doesn't have a stated week-start preference; pick one for v1 and move on.
- **Point events only** — no job duration / `endsAt` column. Calendar chips render at the start time; no time blocks.
- **`localStorage` for tab persistence**. Single-foreman tool; no need to scope per-tier.
- **`DispatchCard` filter updated to use `stage`** — small consistency improvement landed alongside the new route.

## 11. Out of scope (explicit)

- Drag-and-drop reschedule on the calendar
- Job duration / time blocks (no `endsAt` column today; deferred)
- Multi-foreman / crew schedule view (single-tenant for v1)
- Recurring jobs / scheduling templates
- iCal / Google Calendar export
- Conflict detection ("you have two jobs scheduled at 2pm")
- Calendar week-view or day-view (month + list cover the common needs)
- Stage-transition shortcuts from the schedule (advance a job's stage from the chip context menu)

## 12. Acceptance

- Portal: full suite green; tsc + build clean. Expect ~+15 tests across 6 new files.
- Live (foreman demo): visit `/console/schedule` -> see a list with `NEEDS SCHEDULING (N)`, `TODAY`, `THIS WEEK`, etc. Click an unscheduled job -> `EditJobModal` opens -> set `scheduledAt` to today -> save -> the row moves into `TODAY`. Switch to `[CALENDAR]` -> see the same job as a chip on today's cell. Refresh -> view stays on calendar (localStorage). Click `[<<]` -> previous month renders without errors. Click `[today]` -> snaps back. Verify on mobile width (375px) -> chips collapse to dots; day cell tap expands inline list.

---

## Self-review

- [x] **Spec coverage** vs the parent plan's Slice 4 bullet ("Portal: a scheduling/calendar surface over `jobs.scheduledAt` (a calendar or schedule list + reschedule); the `DispatchCard` 'needs scheduling' items become actionable; lighter than the full Intent/proposal flow."): list + calendar (§6.3, §6.4); reschedule via EditJobModal (§7); DispatchCard updated (§8.4); no Intent compiler (out of scope §11).
- [x] **Placeholder scan**: no TBD/TODO. Every section, component, and test is specified concretely.
- [x] **Type consistency**: `ScheduleData` interface field names, `ScheduleListView` / `ScheduleCalendarView`, `useScheduleData` — consistent across §4, §6, §8.
- [x] **Internal consistency**: §6.4's chip color palette matches §10's "stage-aware" decision; §7's click-to-edit matches §5.1's modal mount; §8.4 explicitly handles the `DispatchCard` update mentioned in §3 and §10.
- [x] **Scope check**: single subsystem (a schedule view over existing data), no backend changes, two presentational components plus a tab toggle and a hook. The two-view choice is the only thing that doubles the component count; the data layer is shared.
