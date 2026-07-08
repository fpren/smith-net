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
import { httpCall } from './httpCall';

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
    const r = await httpCall<{}>('/api/presence/location', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        lat: input.lat,
        lng: input.lng,
        accuracy_m: input.accuracyM,
        battery_pct: input.batteryPct,
      }),
    });
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    return { ok: true };
  },

  getCurrentShift: async (): Promise<PresenceResult<{ shiftId: string | null; startedAt: string | null; entryType: string | null; jobTitle: string | null; taskTitle: string | null }>> => {
    const r = await httpCall<{ shift?: any }>('/api/shifts/current');
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    const data = r.data;
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
    const r = await httpCall<{ jobs?: any[] }>('/api/shifts/jobs');
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    return { ok: true, jobs: r.data.jobs ?? [] };
  },

  getJobTasks: async (
    jobId: string,
  ): Promise<PresenceResult<{ tasks: Array<{ id: string; title: string; status: string }> }>> => {
    const r = await httpCall<{ tasks?: any[] }>(`/api/shifts/jobs/${encodeURIComponent(jobId)}/tasks`);
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    return { ok: true, tasks: r.data.tasks ?? [] };
  },

  getTodayShifts: async (
    sinceMs: number,
  ): Promise<PresenceResult<{ shifts: TimeEntryRow[] }>> => {
    const r = await httpCall<{ shifts?: Array<Partial<TimeEntryRow>> }>(`/api/shifts/today?since=${sinceMs}`);
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    const data = r.data;
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
