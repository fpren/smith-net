# Clock Parity Implementation Plan

> **Status: BUILT (2026-05-25), with deviations.** Tasks 1-4 below shipped as
> written. Task 5 (header opens dialogs) was built then **reverted** -- the header
> keeps its instant on/off switch instead. Added beyond this plan: a dedicated
> `[Clock]` tab + framed `TimeScreen` container; clock-OUT is instant (ClockOutDialog
> removed); and an all-tier **clock-scoped job + task picker** (`GET /api/shifts/jobs`
> + `/jobs/:id/tasks`, `shifts.task_id/task_title` via migration 029). See the
> design doc's "As-built amendments" for the full picture and commit list.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mirror the APK time clock in the portal: extend the `shifts` model with entry type / job / reason, add clock-in/out dialogs, a dedicated `/console/time` screen with an 8-hour daily-summary bar and a read-only today's-entries log, and a richer header.

**Architecture:** Extend the existing `shifts` table + `crewPositionService` + `shiftsRoutes` (zod-validated). The portal `useShiftToggle` hook gains `clockIn(opts)` / `clockOut(reason)` driven by `ClockInDialog` / `ClockOutDialog`; a new `/console/time` route (all-tier) renders the timer, `DailySummaryBar`, and `TodayEntriesList` from `useTodayEntries`. Optimistic clock-in/out preserved.

**Tech Stack:** Backend Node/Express + pg + **Jest** (DB-gated via `isPgEnabled()`); portal Vite + React 18 + TS + zustand + **Vitest**/jsdom + @testing-library/react.

> **Runner note (verified):** backend = `npx jest <path>` with `DATABASE_URL` exported (dev: `postgresql://localhost/smithnet`; `psql` at `/opt/homebrew/Cellar/postgresql@17/17.7_1/bin/psql`); ambient jest globals (`jest.fn`/`jest.spyOn`, no vitest import). Portal = `npx vitest run <path>`, `import { vi } from 'vitest'`. After backend changes, the live `:3030` dev server needs a restart to pick them up (ts-node-dev serves stale code — see [[project_portal_dev_backend_port]]); tests don't.

**Spec:** `docs/superpowers/specs/2026-05-25-clock-parity-design.md`

---

## File Structure

**Backend**
- Create: `backend/migrations/028_shifts_time_entry_fields.sql`
- Create: `backend/src/schemas/shifts.ts` (zod `StartShiftBody` / `EndShiftBody`)
- Modify: `backend/src/crewPositionService.ts` (Shift fields + start/end signatures + selects)
- Modify: `backend/src/shiftsRoutes.ts` (validateBody + pass opts + serialize new fields)
- Modify: `backend/src/__tests__/shifts-routes.test.ts` (new field + validation tests)

**Portal**
- Modify: `desktop/portal/src/console/api/presenceClient.ts` (start opts, end reason, current + today full fields)
- Modify: `desktop/portal/src/console/hooks/useCurrentShift.ts` (entryType + jobTitle in snapshot)
- Modify: `desktop/portal/src/console/components/header/useShiftToggle.ts` (`clockIn`/`clockOut`)
- Modify: `desktop/portal/src/console/components/header/ClockButton.tsx` (use clockIn/clockOut)
- Modify: `desktop/portal/src/console/components/header/ShiftClock.tsx` (dialogs + entry/job display)
- Create: `desktop/portal/src/console/components/time/ClockInDialog.tsx`
- Create: `desktop/portal/src/console/components/time/ClockOutDialog.tsx`
- Create: `desktop/portal/src/console/components/time/DailySummaryBar.tsx`
- Create: `desktop/portal/src/console/components/time/dailySummary.ts` (pure slot math)
- Create: `desktop/portal/src/console/components/time/TodayEntriesList.tsx`
- Create: `desktop/portal/src/console/components/time/useTodayEntries.ts`
- Create: `desktop/portal/src/console/components/time/TimeScreen.tsx`
- Create: `desktop/portal/src/console/routes/TimeRoute.tsx`
- Modify: `desktop/portal/src/App.tsx` (one route, RequireAuth only)
- Tests: `__tests__/ClockInDialog.test.tsx`, `ClockOutDialog.test.tsx`, `dailySummary.test.ts`, `TodayEntriesList.test.tsx`, `useTodayEntries.test.ts`; update `header/__tests__/ShiftClock.test.tsx`

---

## Task 1: Backend model + endpoints

**Files:** Create `028_shifts_time_entry_fields.sql`, `schemas/shifts.ts`; Modify `crewPositionService.ts`, `shiftsRoutes.ts`; Test `__tests__/shifts-routes.test.ts`.

- [ ] **Step 1: Write the migration**

`backend/migrations/028_shifts_time_entry_fields.sql`:

```sql
-- Clock parity: enrich shifts to mirror the APK TimeEntry. All nullable/defaulted
-- so existing rows and the current bare clock-in keep working.
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS entry_type       TEXT NOT NULL DEFAULT 'regular';
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_id           UUID;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_title        TEXT;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS clock_out_reason TEXT;

-- job_id references the board when picked from it; NULL for free-text-only tags.
-- ON DELETE SET NULL so deleting a job never orphans/breaks a shift row.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE constraint_name = 'shifts_job_id_fkey') THEN
    ALTER TABLE shifts
      ADD CONSTRAINT shifts_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'shifts_entry_type_check') THEN
    ALTER TABLE shifts ADD CONSTRAINT shifts_entry_type_check
      CHECK (entry_type IN ('regular','overtime','break','travel','on_call'));
  END IF;
END $$;
```

- [ ] **Step 2: Apply the migration**

Run: `cd backend && psql "$DATABASE_URL" -f migrations/028_shifts_time_entry_fields.sql`
Expected: `ALTER TABLE` x4 + two `DO` blocks succeed (idempotent on re-run).

- [ ] **Step 3: Write the zod schemas**

`backend/src/schemas/shifts.ts`:

```ts
/**
 * Clock parity: zod schemas for /api/shifts/{start,end}. .strict() rejects
 * unknown fields (mass-assignment defense).
 */
import { z } from 'zod';

export const ENTRY_TYPES = ['regular', 'overtime', 'break', 'travel', 'on_call'] as const;

export const StartShiftBody = z
  .object({
    source: z.enum(['android', 'web', 'admin']).optional(),
    entryType: z.enum(ENTRY_TYPES).optional(),
    jobId: z.string().uuid().optional(),
    jobTitle: z.string().trim().min(1).max(200).optional(),
  })
  .strict();
export type StartShiftBody = z.infer<typeof StartShiftBody>;

export const EndShiftBody = z
  .object({
    reason: z.string().trim().min(1).max(500).optional(),
  })
  .strict();
export type EndShiftBody = z.infer<typeof EndShiftBody>;
```

- [ ] **Step 4: Write the failing route tests**

Append to `backend/src/__tests__/shifts-routes.test.ts` (reuse its existing harness: `buildApp`, `makeUserWithToken`/equivalent, `request`, `pg`, `describeDb`). If those helpers differ, mirror `notifications-routes.test.ts`. Add this describe block:

```ts
describeDb('shifts time-entry fields', () => {
  it('start persists + serializes entryType/jobTitle (camelCase)', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st1');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ source: 'web', entryType: 'overtime', jobTitle: 'Kitchen Reno' });
    expect(res.status).toBe(200);
    expect(res.body.shift.entryType).toBe('overtime');
    expect(res.body.shift.jobTitle).toBe('Kitchen Reno');
    expect(res.body.shift.jobId).toBeNull();
    expect(res.body.shift.clockOutReason).toBeNull();
  });

  it('start defaults entryType to regular when omitted', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st2');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({});
    expect(res.status).toBe(200);
    expect(res.body.shift.entryType).toBe('regular');
  });

  it('rejects an invalid entryType with 400', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st3');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ entryType: 'napping' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('end persists the clock-out reason', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st4');
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    const res = await request(app)
      .post('/api/shifts/end')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ reason: 'lunch' });
    expect(res.status).toBe(200);
    expect(res.body.shift.clockOutReason).toBe('lunch');
    expect(res.body.shift.endedAt).toBeTruthy();
  });

  it('a second open shift still 409s', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st5');
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    const res = await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    expect(res.status).toBe(409);
  });
});
```

> Note: if `shifts-routes.test.ts` lacks a `makeUserWithToken(role, suffix)` helper, add one matching `notifications-routes.test.ts` (createUserAndProfile + generateTokens) and a `buildApp` mounting `authRouter` + `shiftsRouter` at `/api/auth` + `/api/shifts`.

Run (RED): `cd backend && npx jest src/__tests__/shifts-routes.test.ts`
Expected: the new block fails (fields undefined / invalid type not 400 yet).

- [ ] **Step 5: Extend the service**

In `backend/src/crewPositionService.ts`, extend the `Shift` interface:

```ts
export interface Shift {
  id: string;
  user_id: string;
  started_at: Date;
  ended_at: Date | null;
  source: 'android' | 'web' | 'admin';
  entry_type: string;
  job_id: string | null;
  job_title: string | null;
  clock_out_reason: string | null;
}
```

Replace `startShift`, `endShift`, `getCurrentShift`, and `getShiftsSince` with versions that read/write the new columns:

```ts
  async startShift(
    userId: string,
    source: Shift['source'],
    opts: { entryType?: string; jobId?: string; jobTitle?: string } = {}
  ): Promise<Shift> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `INSERT INTO shifts (user_id, source, entry_type, job_id, job_title)
       VALUES ($1, $2, COALESCE($3, 'regular'), $4, $5)
       RETURNING id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, clock_out_reason`,
      [userId, source, opts.entryType ?? null, opts.jobId ?? null, opts.jobTitle ?? null]
    );
    return r.rows[0];
  }

  async endShift(userId: string, reason?: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `UPDATE shifts
          SET ended_at = NOW(), clock_out_reason = $2
        WHERE user_id = $1 AND ended_at IS NULL
        RETURNING id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, clock_out_reason`,
      [userId, reason ?? null]
    );
    return r.rows[0] ?? null;
  }

  async getCurrentShift(userId: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `SELECT id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, clock_out_reason
         FROM shifts
        WHERE user_id = $1 AND ended_at IS NULL
        LIMIT 1`,
      [userId]
    );
    return r.rows[0] ?? null;
  }

  async getShiftsSince(userId: string, sinceMs: number): Promise<Shift[]> {
    const res = await requirePg().query<Shift>(
      `SELECT id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, clock_out_reason
         FROM shifts
        WHERE user_id = $1 AND (ended_at IS NULL OR ended_at >= to_timestamp($2 / 1000.0))
        ORDER BY started_at ASC`,
      [userId, sinceMs]
    );
    return res.rows;
  }
```

- [ ] **Step 6: Wire validation + serialization in the routes**

In `backend/src/shiftsRoutes.ts`: add imports

```ts
import { validateBody } from './middleware/validate';
import { StartShiftBody, EndShiftBody } from './schemas/shifts';
```

Replace `serializeShift` to include the new fields:

```ts
function serializeShift(s: Shift) {
  return {
    id: s.id,
    userId: s.user_id,
    startedAt: s.started_at,
    endedAt: s.ended_at,
    source: s.source,
    entryType: s.entry_type,
    jobId: s.job_id,
    jobTitle: s.job_title,
    clockOutReason: s.clock_out_reason,
  };
}
```

Replace the `/start` handler (drop the manual `VALID_SOURCES` check — the zod enum covers it; add `validateBody`; handle the FK violation defensively):

```ts
shiftsRouter.post('/start', authenticateToken, validateBody(StartShiftBody), async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const { source = 'web', entryType, jobId, jobTitle } = req.body as StartShiftBody;
  try {
    const shift = await crewPositionService.startShift(userId, source, { entryType, jobId, jobTitle });
    await auditLog.log(AuditAction.SHIFT_STARTED, userId, { shift_id: shift.id, source, entry_type: shift.entry_type });
    requestLogger().info({ event: 'shift_started', userId, source, shiftId: shift.id }, 'shift started');
    return res.status(200).json({ shift: serializeShift(shift) });
  } catch (err) {
    const code = (err as { code?: string }).code;
    if (code === '23505') return res.status(409).json({ error: 'shift already open' });
    if (code === '23503') return res.status(400).json({ error: 'invalid jobId' });
    throw err;
  }
});
```

Replace the `/end` handler:

```ts
shiftsRouter.post('/end', authenticateToken, validateBody(EndShiftBody), async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const { reason } = req.body as EndShiftBody;
  const shift = await crewPositionService.endShift(userId, reason);
  if (!shift) {
    return res.status(404).json({ error: 'no open shift' });
  }
  await auditLog.log(AuditAction.SHIFT_ENDED, userId, { shift_id: shift.id });
  requestLogger().info({ event: 'shift_ended', userId, shiftId: shift.id }, 'shift ended');
  return res.status(200).json({ shift: serializeShift(shift) });
});
```

(The `VALID_SOURCES` const and the old inline source check can be removed.)

- [ ] **Step 7: Run tests + tsc (GREEN)**

Run: `cd backend && npx jest src/__tests__/shifts-routes.test.ts && npx tsc --noEmit`
Expected: all shift tests pass (incl. the new block); tsc clean.

- [ ] **Step 8: Commit**

```bash
git add backend/migrations/028_shifts_time_entry_fields.sql backend/src/schemas/shifts.ts backend/src/crewPositionService.ts backend/src/shiftsRoutes.ts backend/src/__tests__/shifts-routes.test.ts
git commit -m "$(cat <<'EOF'
feat(clock): shifts model + endpoints gain entry type / job / clock-out reason

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Dialogs + optimistic wiring

**Files:** Modify `presenceClient.ts`, `useCurrentShift.ts`, `useShiftToggle.ts`, `ClockButton.tsx`; Create `time/ClockInDialog.tsx`, `time/ClockOutDialog.tsx` + tests.

- [ ] **Step 1: Extend the presence client**

In `desktop/portal/src/console/api/presenceClient.ts`, add the shared row type and update `startShift`, `getCurrentShift`, `getTodayShifts`:

```ts
export interface TimeEntryRow {
  id: string;
  startedAt: string | null;
  endedAt: string | null;
  source: string;
  entryType: string;
  jobId: string | null;
  jobTitle: string | null;
  clockOutReason: string | null;
}
```

```ts
  startShift: async (
    source: string,
    opts: { entryType?: string; jobId?: string; jobTitle?: string } = {},
  ): Promise<PresenceResult<{ shiftId: string }>> => {
    const res = await fetch('/api/shifts/start', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source, ...opts }),
    });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    const data = await res.json();
    return { ok: true, shiftId: data.shift.id };
  },

  endShift: async (reason?: string): Promise<PresenceResult<{}>> => {
    const res = await fetch('/api/shifts/end', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reason ? { reason } : {}),
    });
    if (res.status === 404) return { ok: true };
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    return { ok: true };
  },
```

Update `getCurrentShift` to also return `entryType` + `jobTitle`:

```ts
  getCurrentShift: async (): Promise<PresenceResult<{ shiftId: string | null; startedAt: string | null; entryType: string | null; jobTitle: string | null }>> => {
    const res = await fetch('/api/shifts/current', { credentials: 'include' });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    const data = await res.json();
    return {
      ok: true,
      shiftId: data.shift?.id ?? null,
      startedAt: data.shift?.startedAt ?? null,
      entryType: data.shift?.entryType ?? null,
      jobTitle: data.shift?.jobTitle ?? null,
    };
  },
```

Update `getTodayShifts` to return full `TimeEntryRow[]` (the day-total only reads startedAt/endedAt, so it stays compatible):

```ts
  getTodayShifts: async (
    sinceMs: number,
  ): Promise<PresenceResult<{ shifts: TimeEntryRow[] }>> => {
    const res = await fetch(`/api/shifts/today?since=${sinceMs}`, { credentials: 'include' });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    const data = await res.json();
    return {
      ok: true,
      shifts: (data.shifts ?? []).map((s: Partial<TimeEntryRow>) => ({
        id: s.id ?? '',
        startedAt: s.startedAt ?? null,
        endedAt: s.endedAt ?? null,
        source: s.source ?? 'web',
        entryType: s.entryType ?? 'regular',
        jobId: s.jobId ?? null,
        jobTitle: s.jobTitle ?? null,
        clockOutReason: s.clockOutReason ?? null,
      })),
    };
  },
```

- [ ] **Step 2: Extend `useCurrentShift` snapshot**

In `desktop/portal/src/console/hooks/useCurrentShift.ts`, add `entryType` + `jobTitle` to `ShiftSnapshot`, the initial state, and the `refresh` mapping:

```ts
interface ShiftSnapshot {
  shiftId: string | null;
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
}
```
Initial state adds `entryType: null, jobTitle: null`. In `refresh`:
```ts
    if (r.ok) {
      setState({
        shiftId: r.shiftId,
        onClock: r.shiftId !== null,
        startedAt: r.startedAt,
        entryType: r.entryType,
        jobTitle: r.jobTitle,
      });
    }
```

- [ ] **Step 3: Refactor `useShiftToggle` to `clockIn` / `clockOut`**

Replace `desktop/portal/src/console/components/header/useShiftToggle.ts`:

```ts
// desktop/portal/src/console/components/header/useShiftToggle.ts
//
// Shared clock-in/out logic. Optimistic (APK-style): the new shift state is
// applied locally the instant the dialog is confirmed, then the server round-trip
// reconciles on success or rolls back (with a toast) on failure. ClockInDialog /
// ClockOutDialog drive these; ShiftClock + TimeScreen render from one instance.
import { useState } from 'react';
import { presenceClient } from '../../api/presenceClient';
import { useCurrentShift } from '../../hooks/useCurrentShift';
import { useToastStore } from '../../stores/toastStore';

export interface ClockInOpts {
  entryType: string;
  jobId?: string;
  jobTitle?: string;
}

export interface ShiftToggle {
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
  busy: boolean;
  clockIn: (opts: ClockInOpts) => Promise<void>;
  clockOut: (reason?: string) => Promise<void>;
}

export function useShiftToggle(): ShiftToggle {
  const { shiftId, onClock, startedAt, entryType, jobTitle, refresh, setLocal } = useCurrentShift();
  const [busy, setBusy] = useState(false);
  const pushToast = useToastStore((s) => s.push);

  async function clockIn(opts: ClockInOpts) {
    if (busy || onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle };
    setBusy(true);
    setLocal({
      shiftId: null,
      onClock: true,
      startedAt: new Date().toISOString(),
      entryType: opts.entryType,
      jobTitle: opts.jobTitle ?? null,
    });
    const result = await presenceClient.startShift('web', opts);
    if (result.ok) await refresh();
    else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock in failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  async function clockOut(reason?: string) {
    if (busy || !onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle };
    setBusy(true);
    setLocal({ shiftId: null, onClock: false, startedAt: null, entryType: null, jobTitle: null });
    const result = await presenceClient.endShift(reason);
    if (result.ok) await refresh();
    else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock out failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  return { onClock, startedAt, entryType, jobTitle, busy, clockIn, clockOut };
}
```

- [ ] **Step 4: Update `ClockButton` to the new API**

`desktop/portal/src/console/components/header/ClockButton.tsx` currently calls `toggle()`. Change its click handler to use the new methods (quick default REGULAR, no dialog — this is the dashboard quick button):

```tsx
const { onClock, busy, clockIn, clockOut } = useShiftToggle();
// ...onClick:
onClick={() => (onClock ? clockOut() : clockIn({ entryType: 'regular' }))}
```
(Keep the rest of the component as-is. Grep the file for `toggle` and replace the single call site.)

- [ ] **Step 5: Write the failing dialog tests**

`desktop/portal/src/console/components/time/__tests__/ClockInDialog.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ClockInDialog } from '../ClockInDialog';

// jobsClient.list is called to populate the optional board picker; default to a
// 403 (solo) so only the free-text path shows unless a test overrides it.
vi.mock('../../../api/jobsClient', () => ({
  jobsClient: { list: vi.fn().mockResolvedValue({ ok: false, status: 403, error: 'tier' }) },
}));

describe('ClockInDialog', () => {
  it('renders all 5 entry types and a free-text job field', async () => {
    render(<ClockInDialog open onClose={() => {}} onConfirm={() => {}} />);
    for (const t of ['Regular', 'Overtime', 'Break', 'Travel', 'On call']) {
      expect(screen.getByText(t)).toBeInTheDocument();
    }
    expect(screen.getByPlaceholderText(/job name/i)).toBeInTheDocument();
  });

  it('confirms with the selected entry type + free-text job title', () => {
    const onConfirm = vi.fn();
    render(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Overtime'));
    fireEvent.change(screen.getByPlaceholderText(/job name/i), { target: { value: 'Kitchen Reno' } });
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(onConfirm).toHaveBeenCalledWith({ entryType: 'overtime', jobTitle: 'Kitchen Reno' });
  });
});
```

`desktop/portal/src/console/components/time/__tests__/ClockOutDialog.test.tsx`:

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ClockOutDialog } from '../ClockOutDialog';

describe('ClockOutDialog', () => {
  it('reveals a custom field only when OTHER is selected and passes the custom reason', () => {
    const onConfirm = vi.fn();
    render(<ClockOutDialog open onClose={() => {}} onConfirm={onConfirm} />);
    expect(screen.queryByPlaceholderText(/specify/i)).not.toBeInTheDocument();
    fireEvent.click(screen.getByText('Other'));
    const custom = screen.getByPlaceholderText(/specify/i);
    fireEvent.change(custom, { target: { value: 'left site' } });
    fireEvent.click(screen.getByRole('button', { name: /clock out/i }));
    expect(onConfirm).toHaveBeenCalledWith('left site');
  });

  it('passes a preset reason label when a non-OTHER reason is chosen', () => {
    const onConfirm = vi.fn();
    render(<ClockOutDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Lunch Break'));
    fireEvent.click(screen.getByRole('button', { name: /clock out/i }));
    expect(onConfirm).toHaveBeenCalledWith('lunch');
  });
});
```

Run (RED): `cd desktop/portal && npx vitest run src/console/components/time/__tests__/ClockInDialog.test.tsx src/console/components/time/__tests__/ClockOutDialog.test.tsx`
Expected: fail (modules missing).

- [ ] **Step 6: Write `ClockInDialog`**

`desktop/portal/src/console/components/time/ClockInDialog.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { jobsClient, type Job } from '../../api/jobsClient';
import type { ClockInOpts } from '../header/useShiftToggle';

const ENTRY_TYPES: { value: string; label: string }[] = [
  { value: 'regular', label: 'Regular' },
  { value: 'overtime', label: 'Overtime' },
  { value: 'break', label: 'Break' },
  { value: 'travel', label: 'Travel' },
  { value: 'on_call', label: 'On call' },
];

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: (opts: ClockInOpts) => void;
}

export function ClockInDialog({ open, onClose, onConfirm }: Props) {
  const [entryType, setEntryType] = useState('regular');
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobText, setJobText] = useState('');
  const [boardJobs, setBoardJobs] = useState<Job[]>([]);

  // Optional board picker: only foreman/enterprise can fetch /api/jobs (others
  // get 403). On 403 we silently show free-text only.
  useEffect(() => {
    if (!open) return;
    let alive = true;
    void jobsClient.list().then((r) => {
      if (alive && r.ok) setBoardJobs(r.jobs);
    });
    return () => {
      alive = false;
    };
  }, [open]);

  const confirm = () => {
    const opts: ClockInOpts = { entryType };
    if (jobId) opts.jobId = jobId;
    const title = jobText.trim() || boardJobs.find((j) => j.id === jobId)?.title;
    if (title) opts.jobTitle = title;
    onConfirm(opts);
  };

  return (
    <Modal open={open} onClose={onClose} title="Clock in">
      <div className="flex flex-col gap-3 text-console-text text-sm">
        <div className="flex flex-wrap gap-2">
          {ENTRY_TYPES.map((t) => (
            <button
              key={t.value}
              type="button"
              onClick={() => setEntryType(t.value)}
              className={`px-2 py-1 rounded border ${entryType === t.value ? 'border-console-accent text-console-accent' : 'border-console-border text-console-text-muted'}`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {boardJobs.length > 0 && (
          <select
            className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
            value={jobId ?? ''}
            onChange={(e) => {
              setJobId(e.target.value || null);
              if (e.target.value) setJobText('');
            }}
          >
            <option value="">No job (general time)</option>
            {boardJobs.map((j) => (
              <option key={j.id} value={j.id}>{j.title}</option>
            ))}
          </select>
        )}

        <input
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
          placeholder="Or enter job name"
          value={jobText}
          onChange={(e) => {
            setJobText(e.target.value);
            if (e.target.value) setJobId(null);
          }}
        />

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm}>Clock in</Button>
        </div>
      </div>
    </Modal>
  );
}
```

- [ ] **Step 7: Write `ClockOutDialog`**

`desktop/portal/src/console/components/time/ClockOutDialog.tsx`:

```tsx
import { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';

const REASONS: { value: string; label: string }[] = [
  { value: 'lunch', label: 'Lunch Break' },
  { value: 'job_done', label: 'Job Completed' },
  { value: 'end_day', label: 'End of Day' },
  { value: 'break', label: 'Short Break' },
  { value: 'other', label: 'Other' },
];

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
}

export function ClockOutDialog({ open, onClose, onConfirm }: Props) {
  const [reason, setReason] = useState<string | null>(null);
  const [custom, setCustom] = useState('');

  const confirm = () => {
    if (reason === 'other') onConfirm(custom.trim() || 'other');
    else onConfirm(reason ?? undefined);
  };

  return (
    <Modal open={open} onClose={onClose} title="Clock out">
      <div className="flex flex-col gap-3 text-console-text text-sm">
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button
              key={r.value}
              type="button"
              onClick={() => setReason(r.value)}
              className={`px-2 py-1 rounded border ${reason === r.value ? 'border-console-accent text-console-accent' : 'border-console-border text-console-text-muted'}`}
            >
              {r.label}
            </button>
          ))}
        </div>

        {reason === 'other' && (
          <input
            className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
            placeholder="Specify reason"
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
          />
        )}

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm} disabled={!reason}>Clock out</Button>
        </div>
      </div>
    </Modal>
  );
}
```

- [ ] **Step 8: Run dialog tests + tsc (GREEN)**

Run: `cd desktop/portal && npx vitest run src/console/components/time/__tests__/ClockInDialog.test.tsx src/console/components/time/__tests__/ClockOutDialog.test.tsx && npx tsc --noEmit`
Expected: 4 tests pass; tsc clean. (If `jobsClient.list` result key differs from `r.jobs`, adjust the dialog + mock to match `jobsClient.ts`.)

- [ ] **Step 9: Commit**

```bash
git add desktop/portal/src/console/api/presenceClient.ts desktop/portal/src/console/hooks/useCurrentShift.ts desktop/portal/src/console/components/header/useShiftToggle.ts desktop/portal/src/console/components/header/ClockButton.tsx desktop/portal/src/console/components/time/ClockInDialog.tsx desktop/portal/src/console/components/time/ClockOutDialog.tsx desktop/portal/src/console/components/time/__tests__/ClockInDialog.test.tsx desktop/portal/src/console/components/time/__tests__/ClockOutDialog.test.tsx
git commit -m "$(cat <<'EOF'
feat(clock): clock-in/out dialogs + optimistic clockIn/clockOut hook

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `/console/time` screen + entries log

**Files:** Create `time/useTodayEntries.ts`, `time/TodayEntriesList.tsx`, `time/TimeScreen.tsx`, `routes/TimeRoute.tsx`; Modify `App.tsx`; Tests `useTodayEntries.test.ts`, `TodayEntriesList.test.tsx`.

- [ ] **Step 1: Write the failing `useTodayEntries` test**

`desktop/portal/src/console/components/time/__tests__/useTodayEntries.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTodayEntries } from '../useTodayEntries';
import * as presence from '../../../api/presenceClient';

describe('useTodayEntries', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('fetches today entries on mount and exposes them', async () => {
    const rows = [{ id: 'a', startedAt: '2026-05-25T09:00:00Z', endedAt: '2026-05-25T10:00:00Z', source: 'web', entryType: 'regular', jobId: null, jobTitle: 'Reno', clockOutReason: 'lunch' }];
    const spy = vi.spyOn(presence.presenceClient, 'getTodayShifts').mockResolvedValue({ ok: true, shifts: rows });
    const { result } = renderHook(() => useTodayEntries());
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalled();
    expect(result.current.map((e) => e.id)).toEqual(['a']);
  });
});
```

Run (RED): `cd desktop/portal && npx vitest run src/console/components/time/__tests__/useTodayEntries.test.ts` -> fail.

- [ ] **Step 2: Write `useTodayEntries`**

`desktop/portal/src/console/components/time/useTodayEntries.ts`:

```ts
import { useEffect, useState } from 'react';
import { presenceClient, type TimeEntryRow } from '../../api/presenceClient';
import { startOfTodayMs } from '../header/shiftFormat';

// Today's full time-entry rows for the /console/time screen. Mirrors
// useDayShiftTotal's fetch but keeps every field (it needs the rows, not just
// the summed seconds). Re-fetches on the on/off-clock transition + a 30s poll.
export function useTodayEntries(onClock?: boolean, pollMs = 30_000): TimeEntryRow[] {
  const [rows, setRows] = useState<TimeEntryRow[]>([]);
  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      const r = await presenceClient.getTodayShifts(startOfTodayMs());
      if (!cancelled && r.ok) setRows(r.shifts);
    };
    void refresh();
    const id = setInterval(() => void refresh(), pollMs);
    return () => { cancelled = true; clearInterval(id); };
  }, [pollMs, onClock]);
  return rows;
}
```

Run (GREEN): same vitest command -> pass.

- [ ] **Step 3: Write the failing `TodayEntriesList` test**

`desktop/portal/src/console/components/time/__tests__/TodayEntriesList.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TodayEntriesList } from '../TodayEntriesList';
import type { TimeEntryRow } from '../../../api/presenceClient';

function row(o: Partial<TimeEntryRow>): TimeEntryRow {
  return { id: 'r', startedAt: '2026-05-25T09:00:00Z', endedAt: '2026-05-25T10:30:00Z', source: 'web', entryType: 'regular', jobId: null, jobTitle: null, clockOutReason: null, ...o };
}

describe('TodayEntriesList', () => {
  it('renders the empty state', () => {
    render(<TodayEntriesList entries={[]} />);
    expect(screen.getByText(/no entries/i)).toBeInTheDocument();
  });

  it('renders a closed entry with type, job, reason and duration', () => {
    render(<TodayEntriesList entries={[row({ jobTitle: 'Kitchen', clockOutReason: 'lunch' })]} />);
    expect(screen.getByText(/REGULAR/i)).toBeInTheDocument();
    expect(screen.getByText(/Kitchen/)).toBeInTheDocument();
    expect(screen.getByText(/1:30/)).toBeInTheDocument(); // 90 min duration
  });

  it('shows NOW for an active (open) entry', () => {
    render(<TodayEntriesList entries={[row({ endedAt: null })]} />);
    expect(screen.getByText(/NOW/)).toBeInTheDocument();
  });
});
```

Run (RED): `npx vitest run src/console/components/time/__tests__/TodayEntriesList.test.tsx` -> fail.

- [ ] **Step 4: Write `TodayEntriesList`**

`desktop/portal/src/console/components/time/TodayEntriesList.tsx`:

```tsx
import type { TimeEntryRow } from '../../api/presenceClient';

function hm(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
}
function durationHMM(startIso: string, endIso: string | null): string {
  const end = endIso ? new Date(endIso).getTime() : Date.now();
  const mins = Math.max(0, Math.floor((end - new Date(startIso).getTime()) / 60000));
  return `${Math.floor(mins / 60)}:${String(mins % 60).padStart(2, '0')}`;
}

export function TodayEntriesList({ entries }: { entries: TimeEntryRow[] }) {
  if (entries.length === 0) {
    return <div className="text-console-text-muted text-sm">No entries today.</div>;
  }
  return (
    <div className="flex flex-col gap-1.5 font-mono text-[13px] text-console-text">
      {entries.map((e) => (
        <div key={e.id} className="flex items-baseline gap-2">
          <span className="tabular-nums text-console-text-muted">
            {e.startedAt ? hm(e.startedAt) : '--:--'} - {e.endedAt ? hm(e.endedAt) : <span className="text-console-ok">NOW</span>}
          </span>
          <span className="uppercase text-[11px]">{e.entryType}</span>
          {e.jobTitle && <span className="text-console-accent truncate">@{e.jobTitle}</span>}
          {e.clockOutReason && <span className="text-console-text-muted text-[11px] truncate">- {e.clockOutReason}</span>}
          {e.startedAt && (
            <span className="ml-auto tabular-nums font-medium">{durationHMM(e.startedAt, e.endedAt)}</span>
          )}
        </div>
      ))}
    </div>
  );
}
```

Run (GREEN): same vitest command -> pass.

- [ ] **Step 5: Write `TimeScreen` + `TimeRoute`**

`desktop/portal/src/console/components/time/TimeScreen.tsx`:

```tsx
import { useEffect, useState } from 'react';
import { useShiftToggle } from '../header/useShiftToggle';
import { useTodayEntries } from './useTodayEntries';
import { ClockInDialog } from './ClockInDialog';
import { ClockOutDialog } from './ClockOutDialog';
import { DailySummaryBar } from './DailySummaryBar';
import { TodayEntriesList } from './TodayEntriesList';
import { formatElapsed, startOfTodayMs, sumClosedSecondsToday } from '../header/shiftFormat';
import { Button } from '../ui/Button';

export function TimeScreen() {
  const { onClock, startedAt, entryType, jobTitle, busy, clockIn, clockOut } = useShiftToggle();
  const entries = useTodayEntries(onClock);
  const [now, setNow] = useState(() => Date.now());
  const [showIn, setShowIn] = useState(false);
  const [showOut, setShowOut] = useState(false);

  useEffect(() => {
    if (!onClock) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [onClock]);

  const currentElapsed = onClock && startedAt ? (now - new Date(startedAt).getTime()) / 1000 : 0;
  const todaySeconds = sumClosedSecondsToday(entries, startOfTodayMs()) + currentElapsed;

  return (
    <div className="h-full overflow-y-auto bg-console-bg p-4 font-mono text-console-text">
      <div className="text-xs uppercase tracking-wide text-console-text-muted mb-3">Time Clock</div>

      <div className="flex items-center gap-3 mb-2">
        <span className="text-2xl tabular-nums" aria-label="shift elapsed">
          {onClock ? formatElapsed(currentElapsed) : '--:--:--'}
        </span>
        <span className={onClock ? 'text-console-ok text-sm' : 'text-console-text-muted text-sm'}>
          {onClock ? 'ON CLOCK' : 'OFF CLOCK'}
        </span>
        {onClock && entryType && (
          <span className="text-console-text-muted text-xs uppercase">
            {entryType}{jobTitle ? ` @ ${jobTitle}` : ''}
          </span>
        )}
      </div>

      <div className="mb-4">
        {onClock
          ? <Button variant="secondary" disabled={busy} onClick={() => setShowOut(true)}>clock out</Button>
          : <Button disabled={busy} onClick={() => setShowIn(true)}>clock in</Button>}
      </div>

      <DailySummaryBar secondsWorked={todaySeconds} />

      <div className="mt-4">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Today's entries</div>
        <TodayEntriesList entries={entries} />
      </div>

      <ClockInDialog
        open={showIn}
        onClose={() => setShowIn(false)}
        onConfirm={(opts) => { setShowIn(false); void clockIn(opts); }}
      />
      <ClockOutDialog
        open={showOut}
        onClose={() => setShowOut(false)}
        onConfirm={(reason) => { setShowOut(false); void clockOut(reason); }}
      />
    </div>
  );
}
```

`desktop/portal/src/console/routes/TimeRoute.tsx`:

```tsx
import { TimeScreen } from '../components/time/TimeScreen';

export function TimeRoute() {
  return <TimeScreen />;
}
```

- [ ] **Step 6: Add the route (all-tier)**

In `desktop/portal/src/App.tsx`: add the import alongside the other route imports:

```ts
import { TimeRoute } from './console/routes/TimeRoute';
```

Add the route inside the `/console` block, NOT wrapped in `RequireForemanTier` (next to `home`/`settings`):

```tsx
        <Route path="time" element={<TimeRoute />} />
```

- [ ] **Step 7: Build + tsc + run new tests**

Run: `cd desktop/portal && npx vitest run src/console/components/time && npx tsc --noEmit`
Expected: time-folder tests pass; tsc clean. (DailySummaryBar is added in Task 4; if its import fails here, do Task 4 first or stub it — but per build order Task 4 follows; to keep T3 self-contained, create a minimal `DailySummaryBar` placeholder that renders `secondsWorked` and is fully implemented + tested in Task 4. Place this note: implement the real bar in Task 4.)

> To keep Task 3 compiling before Task 4, create `DailySummaryBar.tsx` now as a minimal stub: `export function DailySummaryBar({ secondsWorked }: { secondsWorked: number }) { return <div data-testid="daily-bar" />; }` — Task 4 replaces its body + adds the pure math + tests.

- [ ] **Step 8: Commit**

```bash
git add desktop/portal/src/console/components/time/useTodayEntries.ts desktop/portal/src/console/components/time/TodayEntriesList.tsx desktop/portal/src/console/components/time/TimeScreen.tsx desktop/portal/src/console/components/time/DailySummaryBar.tsx desktop/portal/src/console/routes/TimeRoute.tsx desktop/portal/src/App.tsx desktop/portal/src/console/components/time/__tests__/useTodayEntries.test.ts desktop/portal/src/console/components/time/__tests__/TodayEntriesList.test.tsx
git commit -m "$(cat <<'EOF'
feat(clock): /console/time screen + read-only today's entries log (all-tier)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Daily summary bar (8-hour, half-hour slots, overtime)

**Files:** Create `time/dailySummary.ts`; replace the `time/DailySummaryBar.tsx` stub; Tests `dailySummary.test.ts`.

- [ ] **Step 1: Write the failing pure-math test**

`desktop/portal/src/console/components/time/__tests__/dailySummary.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { computeSlots, SLOT_COUNT, overtimeSeconds } from '../dailySummary';

describe('dailySummary', () => {
  it('all empty at zero', () => {
    expect(computeSlots(0)).toEqual(new Array(SLOT_COUNT).fill(0));
    expect(overtimeSeconds(0)).toBe(0);
  });

  it('3.5h fills 7 full slots then empties (half-hour resolution)', () => {
    const slots = computeSlots(3.5 * 3600); // 7 * 1800s
    expect(slots.slice(0, 7)).toEqual(new Array(7).fill(2)); // 2 = full
    expect(slots[7]).toBe(0);
  });

  it('a partial slot reads as half (1)', () => {
    const slots = computeSlots(15 * 60); // 15 min = half of one 30-min slot
    expect(slots[0]).toBe(1);
  });

  it('caps the bar at 8h and reports overtime separately', () => {
    const slots = computeSlots(9 * 3600);
    expect(slots).toEqual(new Array(SLOT_COUNT).fill(2)); // all full
    expect(overtimeSeconds(9 * 3600)).toBe(3600); // 1h OT
  });
});
```

Run (RED): `cd desktop/portal && npx vitest run src/console/components/time/__tests__/dailySummary.test.ts` -> fail.

- [ ] **Step 2: Write the pure math**

`desktop/portal/src/console/components/time/dailySummary.ts`:

```ts
// Pure math for the 8-hour daily summary bar. 16 half-hour slots = 8h target.
// Each slot is 0 (empty) / 1 (half, >= 15 min) / 2 (full, >= 30 min).
export const SLOT_COUNT = 16;
export const SLOT_SECONDS = 1800; // 30 min
export const TARGET_SECONDS = SLOT_COUNT * SLOT_SECONDS; // 8h

export function computeSlots(secondsWorked: number): number[] {
  const s = Math.max(0, secondsWorked);
  return Array.from({ length: SLOT_COUNT }, (_, i) => {
    const into = s - i * SLOT_SECONDS;
    if (into >= SLOT_SECONDS) return 2;
    if (into >= SLOT_SECONDS / 2) return 1;
    return 0;
  });
}

export function overtimeSeconds(secondsWorked: number): number {
  return Math.max(0, secondsWorked - TARGET_SECONDS);
}
```

Run (GREEN): same vitest command -> pass.

- [ ] **Step 3: Replace the `DailySummaryBar` stub with the real component**

`desktop/portal/src/console/components/time/DailySummaryBar.tsx`:

```tsx
import { computeSlots, overtimeSeconds, TARGET_SECONDS } from './dailySummary';
import { formatElapsed } from '../header/shiftFormat';

const GLYPH = ['-', '+', '#']; // empty / half / full (ASCII, no emoji)

export function DailySummaryBar({ secondsWorked }: { secondsWorked: number }) {
  const slots = computeSlots(secondsWorked);
  const ot = overtimeSeconds(secondsWorked);
  const over = ot > 0;
  const worked = formatElapsed(secondsWorked).slice(0, 5); // HH:MM
  return (
    <div className="font-mono text-sm flex items-center gap-2" data-testid="daily-bar">
      <span className={over ? 'text-console-warn' : 'text-console-accent'}>
        [{slots.map((s) => GLYPH[s]).join('')}]
      </span>
      <span className={over ? 'text-console-warn tabular-nums' : 'text-console-text-muted tabular-nums'}>
        {worked} / {formatElapsed(TARGET_SECONDS).slice(0, 5)}
        {over ? ` (+${formatElapsed(ot).slice(0, 5)} OT)` : ''}
      </span>
    </div>
  );
}
```

- [ ] **Step 4: Run the time-folder tests + tsc (GREEN)**

Run: `cd desktop/portal && npx vitest run src/console/components/time && npx tsc --noEmit`
Expected: all time tests pass (incl. dailySummary); tsc clean.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/time/dailySummary.ts desktop/portal/src/console/components/time/DailySummaryBar.tsx desktop/portal/src/console/components/time/__tests__/dailySummary.test.ts
git commit -m "$(cat <<'EOF'
feat(clock): 8-hour daily summary bar with half-hour slots + overtime

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Header integration

**Files:** Modify `header/ShiftClock.tsx`; update `header/__tests__/ShiftClock.test.tsx`.

- [ ] **Step 1: Update the ShiftClock test for the new hook shape + dialogs**

The existing `ShiftClock.test.tsx` mocks `useShiftToggle` with `{ onClock, startedAt, busy, toggle }`. Update the mock to the new shape and assert the dialog-driven behavior. Replace the mock + add a dialog test:

```tsx
const h = vi.hoisted(() => ({
  toggle: {
    onClock: false, startedAt: null as string | null, entryType: null as string | null,
    jobTitle: null as string | null, busy: false, clockIn: vi.fn(), clockOut: vi.fn(),
  },
  dayTotal: 0,
}));
vi.mock('../useShiftToggle', () => ({ useShiftToggle: () => h.toggle }));
vi.mock('../useDayShiftTotal', () => ({ useDayShiftTotal: () => h.dayTotal }));
// ShiftClock now renders ClockInDialog, which calls jobsClient.list() on open;
// stub it so the test never fires a real fetch.
vi.mock('../../../api/jobsClient', () => ({
  jobsClient: { list: vi.fn().mockResolvedValue({ ok: false, status: 403, error: 'tier' }) },
}));
```

Keep the existing `formatElapsed` + off-clock/on-clock layout assertions (they still hold: off-clock shows day total, on-clock shows the elapsed timer). Add:

```tsx
  it('clicking clock-in opens the dialog (does not immediately clock in)', () => {
    h.toggle = { onClock: false, startedAt: null, entryType: null, jobTitle: null, busy: false, clockIn: vi.fn(), clockOut: vi.fn() };
    h.dayTotal = 0;
    render(<ShiftClock />);
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(h.toggle.clockIn).not.toHaveBeenCalled(); // dialog first
    expect(screen.getByText(/clock in/i)).toBeInTheDocument(); // dialog title visible
  });

  it('on the clock shows the entry type + job', () => {
    const startedAt = new Date(Date.now() - 60_000).toISOString();
    h.toggle = { onClock: true, startedAt, entryType: 'overtime', jobTitle: 'Kitchen', busy: false, clockIn: vi.fn(), clockOut: vi.fn() };
    render(<ShiftClock />);
    expect(screen.getByText(/overtime/i)).toBeInTheDocument();
    expect(screen.getByText(/Kitchen/)).toBeInTheDocument();
  });
```
(Add `fireEvent` to the testing-library import.)

Run (RED): `cd desktop/portal && npx vitest run src/console/components/header/__tests__/ShiftClock.test.tsx` -> fail.

- [ ] **Step 2: Update `ShiftClock` to use dialogs + show entry/job**

Rewrite `desktop/portal/src/console/components/header/ShiftClock.tsx` to: read the new hook fields; render the clock-in/out box that OPENS the dialogs (not toggles); show `entryType @ job` on-clock. Keep the existing mirror layout (on-clock: elapsed left; off-clock: day total right):

```tsx
// desktop/portal/src/console/components/header/ShiftClock.tsx
import { useEffect, useState } from 'react';
import { useShiftToggle } from './useShiftToggle';
import { useDayShiftTotal } from './useDayShiftTotal';
import { ClockInDialog } from '../time/ClockInDialog';
import { ClockOutDialog } from '../time/ClockOutDialog';
import { formatElapsed } from './shiftFormat';
export { formatElapsed } from './shiftFormat';

function formatStart(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function ShiftClock() {
  const { onClock, startedAt, entryType, jobTitle, busy, clockIn, clockOut } = useShiftToggle();
  const dayTotalSeconds = useDayShiftTotal();
  const [now, setNow] = useState(() => Date.now());
  const [showIn, setShowIn] = useState(false);
  const [showOut, setShowOut] = useState(false);

  useEffect(() => {
    if (!onClock) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [onClock]);

  const elapsed = onClock && startedAt ? (now - new Date(startedAt).getTime()) / 1000 : null;

  return (
    <div
      role="group"
      aria-label="shift"
      className="flex items-center gap-3 bg-console-bg border border-console-border rounded-md px-3 py-1.5"
    >
      {elapsed !== null && startedAt && (
        <>
          <span
            className="text-console-ok text-sm tabular-nums whitespace-nowrap"
            style={{ fontFamily: 'var(--font-mono)' }}
            aria-label="shift elapsed"
          >
            {formatElapsed(elapsed)}
          </span>
          <span className="hidden sm:inline text-console-text-muted text-[10px] whitespace-nowrap uppercase">
            {entryType ?? 'regular'}{jobTitle ? ` @ ${jobTitle}` : ''} · started {formatStart(startedAt)}
          </span>
        </>
      )}

      <button
        type="button"
        disabled={busy}
        onClick={() => (onClock ? setShowOut(true) : setShowIn(true))}
        className={`text-sm whitespace-nowrap disabled:opacity-50 ${onClock ? 'text-console-ok' : 'text-console-text-muted'}`}
      >
        {onClock ? '● ON CLOCK · clock out' : '○ OFF CLOCK · clock in'}
      </button>

      {!onClock && (
        <span
          className="text-console-text text-sm tabular-nums whitespace-nowrap"
          style={{ fontFamily: 'var(--font-mono)' }}
          aria-label="day total"
        >
          {formatElapsed(dayTotalSeconds)}
        </span>
      )}

      <ClockInDialog open={showIn} onClose={() => setShowIn(false)} onConfirm={(opts) => { setShowIn(false); void clockIn(opts); }} />
      <ClockOutDialog open={showOut} onClose={() => setShowOut(false)} onConfirm={(reason) => { setShowOut(false); void clockOut(reason); }} />
    </div>
  );
}
```

> Note: this replaces the old `ClockToggleButton` usage with an inline box that opens the dialog. If `ClockToggleButton` becomes unused after this, leave it in place (don't delete in this slice); a follow-up can remove it. Verify no other file imports it before considering removal.

Run (GREEN): `cd desktop/portal && npx vitest run src/console/components/header/__tests__/ShiftClock.test.tsx` -> pass.

- [ ] **Step 3: Full portal gates**

Run: `cd desktop/portal && npx vitest run && npx tsc --noEmit && npm run build`
Expected: full suite green; tsc clean; build succeeds.

- [ ] **Step 4: Commit**

```bash
git add desktop/portal/src/console/components/header/ShiftClock.tsx desktop/portal/src/console/components/header/__tests__/ShiftClock.test.tsx
git commit -m "$(cat <<'EOF'
feat(clock): header opens clock-in/out dialogs + shows entry type / job

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Final verification (after all tasks)

- [ ] Backend: `cd backend && npx tsc --noEmit && npx jest src/__tests__/shifts-routes.test.ts` (with `DATABASE_URL`) — clean + green.
- [ ] Portal: `cd desktop/portal && npx vitest run && npx tsc --noEmit && npm run build` — green + clean + builds.
- [ ] **Deferred-verify (live):** restart the `:3030` dev backend to pick up the migration + endpoint changes ([[project_portal_dev_backend_port]]), then: open `/console/time`, click clock-in -> dialog (pick type + job) -> confirm -> timer starts, entry appears in the log; the daily bar fills; clock-out -> reason dialog -> entry closes with the reason. Confirm the header shows `TYPE @ Job` while on-clock and that a solo user reaches `/console/time` (not redirected).

## Notes / scope
- Entries log is **read-only** (edit/delete = Enterprise, deferred).
- `/console/time` is **all-tier**; the clock-in board picker only appears for tiers that can fetch `/api/jobs` (solo gets free-text only, gracefully).
- `jobId` cross-org validation is not enforced in v1 (the picker only shows the user's own jobs; a hand-crafted `jobId` just stores a reference on the user's own row — no cross-user exposure). Future hardening.
- Mesh/offline, GPS source, weekly/archive views, and the AISupervisor daily-log hook are out of scope (spec section 2).
