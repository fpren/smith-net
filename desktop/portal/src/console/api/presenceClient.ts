// desktop/portal/src/console/api/presenceClient.ts
//
// Fetch wrappers for Phase 3.5 shift lifecycle and location routes.
//
// Backend wire format (camelCase, wrapped in named keys):
//   POST /api/shifts/start  -> { shift: { id, userId, startedAt, endedAt, source } }
//   POST /api/shifts/end    -> { shift: {...} }  or  404 { error }
//   GET  /api/shifts/current -> { shift: {...} | null }
//   POST /api/presence/location -> { position: {...} }

export type PresenceResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

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
};
