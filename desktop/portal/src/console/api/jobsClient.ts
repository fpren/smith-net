// desktop/portal/src/console/api/jobsClient.ts
import { mutate } from '../offline/outbox';
import { useAuthStore } from '../auth/authStore';
import { httpCall } from './httpCall';

export type JobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled';

export type JobStage =
  | 'lead'
  | 'proposal'
  | 'approved'
  | 'in_progress'
  | 'review'
  | 'invoice'
  | 'closed';

export interface Job {
  id: string;
  foremanId: string;
  clientId: string | null;
  client: { id: string; name: string } | null;
  engagementId: string | null;
  title: string;
  description: string | null;
  status: JobStatus;
  stage: JobStage;
  scheduledAt: string | null;     // ISO 8601 from server
  location: string | null;
  latitude: number | null;        // Plan 4: populated async after Nominatim geocode
  longitude: number | null;
  geocodedAt: string | null;      // ISO 8601 when geocode landed
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

/** W6: result of an outbox-routed mutation -- either a normal JobsResult, or a
 *  "queued" marker when there was no network and the op was stored for replay. */
export type OutboxResult<T> = JobsResult<T> | { ok: true; queued: true };

interface JsonInit { method?: string; body?: unknown }

// W6: route a create/update through the offline outbox (network-first, queue on
// no-connection) and map its result back to the JobsResult shape.
async function outboxMutate<T>(path: string, method: string, body: unknown, label: string): Promise<OutboxResult<T>> {
  const profileId = useAuthStore.getState().user?.id ?? 'anon';
  const r = await mutate<T>({ profileId, method, path, body, label });
  if (r.queued) return { ok: true, queued: true };
  if (r.ok) return { ok: true, ...((r.data ?? {}) as T) } as JobsResult<T>;
  return { ok: false, status: r.status ?? 0, error: r.error ?? 'Request failed' };
}

async function call<T>(path: string, init: JsonInit = {}): Promise<JobsResult<T>> {
  const r = await httpCall<T>(path, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  if (r.ok) {
    return { ok: true, ...((r.data ?? {}) as T) } as JobsResult<T>;
  }

  const errBody = r.body ?? {};
  return {
    ok: false,
    status: r.status,
    error: r.error,
    details: errBody.details,
    code: errBody.code,
    from: errBody.from,
    to: errBody.to,
  };
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
  clientId?: string | null;
}

export const jobsClient = {
  list: () => call<ListResp>('/api/jobs'),
  getById: (id: string) => call<OneResp>(`/api/jobs/${encodeURIComponent(id)}`),
  create: (input: CreateJobInput) => outboxMutate<MutateResp>('/api/jobs', 'POST', input, 'job:create'),
  update: (id: string, patch: UpdateJobInput) =>
    outboxMutate<MutateResp>(`/api/jobs/${encodeURIComponent(id)}`, 'PATCH', patch, 'job:update'),
  changeStatus: (id: string, status: JobStatus) =>
    call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }),
  changeStage: (id: string, stage: JobStage) =>
    call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}/stage`, { method: 'PATCH', body: { stage } }),
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
