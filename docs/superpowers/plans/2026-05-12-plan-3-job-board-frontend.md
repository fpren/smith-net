# Plan 3 — Job Board Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the operator-console Job Board MVP: list, detail, create, status workflow, crew assignment via search-modal. Polling at 15s.

**Architecture:** Frontend-only feature plus one small backend addition. New `console/api/`, `console/stores/`, `console/hooks/`, `console/routes/`, `console/components/jobs/` modules mirror the Plan 1 + Plan 2 conventions. One new backend endpoint (`GET /api/profiles?q=`) feeds the assign-crew modal. Polling at 15s pauses on `document.hidden`. No WebSocket, no drag-and-drop.

**Tech Stack:** React 18 + Vite + TypeScript + Zustand + Tailwind + Vitest + RTL + MSW (all installed). Backend: Express + `pg.Pool` + `zod` + `jest` + `supertest` (all installed). **No new npm dependencies.**

**Spec:** `docs/superpowers/specs/2026-05-12-plan-3-job-board-frontend-design.md`

**Scope boundary (NOT in this plan):**
- WebSocket / live push (separate plan)
- Drag-and-drop kanban (separate plan)
- `/console/map` route + MapLibre (Plan 4)
- `/console/crew` browse page (Plan 4 — Plan 3 has the search modal only)
- `/console/clients/*` (Plan 5)
- Optimistic updates + rollback
- Persistent collapse-state across sessions

**DB requirement:** Plan 3 doesn't apply migrations. Frontend tests need no DB (MSW mocks everything). Backend `profiles-routes.test.ts` uses the in-memory `userStore`, also no DB. Manual browser walkthrough at end requires `DATABASE_URL` + migration 003 from Plan 2.

---

## File Structure

**New backend files:**
- `backend/src/schemas/profiles.ts`
- `backend/src/profilesRoutes.ts`
- `backend/src/__tests__/profiles-routes.test.ts`

**Modified backend files:**
- `backend/src/schemas/index.ts` (one new export line)
- `backend/src/server.ts` (one new import + one new `app.use` line)

**New frontend files (under `desktop/portal/src/console/`):**
- `api/jobsClient.ts`
- `api/profilesClient.ts`
- `stores/jobsStore.ts`
- `stores/toastStore.ts`
- `hooks/useJobsPolling.ts`
- `hooks/useToast.ts`
- `components/ui/Toast.tsx`
- `components/ui/__tests__/Toast.test.tsx`
- `components/jobs/JobCard.tsx`
- `components/jobs/JobStatusBadge.tsx`
- `components/jobs/StatusButtons.tsx`
- `components/jobs/CreateJobModal.tsx`
- `components/jobs/AssignCrewModal.tsx`
- `components/jobs/__tests__/JobCard.test.tsx`
- `components/jobs/__tests__/JobStatusBadge.test.tsx`
- `components/jobs/__tests__/StatusButtons.test.tsx`
- `components/jobs/__tests__/CreateJobModal.test.tsx`
- `components/jobs/__tests__/AssignCrewModal.test.tsx`
- `routes/JobsListRoute.tsx`
- `routes/JobDetailRoute.tsx`
- `routes/__tests__/JobsListRoute.test.tsx`
- `routes/__tests__/JobDetailRoute.test.tsx`
- `api/__tests__/jobsClient.test.ts`
- `api/__tests__/profilesClient.test.ts`
- `stores/__tests__/jobsStore.test.ts`
- `hooks/__tests__/useJobsPolling.test.ts`

**Modified frontend files:**
- `App.tsx` (add two new routes)
- `ConsoleShell.tsx` (replace placeholder nav with real nav items)
- `console/test/msw-handlers.ts` (extend with `/api/jobs/*` and `/api/profiles` mocks)

---

## Task 1: Backend — `GET /api/profiles?q=` endpoint

**Files:**
- Create: `backend/src/schemas/profiles.ts`
- Create: `backend/src/profilesRoutes.ts`
- Create: `backend/src/__tests__/profiles-routes.test.ts`
- Modify: `backend/src/schemas/index.ts`
- Modify: `backend/src/server.ts`

- [ ] **Step 1: Create the schema**

Write `backend/src/schemas/profiles.ts`:

```ts
import { z } from 'zod';

export const ProfileQuery = z.object({
  q: z.string().trim().min(2).max(100),
}).strict();
export type ProfileQuery = z.infer<typeof ProfileQuery>;
```

- [ ] **Step 2: Add to barrel export**

Modify `backend/src/schemas/index.ts`. Current content:

```ts
export * as auth from './auth';
export * as jobs from './jobs';
```

Add:

```ts
export * as profiles from './profiles';
```

- [ ] **Step 3: Write the failing test**

Create `backend/src/__tests__/profiles-routes.test.ts`:

```ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { profilesRouter } from '../profilesRoutes';
import { userStore, generateTokens, UserRole } from '../auth';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/profiles', profilesRouter);
  return app;
}

describe('GET /api/profiles', () => {
  const app = buildApp();

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/profiles?q=admin');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const user = await userStore.createUser(
      `solo-profiles-${Date.now()}@example.com`,
      'password123',
      'Solo',
      UserRole.SOLO
    );
    const { accessToken } = generateTokens(user);
    const res = await request(app)
      .get('/api/profiles?q=admin')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('returns 400 validation when q is missing', async () => {
    const user = await userStore.createUser(
      `foreman-profiles-missing-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = generateTokens(user);
    const res = await request(app)
      .get('/api/profiles')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('returns 400 validation when q is too short', async () => {
    const user = await userStore.createUser(
      `foreman-profiles-short-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = generateTokens(user);
    const res = await request(app)
      .get('/api/profiles?q=x')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('returns matching profiles by email substring', async () => {
    const target = await userStore.createUser(
      `target-search-${Date.now()}@example.com`,
      'password123',
      'Searchable Person',
      UserRole.TEAM_MEMBER
    );
    const foreman = await userStore.createUser(
      `foreman-search-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=target-search`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.profiles)).toBe(true);
    const found = res.body.profiles.find((p: any) => p.id === target.id);
    expect(found).toBeDefined();
    expect(found.email).toContain('target-search');
    expect(found.displayName).toBe('Searchable Person');
    expect(found.role).toBe('team');
  });

  it('returns matching profiles by displayName substring', async () => {
    const target = await userStore.createUser(
      `dn-${Date.now()}@example.com`,
      'password123',
      'Distinctive Crew Member',
      UserRole.TEAM_MEMBER
    );
    const foreman = await userStore.createUser(
      `foreman-dn-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=Distinctive`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profiles.find((p: any) => p.id === target.id)).toBeDefined();
  });

  it('caps results at 20', async () => {
    const foreman = await userStore.createUser(
      `foreman-cap-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    // Create 25 matchable users
    const tag = `bulk-${Date.now()}`;
    for (let i = 0; i < 25; i++) {
      await userStore.createUser(`${tag}-${i}@example.com`, 'password123', `Bulk ${i}`, UserRole.TEAM_MEMBER);
    }
    const { accessToken } = generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=${tag}`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profiles.length).toBeLessThanOrEqual(20);
  });
});
```

- [ ] **Step 4: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest profiles-routes 2>&1 | tail -10
```

Expected: FAIL — `Cannot find module '../profilesRoutes'`.

- [ ] **Step 5: Implement `profilesRoutes.ts`**

Create `backend/src/profilesRoutes.ts`:

```ts
// backend/src/profilesRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, userStore, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { validateQuery } from './middleware/validate';
import { ProfileQuery } from './schemas/profiles';

export const profilesRouter = Router();

profilesRouter.use(authenticateToken, requireConsoleTier);

profilesRouter.get('/', validateQuery(ProfileQuery), (req: AuthenticatedRequest, res: Response) => {
  // Body was replaced by validateQuery — but validateQuery applies to req.query.
  // Re-read req.query as the typed shape.
  const q = ((req.query as unknown) as ProfileQuery).q;
  const needle = q.toLowerCase();
  const matches = userStore.getAllUsers()
    .filter((u) =>
      u.isActive &&
      (u.email.toLowerCase().includes(needle) || u.displayName.toLowerCase().includes(needle))
    )
    .slice(0, 20)
    .map((u) => ({
      id: u.id,
      email: u.email,
      displayName: u.displayName,
      role: u.role,
    }));
  res.json({ profiles: matches });
});

console.log('[Profiles] routes initialized');
```

- [ ] **Step 6: Mount in server.ts**

Modify `backend/src/server.ts`. Add to imports:

```ts
import { profilesRouter } from './profilesRoutes';
```

Add the mount line after the `/api/jobs` mount (around line 128):

```ts
// Mount Profiles API (read-only crew search for console)
app.use('/api/profiles', profilesRouter);
```

- [ ] **Step 7: Run — confirm 7 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest profiles-routes 2>&1 | tail -10
```

Expected: 7 PASS.

- [ ] **Step 8: Run full backend suite — confirm baseline**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test 2>&1 | tail -5
```

Expected: 64 passed, 4 failed (57 + 7 new). 16 still skipped (Plan 2 integration tests waiting for DATABASE_URL).

- [ ] **Step 9: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/schemas/profiles.ts backend/src/schemas/index.ts backend/src/profilesRoutes.ts backend/src/server.ts backend/src/__tests__/profiles-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(profiles): GET /api/profiles?q= for crew-search modal"
```

---

## Task 2: Frontend — Toast primitive + toastStore + useToast (TDD)

**Files:**
- Create: `desktop/portal/src/console/stores/toastStore.ts`
- Create: `desktop/portal/src/console/hooks/useToast.ts`
- Create: `desktop/portal/src/console/components/ui/Toast.tsx`
- Create: `desktop/portal/src/console/components/ui/__tests__/Toast.test.tsx`

- [ ] **Step 1: Create `toastStore.ts`**

```ts
// desktop/portal/src/console/stores/toastStore.ts
import { create } from 'zustand';

export type ToastTone = 'info' | 'error';

export interface ToastEntry {
  id: number;
  message: string;
  tone: ToastTone;
  duration: number;
}

interface ToastState {
  toasts: ToastEntry[];
  push: (entry: Omit<ToastEntry, 'id'>) => number;
  dismiss: (id: number) => void;
}

const MAX_STACK = 5;
let nextId = 1;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (entry) => {
    const id = nextId++;
    set((state) => {
      const next = [{ id, ...entry }, ...state.toasts];
      return { toasts: next.slice(0, MAX_STACK) };
    });
    return id;
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));
```

- [ ] **Step 2: Create `useToast.ts` hook**

```ts
// desktop/portal/src/console/hooks/useToast.ts
import { useToastStore, ToastTone } from '../stores/toastStore';

const DEFAULT_DURATION = 4000;

export function useToast() {
  const push = useToastStore((s) => s.push);
  return {
    info: (message: string, duration: number = DEFAULT_DURATION) =>
      push({ message, tone: 'info', duration }),
    error: (message: string, duration: number = DEFAULT_DURATION) =>
      push({ message, tone: 'error', duration }),
  };
}
```

- [ ] **Step 3: Write the failing test for Toast component**

Create `desktop/portal/src/console/components/ui/__tests__/Toast.test.tsx`:

```tsx
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ToastContainer } from '../Toast';
import { useToastStore } from '../../../stores/toastStore';

describe('Toast', () => {
  beforeEach(() => {
    useToastStore.setState({ toasts: [] });
  });

  it('renders nothing when no toasts', () => {
    render(<ToastContainer />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('renders an info toast pushed via store', () => {
    useToastStore.getState().push({ message: 'hello world', tone: 'info', duration: 4000 });
    render(<ToastContainer />);
    expect(screen.getByText('hello world')).toBeInTheDocument();
  });

  it('renders multiple toasts stacked', () => {
    useToastStore.getState().push({ message: 'first', tone: 'info', duration: 4000 });
    useToastStore.getState().push({ message: 'second', tone: 'error', duration: 4000 });
    render(<ToastContainer />);
    expect(screen.getByText('first')).toBeInTheDocument();
    expect(screen.getByText('second')).toBeInTheDocument();
  });

  it('dismisses a toast when its [x] is clicked', async () => {
    useToastStore.getState().push({ message: 'click me away', tone: 'info', duration: 60000 });
    render(<ToastContainer />);
    expect(screen.getByText('click me away')).toBeInTheDocument();
    const dismissBtn = screen.getByRole('button', { name: /dismiss/i });
    await userEvent.click(dismissBtn);
    expect(screen.queryByText('click me away')).not.toBeInTheDocument();
  });

  it('auto-dismisses after duration', () => {
    vi.useFakeTimers();
    useToastStore.getState().push({ message: 'auto-bye', tone: 'info', duration: 1000 });
    render(<ToastContainer />);
    expect(screen.getByText('auto-bye')).toBeInTheDocument();
    act(() => {
      vi.advanceTimersByTime(1100);
    });
    expect(screen.queryByText('auto-bye')).not.toBeInTheDocument();
    vi.useRealTimers();
  });

  it('caps the stack at 5 — pushing a 6th drops the oldest', () => {
    for (let i = 1; i <= 6; i++) {
      useToastStore.getState().push({ message: `toast-${i}`, tone: 'info', duration: 60000 });
    }
    render(<ToastContainer />);
    expect(useToastStore.getState().toasts.length).toBe(5);
    // newest at top of stack; toast-1 was the oldest and should have been dropped
    expect(screen.queryByText('toast-1')).not.toBeInTheDocument();
    expect(screen.getByText('toast-6')).toBeInTheDocument();
  });
});
```

- [ ] **Step 4: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- Toast 2>&1 | tail -10
```

Expected: FAIL — `Cannot find module '../Toast'`.

- [ ] **Step 5: Implement `Toast.tsx`**

Create `desktop/portal/src/console/components/ui/Toast.tsx`:

```tsx
import { useEffect } from 'react';
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';
import { useToastStore, ToastEntry, ToastTone } from '../../stores/toastStore';

const TONE_CLASSES: Record<ToastTone, string> = {
  info: 'bg-console-surface text-console-text border-console-border',
  error: 'bg-console-surface text-console-danger border-console-danger',
};

function ToastItem({ toast }: { toast: ToastEntry }) {
  const dismiss = useToastStore((s) => s.dismiss);
  useEffect(() => {
    const id = setTimeout(() => dismiss(toast.id), toast.duration);
    return () => clearTimeout(id);
  }, [toast.id, toast.duration, dismiss]);

  return (
    <div
      role="status"
      className={twMerge(
        clsx(
          'border px-4 py-2 font-mono text-sm flex items-start gap-3 min-w-[280px] max-w-[480px]',
          TONE_CLASSES[toast.tone]
        )
      )}
    >
      <span className="flex-1">{toast.message}</span>
      <button
        aria-label="Dismiss"
        className="text-console-text-muted hover:text-console-text font-mono"
        onClick={() => dismiss(toast.id)}
      >
        [x]
      </button>
    </div>
  );
}

export function ToastContainer() {
  const toasts = useToastStore((s) => s.toasts);
  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </div>
  );
}
```

- [ ] **Step 6: Run — confirm 6 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- Toast 2>&1 | tail -10
```

Expected: 6 PASS.

- [ ] **Step 7: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/stores/toastStore.ts desktop/portal/src/console/hooks/useToast.ts desktop/portal/src/console/components/ui/Toast.tsx desktop/portal/src/console/components/ui/__tests__/Toast.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): Toast UI primitive + toastStore + useToast"
```

---

## Task 3: Frontend — `jobsClient.ts` + tests (TDD)

**Files:**
- Create: `desktop/portal/src/console/api/jobsClient.ts`
- Create: `desktop/portal/src/console/api/__tests__/jobsClient.test.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts` (extend with `/api/jobs/*` mocks)

- [ ] **Step 1: Extend MSW handlers**

Add to `desktop/portal/src/console/test/msw-handlers.ts`. The existing handlers cover `/api/auth/*` — append the jobs handlers to the exported `handlers` array.

Add to the file:

```ts
// Append these handlers inside the handlers array (before the closing bracket):

  http.get('/api/jobs', () => {
    return HttpResponse.json({
      jobs: [
        {
          id: 'job-1',
          foremanId: 'user-1',
          clientId: null,
          engagementId: null,
          title: 'Test Job',
          description: null,
          status: 'planned',
          scheduledAt: null,
          location: 'Test Location',
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
        },
      ],
    });
  }),

  http.get('/api/jobs/:id', ({ params }) => {
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: 'planned',
        scheduledAt: null,
        location: 'Detail Location',
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T10:00:00Z',
      },
      crew: [],
    });
  }),

  http.post('/api/jobs', async ({ request }) => {
    const body = (await request.json()) as { title: string; location?: string };
    return HttpResponse.json(
      {
        job: {
          id: 'new-job-id',
          foremanId: 'user-1',
          clientId: null,
          engagementId: null,
          title: body.title,
          description: null,
          status: 'planned',
          scheduledAt: null,
          location: body.location ?? null,
          createdAt: '2026-05-11T10:00:00Z',
          updatedAt: '2026-05-11T10:00:00Z',
        },
      },
      { status: 201 }
    );
  }),

  http.patch('/api/jobs/:id', async ({ params, request }) => {
    const body = (await request.json()) as Record<string, any>;
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        engagementId: null,
        title: body.title ?? 'Detail Job',
        description: body.description ?? null,
        status: 'planned',
        scheduledAt: body.scheduledAt ?? null,
        location: body.location ?? null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
      },
    });
  }),

  http.patch('/api/jobs/:id/status', async ({ params, request }) => {
    const body = (await request.json()) as { status: string };
    return HttpResponse.json({
      job: {
        id: params.id,
        foremanId: 'user-1',
        clientId: null,
        engagementId: null,
        title: 'Detail Job',
        description: null,
        status: body.status,
        scheduledAt: null,
        location: null,
        createdAt: '2026-05-11T10:00:00Z',
        updatedAt: '2026-05-11T11:00:00Z',
      },
    });
  }),

  http.post('/api/jobs/:id/assign', async ({ params, request }) => {
    const body = (await request.json()) as { profileId: string; roleOnJob?: string };
    return HttpResponse.json(
      {
        assignment: {
          jobId: params.id,
          profileId: body.profileId,
          roleOnJob: body.roleOnJob ?? 'crew',
          assignedAt: '2026-05-11T11:00:00Z',
        },
      },
      { status: 201 }
    );
  }),

  http.delete('/api/jobs/:id/assign/:profileId', () => {
    return new HttpResponse(null, { status: 204 });
  }),
```

- [ ] **Step 2: Write the failing test**

Create `desktop/portal/src/console/api/__tests__/jobsClient.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { jobsClient } from '../jobsClient';

describe('jobsClient', () => {
  it('list returns jobs', async () => {
    const result = await jobsClient.list();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.jobs).toHaveLength(1);
      expect(result.jobs[0].title).toBe('Test Job');
    }
  });

  it('getById returns job + crew', async () => {
    const result = await jobsClient.getById('abc');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.id).toBe('abc');
      expect(Array.isArray(result.crew)).toBe(true);
    }
  });

  it('create returns the new job on 201', async () => {
    const result = await jobsClient.create({ title: 'Brand new', location: 'X' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.id).toBe('new-job-id');
      expect(result.job.title).toBe('Brand new');
      expect(result.job.status).toBe('planned');
    }
  });

  it('update patches a job', async () => {
    const result = await jobsClient.update('abc', { title: 'Renamed' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.title).toBe('Renamed');
    }
  });

  it('changeStatus updates status', async () => {
    const result = await jobsClient.changeStatus('abc', 'in_progress');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.status).toBe('in_progress');
    }
  });

  it('assignCrew returns the assignment on 201', async () => {
    const result = await jobsClient.assignCrew('abc', 'profile-x', 'lead');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.assignment.profileId).toBe('profile-x');
      expect(result.assignment.roleOnJob).toBe('lead');
    }
  });

  it('unassignCrew returns ok on 204', async () => {
    const result = await jobsClient.unassignCrew('abc', 'profile-x');
    expect(result.ok).toBe(true);
  });
});
```

- [ ] **Step 3: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- jobsClient 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 4: Implement `jobsClient.ts`**

Create `desktop/portal/src/console/api/jobsClient.ts`:

```ts
// desktop/portal/src/console/api/jobsClient.ts

export type JobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled';

export interface Job {
  id: string;
  foremanId: string;
  clientId: string | null;
  engagementId: string | null;
  title: string;
  description: string | null;
  status: JobStatus;
  scheduledAt: string | null;     // ISO 8601 from server
  location: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CrewAssignment {
  jobId: string;
  profileId: string;
  roleOnJob: 'crew' | 'lead';
  assignedAt: string;
}

export type JobsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string; from?: JobStatus; to?: JobStatus };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<JobsResult<T>> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    credentials: 'include',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  if (res.status === 204) {
    return { ok: true } as JobsResult<T>;
  }

  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return {
      ok: false,
      status: res.status,
      error: errBody.error || 'Request failed',
      details: errBody.details,
      code: errBody.code,
      from: errBody.from,
      to: errBody.to,
    };
  }

  const data = (await res.json()) as T;
  return { ok: true, ...data } as JobsResult<T>;
}

interface ListResp { jobs: Job[] }
interface OneResp { job: Job; crew: CrewAssignment[] }
interface MutateResp { job: Job }
interface AssignResp { assignment: CrewAssignment }

export interface CreateJobInput {
  title: string;
  description?: string;
  scheduledAt?: string;
  location?: string;
  clientId?: string;
  engagementId?: string;
}

export interface UpdateJobInput {
  title?: string;
  description?: string | null;
  scheduledAt?: string | null;
  location?: string | null;
}

export const jobsClient = {
  list: () => call<ListResp>('/api/jobs'),
  getById: (id: string) => call<OneResp>(`/api/jobs/${encodeURIComponent(id)}`),
  create: (input: CreateJobInput) => call<MutateResp>('/api/jobs', { method: 'POST', body: input }),
  update: (id: string, patch: UpdateJobInput) =>
    call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  changeStatus: (id: string, status: JobStatus) =>
    call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }),
  assignCrew: (id: string, profileId: string, roleOnJob?: 'crew' | 'lead') =>
    call<AssignResp>(`/api/jobs/${encodeURIComponent(id)}/assign`, {
      method: 'POST',
      body: { profileId, ...(roleOnJob ? { roleOnJob } : {}) },
    }),
  unassignCrew: (id: string, profileId: string) =>
    call<{}>(`/api/jobs/${encodeURIComponent(id)}/assign/${encodeURIComponent(profileId)}`, {
      method: 'DELETE',
    }),
};
```

- [ ] **Step 5: Run — confirm 7 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- jobsClient 2>&1 | tail -10
```

Expected: 7 PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/api/jobsClient.ts desktop/portal/src/console/api/__tests__/jobsClient.test.ts desktop/portal/src/console/test/msw-handlers.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): jobsClient + MSW handlers for /api/jobs/*"
```

---

## Task 4: Frontend — `profilesClient.ts` + tests (TDD)

**Files:**
- Create: `desktop/portal/src/console/api/profilesClient.ts`
- Create: `desktop/portal/src/console/api/__tests__/profilesClient.test.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts`

- [ ] **Step 1: Append profiles handler to MSW**

Add to `desktop/portal/src/console/test/msw-handlers.ts` (inside the handlers array):

```ts
  http.get('/api/profiles', ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams.get('q') || '';
    if (q.length < 2) {
      return HttpResponse.json(
        { error: 'Validation failed', code: 'validation', details: {} },
        { status: 400 }
      );
    }
    return HttpResponse.json({
      profiles: [
        { id: 'p-1', email: 'alice@example.com', displayName: 'Alice', role: 'team' },
        { id: 'p-2', email: 'bob@example.com', displayName: 'Bob', role: 'lead' },
      ].filter((p) => p.email.includes(q) || p.displayName.toLowerCase().includes(q.toLowerCase())),
    });
  }),
```

- [ ] **Step 2: Write the failing test**

Create `desktop/portal/src/console/api/__tests__/profilesClient.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { profilesClient } from '../profilesClient';

describe('profilesClient', () => {
  it('search returns matching profiles', async () => {
    const result = await profilesClient.search('alice');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.profiles).toHaveLength(1);
      expect(result.profiles[0].email).toBe('alice@example.com');
    }
  });

  it('search returns 400 when query is too short', async () => {
    const result = await profilesClient.search('a');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(400);
      expect(result.code).toBe('validation');
    }
  });
});
```

- [ ] **Step 3: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- profilesClient 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 4: Implement `profilesClient.ts`**

```ts
// desktop/portal/src/console/api/profilesClient.ts
export type ProfileRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';

export interface ProfileMatch {
  id: string;
  email: string;
  displayName: string;
  role: ProfileRole;
}

export type ProfilesResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

interface SearchResp { profiles: ProfileMatch[] }

export const profilesClient = {
  search: async (q: string): Promise<ProfilesResult<SearchResp>> => {
    const res = await fetch(`/api/profiles?q=${encodeURIComponent(q)}`, {
      credentials: 'include',
    });
    if (!res.ok) {
      const errBody = await res.json().catch(() => ({ error: res.statusText }));
      return {
        ok: false,
        status: res.status,
        error: errBody.error || 'Request failed',
        details: errBody.details,
        code: errBody.code,
      };
    }
    const data = (await res.json()) as SearchResp;
    return { ok: true, ...data };
  },
};
```

- [ ] **Step 5: Run — confirm 2 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- profilesClient 2>&1 | tail -10
```

Expected: 2 PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/api/profilesClient.ts desktop/portal/src/console/api/__tests__/profilesClient.test.ts desktop/portal/src/console/test/msw-handlers.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): profilesClient + MSW handler for /api/profiles"
```

---

## Task 5: Frontend — `jobsStore` (TDD)

**Files:**
- Create: `desktop/portal/src/console/stores/jobsStore.ts`
- Create: `desktop/portal/src/console/stores/__tests__/jobsStore.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// desktop/portal/src/console/stores/__tests__/jobsStore.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useJobsStore } from '../jobsStore';
import type { Job, CrewAssignment } from '../../api/jobsClient';

const sampleJob: Job = {
  id: 'job-1',
  foremanId: 'f-1',
  clientId: null,
  engagementId: null,
  title: 'First job',
  description: null,
  status: 'planned',
  scheduledAt: null,
  location: null,
  createdAt: '2026-05-11T10:00:00Z',
  updatedAt: '2026-05-11T10:00:00Z',
};

const sampleCrew: CrewAssignment = {
  jobId: 'job-1',
  profileId: 'p-1',
  roleOnJob: 'crew',
  assignedAt: '2026-05-11T10:30:00Z',
};

describe('jobsStore', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
  });

  it('starts empty', () => {
    const s = useJobsStore.getState();
    expect(s.jobs).toEqual([]);
    expect(s.detailJob).toBeNull();
    expect(s.detailCrew).toEqual([]);
    expect(s.isStale).toBe(false);
  });

  it('setJobs replaces the list and updates lastFetchedAt', () => {
    const before = useJobsStore.getState().lastFetchedAt;
    useJobsStore.getState().setJobs([sampleJob]);
    const s = useJobsStore.getState();
    expect(s.jobs).toHaveLength(1);
    expect(s.lastFetchedAt).not.toBe(before);
    expect(s.lastFetchedAt).toBeGreaterThan(0);
  });

  it('setDetail populates detail slice', () => {
    useJobsStore.getState().setDetail(sampleJob, [sampleCrew]);
    const s = useJobsStore.getState();
    expect(s.detailJob).toEqual(sampleJob);
    expect(s.detailCrew).toEqual([sampleCrew]);
  });

  it('upsertJob inserts when not present', () => {
    useJobsStore.getState().setJobs([]);
    useJobsStore.getState().upsertJob(sampleJob);
    expect(useJobsStore.getState().jobs).toEqual([sampleJob]);
  });

  it('upsertJob updates in place when present', () => {
    useJobsStore.getState().setJobs([sampleJob]);
    const updated: Job = { ...sampleJob, title: 'Renamed', status: 'in_progress' };
    useJobsStore.getState().upsertJob(updated);
    const s = useJobsStore.getState();
    expect(s.jobs).toHaveLength(1);
    expect(s.jobs[0].title).toBe('Renamed');
    expect(s.jobs[0].status).toBe('in_progress');
  });

  it('upsertJob updates detailJob too when ids match', () => {
    useJobsStore.getState().setDetail(sampleJob, []);
    const updated: Job = { ...sampleJob, status: 'complete' };
    useJobsStore.getState().upsertJob(updated);
    expect(useJobsStore.getState().detailJob?.status).toBe('complete');
  });

  it('markStale toggles the flag', () => {
    useJobsStore.getState().markStale(true);
    expect(useJobsStore.getState().isStale).toBe(true);
    useJobsStore.getState().markStale(false);
    expect(useJobsStore.getState().isStale).toBe(false);
  });

  it('clear resets everything', () => {
    useJobsStore.getState().setJobs([sampleJob]);
    useJobsStore.getState().setDetail(sampleJob, [sampleCrew]);
    useJobsStore.getState().markStale(true);
    useJobsStore.getState().clear();
    const s = useJobsStore.getState();
    expect(s.jobs).toEqual([]);
    expect(s.detailJob).toBeNull();
    expect(s.detailCrew).toEqual([]);
    expect(s.isStale).toBe(false);
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- jobsStore 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implement `jobsStore.ts`**

```ts
// desktop/portal/src/console/stores/jobsStore.ts
import { create } from 'zustand';
import type { Job, CrewAssignment } from '../api/jobsClient';

interface JobsState {
  jobs: Job[];
  detailJob: Job | null;
  detailCrew: CrewAssignment[];
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  lastFetchedAt: number | null;
  isStale: boolean;

  setJobs: (jobs: Job[]) => void;
  setDetail: (job: Job, crew: CrewAssignment[]) => void;
  upsertJob: (job: Job) => void;
  markListLoading: (b: boolean) => void;
  markDetailLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useJobsStore = create<JobsState>((set) => ({
  jobs: [],
  detailJob: null,
  detailCrew: [],
  isLoadingList: false,
  isLoadingDetail: false,
  lastFetchedAt: null,
  isStale: false,

  setJobs: (jobs) => set({ jobs, lastFetchedAt: Date.now(), isStale: false }),
  setDetail: (detailJob, detailCrew) => set({ detailJob, detailCrew }),

  upsertJob: (job) => set((state) => {
    const idx = state.jobs.findIndex((j) => j.id === job.id);
    const nextJobs = idx === -1
      ? [job, ...state.jobs]
      : state.jobs.map((j, i) => (i === idx ? job : j));
    const nextDetail = state.detailJob && state.detailJob.id === job.id ? job : state.detailJob;
    return { jobs: nextJobs, detailJob: nextDetail };
  }),

  markListLoading: (isLoadingList) => set({ isLoadingList }),
  markDetailLoading: (isLoadingDetail) => set({ isLoadingDetail }),
  markStale: (isStale) => set({ isStale }),

  clear: () => set({
    jobs: [],
    detailJob: null,
    detailCrew: [],
    isLoadingList: false,
    isLoadingDetail: false,
    lastFetchedAt: null,
    isStale: false,
  }),
}));
```

- [ ] **Step 4: Run — confirm 8 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- jobsStore 2>&1 | tail -10
```

Expected: 8 PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/stores/jobsStore.ts desktop/portal/src/console/stores/__tests__/jobsStore.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): jobsStore with list/detail slices + upsert + stale flag"
```

---

## Task 6: Frontend — `useJobsPolling` (TDD with fake timers)

**Files:**
- Create: `desktop/portal/src/console/hooks/useJobsPolling.ts`
- Create: `desktop/portal/src/console/hooks/__tests__/useJobsPolling.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// desktop/portal/src/console/hooks/__tests__/useJobsPolling.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useJobsPolling } from '../useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import * as jobsClient from '../../api/jobsClient';

describe('useJobsPolling', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount (list scope)', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('fires another fetch after the interval', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
    await act(async () => {
      vi.advanceTimersByTime(15001);
      await Promise.resolve();
    });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('detail scope calls getById with the id', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'getById').mockResolvedValue({
      ok: true,
      job: { id: 'x', foremanId: 'f', clientId: null, engagementId: null, title: 't', description: null, status: 'planned', scheduledAt: null, location: null, createdAt: '', updatedAt: '' },
      crew: [],
    });
    renderHook(() => useJobsPolling({ detail: 'x' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledWith('x');
  });

  it('sets isStale=true on fetch failure and stops on visibility hidden', async () => {
    vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useJobsStore.getState().isStale).toBe(true);
  });

  it('cleans up interval on unmount', async () => {
    const spy = vi.spyOn(jobsClient.jobsClient, 'list').mockResolvedValue({ ok: true, jobs: [] });
    const { unmount } = renderHook(() => useJobsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => {
      vi.advanceTimersByTime(60000);
      await Promise.resolve();
    });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- useJobsPolling 2>&1 | tail -10
```

Expected: FAIL.

- [ ] **Step 3: Implement `useJobsPolling.ts`**

```ts
// desktop/portal/src/console/hooks/useJobsPolling.ts
import { useEffect, useRef } from 'react';
import { jobsClient } from '../api/jobsClient';
import { useJobsStore } from '../stores/jobsStore';

type Scope = 'list' | { detail: string };

export function useJobsPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      if (scope === 'list') {
        useJobsStore.getState().markListLoading(true);
        const result = await jobsClient.list();
        useJobsStore.getState().markListLoading(false);
        if (result.ok) {
          useJobsStore.getState().setJobs(result.jobs);
        } else {
          useJobsStore.getState().markStale(true);
        }
      } else {
        const id = scope.detail;
        useJobsStore.getState().markDetailLoading(true);
        const result = await jobsClient.getById(id);
        useJobsStore.getState().markDetailLoading(false);
        if (result.ok) {
          useJobsStore.getState().setDetail(result.job, result.crew);
          useJobsStore.getState().markStale(false);
        } else {
          useJobsStore.getState().markStale(true);
        }
      }
    };

    const startInterval = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, intervalMs);
    };

    const stopInterval = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        startInterval();
      } else {
        stopInterval();
      }
    };

    // Initial: kick fetch + start interval
    fetchOnce();
    startInterval();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      stopInterval();
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
```

- [ ] **Step 4: Run — confirm 5 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- useJobsPolling 2>&1 | tail -10
```

Expected: 5 PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/hooks/useJobsPolling.ts desktop/portal/src/console/hooks/__tests__/useJobsPolling.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): useJobsPolling — 15s interval, visibility-aware"
```

---

## Task 7: Frontend — `JobStatusBadge` + `JobCard` (TDD, two small components)

**Files:**
- Create: `desktop/portal/src/console/components/jobs/JobStatusBadge.tsx`
- Create: `desktop/portal/src/console/components/jobs/JobCard.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/JobStatusBadge.test.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/JobCard.test.tsx`

- [ ] **Step 1: Write JobStatusBadge test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { JobStatusBadge } from '../JobStatusBadge';

describe('JobStatusBadge', () => {
  it('renders PLANNED label for planned status', () => {
    render(<JobStatusBadge status="planned" />);
    expect(screen.getByText('PLANNED')).toBeInTheDocument();
  });

  it('renders IN PROGRESS label for in_progress status', () => {
    render(<JobStatusBadge status="in_progress" />);
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
  });

  it('renders COMPLETE label for complete status', () => {
    render(<JobStatusBadge status="complete" />);
    expect(screen.getByText('COMPLETE')).toBeInTheDocument();
  });

  it('renders CANCELLED label and danger tone for cancelled status', () => {
    const { container } = render(<JobStatusBadge status="cancelled" />);
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
    expect((container.firstChild as HTMLElement).className).toMatch(/text-console-danger/);
  });
});
```

- [ ] **Step 2: Implement `JobStatusBadge.tsx`**

```tsx
import { Badge } from '../ui/Badge';
import type { JobStatus } from '../../api/jobsClient';

const STATUS_TONE: Record<JobStatus, 'default' | 'ok' | 'warn' | 'danger'> = {
  planned: 'default',
  in_progress: 'ok',
  complete: 'ok',
  cancelled: 'danger',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  planned: 'PLANNED',
  in_progress: 'IN PROGRESS',
  complete: 'COMPLETE',
  cancelled: 'CANCELLED',
};

export function JobStatusBadge({ status }: { status: JobStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>;
}
```

- [ ] **Step 3: Run — confirm 4 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobStatusBadge 2>&1 | tail -10
```

- [ ] **Step 4: Write JobCard test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobCard } from '../JobCard';
import type { Job } from '../../../api/jobsClient';

const baseJob: Job = {
  id: 'abcdef1234567890',
  foremanId: 'f-1',
  clientId: null,
  engagementId: null,
  title: 'Install panel',
  description: null,
  status: 'planned',
  scheduledAt: null,
  location: '123 Main St',
  createdAt: '2026-05-11T10:00:00Z',
  updatedAt: '2026-05-11T10:00:00Z',
};

describe('JobCard', () => {
  it('renders title', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('Install panel')).toBeInTheDocument();
  });

  it('renders location', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('123 Main St')).toBeInTheDocument();
  });

  it('renders "unsch" when scheduledAt is null', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('unsch')).toBeInTheDocument();
  });

  it('renders id prefix (first 8 chars after #)', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('#abcdef12')).toBeInTheDocument();
  });

  it('detail link href points at /console/jobs/:id', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    const link = screen.getByRole('link', { name: /detail/i });
    expect(link).toHaveAttribute('href', `/console/jobs/${baseJob.id}`);
  });
});
```

- [ ] **Step 5: Implement `JobCard.tsx`**

```tsx
import { Link } from 'react-router-dom';
import type { Job } from '../../api/jobsClient';

function relativeTime(iso: string | null): string {
  if (iso === null) return 'unsch';
  const target = new Date(iso).getTime();
  const now = Date.now();
  const diff = target - now;
  const minutes = Math.round(diff / 60000);
  const hours = Math.round(minutes / 60);
  const days = Math.round(hours / 24);
  if (Math.abs(minutes) < 60) return minutes >= 0 ? `in ${minutes}m` : `${-minutes}m ago`;
  if (Math.abs(hours) < 24) return hours >= 0 ? `in ${hours}h` : `${-hours}h ago`;
  if (days === 1) return 'tomorrow';
  if (days === -1) return 'yesterday';
  return days >= 0 ? `in ${days}d` : `${-days}d ago`;
}

export function JobCard({ job }: { job: Job }) {
  return (
    <div className="grid grid-cols-[10ch_1fr_20ch_12ch_8ch] gap-3 items-center px-3 py-2 border-b border-console-border text-sm font-mono">
      <span className="text-console-accent">#{job.id.slice(0, 8)}</span>
      <span className="text-console-text truncate">{job.title}</span>
      <span className="text-console-text-muted truncate">{job.location ?? '—'}</span>
      <span className="text-console-text-muted">{relativeTime(job.scheduledAt)}</span>
      <Link to={`/console/jobs/${job.id}`} className="text-console-accent hover:underline text-right">
        [-> detail]
      </Link>
    </div>
  );
}
```

- [ ] **Step 6: Run JobCard tests — confirm 5 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobCard 2>&1 | tail -10
```

Expected: 5 PASS.

- [ ] **Step 7: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/jobs/JobStatusBadge.tsx desktop/portal/src/console/components/jobs/JobCard.tsx desktop/portal/src/console/components/jobs/__tests__/
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): JobStatusBadge + JobCard presentation components"
```

---

## Task 8: Frontend — `StatusButtons` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/jobs/StatusButtons.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/StatusButtons.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { StatusButtons } from '../StatusButtons';

describe('StatusButtons', () => {
  it('renders Start + Cancel for planned status', () => {
    render(<StatusButtons jobId="j-1" status="planned" onChanged={vi.fn()} />);
    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /complete/i })).not.toBeInTheDocument();
  });

  it('renders Complete + Cancel for in_progress status', () => {
    render(<StatusButtons jobId="j-1" status="in_progress" onChanged={vi.fn()} />);
    expect(screen.getByRole('button', { name: /complete/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /start/i })).not.toBeInTheDocument();
  });

  it('renders nothing for complete status (terminal)', () => {
    const { container } = render(<StatusButtons jobId="j-1" status="complete" onChanged={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing for cancelled status (terminal)', () => {
    const { container } = render(<StatusButtons jobId="j-1" status="cancelled" onChanged={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('calls onChanged with the new job after clicking Start', async () => {
    const onChanged = vi.fn();
    render(<StatusButtons jobId="j-1" status="planned" onChanged={onChanged} />);
    await userEvent.click(screen.getByRole('button', { name: /start/i }));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    const callArg = onChanged.mock.calls[0][0];
    expect(callArg.status).toBe('in_progress');
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- StatusButtons 2>&1 | tail -10
```

- [ ] **Step 3: Implement `StatusButtons.tsx`**

```tsx
import { useState } from 'react';
import { Button } from '../ui/Button';
import { jobsClient, JobStatus, Job } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';

const NEXT_STATES: Record<JobStatus, { label: string; status: JobStatus }[]> = {
  planned: [
    { label: '▶ Start', status: 'in_progress' },
    { label: '✕ Cancel', status: 'cancelled' },
  ],
  in_progress: [
    { label: '✓ Complete', status: 'complete' },
    { label: '✕ Cancel', status: 'cancelled' },
  ],
  complete: [],
  cancelled: [],
};

interface Props {
  jobId: string;
  status: JobStatus;
  onChanged: (job: Job) => void;
}

export function StatusButtons({ jobId, status, onChanged }: Props) {
  const [pending, setPending] = useState<JobStatus | null>(null);
  const toast = useToast();

  const next = NEXT_STATES[status];
  if (next.length === 0) return null;

  async function handleClick(target: JobStatus) {
    setPending(target);
    const result = await jobsClient.changeStatus(jobId, target);
    setPending(null);
    if (result.ok) {
      onChanged(result.job);
    } else if (result.code === 'invalid_status_transition') {
      toast.error(`Server rejected this transition (was ${result.from}, tried ${result.to}). Refreshing...`);
      // The parent route's polling hook will pick up the actual state on next tick.
    } else {
      toast.error(result.error || 'Failed to change status');
    }
  }

  return (
    <div className="flex gap-2">
      {next.map((opt) => (
        <Button
          key={opt.status}
          onClick={() => handleClick(opt.status)}
          disabled={pending !== null}
        >
          {pending === opt.status ? `${opt.label}...` : opt.label}
        </Button>
      ))}
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm 5 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- StatusButtons 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/jobs/StatusButtons.tsx desktop/portal/src/console/components/jobs/__tests__/StatusButtons.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): StatusButtons respecting jobs state machine"
```

---

## Task 9: Frontend — `CreateJobModal` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/jobs/CreateJobModal.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/CreateJobModal.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { CreateJobModal } from '../CreateJobModal';

describe('CreateJobModal', () => {
  it('renders title, location, description fields when open', () => {
    render(<MemoryRouter><CreateJobModal open onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    expect(screen.getByLabelText(/title/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/location/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/description/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument();
  });

  it('does not render when open=false', () => {
    render(<MemoryRouter><CreateJobModal open={false} onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    expect(screen.queryByLabelText(/title/i)).not.toBeInTheDocument();
  });

  it('shows inline error when title is empty and submit clicked', async () => {
    render(<MemoryRouter><CreateJobModal open onClose={vi.fn()} onCreated={vi.fn()} /></MemoryRouter>);
    await userEvent.click(screen.getByRole('button', { name: /create/i }));
    expect(await screen.findByText(/title is required/i)).toBeInTheDocument();
  });

  it('submits and calls onCreated then onClose on success', async () => {
    const onClose = vi.fn();
    const onCreated = vi.fn();
    render(<MemoryRouter><CreateJobModal open onClose={onClose} onCreated={onCreated} /></MemoryRouter>);
    await userEvent.type(screen.getByLabelText(/title/i), 'Brand new');
    await userEvent.type(screen.getByLabelText(/location/i), 'X');
    await userEvent.click(screen.getByRole('button', { name: /create/i }));
    await waitFor(() => {
      expect(onCreated).toHaveBeenCalled();
      expect(onClose).toHaveBeenCalled();
    });
    const createdJob = onCreated.mock.calls[0][0];
    expect(createdJob.title).toBe('Brand new');
  });
});
```

- [ ] **Step 2: Implement `CreateJobModal.tsx`**

```tsx
import { FormEvent, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { jobsClient, Job } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (job: Job) => void;
}

export function CreateJobModal({ open, onClose, onCreated }: Props) {
  const [title, setTitle] = useState('');
  const [location, setLocation] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [description, setDescription] = useState('');
  const [titleError, setTitleError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTitleError(null);
    if (!title.trim()) {
      setTitleError('Title is required');
      return;
    }
    setBusy(true);
    const result = await jobsClient.create({
      title: title.trim(),
      ...(location ? { location } : {}),
      ...(scheduledAt ? { scheduledAt: new Date(scheduledAt).toISOString() } : {}),
      ...(description ? { description } : {}),
    });
    setBusy(false);
    if (!result.ok) {
      toast.error(result.error || 'Failed to create job');
      return;
    }
    setTitle(''); setLocation(''); setScheduledAt(''); setDescription('');
    onCreated(result.job);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Create Job">
      <form onSubmit={onSubmit} className="flex flex-col gap-3 min-w-[360px]">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={titleError ?? undefined} />
        <Input label="Location" value={location} onChange={(e) => setLocation(e.target.value)} />
        <Input label="Scheduled At" type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} />
        <label className="flex flex-col gap-1 font-mono text-sm">
          <span className="text-console-text-muted text-xs uppercase tracking-wide">Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="bg-console-bg border border-console-border px-3 py-2 text-console-text focus:outline-none focus:border-console-accent font-mono"
            rows={4}
          />
        </label>
        <div className="flex gap-2 justify-end mt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Creating...' : 'Create'}</Button>
        </div>
      </form>
    </Modal>
  );
}
```

- [ ] **Step 3: Run — confirm 4 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- CreateJobModal 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/jobs/CreateJobModal.tsx desktop/portal/src/console/components/jobs/__tests__/CreateJobModal.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): CreateJobModal with title validation"
```

---

## Task 10: Frontend — `AssignCrewModal` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/jobs/AssignCrewModal.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/AssignCrewModal.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AssignCrewModal } from '../AssignCrewModal';

describe('AssignCrewModal', () => {
  beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); });
  afterEach(() => { vi.useRealTimers(); });

  it('shows "type to search" hint when query is empty', () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    expect(screen.getByText(/type to search/i)).toBeInTheDocument();
  });

  it('triggers search after debounce when 2+ chars typed', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'al');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
  });

  it('selecting a result reveals role selector + assign button', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
    await user.click(screen.getByText('alice@example.com'));
    expect(screen.getByRole('button', { name: /assign/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/role/i)).toBeInTheDocument();
  });

  it('marks already-assigned profiles and prevents selection', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={['p-1']} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText(/already assigned/i)).toBeInTheDocument());
  });

  it('submit calls onAssigned with the new assignment', async () => {
    const onAssigned = vi.fn();
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={onAssigned} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
    await user.click(screen.getByText('alice@example.com'));
    await user.click(screen.getByRole('button', { name: /assign/i }));
    await waitFor(() => expect(onAssigned).toHaveBeenCalled());
    const arg = onAssigned.mock.calls[0][0];
    expect(arg.profileId).toBe('p-1');
  });
});
```

- [ ] **Step 2: Implement `AssignCrewModal.tsx`**

```tsx
import { FormEvent, useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { profilesClient, ProfileMatch } from '../../api/profilesClient';
import { jobsClient, CrewAssignment } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';

interface Props {
  open: boolean;
  jobId: string;
  alreadyAssigned: string[];      // profile ids already assigned to this job
  onClose: () => void;
  onAssigned: (assignment: CrewAssignment) => void;
}

const DEBOUNCE_MS = 300;

export function AssignCrewModal({ open, jobId, alreadyAssigned, onClose, onAssigned }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProfileMatch[]>([]);
  const [selected, setSelected] = useState<ProfileMatch | null>(null);
  const [role, setRole] = useState<'crew' | 'lead'>('crew');
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  // Debounced search
  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      setSearchError(null);
      return;
    }
    const id = setTimeout(async () => {
      setSearching(true);
      setSearchError(null);
      const result = await profilesClient.search(query.trim());
      setSearching(false);
      if (result.ok) {
        setResults(result.profiles);
      } else {
        setSearchError(result.error);
        setResults([]);
      }
    }, DEBOUNCE_MS);
    return () => clearTimeout(id);
  }, [query]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setSubmitError(null);
    setBusy(true);
    const result = await jobsClient.assignCrew(jobId, selected.id, role);
    setBusy(false);
    if (!result.ok) {
      if (result.code === 'duplicate_assignment') {
        setSubmitError('Already assigned to this job.');
      } else {
        toast.error(result.error || 'Failed to assign crew');
      }
      return;
    }
    onAssigned(result.assignment);
    setQuery(''); setResults([]); setSelected(null); setRole('crew');
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Assign Crew">
      <form onSubmit={onSubmit} className="flex flex-col gap-3 min-w-[420px]">
        <Input
          label="Search by name or email"
          value={query}
          onChange={(e) => { setQuery(e.target.value); setSelected(null); }}
          placeholder="2+ chars"
        />
        {query.trim().length < 2 && <div className="text-console-text-muted text-xs">Type to search profiles.</div>}
        {searching && <div className="text-console-text-muted text-xs">[searching...]</div>}
        {searchError && <div className="text-console-danger text-xs">{searchError}</div>}
        <div className="flex flex-col">
          {results.map((p) => {
            const assigned = alreadyAssigned.includes(p.id);
            return (
              <button
                type="button"
                key={p.id}
                disabled={assigned}
                onClick={() => setSelected(p)}
                className={`flex items-center justify-between px-3 py-2 text-sm font-mono border-b border-console-border text-left ${
                  assigned ? 'opacity-50 cursor-not-allowed' : 'hover:bg-console-bg'
                } ${selected?.id === p.id ? 'bg-console-bg' : ''}`}
              >
                <span className="flex-1">
                  <span className="text-console-text">{p.displayName}</span>{' '}
                  <span className="text-console-text-muted">{p.email}</span>
                </span>
                <Badge tone="default">{p.role}</Badge>
                {assigned && <span className="ml-2 text-console-text-muted text-xs">(already assigned)</span>}
              </button>
            );
          })}
        </div>
        {selected && (
          <>
            <label className="flex flex-col gap-1 font-mono text-sm">
              <span className="text-console-text-muted text-xs uppercase tracking-wide">Role</span>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value as 'crew' | 'lead')}
                className="bg-console-bg border border-console-border px-3 py-2 text-console-text focus:outline-none focus:border-console-accent font-mono"
              >
                <option value="crew">crew</option>
                <option value="lead">lead</option>
              </select>
            </label>
            {submitError && <div className="text-console-danger text-xs">{submitError}</div>}
            <div className="flex gap-2 justify-end">
              <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
              <Button type="submit" disabled={busy}>{busy ? 'Assigning...' : 'Assign'}</Button>
            </div>
          </>
        )}
      </form>
    </Modal>
  );
}
```

- [ ] **Step 3: Run — confirm 5 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- AssignCrewModal 2>&1 | tail -15
```

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/jobs/AssignCrewModal.tsx desktop/portal/src/console/components/jobs/__tests__/AssignCrewModal.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): AssignCrewModal with debounced search + role selector"
```

---

## Task 11: Frontend — `JobsListRoute` (TDD)

**Files:**
- Create: `desktop/portal/src/console/routes/JobsListRoute.tsx`
- Create: `desktop/portal/src/console/routes/__tests__/JobsListRoute.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobsListRoute } from '../JobsListRoute';
import { useJobsStore } from '../../stores/jobsStore';
import type { Job } from '../../api/jobsClient';

const j = (id: string, status: Job['status']): Job => ({
  id, foremanId: 'f-1', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('JobsListRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });

  it('renders 4 status section headers', async () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'in_progress'), j('c', 'complete'), j('d', 'cancelled')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/planned/i)).toBeInTheDocument();
    expect(screen.getByText(/in progress/i)).toBeInTheDocument();
    expect(screen.getByText(/^complete/i)).toBeInTheDocument();
    expect(screen.getByText(/cancelled/i)).toBeInTheDocument();
  });

  it('renders correct count next to each header', () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText('PLANNED (2)')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument();
    expect(screen.getByText('COMPLETE (0)')).toBeInTheDocument();
  });

  it('shows empty state when zero jobs total', () => {
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/no jobs yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create your first/i })).toBeInTheDocument();
  });

  it('renders [+ Create Job] button when jobs exist', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByRole('button', { name: /create job/i })).toBeInTheDocument();
  });

  it('renders stale strip when isStale is true', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    useJobsStore.getState().markStale(true);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/couldn't refresh/i)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Implement `JobsListRoute.tsx`**

```tsx
import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { CreateJobModal } from '../components/jobs/CreateJobModal';
import { JobCard } from '../components/jobs/JobCard';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import type { JobStatus, Job } from '../api/jobsClient';

const STATUSES: { status: JobStatus; label: string; defaultOpen: boolean }[] = [
  { status: 'planned',     label: 'PLANNED',     defaultOpen: true  },
  { status: 'in_progress', label: 'IN PROGRESS', defaultOpen: true  },
  { status: 'complete',    label: 'COMPLETE',    defaultOpen: false },
  { status: 'cancelled',   label: 'CANCELLED',   defaultOpen: false },
];

function StatusSection({ label, jobs, defaultOpen }: { label: string; jobs: Job[]; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-console-border mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-console-surface text-console-text-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({jobs.length})</span>
        <span>{open ? '▼' : '▶'}</span>
      </button>
      {open && (
        <div>
          {jobs.length === 0 && <div className="px-3 py-2 text-console-text-muted text-sm">—</div>}
          {jobs.map((j) => <JobCard key={j.id} job={j} />)}
        </div>
      )}
    </div>
  );
}

export function JobsListRoute() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const isStale = useJobsStore((s) => s.isStale);
  const [showCreate, setShowCreate] = useState(false);

  const byStatus = (st: JobStatus) => jobs.filter((j) => j.status === st);

  if (jobs.length === 0) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4">
        <div className="text-console-text-muted">No jobs yet.</div>
        <Button onClick={() => setShowCreate(true)}>Create your first job</Button>
        <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-console-text text-lg">Jobs</h1>
        <Button onClick={() => setShowCreate(true)}>+ Create Job</Button>
      </div>
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh — showing cached data
        </div>
      )}
      {STATUSES.map((s) => (
        <StatusSection key={s.status} label={s.label} jobs={byStatus(s.status)} defaultOpen={s.defaultOpen} />
      ))}
      <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
```

- [ ] **Step 3: Run — confirm 5 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobsListRoute 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/routes/JobsListRoute.tsx desktop/portal/src/console/routes/__tests__/JobsListRoute.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): JobsListRoute with collapsible status sections"
```

---

## Task 12: Frontend — `JobDetailRoute` (TDD)

**Files:**
- Create: `desktop/portal/src/console/routes/JobDetailRoute.tsx`
- Create: `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobDetailRoute } from '../JobDetailRoute';
import { useJobsStore } from '../../stores/jobsStore';

describe('JobDetailRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });

  function renderAt(path: string) {
    return render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/console/jobs/:id" element={<JobDetailRoute />} />
        </Routes>
      </MemoryRouter>
    );
  }

  it('renders the job title once detail is loaded', async () => {
    renderAt('/console/jobs/abc');
    // useJobsPolling hits MSW mock → store gets populated
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
  });

  it('shows StatusButtons for non-terminal status', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
  });

  it('shows [+ Assign crew] button', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /assign crew/i })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Implement `JobDetailRoute.tsx`**

```tsx
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { JobStatusBadge } from '../components/jobs/JobStatusBadge';
import { StatusButtons } from '../components/jobs/StatusButtons';
import { AssignCrewModal } from '../components/jobs/AssignCrewModal';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import { jobsClient } from '../api/jobsClient';
import { useToast } from '../hooks/useToast';

export function JobDetailRoute() {
  const { id } = useParams<{ id: string }>();
  useJobsPolling({ detail: id ?? '' });
  const job = useJobsStore((s) => s.detailJob);
  const crew = useJobsStore((s) => s.detailCrew);
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const setDetail = useJobsStore((s) => s.setDetail);
  const [showAssign, setShowAssign] = useState(false);
  const toast = useToast();

  if (!job || job.id !== id) {
    return <div className="text-console-text-muted">Loading...</div>;
  }

  async function onUnassign(profileId: string) {
    const result = await jobsClient.unassignCrew(job!.id, profileId);
    if (!result.ok) {
      toast.error(result.error || 'Failed to unassign');
      return;
    }
    setDetail(job!, crew.filter((c) => c.profileId !== profileId));
  }

  return (
    <div className="font-mono">
      <Link to="/console/jobs" className="text-console-accent text-sm">← back to jobs</Link>
      <div className="flex items-center gap-3 mt-2">
        <JobStatusBadge status={job.status} />
        <h1 className="text-console-text text-lg">{job.title}</h1>
      </div>
      <dl className="text-sm grid grid-cols-[12ch_1fr] gap-y-1 mt-4">
        <dt className="text-console-text-muted">id</dt>          <dd>#{job.id}</dd>
        <dt className="text-console-text-muted">scheduled</dt>   <dd>{job.scheduledAt ?? '—'}</dd>
        <dt className="text-console-text-muted">location</dt>    <dd>{job.location ?? '—'}</dd>
        <dt className="text-console-text-muted">created</dt>     <dd>{job.createdAt}</dd>
      </dl>
      <div className="mt-6">
        <StatusButtons jobId={job.id} status={job.status} onChanged={upsertJob} />
      </div>
      <div className="mt-8">
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-console-text-muted text-xs uppercase tracking-wide">Crew ({crew.length})</h2>
          <Button onClick={() => setShowAssign(true)}>+ Assign crew</Button>
        </div>
        {crew.length === 0 && <div className="text-console-text-muted text-sm">No crew assigned.</div>}
        {crew.map((c) => (
          <div key={c.profileId} className="flex items-center justify-between border-b border-console-border px-3 py-2 text-sm">
            <span>{c.profileId} <span className="text-console-text-muted">({c.roleOnJob})</span></span>
            <button onClick={() => onUnassign(c.profileId)} className="text-console-danger">[x]</button>
          </div>
        ))}
      </div>
      <AssignCrewModal
        open={showAssign}
        jobId={job.id}
        alreadyAssigned={crew.map((c) => c.profileId)}
        onClose={() => setShowAssign(false)}
        onAssigned={(a) => setDetail(job, [...crew, a])}
      />
    </div>
  );
}
```

- [ ] **Step 3: Run — confirm 3 pass**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobDetailRoute 2>&1 | tail -10
```

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/routes/JobDetailRoute.tsx desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): JobDetailRoute with status workflow + crew section"
```

---

## Task 13: Frontend — `ConsoleShell` nav update + wire routes

**Files:**
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`
- Modify: `desktop/portal/src/App.tsx`
- Modify: `desktop/portal/src/main.tsx` (mount `<ToastContainer />` once globally)

- [ ] **Step 1: Update `ConsoleShell.tsx` nav**

Find the `<nav>` block in `desktop/portal/src/console/ConsoleShell.tsx`. The current content is:

```tsx
<nav className="w-48 border-r border-console-border bg-console-surface p-4 text-sm text-console-text-muted">
  <div className="uppercase tracking-wide text-xs mb-2">Nav</div>
  <div className="text-console-text-muted/60">{'(routes coming soon)'}</div>
</nav>
```

Replace with:

```tsx
<nav className="w-48 border-r border-console-border bg-console-surface p-4 text-sm text-console-text-muted">
  <div className="uppercase tracking-wide text-xs mb-2">Nav</div>
  <NavLink
    to="/console/jobs"
    className={({ isActive }) =>
      `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
    }
  >
    Jobs
  </NavLink>
  <div className="block px-2 py-1 text-console-text-muted/60 cursor-not-allowed" title="Coming soon">
    Map
  </div>
</nav>
```

Add `NavLink` to the react-router-dom import at the top of the file:

```tsx
import { NavLink, useNavigate } from 'react-router-dom';
```

- [ ] **Step 2: Wire the new routes in `App.tsx`**

The current `App.tsx` has a single `/console` route. Replace it with three routes (login, register stay as-is; nested `/console/*` for the authenticated app).

Current relevant block:

```tsx
<Route
  path="/console"
  element={
    <RequireAuth>
      <ConsoleShell>
        <PlaceholderConsoleRoute />
      </ConsoleShell>
    </RequireAuth>
  }
/>
```

Replace with:

```tsx
<Route
  path="/console"
  element={
    <RequireAuth>
      <ConsoleShell><Outlet /></ConsoleShell>
    </RequireAuth>
  }
>
  <Route index element={<JobsListRoute />} />
  <Route path="jobs" element={<JobsListRoute />} />
  <Route path="jobs/:id" element={<JobDetailRoute />} />
</Route>
```

Add imports near the top of `App.tsx`:

```tsx
import { Outlet } from 'react-router-dom';
import { JobsListRoute } from './console/routes/JobsListRoute';
import { JobDetailRoute } from './console/routes/JobDetailRoute';
```

Remove the now-unused `PlaceholderConsoleRoute` import.

Modify `ConsoleShell.tsx` to accept `children` and place them in the main pane (it already does this; just verify the change to `<Outlet />` flows correctly).

- [ ] **Step 3: Mount `ToastContainer` globally in `main.tsx`**

Modify `desktop/portal/src/main.tsx`. Current:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './console/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
    <App />
    </BrowserRouter>
  </React.StrictMode>,
)
```

Add ToastContainer import + render it once at the root:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ToastContainer } from './console/components/ui/Toast'
import './console/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
      <ToastContainer />
    </BrowserRouter>
  </React.StrictMode>,
)
```

- [ ] **Step 4: Run the full frontend test suite — confirm no regressions**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run 2>&1 | tail -10
```

Expected: all previous tests still pass + the new Plan 3 tests pass. No new failures.

- [ ] **Step 5: Run frontend tsc check**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npx tsc --noEmit 2>&1 | grep -E "console/" | head -10 || echo "no console errors"
```

Expected: "no console errors". (Pre-existing legacy-file errors like Auth.tsx are fine.)

- [ ] **Step 6: Run frontend build**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 7: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/ConsoleShell.tsx desktop/portal/src/App.tsx desktop/portal/src/main.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): wire /console/jobs routes + nav + ToastContainer"
```

---

## Task 14: Manual browser walkthrough

No code changes. Requires `DATABASE_URL` set + migration 003 applied.

- [ ] **Step 1: Apply migration 003 (if not already applied)**

```bash
psql "$DATABASE_URL" -f /Users/fegensprenelon/smith-net/backend/migrations/003_jobs_expansion.sql
```

- [ ] **Step 2: Start backend in one terminal**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=verification-secret-at-least-32-chars-long-please-thanks DATABASE_URL="$DATABASE_URL" npm run dev
```

- [ ] **Step 3: Start frontend in another terminal**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run dev
```

- [ ] **Step 4: Walk the flow in browser**

1. Open http://localhost:5173/console — redirects to `/console/login` if not authenticated.
2. Log in as `admin@smithnet.local` / `admin123` (or your dev admin password).
3. Click `Jobs` in the left nav → land at `/console/jobs` → empty state with `[Create your first job]` button.
4. Click create → modal opens → fill title="Smoke test", location="HQ" → submit → land on `/console/jobs/<new-id>` detail page.
5. Detail shows status PLANNED badge + `[▶ Start]` and `[✕ Cancel]` buttons + `[+ Assign crew]`.
6. Click `Start` → status flips to IN PROGRESS, button set changes to `[✓ Complete]` + `[✕ Cancel]`.
7. Click `+ Assign crew` → modal opens → type "admin" → after 300ms see admin profile in results → click it → role dropdown appears → submit → modal closes, crew section shows the new assignment.
8. Click `[x]` on the assignment → list shrinks.
9. Click `Complete` → status flips to COMPLETE, status buttons disappear (terminal).
10. Go back to `/console/jobs` → COMPLETE section now shows `(1)` count → click header to expand → see the job.
11. Hide the tab for 30s, return → polling fires immediately, list refreshes.
12. Disable network in DevTools → wait 15s → top of list shows `[OFFLINE] Couldn't refresh — showing cached data`.
13. Re-enable network → next 15s tick clears the strip.
14. Log out → register a new account (Solo role by default) → log in → navigate to `/console/jobs` → tier-gate "Upgrade Required" card appears.

If all 14 steps pass, Plan 3 is done.

- [ ] **Step 5: Confirm clean working tree + print commit summary**

```bash
git -C /Users/fegensprenelon/smith-net status --short
git -C /Users/fegensprenelon/smith-net log --oneline 5acef6c..HEAD
```

Expected: no uncommitted Plan 3 files; ~13 new commits since the Plan 3 spec.

---

## Self-Review

**Spec coverage:**
- Backend `/api/profiles?q=` endpoint — Task 1
- Toast primitive + toastStore + useToast — Task 2
- `jobsClient` + MSW handlers — Task 3
- `profilesClient` + MSW handler — Task 4
- `jobsStore` — Task 5
- `useJobsPolling` — Task 6
- `JobStatusBadge` + `JobCard` — Task 7
- `StatusButtons` — Task 8
- `CreateJobModal` — Task 9
- `AssignCrewModal` — Task 10
- `JobsListRoute` — Task 11
- `JobDetailRoute` — Task 12
- `ConsoleShell` nav + `App.tsx` route wire + `ToastContainer` mount — Task 13
- Manual walkthrough — Task 14

Every spec section maps to a task. No gaps.

**Placeholder scan:** None. Every step has actual code or actual commands.

**Type consistency:**
- `Job`, `CrewAssignment`, `JobStatus` types defined in `jobsClient.ts` (Task 3) — consumed by jobsStore (Task 5), components (Tasks 7-12), routes (Tasks 11-12)
- `ProfileMatch`, `ProfileRole` defined in `profilesClient.ts` (Task 4) — consumed by `AssignCrewModal` (Task 10)
- `ToastTone`, `ToastEntry` defined in `toastStore.ts` (Task 2) — consumed by Toast component + useToast hook
- All MSW handlers (Task 3 + Task 4) return shapes that match the client types

**Pre-existing concerns (out of scope):**
- 4 pre-existing backend test failures (`api-auth-integration.test.ts`, `auth-middleware.test.ts`) — Plan 3 must not regress them; not in scope to fix
- 16 backend integration tests still skipping until `DATABASE_URL` is set — Plan 3 does NOT change this

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-12-plan-3-job-board-frontend.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks
2. **Inline Execution** — execute tasks in this session with checkpoints

**Which approach?**
