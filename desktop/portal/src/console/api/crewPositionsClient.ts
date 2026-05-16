// desktop/portal/src/console/api/crewPositionsClient.ts
//
// Fetch wrapper for Phase 3.5 GET /api/crew/positions (foreman+ only).
//
// Backend response shape (camelCase, wrapped):
//   { positions: [{ userId, displayName, latitude, longitude,
//                   accuracyM, recordedAt, source, batteryPct }, ...] }

export interface CrewPosition {
  userId: string;
  displayName: string;
  latitude: number;
  longitude: number;
  accuracyM: number | null;
  recordedAt: string;
  source: string;
  batteryPct: number | null;
}

export type CrewPositionsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

interface ListResp { positions: CrewPosition[] }

export const crewPositionsClient = {
  list: async (): Promise<CrewPositionsResult<ListResp>> => {
    const res = await fetch('/api/crew/positions', { credentials: 'include' });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      return { ok: false, status: res.status, error: body.error || 'Failed', code: body.code };
    }
    const data = (await res.json()) as ListResp;
    return { ok: true, ...data };
  },
};
