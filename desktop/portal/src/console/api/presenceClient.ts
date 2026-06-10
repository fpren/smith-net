// desktop/portal/src/console/api/presenceClient.ts
//
// Fetch wrappers for Phase 3.5 shift lifecycle and location routes.
//
// Backend wire format (camelCase, wrapped in named keys):
//   POST /api/shifts/start  -> { shift: { id, userId, startedAt, endedAt, source } }
//   POST /api/shifts/end    -> { shift: {...} }  or  404 { error }
//   GET  /api/shifts/current -> { shift: {...} | null }
//   POST /api/presence/location -> { position: {...} }

import { mutate } from '../offline/outbox';
import { useAuthStore } from '../auth/authStore';

export type PresenceResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

/** W6: clock action either completed, queued offline, or failed. */
export type PresenceOutboxResult<T> = PresenceResult<T> | { ok: true; queued: true };

export interface TimeEntryRow {
  id: string;
  startedAt: string | null;
  endedAt: string | null;
  source: string;
  entryType: string;
  jobId: string | null;
  jobTitle: string | null;
  taskId: string | null;
  taskTitle: string | null;
  clockOutReason: string | null;
}

export interface PostLocationInput {
  lat: number;
  lng: number;
  accuracyM?: number;
  batteryPct?: number;
}

async function parseError(res: Response): Promise<{ error: string; code?: string }> {
  try {
    const body = await res.json();
    return { error: body.error || res.statusText, code: body.code };
  } catch {
    return { error: res.statusText };
  }
}

export const presenceClient = {
  startShift: async (
    source: string,
    opts: { entryType?: string; jobId?: string; jobTitle?: string; taskId?: string; taskTitle?: string } = {},
  ): Promise<PresenceOutboxResult<{ shiftId: string }>> => {
    const profileId = useAuthStore.getState().user?.id ?? 'anon';
    const r = await mutate<{ shift: { id: string } }>({
      profileId, method: 'POST', path: '/api/shifts/start', body: { source, ...opts }, label: 'shift:start',
    });
    if (r.queued) return { ok: true, queued: true };
    if (r.ok) return { ok: true, shiftId: r.data!.shift.id };
    return { ok: false, status: r.status ?? 0, error: r.error ?? 'Clock in failed' };
  },

  endShift: async (reason?: string): Promise<PresenceOutboxResult<{}>> => {
    const profileId = useAuthStore.getState().user?.id ?? 'anon';
    const r = await mutate<{}>({
      profileId, method: 'POST', path: '/api/shifts/end', body: reason ? { reason } : {}, label: 'shift:end',
    });
    if (r.queued) return { ok: true, queued: true };
    // 404 = no open shift; treat as success (already clocked out).
    if (r.ok || r.status === 404) return { ok: true };
    return { ok: false, status: r.status ?? 0, error: r.error ?? 'Clock out failed' };
  },

  postLocation: async (input: PostLocationInput): Promise<PresenceResult<{}>> => {
    const res = await fetch('/api/presence/location', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        lat: input.lat,
        lng: input.lng,
        accuracy_m: input.accuracyM,
        battery_pct: input.batteryPct,
      }),
    });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    return { ok: true };
  },

  getCurrentShift: async (): Promise<PresenceResult<{ shiftId: string | null; startedAt: string | null; entryType: string | null; jobTitle: string | null; taskTitle: string | null }>> => {
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
      taskTitle: data.shift?.taskTitle ?? null,
    };
  },

  getMyJobs: async (): Promise<PresenceResult<{ jobs: Array<{ id: string; title: string; status: string }> }>> => {
    const res = await fetch('/api/shifts/jobs', { credentials: 'include' });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    const data = await res.json();
    return { ok: true, jobs: data.jobs ?? [] };
  },

  getJobTasks: async (
    jobId: string,
  ): Promise<PresenceResult<{ tasks: Array<{ id: string; title: string; status: string }> }>> => {
    const res = await fetch(`/api/shifts/jobs/${encodeURIComponent(jobId)}/tasks`, { credentials: 'include' });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    const data = await res.json();
    return { ok: true, tasks: data.tasks ?? [] };
  },

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
        taskId: s.taskId ?? null,
        taskTitle: s.taskTitle ?? null,
        clockOutReason: s.clockOutReason ?? null,
      })),
    };
  },
};
