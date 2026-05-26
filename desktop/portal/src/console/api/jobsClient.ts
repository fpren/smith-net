// desktop/portal/src/console/api/jobsClient.ts

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

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<JobsResult<T>> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    credentials: 'include',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  if (res.status === 204) {
    return { ok: true } as JobsResult<T>;
  }

  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return {
      ok: false,
      status: res.status,
      error: errBody.error || 'Request failed',
      details: errBody.details,
      code: errBody.code,
      from: errBody.from,
      to: errBody.to,
    };
  }

  const data = (await res.json()) as T;
  return { ok: true, ...data } as JobsResult<T>;
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
  create: (input: CreateJobInput) => call<MutateResp>('/api/jobs', { method: 'POST', body: input }),
  update: (id: string, patch: UpdateJobInput) =>
    call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
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
