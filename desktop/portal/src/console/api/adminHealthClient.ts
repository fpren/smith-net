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
    const res = await fetch('/api/admin/health', { credentials: 'include' });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      return { ok: false, status: res.status, error: body.error || 'Failed' };
    }
    const data = (await res.json()) as HealthData;
    return { ok: true, ...data };
  },
};
