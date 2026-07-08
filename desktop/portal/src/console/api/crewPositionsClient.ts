// desktop/portal/src/console/api/crewPositionsClient.ts
//
// Fetch wrapper for Phase 3.5 GET /api/crew/positions (foreman+ only).
//
// Backend response shape (camelCase, wrapped):
//   { positions: [{ userId, displayName, latitude, longitude,
//                   accuracyM, recordedAt, source, batteryPct }, ...] }

import { httpCall } from './httpCall';

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
    const r = await httpCall<ListResp>('/api/crew/positions');
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error, code: r.body?.code };
    }
    return { ok: true, ...r.data };
  },
};
