// desktop/portal/src/console/api/presenceClient.ts
//
// Fetch wrappers for Phase 3.5 Slice 1 shift lifecycle and location routes.
//
// Backend response shapes (verified against backend/src/shiftsRoutes.ts and
// backend/src/presenceLocationRoutes.ts):
//   POST /api/shifts/start  -> raw Shift row (snake_case, no wrapper)
//   POST /api/shifts/end    -> raw Shift row or 404 { error }
//   GET  /api/shifts/current -> raw Shift row | null (no wrapper)
//   POST /api/presence/location -> raw CrewPosition row (snake_case, no wrapper)

export type PresenceResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

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
  startShift: async (source: string): Promise<PresenceResult<{ shiftId: string }>> => {
    const res = await fetch('/api/shifts/start', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ source }),
    });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    // Backend returns raw row: { id, user_id, started_at, ended_at, source }
    const data = await res.json();
    return { ok: true, shiftId: data.id };
  },

  endShift: async (): Promise<PresenceResult<{}>> => {
    const res = await fetch('/api/shifts/end', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    });
    // 404 means no open shift = already ended; treat as success
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
      // Backend expects snake_case keys per presenceLocationRoutes.ts
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

  getCurrentShift: async (): Promise<PresenceResult<{ shiftId: string | null }>> => {
    const res = await fetch('/api/shifts/current', { credentials: 'include' });
    if (!res.ok) {
      const e = await parseError(res);
      return { ok: false, status: res.status, ...e };
    }
    // Backend returns null or raw shift row ({ id, user_id, ... })
    const data = await res.json();
    return { ok: true, shiftId: data?.id ?? null };
  },
};
