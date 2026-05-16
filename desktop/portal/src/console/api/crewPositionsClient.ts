// desktop/portal/src/console/api/crewPositionsClient.ts
//
// Fetch wrapper for Phase 3.5 Slice 1 GET /api/crew/positions.
//
// Backend response shape (verified against backend/src/presenceLocationRoutes.ts
// and backend/src/crewPositionService.ts listOpenPositions):
//   GET /api/crew/positions -> raw array of crew_positions rows (snake_case, no wrapper)
//   Fields: user_id, latitude, longitude, accuracy_m, recorded_at, source, battery_pct
//   NOTE: there is no displayName field — crew_positions stores coordinates only.

export interface CrewPosition {
  user_id: string;
  latitude: number;
  longitude: number;
  accuracy_m: number | null;
  recorded_at: string;
  source: string;
  battery_pct: number | null;
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
    // Backend returns the array directly, not wrapped in { positions: [...] }
    const positions = (await res.json()) as CrewPosition[];
    return { ok: true, positions };
  },
};
