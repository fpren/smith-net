// desktop/portal/src/console/api/adminHealthClient.ts
//
// Phase 4 frontend polish: GET /api/admin/health (admin-only).
//
// Backend shape (Phase 4 Slice 1):
//   { workers: [{ workerId, kinds[], lastBeatAt, ageSec }],
//     queue: {
//       byKindState: [{ kind, state, count }],
//       oldestQueued: { kind, scheduledAt, ageSec } | null,
//       oldestRunning: { kind, lockedAt, ageSec } | null,
//     }
//   }

import { httpCall } from './httpCall';

export interface WorkerHeartbeat {
  workerId: string;
  kinds: string[];
  lastBeatAt: string;
  ageSec: number;
}

export interface QueueByKindState {
  kind: string;
  state: string;
  count: number;
}

export interface QueueOldest {
  kind: string;
  scheduledAt?: string;
  lockedAt?: string;
  ageSec: number;
}

export interface HealthData {
  workers: WorkerHeartbeat[];
  queue: {
    byKindState: QueueByKindState[];
    oldestQueued: QueueOldest | null;
    oldestRunning: QueueOldest | null;
  };
}

export type HealthResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

export const adminHealthClient = {
  get: async (): Promise<HealthResult<HealthData>> => {
    const r = await httpCall<HealthData>('/api/admin/health');
    if (!r.ok) {
      return { ok: false, status: r.status, error: r.error };
    }
    return { ok: true, ...r.data };
  },
};
